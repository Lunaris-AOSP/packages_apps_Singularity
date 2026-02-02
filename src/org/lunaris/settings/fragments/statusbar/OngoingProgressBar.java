package org.lunaris.settings.fragments.statusbar;

import android.content.Context;
import android.os.Bundle;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import androidx.preference.Preference;

import com.android.internal.util.lunaris.VibrationUtils;

import java.util.List;

@SearchIndexable
public class OngoingProgressBar extends SettingsPreferenceFragment {

    public static final String TAG = "OngoingProgressBar";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.ongoing_progress_settings);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
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
            new BaseSearchIndexProvider(R.xml.ongoing_progress_settings) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    return keys;
                }
            };
}