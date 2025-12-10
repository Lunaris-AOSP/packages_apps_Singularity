/*
 * Copyright (C) 2024-2025 Lunaris AOSP
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.android.ThemeUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.List;

import org.lunaris.settings.preferences.GlobalSettingListPreference;
import org.lunaris.settings.preferences.SystemSettingListPreference;
import org.lunaris.settings.utils.SystemRestartUtils;
import org.lunaris.settings.utils.SystemUtils;

import com.android.internal.util.android.VibrationUtils;

@SearchIndexable
public class Themes extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "Themes";

    private static final String KEY_LOCK_SOUND = "lock_sound";
    private static final String KEY_UNLOCK_SOUND = "unlock_sound";
    private static final String KEY_ANIMATIONS_CATEGORY = "themes_animations_category";
    private static final String KEY_POWERMENU_STYLE = "powermenu_style";
    private static final String KEY_FONT_SETTINGS = "font_settings";

    private static final String[] POWER_MENU_OVERLAYS = {
            "com.android.theme.powermenu.cyberpunk",
            "com.android.theme.powermenu.duoline",
            "com.android.theme.powermenu.fluid",
            "com.android.theme.powermenu.ios",
            "com.android.theme.powermenu.layers"
    };

    private GlobalSettingListPreference mLockSound;
    private GlobalSettingListPreference mUnlockSound;
    private PreferenceCategory mAnimationsCategory;
    private SystemSettingListPreference mPowerMenuStylePref;
    private Preference mFontSettingsPref;
    private ThemeUtils mThemeUtils;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lunaris_settings_themes);
        mThemeUtils = ThemeUtils.getInstance(getActivity());

        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources resources = context.getResources();

        mLockSound = (GlobalSettingListPreference) findPreference(KEY_LOCK_SOUND);
        mLockSound.setOnPreferenceChangeListener(this);
        
        mUnlockSound = (GlobalSettingListPreference) findPreference(KEY_UNLOCK_SOUND);
        mUnlockSound.setOnPreferenceChangeListener(this);
        
        mAnimationsCategory = (PreferenceCategory) findPreference(KEY_ANIMATIONS_CATEGORY);

        mPowerMenuStylePref = findPreference(KEY_POWERMENU_STYLE);
        mPowerMenuStylePref.setOnPreferenceChangeListener(this);

        mFontSettingsPref = findPreference(KEY_FONT_SETTINGS);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(getContext(), 3);
        }
        return super.onPreferenceTreeClick(preference);
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

    private void updatePowerMenuStyle() {
        updateStyle(KEY_POWERMENU_STYLE, "android.theme.customization.powermenu", 
                "com.android.systemui", 0, POWER_MENU_OVERLAYS, false);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();
        
        int value = 0;
        if (newValue instanceof String) {
            try {
                value = Integer.parseInt((String) newValue);
            } catch (NumberFormatException e) {
                if (preference == mLockSound || preference == mUnlockSound) {
                    SystemUtils.showSystemUiRestartDialog(context);
                    return true;
                }
                return false;
            }
        }
        
        if (preference == mPowerMenuStylePref) {
            Settings.System.putIntForUser(getActivity().getContentResolver(),
                    KEY_POWERMENU_STYLE, value, UserHandle.USER_CURRENT);
            updatePowerMenuStyle();
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider(R.xml.lunaris_settings_themes) {

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                List<String> keys = super.getNonIndexableKeys(context);
                final Resources resources = context.getResources();
                
                return keys;
            }
        };
}