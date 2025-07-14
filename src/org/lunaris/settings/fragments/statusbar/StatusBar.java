/*
 * Copyright (C) 2017-2024 crDroid Android Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.search.SearchIndexable;

import java.util.List;

import lineageos.preference.LineageSystemSettingListPreference;

import org.lunaris.settings.preferences.SystemSettingListPreference;
import org.lunaris.settings.preferences.SystemSettingSwitchPreference;
import org.lunaris.settings.preferences.colorpicker.ColorPickerPreference;
import org.lunaris.settings.utils.DeviceUtils;
import org.lunaris.settings.utils.SystemUtils;

@SearchIndexable
public class StatusBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "StatusBar";

    private static final String KEY_QUICK_PULLDOWN = "qs_quick_pulldown";

    private static final String KEY_ICONS_CATEGORY = "status_bar_icons_category";
    private static final String KEY_DATA_DISABLED_ICON = "data_disabled_icon";
    private static final String KEY_BLUETOOTH_BATTERY_STATUS = "bluetooth_show_battery";
    private static final String KEY_FOUR_G_ICON = "show_fourg_icon";
    private static final String LOGO_COLOR = "status_bar_logo_color";
    private static final String LOGO_COLOR_PICKER = "status_bar_logo_color_picker";

    private SystemSettingListPreference mLogoColor;
    private ColorPickerPreference mLogoColorPicker;

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;
    private static final int PULLDOWN_DIR_BOTH = 3;

    private LineageSystemSettingListPreference mQuickPulldown;

    private PreferenceCategory mIconsCategory;
    private SystemSettingSwitchPreference mDataDisabledIcon;
    private SystemSettingSwitchPreference mFourgIcon;
    private SystemSettingSwitchPreference mBluetoothBatteryStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lunaris_settings_status_bar);

        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources resources = context.getResources();

        mQuickPulldown =
                (LineageSystemSettingListPreference) findPreference(KEY_QUICK_PULLDOWN);
        mQuickPulldown.setOnPreferenceChangeListener(this);
        updateQuickPulldownSummary(mQuickPulldown.getIntValue(0));

        mIconsCategory = (PreferenceCategory) findPreference(KEY_ICONS_CATEGORY);
        mBluetoothBatteryStatus = (SystemSettingSwitchPreference) findPreference(KEY_BLUETOOTH_BATTERY_STATUS);
        mDataDisabledIcon = (SystemSettingSwitchPreference) findPreference(KEY_DATA_DISABLED_ICON);
        mFourgIcon = (SystemSettingSwitchPreference) findPreference(KEY_FOUR_G_ICON);

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

        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            mQuickPulldown.setEntries(R.array.status_bar_quick_pull_down_entries_rtl);
            mQuickPulldown.setEntryValues(R.array.status_bar_quick_pull_down_values_rtl);
        }

        if (!DeviceUtils.deviceSupportsMobileData(context)) {
            mIconsCategory.removePreference(mDataDisabledIcon);
            mIconsCategory.removePreference(mFourgIcon);
        }

        if (!DeviceUtils.deviceSupportsBluetooth(context)) {
            mIconsCategory.removePreference(mBluetoothBatteryStatus);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();
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
        }
        return false;
    }

    private void updateColorPrefs(int logoColor) {
        if (mLogoColor != null) {
            mLogoColorPicker.setEnabled(logoColor == 2);
        }
    }

    private void updateQuickPulldownSummary(int value) {
        String summary = "";
        switch (value) {
            case PULLDOWN_DIR_NONE:
                summary = getResources().getString(
                    R.string.status_bar_quick_pull_down_off);
                break;
            case PULLDOWN_DIR_RIGHT:
            case PULLDOWN_DIR_LEFT:
            case PULLDOWN_DIR_BOTH:
                summary = getResources().getString(
                    R.string.status_bar_quick_pull_down_summary,
                    getResources().getString(
                        value == PULLDOWN_DIR_RIGHT
                            ? R.string.status_bar_quick_pull_down_right
                            : value == PULLDOWN_DIR_LEFT
                                ? R.string.status_bar_quick_pull_down_left
                                : R.string.status_bar_quick_pull_down_both
                    )
                );
                break;
        }
        mQuickPulldown.setSummary(summary);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider(R.xml.lunaris_settings_status_bar) {

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                List<String> keys = super.getNonIndexableKeys(context);
                final Resources resources = context.getResources();

                if (!DeviceUtils.deviceSupportsMobileData(context)) {
                    keys.add(KEY_DATA_DISABLED_ICON);
                    keys.add(KEY_FOUR_G_ICON);
                }
                if (!DeviceUtils.deviceSupportsBluetooth(context)) {
                    keys.add(KEY_BLUETOOTH_BATTERY_STATUS);
                }
                return keys;
            }
        };
}
