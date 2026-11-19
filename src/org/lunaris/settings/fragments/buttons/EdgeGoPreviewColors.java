/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 */
package org.lunaris.settings.fragments.buttons;

import android.content.Context;
import android.content.res.Configuration;

import androidx.core.graphics.ColorUtils;

import com.android.internal.util.lunaris.EdgeGoConfig;

final class EdgeGoPreviewColors {
    private static boolean dark(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static int accent(Context context, EdgeGoConfig config) {
        return config.bool("material_you_accent_enabled")
                ? context.getColor(dark(context) ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600)
                : config.integer(dark(context) ? "ui_accent_dark_rgb" : "ui_accent_light_rgb") | 0xff000000;
    }

    static int text(Context context) {
        return context.getColor(dark(context) ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_900);
    }

    static int surface(Context context, EdgeGoConfig config) {
        int surface = context.getColor(dark(context) ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50);
        return ColorUtils.blendARGB(surface, accent(context, config), .08f);
    }
}
