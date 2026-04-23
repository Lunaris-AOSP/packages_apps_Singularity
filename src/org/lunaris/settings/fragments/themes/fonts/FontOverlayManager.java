/*
 * Copyright (C) 2024-2026 Lunaris AOSP
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
package org.lunaris.settings.fragments.themes.fonts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.util.lunaris.ThemeUtils;

import org.json.JSONException;
import org.json.JSONObject;

public class FontOverlayManager {

    private static final String TAG = "FontOverlayManager";

    public static final String OVERLAY_CATEGORY_FONT = "android.theme.customization.font";

    private static final String PROP_OVERLAY_FONTS = "persist.sys.ax_overlay_fonts";

    private static final String CONFIG_BODY = "config_bodyFontFamily";
    private static final String CONFIG_BODY_MEDIUM = "config_bodyFontFamilyMedium";
    private static final String CONFIG_HEADLINE = "config_headlineFontFamily";
    private static final String CONFIG_HEADLINE_MEDIUM = "config_headlineFontFamilyMedium";

    private final Context mContext;
    private final ThemeUtils mThemeUtils;
    private final PackageManager mPm;

    public FontOverlayManager(Context context) {
        mContext = context;
        mThemeUtils = ThemeUtils.getInstance(context);
        mPm = context.getPackageManager();
    }

    public String getActiveOverlayPackage() {
        try {
            String raw = Settings.Secure.getStringForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                    UserHandle.USER_CURRENT);
            if (raw == null || raw.isEmpty()) return null;
            JSONObject json = new JSONObject(raw);
            String pkg = json.optString(OVERLAY_CATEGORY_FONT, null);
            return ("android".equals(pkg) || "".equals(pkg)) ? null : pkg;
        } catch (JSONException e) {
            Log.e(TAG, "getActiveOverlayPackage: JSON parse error", e);
            return null;
        }
    }

    public boolean applyOverlay(String overlayPackage) {
        final boolean isDefault = (overlayPackage == null || "android".equals(overlayPackage));
        final String pkg = isDefault ? "android" : overlayPackage;

        try {
            mThemeUtils.setOverlayEnabled(OVERLAY_CATEGORY_FONT, pkg, "android");

            if (isDefault) {
                SystemProperties.set(PROP_OVERLAY_FONTS, "");
            } else {
                writeOverlayFontProp(overlayPackage);
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "applyOverlay failed for: " + overlayPackage, e);
            return false;
        }
    }

    public boolean resetToDefault() {
        return applyOverlay(null);
    }

    private void writeOverlayFontProp(String overlayPackage) {
        try {
            Resources overlayRes = mPm.getResourcesForApplication(overlayPackage);

            String body = readResString(overlayRes, overlayPackage, CONFIG_BODY);
            String bodyMedium = readResString(overlayRes, overlayPackage, CONFIG_BODY_MEDIUM);
            String headline = readResString(overlayRes, overlayPackage, CONFIG_HEADLINE);
            String headlineMedium = readResString(overlayRes, overlayPackage, CONFIG_HEADLINE_MEDIUM);

            String value = body + ":" + bodyMedium + ":" + headline + ":" + headlineMedium;
            SystemProperties.set(PROP_OVERLAY_FONTS, value);
            Log.d(TAG, PROP_OVERLAY_FONTS + "=" + value);

        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "writeOverlayFontProp: package not found: " + overlayPackage);
        } catch (Exception e) {
            Log.w(TAG, "writeOverlayFontProp failed for " + overlayPackage, e);
        }
    }

    private String readResString(Resources overlayRes, String overlayPackage, String configName) {
        try {
            int resId = overlayRes.getIdentifier(configName, "string", overlayPackage);
            if (resId == 0) {
                Log.w(TAG, overlayPackage + " missing '" + configName + "', using pkg name");
                return overlayPackage;
            }
            return overlayRes.getString(resId);
        } catch (Exception e) {
            Log.w(TAG, "readResString failed: " + configName, e);
            return overlayPackage;
        }
    }
}
