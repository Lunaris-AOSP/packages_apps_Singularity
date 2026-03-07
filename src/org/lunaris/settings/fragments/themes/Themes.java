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
package org.lunaris.settings.fragments.themes;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.fragments.themes.SmartPixels;
import org.lunaris.settings.utils.SystemUtils;
import org.lunaris.settings.preferences.SystemSettingListPreference;
import org.lunaris.settings.preferences.SystemSettingSwitchPreference;

import com.android.internal.util.lunaris.VibrationUtils;
import com.android.internal.util.lunaris.ThemeUtils;

import org.lunaris.settings.utils.SystemRestartUtils;

import java.util.List;

@SearchIndexable
public class Themes extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "UserInterface";

    private static final String KEY_FORCE_FULL_SCREEN = "display_cutout_force_fullscreen_settings";
    private static final String SMART_PIXELS = "smart_pixels";
    private static final String KEY_VOLUME_DIALOG_TYPE = "volume_dialog_type";
    private static final String KEY_WIFI_ICON_STYLE = "wifi_icon_style";
    private static final String KEY_QUICKSWITCH = "quickswitch";
    private static final String KEY_SHOW_VOLUME_PERCENTAGE = "show_volume_percentage";

    private Preference mShowCutoutForce;
    private Preference mSmartPixels;
    private Preference mQuickSwitch;
    private SystemSettingListPreference mVolumeDialogType;
    private SystemSettingListPreference mWifiIconStyle;
    private SystemSettingSwitchPreference mShowVolumePercentage;
    private ThemeUtils mThemeUtils;

    private static final String[] WIFI_ICON_OVERLAYS = {
            "com.custom.overlay.systemui.wifiAurora",
            "com.android.systemui.wifibar_c",
            "com.custom.overlay.systemui.wifiLinear",
            "com.android.systemui.wifiNothingDot",
            "com.android.systemui.wifibar_d"
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.lunaris_settings_themes);

        Context mContext = getActivity().getApplicationContext();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        mThemeUtils = ThemeUtils.getInstance(getActivity());

        final String displayCutout =
            mContext.getResources().getString(com.android.internal.R.string.config_mainBuiltInDisplayCutout);

        if (TextUtils.isEmpty(displayCutout)) {
            mShowCutoutForce = (Preference) findPreference(KEY_FORCE_FULL_SCREEN);
            prefScreen.removePreference(mShowCutoutForce);
        }

        mSmartPixels = (Preference) prefScreen.findPreference(SMART_PIXELS);
        boolean mSmartPixelsSupported = getResources().getBoolean(
                com.android.internal.R.bool.config_supportSmartPixels);
        if (!mSmartPixelsSupported)
            prefScreen.removePreference(mSmartPixels);

        mQuickSwitch = (Preference) prefScreen.findPreference(KEY_QUICKSWITCH);
        boolean withGoogleApps = android.os.SystemProperties.getBoolean("with_google_apps", false);
        if (!withGoogleApps)
            prefScreen.removePreference(mQuickSwitch);

        mVolumeDialogType = findPreference(KEY_VOLUME_DIALOG_TYPE);
        if (mVolumeDialogType != null) {
            mVolumeDialogType.setOnPreferenceChangeListener(this);
        }

        mWifiIconStyle = findPreference(KEY_WIFI_ICON_STYLE);
        if (mWifiIconStyle != null) {
            mWifiIconStyle.setOnPreferenceChangeListener(this);
        }

        mShowVolumePercentage = findPreference(KEY_SHOW_VOLUME_PERCENTAGE);
        updateVolumePercentageVisibility();
    }

    private void updateVolumePercentageVisibility() {
        if (mShowVolumePercentage == null) return;
        int type = Settings.System.getIntForUser(
                getContext().getContentResolver(),
                KEY_VOLUME_DIALOG_TYPE, 1,
                UserHandle.USER_CURRENT);
        mShowVolumePercentage.setVisible(type == 1);
    }

    private void updateStyle(String key, String category, String target,
            int defaultValue, String[] overlayPackages, boolean restartSystemUI) {
        final int style = Settings.System.getIntForUser(
                getContext().getContentResolver(),
                key,
                defaultValue,
                UserHandle.USER_CURRENT
        );
        if (mThemeUtils == null) {
            mThemeUtils = ThemeUtils.getInstance(getContext());
        }
        mThemeUtils.setOverlayEnabled(category, target, target);
        if (style > 0 && style <= overlayPackages.length) {
            mThemeUtils.setOverlayEnabled(category, overlayPackages[style - 1], target);
        }
        if (restartSystemUI) {
            SystemRestartUtils.restartSystemUI(getContext());
        }
    }

    private void updateWifiIconStyle() {
        updateStyle(KEY_WIFI_ICON_STYLE, "android.theme.customization.wifi_icon", 
                "com.android.systemui", 0, WIFI_ICON_OVERLAYS, true);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();
        int value = 0;
        
        if (preference == mVolumeDialogType) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            int val = Integer.parseInt((String) newValue);
            if (mShowVolumePercentage != null) {
                mShowVolumePercentage.setVisible(val == 1);
            }
            return true;
        }
        
        if (preference == mWifiIconStyle) {
            value = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(resolver,
                    KEY_WIFI_ICON_STYLE, value, UserHandle.USER_CURRENT);
            updateWifiIconStyle();
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

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lunaris_settings_themes) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    final String displayCutout =
                        context.getResources().getString(com.android.internal.R.string.config_mainBuiltInDisplayCutout);

                    if (TextUtils.isEmpty(displayCutout)) {
                        keys.add(KEY_FORCE_FULL_SCREEN);
                    }

                    boolean mSmartPixelsSupported = context.getResources().getBoolean(
                            com.android.internal.R.bool.config_supportSmartPixels);
                    if (!mSmartPixelsSupported)
                        keys.add(SMART_PIXELS);

                    boolean withGoogleApps = android.os.SystemProperties.getBoolean("with_google_apps", false);
                    if (!withGoogleApps)
                        keys.add(KEY_QUICKSWITCH);

                    return keys;
                }
            };
}
