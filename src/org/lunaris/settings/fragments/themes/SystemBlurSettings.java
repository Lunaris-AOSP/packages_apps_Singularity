/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.themes;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.internal.graphics.drawable.AxBlurCustomization;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.preferences.CustomSeekBarPreference;
import org.lunaris.settings.preferences.colorpicker.ColorPickerPreference;

import java.util.Locale;

@SearchIndexable
public class SystemBlurSettings extends SettingsPreferenceFragment {
    private static final String KEY_SYSTEM_BLUR_RADIUS = "system_blur_radius";
    private static final String STATE_LIGHT_BACKDROP = "system_blur_light_backdrop";
    private static final float MAX_SYSTEM_BLUR_RADIUS_DP = 34f;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mCustomizationListener = this::refreshPreview;
    private final ContentObserver mExtraSettingsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            refreshPreview();
        }
    };

    private AxBlurCustomization mCustomization;
    private SystemBlurPreviewView mPreviewView;
    private TextView mHeaderSummary;
    private TextView mValuesSummary;
    private Button mBackdropToggle;
    private AlertDialog mValuesDialog;
    private boolean mLightBackdrop;
    private boolean mObserving;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLightBackdrop = savedInstanceState.getBoolean(STATE_LIGHT_BACKDROP, false);
        }
        mCustomization = AxBlurCustomization.getInstance(requireContext());
        addPreferencesFromResource(R.xml.system_blur_settings);
        ((CustomSeekBarPreference) findPreference(AxBlurCustomization.RIM_CORNER_TAPER_POWER))
                .setMin(1);
        findPreference(AxBlurCustomization.TINT_MODE).setOnPreferenceChangeListener(
                (preference, value) -> {
                    updateCustomColor(Integer.parseInt((String) value));
                    return true;
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        final View prefsView = super.onCreateView(inflater, container, savedInstanceState);
        if (prefsView == null) return null;

        final Context context = requireContext();
        final boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final View previewContainer = inflater.inflate(R.layout.system_blur_preview, root, false);
        if (landscape) {
            final LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.44f);
            previewParams.gravity = Gravity.CENTER_VERTICAL;
            root.addView(previewContainer, previewParams);
            root.addView(prefsView, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 0.56f));
        } else {
            root.addView(previewContainer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(prefsView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        mPreviewView = previewContainer.findViewById(R.id.system_blur_preview_view);
        mHeaderSummary = previewContainer.findViewById(R.id.system_blur_preview_header_summary);
        mValuesSummary = previewContainer.findViewById(R.id.system_blur_preview_values);
        mBackdropToggle = previewContainer.findViewById(R.id.system_blur_preview_backdrop_toggle);
        final View detailsButton = previewContainer.findViewById(R.id.system_blur_preview_details);

        mPreviewView.setLightBackdrop(mLightBackdrop);
        updateBackdropToggleLabel();

        mBackdropToggle.setOnClickListener(v -> {
            mLightBackdrop = !mLightBackdrop;
            if (mPreviewView != null) {
                mPreviewView.setLightBackdrop(mLightBackdrop);
            }
            updateBackdropToggleLabel();
        });
        mPreviewView.setOnClickListener(v -> showValuesDialog());
        detailsButton.setOnClickListener(v -> showValuesDialog());

        refreshPreview();
        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_LIGHT_BACKDROP, mLightBackdrop);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mObserving) {
            mObserving = true;
            mCustomization.addListener(mCustomizationListener);
            final ContentResolver resolver = requireContext().getContentResolver();
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.DISABLE_WINDOW_BLURS),
                    false, mExtraSettingsObserver);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(KEY_SYSTEM_BLUR_RADIUS),
                    false, mExtraSettingsObserver);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.DIALOG_BLUR_ENABLED),
                    false, mExtraSettingsObserver);
        }
        refreshPreview();
    }

    @Override
    public void onStop() {
        if (mObserving) {
            mObserving = false;
            mCustomization.removeListener(mCustomizationListener);
            requireContext().getContentResolver().unregisterContentObserver(mExtraSettingsObserver);
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCustomColor(Settings.Global.getInt(requireContext().getContentResolver(),
                AxBlurCustomization.TINT_MODE, AxBlurCustomization.TINT_ACCENT));
        refreshPreview();
    }

    @Override
    public void onDestroyView() {
        if (mValuesDialog != null) {
            mValuesDialog.dismiss();
            mValuesDialog = null;
        }
        mPreviewView = null;
        mHeaderSummary = null;
        mValuesSummary = null;
        mBackdropToggle = null;
        super.onDestroyView();
    }

    private void updateCustomColor(int mode) {
        final Preference pref = findPreference(AxBlurCustomization.TINT_COLOR);
        if (pref != null) {
            pref.setVisible(mode == AxBlurCustomization.TINT_CUSTOM);
        }
    }

    private void updateBackdropToggleLabel() {
        if (mBackdropToggle == null) return;
        mBackdropToggle.setText(mLightBackdrop
                ? R.string.system_blur_preview_backdrop_light
                : R.string.system_blur_preview_backdrop_dark);
    }

    private void refreshPreview() {
        if (!isAdded() || mPreviewView == null || mHeaderSummary == null || mValuesSummary == null) {
            return;
        }
        final Context context = requireContext();
        final AxBlurCustomization.State state = mCustomization.getState();
        final boolean blurDisabled = isGlobalWindowBlurDisabled(context);
        final float defaultRadiusPx = readDefaultSystemBlurRadiusPx(context);
        final float effectiveRadiusPx = blurDisabled
                ? 0f : state.resolveRadius(context, defaultRadiusPx);

        mPreviewView.setPreviewState(state, effectiveRadiusPx);
        mHeaderSummary.setText(buildHeaderSummary(context, state, effectiveRadiusPx, blurDisabled));
        mValuesSummary.setText(buildRimValuesSummary(state));
    }

    private boolean isGlobalWindowBlurDisabled(Context context) {
        final int defaultDisabled =
                SystemProperties.getBoolean("ro.custom.blur.enable", true) ? 0 : 1;
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DISABLE_WINDOW_BLURS, defaultDisabled) != 0;
    }

    private float readDefaultSystemBlurRadiusPx(Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        final float maxRadius = MAX_SYSTEM_BLUR_RADIUS_DP * density;
        final float radius = Settings.Secure.getFloat(context.getContentResolver(),
                KEY_SYSTEM_BLUR_RADIUS, maxRadius);
        return Float.isFinite(radius) ? Math.max(0f, Math.min(radius, maxRadius)) : maxRadius;
    }

    private String buildHeaderSummary(Context context, AxBlurCustomization.State state,
            float effectiveRadiusPx, boolean blurDisabled) {
        final float density = context.getResources().getDisplayMetrics().density;
        final String blurText;
        if (blurDisabled) {
            blurText = getString(R.string.system_blur_preview_disabled_label);
        } else {
            final float dp = effectiveRadiusPx / density;
            blurText = state.radiusEnabled
                    ? formatDecimal(dp) + " dp"
                    : Math.round(dp) + " dp";
        }
        final String tintText = formatTintSummary(context, state);
        return getString(R.string.system_blur_radius_title) + " " + blurText
                + " · " + getString(R.string.system_blur_tint_category) + " " + tintText;
    }

    private String buildRimValuesSummary(AxBlurCustomization.State state) {
        final AxBlurCustomization.Rim rim = state.rim;
        if (!rim.enabled) {
            return getString(R.string.system_blur_rim_category) + ": "
                    + getString(R.string.system_blur_preview_state_off);
        }
        return formatColorHex(rim.color)
                + " · Str " + Math.round(rim.strength * 100f) + "%"
                + " · Top " + Math.round(rim.topAlpha * 100f) + "%"
                + " · Btm " + Math.round(rim.bottomAlpha * 100f) + "%"
                + " · Side " + Math.round(rim.sideAlpha * 100f) + "%\n"
                + "Edge " + formatDecimal(rim.edgeWidthDp) + "dp"
                + " · Glow " + formatDecimal(rim.glowDepthDp) + "dp ("
                + Math.round(rim.glowAlpha * 100f) + "%)"
                + " · Cnr " + Math.round(rim.cornerWidthScale * 100f) + "/"
                + Math.round(rim.cornerAlphaScale * 100f) + "% ("
                + formatDecimal(rim.cornerTaperPower) + ")";
    }

    private String buildDetailedValuesText(Context context) {
        final ContentResolver resolver = context.getContentResolver();
        final AxBlurCustomization.State state = mCustomization.getState();
        final boolean dialogBlur = Settings.System.getInt(
                resolver, Settings.System.DIALOG_BLUR_ENABLED, 1) != 0;
        final boolean blurDisabled = isGlobalWindowBlurDisabled(context);
        final float density = context.getResources().getDisplayMetrics().density;
        final float defaultRadiusDp = readDefaultSystemBlurRadiusPx(context) / density;
        final String on = getString(R.string.system_blur_preview_state_on);
        final String off = getString(R.string.system_blur_preview_state_off);

        final StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.dialog_blur_title)).append(": ")
                .append(dialogBlur ? on : off).append('\n');
        sb.append(getString(R.string.system_blur_radius_category)).append(": ");
        if (blurDisabled) {
            sb.append(getString(R.string.system_blur_preview_disabled_label));
        } else if (state.radiusEnabled) {
            sb.append(formatDecimal(state.getRadiusDp())).append(" dp");
        } else {
            sb.append(getString(R.string.system_blur_preview_default_label))
                    .append(" (").append(formatDecimal(defaultRadiusDp)).append(" dp)");
        }
        sb.append('\n');
        sb.append(getString(R.string.system_blur_tint_category)).append(": ")
                .append(formatTintSummary(context, state)).append("\n\n");

        final AxBlurCustomization.Rim rim = state.rim;
        sb.append(getString(R.string.system_blur_rim_category)).append(": ")
                .append(rim.enabled ? on : off).append('\n');
        if (rim.enabled) {
            sb.append(getString(R.string.system_blur_rim_color_title)).append(": ")
                    .append(formatColorHex(rim.color)).append('\n')
                    .append(getString(R.string.system_blur_rim_strength_title)).append(": ")
                    .append(Math.round(rim.strength * 100f)).append("%\n")
                    .append(getString(R.string.system_blur_rim_top_title)).append(": ")
                    .append(Math.round(rim.topAlpha * 100f)).append("%\n")
                    .append(getString(R.string.system_blur_rim_bottom_title)).append(": ")
                    .append(Math.round(rim.bottomAlpha * 100f)).append("%\n")
                    .append(getString(R.string.system_blur_rim_side_title)).append(": ")
                    .append(Math.round(rim.sideAlpha * 100f)).append("%\n")
                    .append(getString(R.string.system_blur_rim_width_title)).append(": ")
                    .append(formatDecimal(rim.edgeWidthDp)).append(" dp\n")
                    .append(getString(R.string.system_blur_rim_depth_title)).append(": ")
                    .append(formatDecimal(rim.glowDepthDp)).append(" dp\n")
                    .append(getString(R.string.system_blur_rim_glow_title)).append(": ")
                    .append(Math.round(rim.glowAlpha * 100f)).append("%\n")
                    .append(getString(R.string.system_blur_rim_corner_width_title)).append(": ")
                    .append(Math.round(rim.cornerWidthScale * 100f)).append("%\n")
                    .append(getString(R.string.system_blur_rim_corner_brightness_title))
                    .append(": ").append(Math.round(rim.cornerAlphaScale * 100f)).append("%\n")
                    .append(getString(R.string.system_blur_rim_taper_title)).append(": ")
                    .append(formatDecimal(rim.cornerTaperPower));
        }
        return sb.toString();
    }

    private String formatTintSummary(Context context, AxBlurCustomization.State state) {
        if (!state.tintEnabled) {
            return getString(R.string.system_blur_preview_default_label);
        }
        final String[] modes = context.getResources().getStringArray(R.array.system_blur_tint_modes);
        final int mode = state.getTintMode();
        final String modeLabel = (mode >= 0 && mode < modes.length) ? modes[mode] : "";
        final String colorPart = mode == AxBlurCustomization.TINT_CUSTOM
                ? String.format(Locale.US, "#%06X", state.getTintColor() & 0x00FFFFFF)
                : modeLabel;
        return colorPart + " (" + state.getTintAlphaPercent() + "%)";
    }

    private void showValuesDialog() {
        if (!isAdded()) return;
        final Context context = requireContext();
        final String details = buildDetailedValuesText(context);
        if (mValuesDialog != null) {
            mValuesDialog.dismiss();
        }
        mValuesDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.system_blur_preview_dialog_title)
                .setMessage(details)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.system_blur_preview_copy, (dialog, which) -> {
                    final ClipboardManager clipboard =
                            context.getSystemService(ClipboardManager.class);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("System blur", details));
                        Toast.makeText(context, R.string.system_blur_preview_copied,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.system_blur_preview_reset_rim, (dialog, which) ->
                        resetRimDefaults())
                .show();
    }

    private void resetRimDefaults() {
        if (!isAdded()) return;
        final Context context = requireContext();
        final ContentResolver resolver = context.getContentResolver();

        Settings.Global.putInt(resolver, AxBlurCustomization.RIM_COLOR,
                AxBlurCustomization.DEFAULT_RIM_COLOR);
        final ColorPickerPreference colorPref = findPreference(AxBlurCustomization.RIM_COLOR);
        if (colorPref != null) {
            colorPref.onColorChanged(AxBlurCustomization.DEFAULT_RIM_COLOR);
        }

        resetSeekBar(resolver, AxBlurCustomization.RIM_STRENGTH,
                AxBlurCustomization.DEFAULT_RIM_STRENGTH);
        resetSeekBar(resolver, AxBlurCustomization.RIM_TOP_ALPHA,
                AxBlurCustomization.DEFAULT_RIM_TOP_ALPHA);
        resetSeekBar(resolver, AxBlurCustomization.RIM_BOTTOM_ALPHA,
                AxBlurCustomization.DEFAULT_RIM_BOTTOM_ALPHA);
        resetSeekBar(resolver, AxBlurCustomization.RIM_SIDE_ALPHA,
                AxBlurCustomization.DEFAULT_RIM_SIDE_ALPHA);
        resetSeekBar(resolver, AxBlurCustomization.RIM_EDGE_WIDTH,
                AxBlurCustomization.DEFAULT_RIM_EDGE_WIDTH_TENTHS);
        resetSeekBar(resolver, AxBlurCustomization.RIM_GLOW_DEPTH,
                AxBlurCustomization.DEFAULT_RIM_GLOW_DEPTH_TENTHS);
        resetSeekBar(resolver, AxBlurCustomization.RIM_GLOW_ALPHA,
                AxBlurCustomization.DEFAULT_RIM_GLOW_ALPHA);
        resetSeekBar(resolver, AxBlurCustomization.RIM_CORNER_WIDTH_SCALE,
                AxBlurCustomization.DEFAULT_RIM_CORNER_WIDTH_SCALE);
        resetSeekBar(resolver, AxBlurCustomization.RIM_CORNER_ALPHA_SCALE,
                AxBlurCustomization.DEFAULT_RIM_CORNER_ALPHA_SCALE);
        resetSeekBar(resolver, AxBlurCustomization.RIM_CORNER_TAPER_POWER,
                AxBlurCustomization.DEFAULT_RIM_CORNER_TAPER_POWER_TENTHS);

        refreshPreview();
        Toast.makeText(context, R.string.system_blur_preview_rim_reset_done,
                Toast.LENGTH_SHORT).show();
    }

    private void resetSeekBar(ContentResolver resolver, String key, int value) {
        Settings.Global.putInt(resolver, key, value);
        final CustomSeekBarPreference pref = findPreference(key);
        if (pref != null) {
            pref.setValue(value, true);
            pref.setValue(value);
        }
    }

    private static String formatDecimal(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String formatColorHex(int color) {
        if (Color.alpha(color) == 0xFF) {
            return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
        }
        return String.format(Locale.US, "#%08X", color);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.system_blur_settings);
}
