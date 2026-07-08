/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 * Copyright (C) 2025-2026 Lunaris OS
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

package org.lunaris.settings.fragments.lockscreen;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.utils.ImageUtils;
import org.lunaris.settings.utils.WallpaperSubjectExtractorService;

import java.io.File;
import java.util.List;

@SearchIndexable
public class WallpaperDepth extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    public static final String TAG = "WallpaperDepth";

    private static final int REQUEST_PICK_IMAGE = 10001;
    private static final int REQUEST_WRITE_STORAGE = 10002;

    private static final String FILE_PREFIX = "DEPTH_WALLPAPER_SUBJECT";
    private static final String SAVE_DIR = "Lunaris-OS/depthwallpaper";

    private Preference mDepthWallpaperCustomImagePicker;
    private Preference mExtractNowPref;
    private Preference mClearSubjectPref;
    private Preference mAutoSubjectPref;

    private boolean mPendingExtraction = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wallpaper_depth);

        mDepthWallpaperCustomImagePicker = findPreference("depth_wallpaper_subject_image_uri");
        mExtractNowPref = findPreference("depth_wallpaper_extract_now");
        mClearSubjectPref = findPreference("depth_wallpaper_clear_subject");
        mAutoSubjectPref = findPreference("depth_wallpaper_auto_subject");

        updateClearSubjectState();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mDepthWallpaperCustomImagePicker) {
            launchImagePicker();
            return true;
        }
        if (preference == mExtractNowPref) {
            triggerExtraction();
            return true;
        }
        if (preference == mClearSubjectPref) {
            confirmClearSubject();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK && result != null) {
            final Uri imgUri = result.getData();
            if (imgUri != null) {
                String path = ImageUtils.saveImageToInternalStorage(
                        getContext(), imgUri, "depthwallpaper", "DEPTH_WALLPAPER_SUBJECT");
                if (path != null) {
                    Settings.System.putStringForUser(
                            getContext().getContentResolver(),
                            "depth_wallpaper_subject_image_uri",
                            path,
                            UserHandle.USER_CURRENT);
                    updateClearSubjectState();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mPendingExtraction) {
                    mPendingExtraction = false;
                    startExtractionService();
                }
            } else {
                mPendingExtraction = false;
                Toast.makeText(getContext(),
                        R.string.depthwall_storage_permission_denied,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void launchImagePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(getContext(),
                    R.string.quick_settings_header_needs_gallery,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void triggerExtraction() {
        Context ctx = getContext();
        if (ctx == null) return;

        if (ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            mPendingExtraction = true;
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
            return;
        }

        startExtractionService();
    }

    private void startExtractionService() {
        Context ctx = getContext();
        if (ctx == null) return;

        Toast.makeText(ctx, R.string.depthwall_extracting_toast, Toast.LENGTH_SHORT).show();

        try {
            Intent intent = new Intent(ctx, WallpaperSubjectExtractorService.class);
            intent.setAction(WallpaperSubjectExtractorService.ACTION_EXTRACT_NOW);
            intent.setComponent(new ComponentName(ctx, WallpaperSubjectExtractorService.class));
            ctx.startService(intent);
        } catch (Exception e) {
            Toast.makeText(ctx, "Failed to start extractor: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClearSubject() {
        Context ctx = getContext();
        if (ctx == null) return;

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.depthwall_clear_subject_title)
                .setMessage(R.string.depthwall_clear_subject_confirm_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> clearSubject())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void clearSubject() {
        Context ctx = getContext();
        if (ctx == null) return;

        Settings.System.putStringForUser(
                ctx.getContentResolver(),
                "depth_wallpaper_subject_image_uri",
                null,
                UserHandle.USER_CURRENT);

        try {
            File dir = new File(Environment.getExternalStorageDirectory(), SAVE_DIR);
            if (dir.exists()) {
                File[] files = dir.listFiles((d, name) ->
                        name.startsWith(FILE_PREFIX) && name.endsWith(".png"));
                if (files != null) {
                    for (File f : files) f.delete();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "clearSubject: failed to delete cached files", e);
        }

        Toast.makeText(ctx, R.string.depthwall_clear_subject_done_toast, Toast.LENGTH_SHORT).show();
        updateClearSubjectState();
    }

    private void updateClearSubjectState() {
        if (mClearSubjectPref == null) return;
        Context ctx = getContext();
        if (ctx == null) return;
        String uri = Settings.System.getStringForUser(
                ctx.getContentResolver(),
                "depth_wallpaper_subject_image_uri",
                UserHandle.USER_CURRENT);
        mClearSubjectPref.setEnabled(uri != null && !uri.isEmpty());
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.wallpaper_depth) {
                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    return super.getNonIndexableKeys(context);
                }
            };
}
