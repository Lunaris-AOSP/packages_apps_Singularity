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

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserManager;
import android.service.wallpaper.WallpaperService;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.SurfaceHolder;

import com.android.settings.R;

import org.lunaris.settings.utils.MediaUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoWallpaperService extends WallpaperService {
    private static final String TAG = "VideoWallpaperService";

    @Override
    public Engine onCreateEngine() {
        return new VideoEngine();
    }

    private class VideoEngine extends Engine implements Drawable.Callback {
        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final ExecutorService mLoader = Executors.newSingleThreadExecutor();
        private final KeyguardManager mKeyguard = getSystemService(KeyguardManager.class);
        private final PowerManager mPower = getSystemService(PowerManager.class);
        private final UserManager mUsers = getSystemService(UserManager.class);
        private final Runnable mDraw = this::drawFrame;
        private SurfaceHolder mHolder;
        private MediaPlayer mPlayer;
        private AnimatedImageDrawable mAnimation;
        private SharedPreferences mPrefs;
        private boolean mPrepared;
        private boolean mVisible;
        private boolean mDestroyed;
        private boolean mLoading;
        private boolean mCanvasConnected;
        private boolean mAmbient;
        private boolean mPauseOnLock = true;
        private float mSpeed = 1f;
        private int mLoadGeneration;
        private int mMessage = R.string.video_wallpaper_current_none;

        private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener =
                (prefs, key) -> {
                    if (mDestroyed) return;
                    if (key == null || MediaUtils.KEY_WALLPAPER_PATH.equals(key)) {
                        reloadWallpaper();
                    } else if (MediaUtils.KEY_PLAYBACK_SPEED.equals(key)
                            || MediaUtils.KEY_PAUSE_ON_LOCK.equals(key)) {
                        loadSettings();
                        updatePlayback();
                    }
                };

        private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mDestroyed) return;
                if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                    initPreferences();
                    reloadWallpaper();
                } else {
                    updatePlayback();
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(false);
            setOffsetNotificationsEnabled(false);
            initPreferences();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_UNLOCKED);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(mScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }

        private void initPreferences() {
            // A direct-boot-aware host can create us before credential storage is available.
            // base rebinds other live wallpapers after unlock; neither path needs file polling.
            if (mPrefs != null || !mUsers.isUserUnlocked()) return;
            mPrefs = MediaUtils.getPreferences(VideoWallpaperService.this);
            mPrefs.registerOnSharedPreferenceChangeListener(mPrefsListener);
            loadSettings();
        }

        private void loadSettings() {
            if (mPrefs == null) return;
            mPauseOnLock = mPrefs.getBoolean(MediaUtils.KEY_PAUSE_ON_LOCK, true);
            mSpeed = MediaUtils.getPlaybackSpeed(mPrefs);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            if (visible && mPlayer == null && mAnimation == null && !mLoading) {
                reloadWallpaper();
            } else {
                updatePlayback();
            }
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode, long animationDuration) {
            mAmbient = inAmbientMode;
            updatePlayback();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            mHolder = holder;
            reloadWallpaper();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mHolder = holder;
            if (mPlayer == null) drawFrame();
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            if (mPlayer == null) drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            ++mLoadGeneration;
            mLoading = false;
            releaseRenderer();
            mHolder = null;
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            mDestroyed = true;
            ++mLoadGeneration;
            unregisterReceiver(mScreenReceiver);
            if (mPrefs != null) mPrefs.unregisterOnSharedPreferenceChangeListener(mPrefsListener);
            releaseRenderer();
            mHandler.removeCallbacksAndMessages(null);
            mLoader.shutdownNow();
            mHolder = null;
            super.onDestroy();
        }

        private boolean hasSurface() {
            return !mDestroyed && mHolder != null && mHolder.getSurface().isValid();
        }

        private boolean shouldPlay() {
            return hasSurface() && mVisible && !mAmbient && mPower.isInteractive()
                    && (isPreview() || !mPauseOnLock || !mKeyguard.isKeyguardLocked());
        }

        private void reloadWallpaper() {
            int generation = ++mLoadGeneration;
            mLoading = false;
            releaseRenderer();
            if (!hasSurface()) return;
            initPreferences();
            if (mPrefs == null) {
                mMessage = R.string.video_wallpaper_waiting_unlock;
                drawFrame();
                return;
            }
            loadSettings();
            mLoading = true;
            mLoader.execute(() -> {
                File file = null;
                AnimatedImageDrawable animation = null;
                int message = R.string.video_wallpaper_current_none;
                try {
                    MediaUtils.migrateWallpaperStorage(VideoWallpaperService.this);
                    file = MediaUtils.getCurrentWallpaperFile(VideoWallpaperService.this);
                    if (file != null && !MediaUtils.isVideoFormat(file.getName())) {
                        animation = MediaUtils.decodeWallpaperImage(file);
                    } else if (file == null && MediaUtils.hasWallpaperSelection(
                            VideoWallpaperService.this)) {
                        message = R.string.video_wallpaper_missing;
                    }
                } catch (IOException | RuntimeException e) {
                    Log.e(TAG, "Could not load wallpaper", e);
                    file = null;
                    message = R.string.video_wallpaper_playback_failed;
                }
                final File loadedFile = file;
                final AnimatedImageDrawable loadedAnimation = animation;
                final int loadMessage = message;
                mHandler.post(() -> {
                    if (generation != mLoadGeneration || !hasSurface()) {
                        if (loadedAnimation != null) loadedAnimation.stop();
                        return;
                    }
                    mLoading = false;
                    mMessage = loadMessage;
                    if (loadedAnimation != null) {
                        mAnimation = loadedAnimation;
                        mAnimation.setCallback(this);
                        updatePlayback();
                    } else if (loadedFile != null) {
                        createPlayer(loadedFile);
                    } else {
                        drawFrame();
                    }
                });
            });
        }

        private void createPlayer(File file) {
            // Canvas and MediaPlayer are different surface producers. Tear down HWUI before
            // attaching the video producer (the same lifecycle used by base ImageWallpaper).
            disconnectCanvas();
            try {
                MediaPlayer player = new MediaPlayer();
                mPlayer = player;
                player.setSurface(mHolder.getSurface());
                player.setDataSource(file.getAbsolutePath());
                player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                player.setLooping(true);
                player.setVolume(0f, 0f);
                player.setOnPreparedListener(prepared -> {
                    if (prepared != mPlayer || !hasSurface()) return;
                    mPrepared = true;
                    // Present a still frame even when the lock-screen pause preference applies.
                    try {
                        prepared.seekTo(0);
                        updatePlayback();
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Could not display the first video frame", e);
                        showPlaybackError();
                    }
                });
                player.setOnErrorListener((failed, what, extra) -> {
                    if (failed == mPlayer) {
                        Log.e(TAG, "Wallpaper playback failed: " + what + "/" + extra);
                        showPlaybackError();
                    }
                    return true;
                });
                player.prepareAsync();
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Could not prepare wallpaper video", e);
                showPlaybackError();
            }
        }

        private void updatePlayback() {
            if (mDestroyed) return;
            boolean play = shouldPlay();
            if (mPlayer != null && mPrepared) {
                try {
                    if (play) {
                        try {
                            // base MediaPlayer starts playback when nonzero speed is applied.
                            // Do this only after visibility and lock state permit playback.
                            mPlayer.setPlaybackParams(new PlaybackParams().setSpeed(mSpeed));
                        } catch (IllegalArgumentException e) {
                            Log.w(TAG, "Playback speed unsupported; using normal playback", e);
                            mPlayer.start();
                        }
                    } else if (mPlayer.isPlaying()) {
                        mPlayer.pause();
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Could not update wallpaper playback", e);
                    showPlaybackError();
                }
            }
            if (mAnimation != null) {
                if (play) {
                    mAnimation.start();
                } else {
                    mAnimation.stop();
                    mHandler.removeCallbacksAndMessages(mAnimation);
                    mHandler.removeCallbacks(mDraw);
                }
            }
            if (mPlayer == null && mVisible) drawFrame();
        }

        private void showPlaybackError() {
            releaseRenderer();
            mMessage = R.string.video_wallpaper_playback_failed;
            drawFrame();
        }

        private void drawFrame() {
            if (!hasSurface() || !mVisible || mPlayer != null) return;
            Canvas canvas = null;
            try {
                canvas = mHolder.lockHardwareCanvas();
                if (canvas == null) return;
                mCanvasConnected = true;
                canvas.drawColor(Color.BLACK);
                if (mAnimation != null) {
                    float scale = Math.max((float) canvas.getWidth() / mAnimation.getIntrinsicWidth(),
                            (float) canvas.getHeight() / mAnimation.getIntrinsicHeight());
                    int width = Math.round(mAnimation.getIntrinsicWidth() * scale);
                    int height = Math.round(mAnimation.getIntrinsicHeight() * scale);
                    int left = (canvas.getWidth() - width) / 2;
                    int top = (canvas.getHeight() - height) / 2;
                    mAnimation.setBounds(left, top, left + width, top + height);
                    mAnimation.draw(canvas);
                } else {
                    TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(18 * getResources().getDisplayMetrics().scaledDensity);
                    String message = getString(mMessage);
                    StaticLayout text = StaticLayout.Builder.obtain(message, 0, message.length(),
                            paint, Math.max(1, (int) (canvas.getWidth() * 0.8f)))
                            .setAlignment(Layout.Alignment.ALIGN_CENTER).build();
                    canvas.save();
                    canvas.translate((canvas.getWidth() - text.getWidth()) / 2f,
                            (canvas.getHeight() - text.getHeight()) / 2f);
                    text.draw(canvas);
                    canvas.restore();
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not draw wallpaper frame", e);
            } finally {
                if (canvas != null) {
                    try {
                        mHolder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Wallpaper surface was lost while drawing", e);
                    }
                }
            }
        }

        @Override
        public void invalidateDrawable(Drawable drawable) {
            if (drawable == mAnimation && mVisible && hasSurface()) {
                mHandler.removeCallbacks(mDraw);
                mHandler.post(mDraw);
            }
        }

        @Override
        public void scheduleDrawable(Drawable drawable, Runnable task, long when) {
            if (drawable == mAnimation && shouldPlay()) mHandler.postAtTime(task, drawable, when);
        }

        @Override
        public void unscheduleDrawable(Drawable drawable, Runnable task) {
            mHandler.removeCallbacks(task, drawable);
        }

        private void disconnectCanvas() {
            if (mCanvasConnected && mHolder != null) {
                mHolder.getSurface().hwuiDestroy();
            }
            mCanvasConnected = false;
        }

        private void releaseRenderer() {
            mHandler.removeCallbacks(mDraw);
            mPrepared = false;
            if (mPlayer != null) {
                MediaPlayer player = mPlayer;
                mPlayer = null;
                player.setOnPreparedListener(null);
                player.setOnErrorListener(null);
                player.release();
            }
            if (mAnimation != null) {
                mAnimation.setCallback(null);
                mAnimation.stop();
                mHandler.removeCallbacksAndMessages(mAnimation);
                mAnimation = null;
            }
            disconnectCanvas();
        }
    }
}
