/*
 * Copyright (C) 2016-2025 crDroid Android Project
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
package org.lunaris.settings.fragments.statusbar;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import org.lunaris.settings.preferences.SystemSettingListPreference;
import org.lunaris.settings.preferences.colorpicker.ColorPickerPreference;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.fragments.statusbar.BatteryBar;
import org.lunaris.settings.fragments.statusbar.Clock;
import org.lunaris.settings.preferences.SystemSettingSeekBarPreference;
import org.lunaris.settings.utils.DeviceUtils;
import org.lunaris.settings.utils.SystemUtils;
import org.lunaris.settings.utils.StatusBarLogoImageUtils;

import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.providers.LineageSettings;

import com.android.internal.util.lunaris.VibrationUtils;

import java.util.List;

@SearchIndexable
public class StatusBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "StatusBar";

    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";
    private static final String QUICK_PULLDOWN = "qs_quick_pulldown";
    private static final String LOGO_COLOR = "status_bar_logo_color";
    private static final String LOGO_COLOR_PICKER = "status_bar_logo_color_picker";
    private static final String LOGO_CUSTOM_STYLE = "status_bar_logo_style";
    private static final String STATUS_BAR_ICON_ORDER_LEGACY = "status_bar_icon_order_legacy";
    private static final String LOGO_CUSTOM_IMAGE = "status_bar_logo_custom_image";

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;
    private static final int PULLDOWN_DIR_ALWAYS = 3;

    private static final int LOGO_STYLE_CUSTOM = 33;
    private static final int LOGO_CUSTOM_IMAGE_REQUEST = 3001;

    private LineageSystemSettingListPreference mStatusBarClock;
    private LineageSystemSettingListPreference mQuickPulldown;
    private SystemSettingListPreference mLogoColor;
    private ColorPickerPreference mLogoColorPicker;
    private SwitchPreferenceCompat mStatusBarIconOrderLegacy;
    private Preference mLogoCustomImage;
    private Preference mLogoStyle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.lunaris_settings_status_bar);

        ContentResolver resolver = getActivity().getContentResolver();
        Context mContext = getActivity().getApplicationContext();

        final PreferenceScreen prefScreen = getPreferenceScreen();

        mStatusBarClock =
                (LineageSystemSettingListPreference) findPreference(STATUS_BAR_CLOCK_STYLE);

        // Adjust status bar preferences for RTL
        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            if (DeviceUtils.hasCenteredCutout(mContext)) {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_notch_rtl);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_notch_rtl);
            } else {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_rtl);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_rtl);
            }
        } else if (DeviceUtils.hasCenteredCutout(mContext)) {
            mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_notch);
            mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_notch);
        }

        mQuickPulldown =
                (LineageSystemSettingListPreference) findPreference(QUICK_PULLDOWN);
        mQuickPulldown.setOnPreferenceChangeListener(this);
        updateQuickPulldownSummary(mQuickPulldown.getIntValue(0));

        mLogoColor = (SystemSettingListPreference) findPreference(LOGO_COLOR);
        int logoColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO_COLOR, 0, UserHandle.USER_CURRENT);
        mLogoColor.setValue(String.valueOf(logoColor));
        mLogoColor.setSummary(mLogoColor.getEntry());
        mLogoColor.setOnPreferenceChangeListener(this);
        mLogoColorPicker = (ColorPickerPreference) findPreference(LOGO_COLOR_PICKER);
        int logoColorPicker = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_LOGO_COLOR_PICKER, 0xFFFFFFFF);
        mLogoColorPicker.setNewPreviewColor(logoColorPicker);
        String logoColorPickerHex = String.format("#%08x", (0xFFFFFFFF & logoColorPicker));
        if (logoColorPickerHex.equals("#ffffffff")) {
            mLogoColorPicker.setSummary(R.string.default_string);
        } else {
            mLogoColorPicker.setSummary(logoColorPickerHex);
        }
        mLogoColorPicker.setOnPreferenceChangeListener(this);
        updateColorPrefs(logoColor);

        mStatusBarIconOrderLegacy = findPreference(STATUS_BAR_ICON_ORDER_LEGACY);
        if (mStatusBarIconOrderLegacy != null) {
            mStatusBarIconOrderLegacy.setOnPreferenceChangeListener(this);
        }

        mLogoStyle = findPreference(LOGO_CUSTOM_STYLE);
        if (mLogoStyle != null) {
            mLogoStyle.setOnPreferenceChangeListener(this);
        }

        mLogoCustomImage = findPreference(LOGO_CUSTOM_IMAGE);
        updateCustomImagePrefVisibility();

        // Adjust status bar preferences for RTL
        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            mQuickPulldown.setEntries(R.array.status_bar_quick_qs_pulldown_entries_rtl);
            mQuickPulldown.setEntryValues(R.array.status_bar_quick_qs_pulldown_values_rtl);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mQuickPulldown) {
            int value = Integer.parseInt((String) newValue);
            updateQuickPulldownSummary(value);
            return true;
        } else if (preference == mLogoColor) {
            int logoColor = Integer.valueOf((String) newValue);
            int index = mLogoColor.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver,
                    Settings.System.STATUS_BAR_LOGO_COLOR, logoColor, UserHandle.USER_CURRENT);
            mLogoColor.setSummary(mLogoColor.getEntries()[index]);
            updateColorPrefs(logoColor);
            return true;
        } else if (preference.getKey() != null
                && preference.getKey().equals(LOGO_CUSTOM_STYLE)) {
            int newStyle = Integer.parseInt(String.valueOf(newValue));
            updateCustomImagePrefVisibility(newStyle);
            return true;
        } else if (preference == mLogoColorPicker) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            if (hex.equals("#ffffffff")) {
                preference.setSummary(R.string.default_string);
            } else {
                preference.setSummary(hex);
            }
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(resolver,
                    Settings.System.STATUS_BAR_LOGO_COLOR_PICKER, intHex);
            return true;
        } else if (preference == mStatusBarIconOrderLegacy) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOGO_CUSTOM_IMAGE_REQUEST
                && resultCode == Activity.RESULT_OK
                && data != null) {
            Uri imgUri = data.getData();
            if (imgUri != null) {
                String savedPath = StatusBarLogoImageUtils.saveLogoImage(
                        getActivity(), imgUri);
                if (savedPath != null) {
                    Settings.System.putStringForUser(
                            getActivity().getContentResolver(),
                            Settings.System.STATUS_BAR_LOGO_CUSTOM_IMAGE_URI,
                            savedPath,
                            UserHandle.USER_CURRENT);
                    updateCustomImagePrefSummary(savedPath);
                } else {
                    Toast.makeText(getContext(),
                            R.string.qs_header_image_error,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void updateCustomImagePrefVisibility() {
        if (mLogoCustomImage == null) return;
        int currentStyle = Settings.System.getIntForUser(
                getActivity().getContentResolver(),
                Settings.System.STATUS_BAR_LOGO_STYLE, 0,
                UserHandle.USER_CURRENT);
        updateCustomImagePrefVisibility(currentStyle);
    }

    private void updateCustomImagePrefVisibility(int style) {
        if (mLogoCustomImage == null) return;
        mLogoCustomImage.setVisible(style == LOGO_STYLE_CUSTOM);
        if (style == LOGO_STYLE_CUSTOM) {
            String path = Settings.System.getStringForUser(
                    getActivity().getContentResolver(),
                    Settings.System.STATUS_BAR_LOGO_CUSTOM_IMAGE_URI,
                    UserHandle.USER_CURRENT);
            updateCustomImagePrefSummary(path);
        }
    }

    private void updateCustomImagePrefSummary(String path) {
        if (mLogoCustomImage == null) return;
        mLogoCustomImage.setSummary(
                path != null && !path.isEmpty()
                        ? path
                        : getString(R.string.status_bar_logo_custom_image_pick_summary));
    }

    private void updateQuickPulldownSummary(int value) {
        String summary="";
        switch (value) {
            case PULLDOWN_DIR_NONE:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_off);
                break;
            case PULLDOWN_DIR_ALWAYS:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_always);
                break;
            case PULLDOWN_DIR_LEFT:
            case PULLDOWN_DIR_RIGHT:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_summary,
                    getResources().getString(value == PULLDOWN_DIR_LEFT
                        ? R.string.status_bar_quick_qs_pulldown_summary_left
                        : R.string.status_bar_quick_qs_pulldown_summary_right));
                break;
        }
        mQuickPulldown.setSummary(summary);
    }

    private void updateColorPrefs(int logoColor) {
        if (mLogoColor != null) {
            mLogoColorPicker.setEnabled(logoColor == 2);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(getContext(), 3);
        }
        if (preference == mLogoCustomImage) {
            try {
                Intent intent = new Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                startActivityForResult(intent, LOGO_CUSTOM_IMAGE_REQUEST);
            } catch (Exception e) {
                Toast.makeText(getContext(),
                        R.string.quick_settings_header_needs_gallery,
                        Toast.LENGTH_LONG).show();
            }
            return true;
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
            new BaseSearchIndexProvider(R.xml.lunaris_settings_status_bar);
}
