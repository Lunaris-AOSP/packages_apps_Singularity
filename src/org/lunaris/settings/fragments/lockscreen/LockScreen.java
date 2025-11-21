/*
 * Copyright (C) 2023-2024 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lunaris.settings.fragments.lockscreen;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.utils.SystemUtils;

import java.util.List;

import com.android.internal.util.android.VibrationUtils;

@SearchIndexable
public class LockScreen extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "LockScreen";

    private static final String KEY_KG_USER_SWITCHER= "kg_user_switcher_enabled";
    private static final String KEY_DOZE_ANIMATION = "screen_animation_enabled";
    private static final String CATEGORY_UDFPS_CUSTOM = "lockscreen_custom_category";
    private static final String PROP_CUSTOM_UDFPS = "lunaris_udfps_custom";
    private static final String KEY_MEDIA_ART_FILTER = "ls_media_art_filter";
    private static final String KEY_PIXEL_SIZE = "ls_media_art_pixel_size";
    private static final int FILTER_PIXELATION = 7;

    private Preference mUserSwitcher;
    private Preference mDozeAnimation;
    private PreferenceCategory mUdfpsCategory;
    private ListPreference mMediaArtFilter;
    private Preference mPixelSize;

    private ContentObserver mMediaFilterObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updatePixelSizeVisibility();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lunaris_settings_lock_screen);

        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        mUserSwitcher = (Preference) findPreference(KEY_KG_USER_SWITCHER);
        if (mUserSwitcher != null) {
            mUserSwitcher.setOnPreferenceChangeListener(this);
        }

        mDozeAnimation = (Preference) findPreference(KEY_DOZE_ANIMATION);
        if (mDozeAnimation != null) {
            mDozeAnimation.setOnPreferenceChangeListener(this);
        }

        mUdfpsCategory = findPreference(CATEGORY_UDFPS_CUSTOM);
        if (mUdfpsCategory != null) {
            boolean showUdfps = SystemProperties.getBoolean(PROP_CUSTOM_UDFPS, false);
            if (!showUdfps) {
                prefScreen.removePreference(mUdfpsCategory);
            }
        }

        mMediaArtFilter = (ListPreference) findPreference(KEY_MEDIA_ART_FILTER);
        mPixelSize = findPreference(KEY_PIXEL_SIZE);
        
        if (mMediaArtFilter != null) {
            mMediaArtFilter.setOnPreferenceChangeListener(this);
        }
        
        updatePixelSizeVisibility();
        
        if (resolver != null) {
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.LS_MEDIA_ART_FILTER),
                false,
                mMediaFilterObserver,
                UserHandle.USER_CURRENT
            );
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePixelSizeVisibility();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Context context = getContext();
        if (context != null && context.getContentResolver() != null) {
            context.getContentResolver().unregisterContentObserver(mMediaFilterObserver);
        }
    }

    private void updatePixelSizeVisibility() {
        if (mPixelSize == null) return;
        
        Context context = getContext();
        if (context == null) return;
        
        final ContentResolver resolver = context.getContentResolver();
        int currentFilter = Settings.System.getIntForUser(
            resolver,
            Settings.System.LS_MEDIA_ART_FILTER,
            0,
            UserHandle.USER_CURRENT
        );
        
        mPixelSize.setVisible(currentFilter == FILTER_PIXELATION);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mUserSwitcher || preference == mDozeAnimation) {
            Context context = getContext();
            if (context != null) {
                SystemUtils.showSystemUiRestartDialog(context);
            }
            return true;
        } else if (preference == mMediaArtFilter) {
            int filterValue = Integer.parseInt((String) newValue);
            if (mPixelSize != null) {
                mPixelSize.setVisible(filterValue == FILTER_PIXELATION);
            }
            return true;
        }
        return false;
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
        new BaseSearchIndexProvider(R.xml.lunaris_settings_lock_screen) {

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                List<String> keys = super.getNonIndexableKeys(context);
                final Resources resources = context.getResources();
                
                ContentResolver resolver = context.getContentResolver();
                int currentFilter = Settings.System.getIntForUser(
                    resolver,
                    Settings.System.LS_MEDIA_ART_FILTER,
                    0,
                    UserHandle.USER_CURRENT
                );
                
                if (currentFilter != 7) {
                    keys.add(KEY_PIXEL_SIZE);
                }
                
                return keys;
            }
        };
}