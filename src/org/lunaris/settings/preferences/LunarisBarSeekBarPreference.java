/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;

/** Uses the same seek bar and reset controls as the other settings pages. */
public class LunarisBarSeekBarPreference extends CustomSeekBarPreference {
    public LunarisBarSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setValue(int value) {
        super.setValue(value);
        notifyChanged();
    }

    @Override
    protected String getTextValue(int value) {
        String key = getKey();
        if (key == null) return super.getTextValue(value);
        if (key.equals("animation_speed")) return (value / 100f) + "×";
        if (key.equals("outline_width")) return (value / 10f) + " dp";
        if (key.contains("scale") || key.contains("opacity")) return value + "%";
        if (key.endsWith("delay") || key.equals("hide_after")) return value + " s";
        if (key.equals("minutes")) return getContext().getString(
                com.android.settings.R.string.lunaris_bar_minutes_value, value);
        if (key.equals("columns")) return Integer.toString(value);
        return value + " dp";
    }
}
