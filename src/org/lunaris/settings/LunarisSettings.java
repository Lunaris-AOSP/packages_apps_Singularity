/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.android.settings.core.SubSettingLauncher;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import androidx.preference.Preference;

import com.android.internal.util.lunaris.VibrationUtils;

@SearchIndexable
public class LunarisSettings extends DashboardFragment {

    private static final String TAG = "LunarisSettings";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        Bundle arguments = getArguments();
        if (arguments != null && arguments.getBoolean("edgego_open", false)) {
            arguments.putBoolean("edgego_open", false);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isAdded()) new SubSettingLauncher(requireContext())
                        .setDestination("org.lunaris.settings.fragments.buttons.EdgeGoSettings")
                        .setSourceMetricsCategory(getMetricsCategory()).setTitleRes(R.string.edgego_title).launch();
            });
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.lunaris_settings;
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

    @Override
    protected String getLogTag() {
        return TAG;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lunaris_settings);
}
