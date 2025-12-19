/*
 * Copyright (C) 2024 crDroid Android Project
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
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.android.internal.util.android.VibrationUtils;

import org.lunaris.settings.utils.SystemRestartUtils;
import org.lunaris.settings.utils.SystemUtils;

import java.util.List;

@SearchIndexable
public class LayoutSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "LayoutSettings";

    private static final String KEY_QS_TILE_SHAPE_STYLE = "qs_tile_shape_style";
    private static final String KEY_QS_TILE_HORIZONTAL_SPACING = "qs_tile_horizontal_spacing";
    private static final String KEY_QS_TILE_VERTICAL_SPACING = "qs_tile_vertical_spacing";

    private Preference mQsTileShapeStyle;
    private Preference mQsTileHorizontalSpacing;
    private Preference mQsTileVerticalSpacing;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.qs_layout_settings);

        mQsTileShapeStyle = findPreference(KEY_QS_TILE_SHAPE_STYLE);
        if (mQsTileShapeStyle != null) {
            mQsTileShapeStyle.setOnPreferenceChangeListener(this);
        }

        mQsTileHorizontalSpacing = findPreference(KEY_QS_TILE_HORIZONTAL_SPACING);
        if (mQsTileHorizontalSpacing != null) {
            mQsTileHorizontalSpacing.setOnPreferenceChangeListener(this);
        }

        mQsTileVerticalSpacing = findPreference(KEY_QS_TILE_VERTICAL_SPACING);
        if (mQsTileVerticalSpacing != null) {
            mQsTileVerticalSpacing.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mQsTileShapeStyle) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mQsTileHorizontalSpacing) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        } else if (preference == mQsTileVerticalSpacing) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.LUNARIS;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(getContext(), 3);
        }
        return super.onPreferenceTreeClick(preference);
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.qs_layout_settings) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    return keys;
                }
            };
}
