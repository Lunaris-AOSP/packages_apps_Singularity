package org.lunaris.settings;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class TopLevelLunarisSettingsPreferenceController extends BasePreferenceController {

    public TopLevelLunarisSettingsPreferenceController(Context context,
            String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
}
