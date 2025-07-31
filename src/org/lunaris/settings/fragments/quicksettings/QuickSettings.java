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
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.preferences.CustomSeekBarPreference;
import org.lunaris.settings.preferences.SecureSettingSwitchPreference;

import org.lunaris.settings.utils.DeviceUtils;
import org.lunaris.settings.utils.SystemRestartUtils;

import java.util.List;

@SearchIndexable
public class QuickSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "QuickSettings";

    private static final String KEY_INTERFACE_CATEGORY = "quick_settings_interface_category";
    private static final String KEY_MISCELLANEOUS_CATEGORY = "quick_settings_miscellaneous_category";
    private static final String KEY_QS_BLUETOOTH_SHOW_DIALOG = "qs_bt_show_dialog";
    private static final String PREF_NOTIFICATION_ROW_TRANSPARENCY = "notification_row_transparency";
    private static final String KEY_QS_REFACTOR_DISABLED = "qs_refactor_disabled";
    private static final String PREF_DUAL_TONE_SHADE = "dual_tone_shade_enabled";
    private static final String PREF_SHADE_BLUR_RADIUS = "shade_blur_radius";

    private PreferenceCategory mInterfaceCategory;
    private PreferenceCategory mMiscellaneousCategory;
    private SwitchPreferenceCompat mNotificationRowTransparencyPref;
    private SecureSettingSwitchPreference mQsRefactorDisabled;
    private SwitchPreferenceCompat mDualToneShadePref;
    private CustomSeekBarPreference mShadeBlurRadiusPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lunaris_settings_quick_settings);

        final Context mContext = getContext();
        final ContentResolver resolver = mContext.getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources resources = mContext.getResources();

        mInterfaceCategory = findPreference(KEY_INTERFACE_CATEGORY);
        mMiscellaneousCategory = findPreference(KEY_MISCELLANEOUS_CATEGORY);
        mNotificationRowTransparencyPref = findPreference(PREF_NOTIFICATION_ROW_TRANSPARENCY);

        mQsRefactorDisabled = (SecureSettingSwitchPreference) findPreference(KEY_QS_REFACTOR_DISABLED);
        mQsRefactorDisabled.setOnPreferenceChangeListener(this);

        mDualToneShadePref = findPreference(PREF_DUAL_TONE_SHADE);

        mShadeBlurRadiusPref = findPreference(PREF_SHADE_BLUR_RADIUS);
        
        updatePreferences();

        if (mDualToneShadePref != null) {
            mDualToneShadePref.setOnPreferenceChangeListener(this);
        }

        if (!DeviceUtils.deviceSupportsBluetooth(mContext)) {
            prefScreen.removePreference(mMiscellaneousCategory);
        }

        if (mNotificationRowTransparencyPref != null) {
            mNotificationRowTransparencyPref.setOnPreferenceChangeListener(this);
        }

        if (mShadeBlurRadiusPref != null) {
            mShadeBlurRadiusPref.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    private void updatePreferences() {
        final ContentResolver resolver = getActivity().getContentResolver();
        final String overlayPackageJson = Settings.Secure.getStringForUser(
                resolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);

        int currentBlur = Settings.System.getIntForUser(resolver,
                PREF_SHADE_BLUR_RADIUS, 17, UserHandle.USER_CURRENT);
        mShadeBlurRadiusPref.setValue(currentBlur);

        boolean dualToneEnabled = Settings.System.getIntForUser(resolver,
                PREF_DUAL_TONE_SHADE, 0, UserHandle.USER_CURRENT) == 1;
        mDualToneShadePref.setChecked(dualToneEnabled);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mNotificationRowTransparencyPref) {
    	    boolean value = (Boolean) newValue;
    	    Settings.System.putIntForUser(resolver, PREF_NOTIFICATION_ROW_TRANSPARENCY,
                    value ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mQsRefactorDisabled) {
            SystemRestartUtils.restartSystemUI(getContext());
            return true;
        } else if (preference == mDualToneShadePref) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, PREF_DUAL_TONE_SHADE,
                    value ? 1 : 0, UserHandle.USER_CURRENT);
           return true;
        } else if (preference == mShadeBlurRadiusPref) {
            int value = (Integer) newValue;
            Settings.System.putIntForUser(resolver, PREF_SHADE_BLUR_RADIUS,
                    value, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
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
