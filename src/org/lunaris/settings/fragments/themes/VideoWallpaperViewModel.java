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
package org.lunaris.settings.fragments.themes;

import android.app.Application;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.settings.R;

import org.lunaris.settings.utils.MediaUtils;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoWallpaperViewModel extends AndroidViewModel {
    private static final String TAG = "VideoWallpaperSettings";
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> mBusy = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> mResult = new MutableLiveData<>(0);

    public VideoWallpaperViewModel(Application application) {
        super(application);
    }

    public LiveData<Boolean> getBusy() {
        return mBusy;
    }

    public LiveData<Integer> getResult() {
        return mResult;
    }

    public void consumeResult() {
        mResult.setValue(0);
    }

    public boolean isBusy() {
        return Boolean.TRUE.equals(mBusy.getValue());
    }

    public void importWallpaper(Uri uri) {
        if (isBusy()) return;
        mBusy.setValue(true);
        mExecutor.execute(() -> {
            int result = R.string.video_wallpaper_saved;
            try {
                MediaUtils.saveMediaToWallpaperStorage(getApplication(), uri);
            } catch (MediaUtils.FileTooLargeException e) {
                result = R.string.video_wallpaper_too_large;
            } catch (MediaUtils.UnsupportedMediaException e) {
                result = R.string.video_wallpaper_unsupported;
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Could not import wallpaper", e);
                result = R.string.video_wallpaper_save_failed;
            } finally {
                mBusy.postValue(false);
            }
            mResult.postValue(result);
        });
    }

    public void clearWallpaper() {
        if (isBusy()) return;
        mBusy.setValue(true);
        mExecutor.execute(() -> {
            int result = R.string.video_wallpaper_cleared;
            try {
                Context context = getApplication();
                int flags = getActiveWallpaperFlags(context);
                // Detach only the screens using this service before deleting its media.
                if (flags != 0) WallpaperManager.getInstance(context).clear(flags);
                if (!MediaUtils.deleteCurrentWallpaper(context)) {
                    result = R.string.video_wallpaper_clear_failed;
                }
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Could not clear wallpaper", e);
                result = R.string.video_wallpaper_clear_failed;
            } finally {
                mBusy.postValue(false);
            }
            mResult.postValue(result);
        });
    }

    public static int getActiveWallpaperFlags(Context context) {
        WallpaperManager manager = WallpaperManager.getInstance(context);
        ComponentName component = new ComponentName(context, VideoWallpaperService.class);
        WallpaperInfo home = manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM);
        WallpaperInfo lock = manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
        int flags = 0;
        if (home != null && component.equals(home.getComponent())) {
            flags |= WallpaperManager.FLAG_SYSTEM;
            // In base, no separate lock wallpaper means it shares the home wallpaper.
            if (manager.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0) {
                flags |= WallpaperManager.FLAG_LOCK;
            }
        }
        if (lock != null && component.equals(lock.getComponent())) {
            flags |= WallpaperManager.FLAG_LOCK;
        }
        return flags;
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdown();
    }
}
