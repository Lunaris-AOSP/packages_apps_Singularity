/*
 * Copyright (C) 2016-2026 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lunaris.settings.fragments.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.SystemProperties;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.fragments.quicksettings.LayoutSettings;
import org.lunaris.settings.fragments.quicksettings.QsHeaderImageSettings;
import org.lunaris.settings.preferences.CustomSeekBarPreference;
import org.lunaris.settings.preferences.SystemSettingSwitchPreference;
import org.lunaris.settings.preferences.SystemSettingListPreference;
import org.lunaris.settings.preferences.SecureSettingListPreference;
import org.lunaris.settings.preferences.SecureSettingSwitchPreference;
import org.lunaris.settings.preferences.SystemSettingSeekBarPreference;
import org.lunaris.settings.utils.DeviceUtils;
import org.lunaris.settings.utils.SystemUtils;

import lineageos.providers.LineageSettings;

import com.android.internal.util.lunaris.VibrationUtils;

import java.util.List;
import java.util.ArrayList;

@SearchIndexable
public class QuickSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "QuickSettings";

    private static final String QS_BRIGHTNESS_CATEGORY = "qs_brightness_slider_category";
    private static final String QS_LAYOUT_CATEGORY = "qs_layout_category";
    private static final String KEY_SHOW_BRIGHTNESS_SLIDER = "qs_show_brightness_slider";
    private static final String KEY_BRIGHTNESS_SLIDER_POSITION = "qs_brightness_slider_position";
    private static final String KEY_BRIGHTNESS_SLIDER_HAPTIC = "qs_brightness_slider_haptic";
    private static final String KEY_SHOW_AUTO_BRIGHTNESS = "qs_show_auto_brightness";
    private static final String KEY_QS_TILE_HAPTIC = "qs_tile_haptic";
    private static final String KEY_QS_COMPACT_PLAYER = "qs_compact_media_player_mode";
    private static final String KEY_SINGLE_QS_TONE = "single_qs_tone_enabled";
    private static final String KEY_DUAL_TARGET_TILE_STYLE = "dual_target_tile_style";
    private static final String KEY_QS_TILE_ALTERNATE_COLOR = "qs_tile_alternate_color";
    private static final String KEY_QS_TILE_STYLE_MINIMAL = "qs_tile_style_minimal";
    private static final String KEY_QS_TILE_STYLE_MINIMAL_INVERT = "qs_tile_style_minimal_invert";
    private static final String KEY_QS_USE_MODIFIED_TILE_SPACING = "qs_use_modified_tile_spacing";
    private static final String KEY_QS_TILE_SHAPE = "qs_tile_shape";
    private static final String KEY_BRIGHTNESS_SLIDER_STYLE = "qs_brightness_slider_style";
    private static final String KEY_BRIGHTNESS_SLIDER_SHAPE = "qs_brightness_slider_shape";
    private static final String KEY_QS_PANEL_STYLE = "qs_panel_style";
    private static final String KEY_QS_TILE_ICON_SHAPE = "qs_tile_icon_shape";
    private static final String KEY_QS_TILE_LABEL_HIDE = "qs_tile_label_hide";
    private static final String KEY_QS_SHOW_MEDIA_PLAYER = "qs_show_media_player";
    private static final String KEY_QS_WIDGET_PANEL = "qs_widget_panel";
    private static final String KEY_QS_WIDGET_IOS_MUSIC = "qs_widget_ios_music";
    private static final String KEY_QS_WIDGET_SLIDER_CORNER = "qs_widget_slider_corner";
    private static final String QS_VOLUME_CATEGORY = "qs_volume_slider_category";
    private static final String KEY_SHOW_VOLUME_SLIDER = "qs_show_volume_slider";
    private static final String KEY_VOLUME_SLIDER_POSITION = "qs_volume_slider_position";
    private static final String KEY_VOLUME_SLIDER_HAPTIC = "qs_volume_slider_haptic";
    private static final String KEY_SHOW_RINGER_BUTTON = "qs_show_ringer_button";
    private static final String KEY_VOLUME_SLIDER_STYLE = "qs_volume_slider_style";
    private static final String KEY_VOLUME_SLIDER_SHAPE = "qs_volume_slider_shape";
    private static final String SHADE_SCRIM_ALPHA = "shade_scrim_alpha";

    private ListPreference mShowBrightnessSlider;
    private ListPreference mBrightnessSliderPosition;
    private SwitchPreferenceCompat mBrightnessSliderHaptic;
    private SwitchPreferenceCompat mShowAutoBrightness;
    private SystemSettingSwitchPreference mBrightnessSliderStyle;
    private SystemSettingListPreference mBrightnessSliderShape;
    private SwitchPreferenceCompat mQsTileHaptic;
    private Preference mQsCompactPlayer;
    private SwitchPreferenceCompat mSingleQsTone;
    private Preference mDualTargetTileStyle;
    private SwitchPreferenceCompat mQsTileAlternateColor;
    private SystemSettingSwitchPreference mQsTileStyleMinimal;
    private SystemSettingSwitchPreference mQsTileStyleMinimalInvert;
    private SystemSettingSwitchPreference mQsUseModifiedTileSpacing;
    private SystemSettingListPreference mQsTileShape;
    private SystemSettingListPreference mQsPanelStyle;
    private Preference mQsTileIconShape;
    private SystemSettingSwitchPreference mQsTileLabelHide;
    private SecureSettingListPreference mQsShowMediaPlayer;
    private SystemSettingSwitchPreference mQsWidgetPanel;
    private SystemSettingSwitchPreference mQsWidgetIosMusic;
    private SystemSettingSwitchPreference mQsWidgetSliderCorner;
    private ListPreference mShowVolumeSlider;
    private ListPreference mVolumeSliderPosition;
    private SwitchPreferenceCompat mVolumeSliderHaptic;
    private SwitchPreferenceCompat mShowRingerButton;
    private SystemSettingSwitchPreference mVolumeSliderStyle;
    private SystemSettingListPreference mVolumeSliderShape;
    private SystemSettingSeekBarPreference mShadeScrimAlphaPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.lunaris_settings_quick_settings);

        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();

        PreferenceCategory brightnessCategory = (PreferenceCategory) findPreference(QS_BRIGHTNESS_CATEGORY);
        PreferenceCategory volumeCategory = findPreference(QS_VOLUME_CATEGORY);
        PreferenceCategory tileCategory = (PreferenceCategory) findPreference(QS_LAYOUT_CATEGORY);

        mShowBrightnessSlider = findPreference(KEY_SHOW_BRIGHTNESS_SLIDER);
        mShowBrightnessSlider.setOnPreferenceChangeListener(this);
        boolean showSlider = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER, 1, UserHandle.USER_CURRENT) > 0;

        mBrightnessSliderPosition = findPreference(KEY_BRIGHTNESS_SLIDER_POSITION);
        mBrightnessSliderPosition.setEnabled(showSlider);

        mQsCompactPlayer = (Preference) findPreference(KEY_QS_COMPACT_PLAYER);
        mQsCompactPlayer.setOnPreferenceChangeListener(this);

        mQsShowMediaPlayer = (SecureSettingListPreference) findPreference(KEY_QS_SHOW_MEDIA_PLAYER);
        if (mQsShowMediaPlayer != null) {
            mQsShowMediaPlayer.setOnPreferenceChangeListener(this);
        }

        mQsWidgetPanel = (SystemSettingSwitchPreference) findPreference(KEY_QS_WIDGET_PANEL);
        if (mQsWidgetPanel != null) {
            mQsWidgetPanel.setOnPreferenceChangeListener(this);
        }

        mQsWidgetIosMusic = (SystemSettingSwitchPreference) findPreference(KEY_QS_WIDGET_IOS_MUSIC);
        mQsWidgetSliderCorner = (SystemSettingSwitchPreference) findPreference(KEY_QS_WIDGET_SLIDER_CORNER);

        updateWidgetPanelDependencies();

        mSingleQsTone = findPreference(KEY_SINGLE_QS_TONE);
        if (mSingleQsTone != null) {
            mSingleQsTone.setOnPreferenceChangeListener(this);
        }

        mDualTargetTileStyle = findPreference(KEY_DUAL_TARGET_TILE_STYLE);
        if (mDualTargetTileStyle != null) {
            mDualTargetTileStyle.setOnPreferenceChangeListener(this);
        }

        mQsTileAlternateColor = findPreference(KEY_QS_TILE_ALTERNATE_COLOR);
        if (mQsTileAlternateColor != null) {
            mQsTileAlternateColor.setOnPreferenceChangeListener(this);
        }

        mQsUseModifiedTileSpacing = findPreference(KEY_QS_USE_MODIFIED_TILE_SPACING);
        if (mQsUseModifiedTileSpacing != null) {
            mQsUseModifiedTileSpacing.setOnPreferenceChangeListener(this);
        }

        mQsTileStyleMinimal = findPreference(KEY_QS_TILE_STYLE_MINIMAL);
        mQsTileStyleMinimalInvert = findPreference(KEY_QS_TILE_STYLE_MINIMAL_INVERT);
        mQsTileShape = findPreference(KEY_QS_TILE_SHAPE);

        if (mQsTileStyleMinimal != null) {
            mQsTileStyleMinimal.setOnPreferenceChangeListener(this);
        }

        mQsPanelStyle = findPreference(KEY_QS_PANEL_STYLE);
        if (mQsPanelStyle != null) {
            mQsPanelStyle.setOnPreferenceChangeListener(this);
        }

        mQsTileIconShape = findPreference(KEY_QS_TILE_ICON_SHAPE);
        mQsTileLabelHide = findPreference(KEY_QS_TILE_LABEL_HIDE);

        updatePanelStyleDependencies();

        mBrightnessSliderStyle = findPreference(KEY_BRIGHTNESS_SLIDER_STYLE);
        mBrightnessSliderShape = findPreference(KEY_BRIGHTNESS_SLIDER_SHAPE);

        if (mBrightnessSliderStyle != null) {
            mBrightnessSliderStyle.setOnPreferenceChangeListener(this);
            updateBrightnessSliderStyleDependencies();
        }

        mBrightnessSliderHaptic = findPreference(KEY_BRIGHTNESS_SLIDER_HAPTIC);
        mQsTileHaptic = findPreference(KEY_QS_TILE_HAPTIC);
        boolean hapticAvailable = DeviceUtils.hasVibrator(context);

        if (hapticAvailable) {
            mBrightnessSliderHaptic.setEnabled(showSlider);
        } else {
            brightnessCategory.removePreference(mBrightnessSliderHaptic);
            tileCategory.removePreference(mQsTileHaptic);
        }

        mShowAutoBrightness = findPreference(KEY_SHOW_AUTO_BRIGHTNESS);
        boolean automaticAvailable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available);

        if (automaticAvailable) {
            mShowAutoBrightness.setEnabled(showSlider);
        } else {
            brightnessCategory.removePreference(mShowAutoBrightness);
        }

        mShowVolumeSlider = findPreference(KEY_SHOW_VOLUME_SLIDER);
        mShowVolumeSlider.setOnPreferenceChangeListener(this);

        boolean showVolumeSlider =
                Settings.System.getIntForUser(resolver,
                KEY_SHOW_VOLUME_SLIDER, 0,
                UserHandle.USER_CURRENT) > 0;

        mVolumeSliderPosition =
                findPreference(KEY_VOLUME_SLIDER_POSITION);
        mVolumeSliderPosition.setEnabled(showVolumeSlider);

        mVolumeSliderStyle =
                findPreference(KEY_VOLUME_SLIDER_STYLE);

        mVolumeSliderShape =
                findPreference(KEY_VOLUME_SLIDER_SHAPE);

        if (mVolumeSliderStyle != null) {
            mVolumeSliderStyle.setOnPreferenceChangeListener(this);
            updateVolumeSliderStyleDependencies();
        }

        mVolumeSliderHaptic =
                findPreference(KEY_VOLUME_SLIDER_HAPTIC);

        mShowRingerButton =
                findPreference(KEY_SHOW_RINGER_BUTTON);

        if (hapticAvailable) {
            mVolumeSliderHaptic.setEnabled(showVolumeSlider);
        } else {
            volumeCategory.removePreference(mVolumeSliderHaptic);
        }

        mShowRingerButton.setEnabled(showVolumeSlider);

        final int defScrimAlpha =
        (SystemProperties.getBoolean("ro.custom.blur.enable", false)
                && Settings.Global.getInt(resolver,
                        Settings.Global.DISABLE_WINDOW_BLURS, 0) == 0)
                ? 60 : 100;

        mShadeScrimAlphaPref = findPreference(SHADE_SCRIM_ALPHA);
        mShadeScrimAlphaPref.setDefaultValue(defScrimAlpha);
        mShadeScrimAlphaPref.setOnPreferenceChangeListener(this);
        int shadeScrimAlpha = Settings.System.getIntForUser(resolver,
                SHADE_SCRIM_ALPHA, defScrimAlpha, UserHandle.USER_CURRENT);
        mShadeScrimAlphaPref.setValue(shadeScrimAlpha);
    }

    private boolean isPanelStyleClassic() {
        ContentResolver resolver = getContext().getContentResolver();
        return Settings.System.getInt(resolver, KEY_QS_PANEL_STYLE, 0) == 1;
    }

    private void updatePanelStyleDependencies() {
        boolean isClassic = isPanelStyleClassic();
        boolean showClassicOffOptions = !isClassic;

        if (mQsTileStyleMinimal != null)
            mQsTileStyleMinimal.setVisible(showClassicOffOptions);
        if (mQsUseModifiedTileSpacing != null)
            mQsUseModifiedTileSpacing.setVisible(showClassicOffOptions);
        if (mDualTargetTileStyle != null)
            mDualTargetTileStyle.setVisible(showClassicOffOptions);

        if (mQsTileIconShape != null)
            mQsTileIconShape.setVisible(isClassic);
        if (mQsTileLabelHide != null)
            mQsTileLabelHide.setVisible(isClassic);

        updateMinimalStyleDependencies(isClassic);
    }

    private void updateWidgetPanelDependencies() {
        if (mQsWidgetPanel == null) return;

        ContentResolver resolver = getContext().getContentResolver();
        boolean isWidgetPanelEnabled = Settings.System.getInt(resolver,
                KEY_QS_WIDGET_PANEL, 0) == 1;

        if (mQsWidgetIosMusic != null)
            mQsWidgetIosMusic.setVisible(isWidgetPanelEnabled);
        if (mQsWidgetSliderCorner != null)
            mQsWidgetSliderCorner.setVisible(isWidgetPanelEnabled);
        if (mQsShowMediaPlayer != null)
            mQsShowMediaPlayer.setVisible(!isWidgetPanelEnabled);
    }

    private void updateMinimalStyleDependencies(boolean isClassic) {
        if (mQsTileStyleMinimal == null) return;

        ContentResolver resolver = getContext().getContentResolver();
        boolean isMinimalEnabled = !isClassic &&
                Settings.System.getInt(resolver, KEY_QS_TILE_STYLE_MINIMAL, 0) == 1;

        if (mQsTileStyleMinimalInvert != null) {
            mQsTileStyleMinimalInvert.setVisible(isMinimalEnabled);
        }

        if (mQsTileShape != null) {
            mQsTileShape.setVisible(!isClassic && !isMinimalEnabled);
        }
    }

    private void updateBrightnessSliderStyleDependencies() {
        if (mBrightnessSliderStyle == null) return;

        ContentResolver resolver = getContext().getContentResolver();
        boolean isSliderStyleEnabled = Settings.System.getInt(resolver,
                KEY_BRIGHTNESS_SLIDER_STYLE, 0) == 1;

        if (mBrightnessSliderShape != null) {
            mBrightnessSliderShape.setVisible(!isSliderStyleEnabled);
        }

        if (mShowAutoBrightness != null) {
            boolean automaticAvailable = getContext().getResources().getBoolean(
                    com.android.internal.R.bool.config_automatic_brightness_available);
            if (automaticAvailable) {
                mShowAutoBrightness.setVisible(!isSliderStyleEnabled);
            }
        }
    }

    private void updateVolumeSliderStyleDependencies() {
        if (mVolumeSliderStyle == null) return;

        ContentResolver resolver =
                getContext().getContentResolver();

        boolean isSliderStyleEnabled =
                Settings.System.getInt(resolver,
                KEY_VOLUME_SLIDER_STYLE, 0) == 1;

        if (mVolumeSliderShape != null) {
            mVolumeSliderShape.setVisible(!isSliderStyleEnabled);
        }

        if (mShowRingerButton != null) {
            mShowRingerButton.setVisible(!isSliderStyleEnabled);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getContext().getContentResolver();

        if (preference == mShowBrightnessSlider) {
            int value = Integer.parseInt((String) newValue);
            mBrightnessSliderPosition.setEnabled(value > 0);
            if (mBrightnessSliderHaptic != null)
                mBrightnessSliderHaptic.setEnabled(value > 0);
            if (mShowAutoBrightness != null)
                mShowAutoBrightness.setEnabled(value > 0);
            updateBrightnessSliderStyleDependencies();
            return true;
        } else if (preference == mQsPanelStyle) {
            updatePanelStyleDependencies();
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mQsCompactPlayer) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mSingleQsTone) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mDualTargetTileStyle) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mQsTileAlternateColor) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mQsUseModifiedTileSpacing) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mQsTileStyleMinimal) {
            updateMinimalStyleDependencies(isPanelStyleClassic());
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mBrightnessSliderStyle) {
            updateBrightnessSliderStyleDependencies();
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mQsShowMediaPlayer) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mQsWidgetPanel) {
            updateWidgetPanelDependencies();
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mShowVolumeSlider) {
            int value = Integer.parseInt((String) newValue);
            mVolumeSliderPosition.setEnabled(value > 0);
            if (mVolumeSliderHaptic != null)
                mVolumeSliderHaptic.setEnabled(value > 0);
            if (mShowRingerButton != null)
                mShowRingerButton.setEnabled(value > 0);
            updateVolumeSliderStyleDependencies();
            return true;
        } else if (preference == mVolumeSliderStyle) {
            updateVolumeSliderStyleDependencies();
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mShadeScrimAlphaPref) {
            int value = (Integer) newValue;
            Settings.System.putIntForUser(resolver, SHADE_SCRIM_ALPHA,
                    value, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(getContext(), 3);
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.LUNARIS;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lunaris_settings_quick_settings) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    final Resources res = context.getResources();
                    final ContentResolver resolver = context.getContentResolver();

                    boolean automaticAvailable = res.getBoolean(
                            com.android.internal.R.bool.config_automatic_brightness_available);
                    if (!automaticAvailable) {
                        keys.add(KEY_SHOW_AUTO_BRIGHTNESS);
                    }

                    boolean hapticAvailable = DeviceUtils.hasVibrator(context);
                    if (!hapticAvailable) {
                        keys.add(KEY_BRIGHTNESS_SLIDER_HAPTIC);
                        keys.add(KEY_QS_TILE_HAPTIC);
                        keys.add(KEY_VOLUME_SLIDER_HAPTIC);
                    }

                    boolean isClassic = Settings.System.getInt(resolver,
                            KEY_QS_PANEL_STYLE, 0) == 1;

                    if (isClassic) {
                        keys.add(KEY_QS_TILE_STYLE_MINIMAL);
                        keys.add(KEY_QS_TILE_STYLE_MINIMAL_INVERT);
                        keys.add(KEY_QS_TILE_SHAPE);
                        keys.add(KEY_QS_USE_MODIFIED_TILE_SPACING);
                        keys.add(KEY_DUAL_TARGET_TILE_STYLE);
                    } else {
                        keys.add(KEY_QS_TILE_ICON_SHAPE);
                        keys.add(KEY_QS_TILE_LABEL_HIDE);
                        boolean isMinimalEnabled = Settings.System.getInt(resolver,
                                KEY_QS_TILE_STYLE_MINIMAL, 0) == 1;

                        if (!isMinimalEnabled) {
                            keys.add(KEY_QS_TILE_STYLE_MINIMAL_INVERT);
                        }

                        if (isMinimalEnabled) {
                            keys.add(KEY_QS_TILE_SHAPE);
                        }
                    }

                    boolean isVolumeSliderStyleEnabled = Settings.System.getInt(resolver,
                        KEY_VOLUME_SLIDER_STYLE, 0) == 1;

                    if (isVolumeSliderStyleEnabled) {
                        keys.add(KEY_VOLUME_SLIDER_SHAPE);
                        keys.add(KEY_SHOW_RINGER_BUTTON);
                    }

                    boolean isSliderStyleEnabled = Settings.System.getInt(resolver,
                            KEY_BRIGHTNESS_SLIDER_STYLE, 0) == 1;
                    
                    if (isSliderStyleEnabled) {
                        keys.add(KEY_BRIGHTNESS_SLIDER_SHAPE);
                        keys.add(KEY_SHOW_AUTO_BRIGHTNESS);
                    }

                    boolean isWidgetPanelEnabled = Settings.System.getInt(resolver,
                            KEY_QS_WIDGET_PANEL, 0) == 1;

                    if (isWidgetPanelEnabled) {
                        keys.add(KEY_QS_SHOW_MEDIA_PLAYER);
                    } else {
                        keys.add(KEY_QS_WIDGET_IOS_MUSIC);
                        keys.add(KEY_QS_WIDGET_SLIDER_CORNER);
                    }

                    return keys;
                }
            };
}
