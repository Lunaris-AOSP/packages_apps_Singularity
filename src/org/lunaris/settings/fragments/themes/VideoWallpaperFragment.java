/*
 * Copyright (C) 2024-2025 Lunaris AOSP
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
package org.lunaris.settings.fragments.themes;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.lunaris.VibrationUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.utils.MediaUtils;

import java.io.File;

@SearchIndexable
public class VideoWallpaperFragment extends SettingsPreferenceFragment {
    private static final String TAG = "VideoWallpaperFragment";

    private Preference mPickVideoPref;
    private Preference mApplyWallpaperPref;
    private Preference mClearWallpaperPref;
    private Preference mCurrentWallpaperPref;
    private ListPreference mPlaybackSpeedPref;
    private SwitchPreferenceCompat mPauseOnLockPref;
    private ActivityResultLauncher<String[]> mVideoPickerLauncher;
    private VideoWallpaperViewModel mModel;
    private SharedPreferences mPrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use the same preference store as the service, including framework persistence.
        getPreferenceManager().setSharedPreferencesName(MediaUtils.PREFS_NAME);
        addPreferencesFromResource(R.xml.video_wallpaper_settings);
        mPrefs = MediaUtils.getPreferences(requireContext());
        mModel = new ViewModelProvider(this).get(VideoWallpaperViewModel.class);

        mPickVideoPref = findPreference("pick_video_wallpaper");
        mApplyWallpaperPref = findPreference("enable_video_wallpaper");
        mClearWallpaperPref = findPreference("clear_video_wallpaper");
        mCurrentWallpaperPref = findPreference("current_video_wallpaper");
        mPlaybackSpeedPref = findPreference(MediaUtils.KEY_PLAYBACK_SPEED);
        mPauseOnLockPref = findPreference(MediaUtils.KEY_PAUSE_ON_LOCK);

        mVideoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) mModel.importWallpaper(uri);
                });
        mPickVideoPref.setOnPreferenceClickListener(preference -> {
            try {
                mVideoPickerLauncher.launch(new String[] {"video/mp4", "image/gif", "image/webp"});
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not open media picker", e);
                Toast.makeText(requireContext(), R.string.video_wallpaper_picker_error,
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        mApplyWallpaperPref.setOnPreferenceClickListener(preference -> {
            showWallpaperPreview();
            return true;
        });
        mClearWallpaperPref.setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.video_wallpaper_clear_confirm_title)
                    .setMessage(R.string.video_wallpaper_clear_confirm_message)
                    .setPositiveButton(R.string.video_wallpaper_clear_action,
                            (dialog, which) -> mModel.clearWallpaper())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
        mModel.getBusy().observe(this, busy -> updatePreferences());
        mModel.getResult().observe(this, message -> {
            if (message != null && message != 0) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                mModel.consumeResult();
                updatePreferences();
            }
        });
    }

    private void showWallpaperPreview() {
        if (MediaUtils.getCurrentWallpaperFile(requireContext()) == null) {
            updatePreferences();
            return;
        }
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(requireContext(), VideoWallpaperService.class));
            startActivity(intent);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not open wallpaper preview", e);
            Toast.makeText(requireContext(), R.string.video_wallpaper_enable_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePreferences() {
        Context context = requireContext();
        File file = MediaUtils.getCurrentWallpaperFile(context);
        boolean hasWallpaper = file != null;
        boolean busy = mModel.isBusy();
        WallpaperManager manager = WallpaperManager.getInstance(context);
        boolean canSet = manager.isWallpaperSupported() && manager.isSetWallpaperAllowed();
        int activeFlags = VideoWallpaperViewModel.getActiveWallpaperFlags(context);

        mCurrentWallpaperPref.setSummary(hasWallpaper
                ? getString(R.string.video_wallpaper_current_summary_dynamic,
                        mPrefs.getString(MediaUtils.KEY_WALLPAPER_NAME, file.getName()),
                        Formatter.formatShortFileSize(context, file.length()))
                : getString(MediaUtils.hasWallpaperSelection(context)
                        ? R.string.video_wallpaper_missing : R.string.video_wallpaper_current_none));
        mPickVideoPref.setSummary(busy ? R.string.video_wallpaper_processing
                : R.string.video_wallpaper_pick_summary);
        mPickVideoPref.setEnabled(!busy && canSet);
        mApplyWallpaperPref.setEnabled(hasWallpaper && !busy && canSet);
        int applySummary = R.string.video_wallpaper_enable_summary;
        if (activeFlags == (WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK)) {
            applySummary = R.string.video_wallpaper_active_both;
        } else if (activeFlags == WallpaperManager.FLAG_SYSTEM) {
            applySummary = R.string.video_wallpaper_active_home;
        } else if (activeFlags == WallpaperManager.FLAG_LOCK) {
            applySummary = R.string.video_wallpaper_active_lock;
        }
        mApplyWallpaperPref.setSummary(applySummary);
        mClearWallpaperPref.setEnabled(!busy && canSet
                && (MediaUtils.hasWallpaperSelection(context) || activeFlags != 0));
        boolean isVideo = hasWallpaper && MediaUtils.isVideoFormat(file.getName());
        mPlaybackSpeedPref.setEnabled(isVideo && !busy);
        mPlaybackSpeedPref.setSummary(isVideo ? "%s"
                : getString(R.string.video_wallpaper_speed_video_only));
        String speed = mPrefs.getString(MediaUtils.KEY_PLAYBACK_SPEED, "1.0");
        mPlaybackSpeedPref.setValue(mPlaybackSpeedPref.findIndexOfValue(speed) >= 0 ? speed : "1.0");
        mPauseOnLockPref.setEnabled(hasWallpaper && !busy);
        mPauseOnLockPref.setChecked(mPrefs.getBoolean(MediaUtils.KEY_PAUSE_ON_LOCK, true));
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(getContext(), 3);
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.video_wallpaper_settings);
}
