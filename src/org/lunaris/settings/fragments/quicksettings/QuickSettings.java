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

package org.lunaris.settings.fragments.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.preferences.SecureSettingSwitchPreference;
import org.lunaris.settings.preferences.SecureSettingSeekBarPreference;
import org.lunaris.settings.preferences.SystemSettingListPreference;

import org.lunaris.settings.utils.DeviceUtils;
import org.lunaris.settings.utils.SystemRestartUtils;
import org.lunaris.settings.utils.SystemUtils;

import com.android.internal.util.android.VibrationUtils;

import java.util.List;

@SearchIndexable
public class QuickSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "QuickSettings";

    private static final String KEY_INTERFACE_CATEGORY = "quick_settings_interface_category";
    private static final String KEY_MISCELLANEOUS_CATEGORY = "quick_settings_miscellaneous_category";
    private static final String KEY_QS_BLUETOOTH_SHOW_DIALOG = "qs_bt_show_dialog";
    private static final String KEY_QS_REFACTOR_DISABLED = "qs_refactor_disabled";
    private static final String KEY_QS_COMPACT_PLAYER = "qs_compact_media_player_mode";
    private static final String KEY_QS_DATA_USAGE = "qs_show_data_usage";
    private static final String KEY_QS_DATA_USAGE_CYCLE_TYPE = "qs_data_usage_cycle_type";
    private static final String KEY_QS_HEADER_CLOCK_STYLE = "qs_header_clock_style";
    private static final String KEY_NOTIF_TRANSPARENCY_LEVEL = "notification_row_transparency_level";
    private static final String KEY_NOTIF_TRANSPARENCY_CONTEXT = "notification_row_transparency_context_aware";

    private PreferenceCategory mInterfaceCategory;
    private PreferenceCategory mMiscellaneousCategory;
    private SecureSettingSwitchPreference mQsRefactorDisabled;
    private Preference mQsCompactPlayer;
    private Preference mDataUsagePreference;
    private ListPreference mDataUsageCycleTypePreference;
    private SystemSettingListPreference mQsHeaderClockStyle;
    private SecureSettingSeekBarPreference mNotifTransparencyLevelPref;
    private SecureSettingSwitchPreference mNotifTransparencyContextPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lunaris_settings_quick_settings);

        final Context mContext = getContext();
        final ContentResolver resolver = mContext.getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources resources = mContext.getResources();

        mMiscellaneousCategory = (PreferenceCategory) findPreference(KEY_MISCELLANEOUS_CATEGORY);

        mQsRefactorDisabled = (SecureSettingSwitchPreference) findPreference(KEY_QS_REFACTOR_DISABLED);
        mQsRefactorDisabled.setOnPreferenceChangeListener(this);

        mQsCompactPlayer = (Preference) findPreference(KEY_QS_COMPACT_PLAYER);
        mQsCompactPlayer.setOnPreferenceChangeListener(this);

        mQsHeaderClockStyle = (SystemSettingListPreference) findPreference(KEY_QS_HEADER_CLOCK_STYLE);
        if (mQsHeaderClockStyle != null) {
            mQsHeaderClockStyle.setOnPreferenceChangeListener(this);
        }

        mDataUsagePreference = findPreference(KEY_QS_DATA_USAGE);
        mDataUsageCycleTypePreference = (ListPreference) findPreference(KEY_QS_DATA_USAGE_CYCLE_TYPE);
        
        if (mDataUsageCycleTypePreference != null) {
            mDataUsageCycleTypePreference.setOnPreferenceChangeListener(this);
        }
        
        updateDataUsageSummary();

        // Initialize notification transparency preferences
        mNotifTransparencyLevelPref = (SecureSettingSeekBarPreference) findPreference(KEY_NOTIF_TRANSPARENCY_LEVEL);
        mNotifTransparencyContextPref = (SecureSettingSwitchPreference) findPreference(KEY_NOTIF_TRANSPARENCY_CONTEXT);
        
        if (mNotifTransparencyLevelPref != null) {
            mNotifTransparencyLevelPref.setOnPreferenceChangeListener(this);
        }
        
        // Initialize context-aware toggle availability based on current slider value
        int currentLevel = Settings.Secure.getInt(resolver, KEY_NOTIF_TRANSPARENCY_LEVEL, 40);
        updateTransparencyContextAvailability(currentLevel);

        if (!DeviceUtils.deviceSupportsBluetooth(mContext)) {
            prefScreen.removePreference(mMiscellaneousCategory);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mQsRefactorDisabled) {
            SystemRestartUtils.restartSystemUI(getContext());
            return true;
        } else if (preference == mQsCompactPlayer) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mQsHeaderClockStyle) {
            String value = newValue.toString();
            if ("0".equals(value)) {
                SystemRestartUtils.restartSystemUI(getContext());
            }
            return true;
        } else if (preference == mDataUsageCycleTypePreference) {
            updateDataUsageSummary(newValue.toString());
            return true;
        } else if (preference == mNotifTransparencyLevelPref) {
            int level = (Integer) newValue;
            updateTransparencyContextAvailability(level);
            return true;
        }
        return false;
    }

    private void updateDataUsageSummary() {
        updateDataUsageSummary(null);
    }

    private void updateDataUsageSummary(String cycleTypeValue) {
        if (mDataUsagePreference == null) return;
        
        final ContentResolver resolver = getActivity().getContentResolver();
        int cycleType;
        
        if (cycleTypeValue != null) {
            try {
                cycleType = Integer.parseInt(cycleTypeValue);
            } catch (NumberFormatException e) {
                cycleType = 0;
            }
        } else {
            cycleType = Settings.Secure.getInt(resolver, KEY_QS_DATA_USAGE_CYCLE_TYPE, 0);
        }
        
        int summaryResId;
        switch (cycleType) {
            case 0:
                summaryResId = R.string.qs_footer_datausage_summary_daily;
                break;
            case 1:
                summaryResId = R.string.qs_footer_datausage_summary_weekly;
                break;
            default:
                summaryResId = R.string.qs_footer_datausage_summary_daily;
                break;
        }
        
        mDataUsagePreference.setSummary(getString(summaryResId));
    }

    private void updateTransparencyContextAvailability(int level) {
        if (mNotifTransparencyContextPref != null) {
            // Disable context-aware toggle when transparency is 0 (AOSP default opaque)
            mNotifTransparencyContextPref.setEnabled(level > 0);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(getContext(), 3);
        }
        return super.onPreferenceTreeClick(preference);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider(R.xml.lunaris_settings_quick_settings) {

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                List<String> keys = super.getNonIndexableKeys(context);
                final Resources resources = context.getResources();

                if (!DeviceUtils.deviceSupportsBluetooth(context)) {
                    keys.add(KEY_QS_BLUETOOTH_SHOW_DIALOG);
                }
                return keys;
            }
        };
}
