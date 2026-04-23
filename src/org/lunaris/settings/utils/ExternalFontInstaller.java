/*
 * Copyright (C) 2025 AxionOS
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lunaris.settings.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamilyUpdateRequest;
import android.graphics.fonts.FontFileUpdateRequest;
import android.graphics.fonts.FontFileUtil;
import android.graphics.fonts.FontManager;
import android.graphics.fonts.FontStyle;
import android.graphics.fonts.FontVariationAxis;
import android.net.Uri;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.statusbar.IStatusBarService;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class ExternalFontInstaller {

    private static final String TAG = "ExternalFontInstaller";

    private static final String CUSTOM_FONT_FILE = "cust_font.ttf";
    private static final String TEMP_PREVIEW_FONT = "preview_font.ttf";

    private static final String OVERLAY_CATEGORY_FONT = "android.theme.customization.font";
    public static final String DEFAULT_FONT_FAMILY = "ext_font";
    private static final String DEFAULT_FONT_OVERLAY = "com.android.theme.font.extfont";

    private static final String PROP_OVERLAY_FONTS = "persist.sys.ax_overlay_fonts";

    private static final int[] FONT_WEIGHTS = {
        100, 200, 300, 400, 500, 600, 700, 800, 900
    };

    private final Context mContext;
    private final FontManager mFontManager;

    public ExternalFontInstaller(Context context) {
        mContext = context;
        mFontManager = context.getSystemService(FontManager.class);
    }

    public static void rebootDevice() {
        try {
            android.os.IBinder binder = ServiceManager.getService("statusbar");
            IStatusBarService svc = IStatusBarService.Stub.asInterface(binder);
            svc.reboot(false, "system_font_change");
        } catch (Exception e) {
            Log.e(TAG, "Failed to reboot device via statusbar service", e);
        }
    }

    public Typeface loadTypefaceFromUri(Uri uri) {
        File tempFile = copyUriToCache(uri, TEMP_PREVIEW_FONT);
        if (tempFile == null) return null;

        String postScriptName = extractPostScriptName(tempFile);
        if (postScriptName == null) {
            tempFile.delete();
            return null;
        }
        return Typeface.createFromFile(tempFile);
    }

    public Typeface loadTypefaceFromFile(File fontFile) {
        if (fontFile == null || !fontFile.exists()) return null;
        String postScriptName = extractPostScriptName(fontFile);
        if (postScriptName == null) return null;
        return Typeface.createFromFile(fontFile);
    }

    public String installFontFromUri(Uri uri) {
        File fontFile = copyUriToCache(uri, CUSTOM_FONT_FILE);
        if (fontFile == null) return null;

        String postScriptName = extractPostScriptName(fontFile);
        if (postScriptName == null) {
            fontFile.delete();
            return null;
        }

        if (!applyFontToSystem(fontFile, postScriptName)) {
            fontFile.delete();
            return null;
        }

        setOverlayFontProp();
        updateThemeOverlays();
        cleanupPreviewFont();
        return postScriptName;
    }

    public String installFontFromFile(File sourceFile) {
        try {
            File fontFile = new File(mContext.getCacheDir(), CUSTOM_FONT_FILE);
            try (FileInputStream in = new FileInputStream(sourceFile);
                 FileOutputStream out = new FileOutputStream(fontFile)) {
                FileUtils.copy(in, out);
            }

            String postScriptName = extractPostScriptName(fontFile);
            if (postScriptName == null) {
                fontFile.delete();
                return null;
            }

            if (!applyFontToSystem(fontFile, postScriptName)) {
                fontFile.delete();
                return null;
            }

            setOverlayFontProp();
            updateThemeOverlays();
            cleanupPreviewFont();

            if (sourceFile.getAbsolutePath().startsWith(
                    mContext.getCacheDir().getAbsolutePath())) {
                sourceFile.delete();
            }

            return postScriptName;
        } catch (Exception e) {
            Log.e(TAG, "installFontFromFile failed", e);
            return null;
        }
    }

    public void resetFontUpdates() {
        try {
            mFontManager.clearUpdates();
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear FontManager updates", e);
        }
        SystemProperties.set(PROP_OVERLAY_FONTS, "");
        cleanupPreviewFont();
    }

    private void setOverlayFontProp() {
        String prop = DEFAULT_FONT_FAMILY + ":" + DEFAULT_FONT_FAMILY
                + ":" + DEFAULT_FONT_FAMILY + ":" + DEFAULT_FONT_FAMILY;
        SystemProperties.set(PROP_OVERLAY_FONTS, prop);
    }

    private File copyUriToCache(Uri uri, String fileName) {
        try {
            File cacheFile = new File(mContext.getCacheDir(), fileName);
            ParcelFileDescriptor pfd =
                    mContext.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) return null;
            try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(cacheFile)) {
                FileUtils.copy(in, out);
            }
            pfd.close();
            return cacheFile;
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy URI to cache", e);
            return null;
        }
    }

    private String extractPostScriptName(File fontFile) {
        try (FileInputStream fis = new FileInputStream(fontFile)) {
            FileChannel channel = fis.getChannel();
            java.nio.MappedByteBuffer buf =
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            return FontFileUtil.getPostScriptName(buf, 0);
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract PostScript name from " + fontFile.getName(), e);
            return null;
        }
    }

    private boolean isVariableFont(File fontFile) {
        try {
            Font font = new Font.Builder(fontFile).build();
            FontVariationAxis[] axes = font.getAxes();
            if (axes == null) return false;
            for (FontVariationAxis axis : axes) {
                if ("wght".equals(axis.getTag())) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean applyFontToSystem(File fontFile, String postScriptName) {
        try {
            mFontManager.clearUpdates();

            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                    fontFile, ParcelFileDescriptor.MODE_READ_ONLY);
            FontFileUpdateRequest fileRequest = new FontFileUpdateRequest(pfd, new byte[0]);

            boolean isVariable = isVariableFont(fontFile);
            List<FontFamilyUpdateRequest.Font> fonts = new ArrayList<>();

            if (isVariable) {
                for (int weight : FONT_WEIGHTS) {
                    List<FontVariationAxis> axes = new ArrayList<>();
                    axes.add(new FontVariationAxis("wght", weight));

                    fonts.add(new FontFamilyUpdateRequest.Font.Builder(
                            postScriptName,
                            new FontStyle(weight, FontStyle.FONT_SLANT_UPRIGHT))
                            .setAxes(axes).build());

                    fonts.add(new FontFamilyUpdateRequest.Font.Builder(
                            postScriptName,
                            new FontStyle(weight, FontStyle.FONT_SLANT_ITALIC))
                            .setAxes(axes).build());
                }
            } else {
                fonts.add(new FontFamilyUpdateRequest.Font.Builder(
                        postScriptName,
                        new FontStyle(FontStyle.FONT_WEIGHT_NORMAL,
                                FontStyle.FONT_SLANT_UPRIGHT))
                        .build());
                fonts.add(new FontFamilyUpdateRequest.Font.Builder(
                        postScriptName,
                        new FontStyle(FontStyle.FONT_WEIGHT_BOLD,
                                FontStyle.FONT_SLANT_UPRIGHT))
                        .build());
            }

            FontFamilyUpdateRequest.FontFamily family =
                    new FontFamilyUpdateRequest.FontFamily.Builder(
                            DEFAULT_FONT_FAMILY, fonts).build();

            FontFamilyUpdateRequest updateRequest = new FontFamilyUpdateRequest.Builder()
                    .addFontFileUpdateRequest(fileRequest)
                    .addFontFamily(family)
                    .build();

            int result = mFontManager.updateFontFamily(
                    updateRequest,
                    mFontManager.getFontConfig().getConfigVersion());

            if (result != FontManager.RESULT_SUCCESS) {
                Log.e(TAG, "FontManager.updateFontFamily failed with code " + result);
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "applyFontToSystem threw an exception", e);
            return false;
        }
    }

    private void updateThemeOverlays() {
        try {
            int userId = UserHandle.myUserId();
            String current = Settings.Secure.getStringForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                    userId);

            JSONObject json = (current == null || current.isEmpty())
                    ? new JSONObject()
                    : new JSONObject(current);

            if (json.has(OVERLAY_CATEGORY_FONT)) {
                json.remove(OVERLAY_CATEGORY_FONT);
                Settings.Secure.putStringForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                        json.toString(),
                        userId);
            }

            json.put(OVERLAY_CATEGORY_FONT, DEFAULT_FONT_OVERLAY);
            Settings.Secure.putStringForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                    json.toString(),
                    userId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist extfont overlay", e);
        }
    }

    private void cleanupPreviewFont() {
        try {
            File f = new File(mContext.getCacheDir(), TEMP_PREVIEW_FONT);
            if (f.exists()) f.delete();
        } catch (Exception e) {
            Log.e(TAG, "Failed to cleanup preview font", e);
        }
    }
}
