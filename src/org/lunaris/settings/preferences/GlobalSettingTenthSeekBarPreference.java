/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;
import java.text.NumberFormat;

public class GlobalSettingTenthSeekBarPreference extends GlobalSettingSeekBarPreference {
    public GlobalSettingTenthSeekBarPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public GlobalSettingTenthSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GlobalSettingTenthSeekBarPreference(Context context) {
        super(context);
    }

    @Override
    protected String getTextValue(int value) {
        if (mDefaultValueTextExists && mDefaultValueExists && value == mDefaultValue) {
            return mDefaultValueText;
        }
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(1);
        return format.format(value / 10f) + mUnits;
    }
}
