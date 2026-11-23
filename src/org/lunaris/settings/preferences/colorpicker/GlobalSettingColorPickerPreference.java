/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.preferences.colorpicker;

import android.content.Context;
import android.util.AttributeSet;

import org.lunaris.settings.preferences.GlobalSettingsStore;

public class GlobalSettingColorPickerPreference extends ColorPickerPreference {
    public GlobalSettingColorPickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPreferenceDataStore(new GlobalSettingsStore(context.getContentResolver()));
    }

    public GlobalSettingColorPickerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlobalSettingColorPickerPreference(Context context) {
        this(context, null);
    }
}
