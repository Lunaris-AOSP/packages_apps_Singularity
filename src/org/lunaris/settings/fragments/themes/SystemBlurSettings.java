/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.themes;

import android.os.Bundle;
import android.provider.Settings;

import com.android.internal.graphics.drawable.AxBlurCustomization;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class SystemBlurSettings extends SettingsPreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.system_blur_settings);
        findPreference(AxBlurCustomization.TINT_MODE).setOnPreferenceChangeListener(
                (preference, value) -> {
                    updateCustomColor(Integer.parseInt((String) value));
                    return true;
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCustomColor(Settings.Global.getInt(requireContext().getContentResolver(),
                AxBlurCustomization.TINT_MODE, AxBlurCustomization.TINT_ACCENT));
    }

    private void updateCustomColor(int mode) {
        findPreference(AxBlurCustomization.TINT_COLOR).setVisible(
                mode == AxBlurCustomization.TINT_CUSTOM);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.system_blur_settings);
}
