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
package org.lunaris.settings.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class MediaUtils {
    private static final String TAG = "MediaUtils";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int MAX_IMAGE_EDGE = 2048;
    public static final String PREFS_NAME = "video_wallpaper_prefs";
    public static final String KEY_WALLPAPER_PATH = "current_wallpaper_path";
    public static final String KEY_WALLPAPER_NAME = "current_wallpaper_name";
    public static final String KEY_PLAYBACK_SPEED = "playback_speed";
    public static final String KEY_PAUSE_ON_LOCK = "pause_on_lock";

    private MediaUtils() {}

    public static final class FileTooLargeException extends IOException {}
    public static final class UnsupportedMediaException extends IOException {}

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Call on a worker thread. Never change the selection until the new file is complete.
    public static synchronized void saveMediaToWallpaperStorage(Context context, Uri uri)
            throws IOException {
        String name = getDisplayName(context, uri);
        String extension = getFileExtension(context, uri, name);
        File directory = getWallpaperDirectory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create wallpaper directory");
        }
        File pending = File.createTempFile("wallpaper_", extension, directory);
        boolean saved = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(pending)) {
                if (input == null) throw new IOException("Could not open selected media");
                copyWithLimit(input, output);
                output.getFD().sync();
            }
            validateMedia(pending);
            SharedPreferences prefs = getPreferences(context);
            String oldPath = prefs.getString(KEY_WALLPAPER_PATH, null);
            String oldName = prefs.getString(KEY_WALLPAPER_NAME, null);
            if (!prefs.edit().putString(KEY_WALLPAPER_PATH, pending.getAbsolutePath())
                    .putString(KEY_WALLPAPER_NAME, name).commit()) {
                // commit() updates memory even on disk failure. Restore the previous selection.
                prefs.edit().putString(KEY_WALLPAPER_PATH, oldPath)
                        .putString(KEY_WALLPAPER_NAME, oldName).commit();
                throw new IOException("Could not save wallpaper selection");
            }
            saved = true;
            deleteOwnedFile(context, oldPath);
        } finally {
            if (!saved && !pending.delete()) {
                Log.w(TAG, "Could not remove incomplete wallpaper");
            }
        }
    }

    // Preserve existing installations while moving away from shared /sdcard storage.
    // Leave the original shared file alone; only this app's private copies are owned here.
    public static synchronized void migrateWallpaperStorage(Context context) {
        File current = getCurrentWallpaperFile(context);
        if (current == null || getWallpaperDirectory(context).equals(current.getParentFile())) {
            return;
        }
        try {
            saveMediaToWallpaperStorage(context, Uri.fromFile(current));
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not migrate wallpaper; keeping the existing selection", e);
        }
    }

    public static File getCurrentWallpaperFile(Context context) {
        String path = getPreferences(context).getString(KEY_WALLPAPER_PATH, null);
        if (path == null) return null;
        File file = new File(path);
        return file.isFile() && file.canRead() && file.length() > 0 ? file : null;
    }

    public static boolean hasWallpaperSelection(Context context) {
        return getPreferences(context).getString(KEY_WALLPAPER_PATH, null) != null;
    }

    public static synchronized boolean deleteCurrentWallpaper(Context context) {
        SharedPreferences prefs = getPreferences(context);
        String path = prefs.getString(KEY_WALLPAPER_PATH, null);
        String name = prefs.getString(KEY_WALLPAPER_NAME, null);
        if (!prefs.edit().remove(KEY_WALLPAPER_PATH).remove(KEY_WALLPAPER_NAME).commit()) {
            prefs.edit().putString(KEY_WALLPAPER_PATH, path)
                    .putString(KEY_WALLPAPER_NAME, name).commit();
            return false;
        }
        deleteOwnedFile(context, path);
        return true;
    }

    private static File getWallpaperDirectory(Context context) {
        return new File(context.getFilesDir(), "video_wallpapers");
    }

    private static void deleteOwnedFile(Context context, String path) {
        if (path == null) return;
        File file = new File(path);
        if (getWallpaperDirectory(context).equals(file.getParentFile())
                && file.exists() && !file.delete()) {
            Log.w(TAG, "Could not remove unused wallpaper");
        }
    }

    public static boolean isVideoFormat(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".mp4");
    }

    public static float getPlaybackSpeed(SharedPreferences prefs) {
        try {
            float speed = Float.parseFloat(prefs.getString(KEY_PLAYBACK_SPEED, "1.0"));
            return Float.isFinite(speed) && speed >= 0.25f && speed <= 2f ? speed : 1f;
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    public static AnimatedImageDrawable decodeWallpaperImage(File file) throws IOException {
        Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),
                (decoder, info, source) -> {
                    String mime = info.getMimeType();
                    if (!info.isAnimated()
                            || !("image/gif".equals(mime) || "image/webp".equals(mime))) {
                        throw new IllegalArgumentException("Expected a GIF or WebP animation");
                    }
                    int width = info.getSize().getWidth();
                    int height = info.getSize().getHeight();
                    float scale = Math.min(1f, (float) MAX_IMAGE_EDGE / Math.max(width, height));
                    decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                            Math.max(1, Math.round(height * scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
        if (!(drawable instanceof AnimatedImageDrawable)) throw new UnsupportedMediaException();
        AnimatedImageDrawable animation = (AnimatedImageDrawable) drawable;
        animation.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
        return animation;
    }

    private static void validateMedia(File file) throws IOException {
        try {
            if (isVideoFormat(file.getName())) {
                try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                    retriever.setDataSource(file.getAbsolutePath());
                    if (!"yes".equals(retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))) {
                        throw new UnsupportedMediaException();
                    }
                    Bitmap frame = retriever.getScaledFrameAtTime(-1,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256);
                    if (frame == null) throw new UnsupportedMediaException();
                    frame.recycle();
                }
            } else {
                decodeWallpaperImage(file).stop();
            }
        } catch (RuntimeException e) {
            throw new UnsupportedMediaException();
        }
    }

    private static void copyWithLimit(InputStream input, FileOutputStream output)
            throws IOException {
        byte[] buffer = new byte[8192];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            size += count;
            if (size > MAX_FILE_SIZE) throw new FileTooLargeException();
            output.write(buffer, 0, count);
        }
        if (size == 0) throw new UnsupportedMediaException();
    }

    private static String getDisplayName(Context context, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri,
                    new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                    return cursor.getString(0);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not read media display name", e);
            }
        }
        String name = uri.getLastPathSegment();
        return name != null ? name : "wallpaper";
    }

    private static String getFileExtension(Context context, Uri uri, String name)
            throws UnsupportedMediaException {
        String mime = context.getContentResolver().getType(uri);
        if (mime != null) {
            switch (mime.toLowerCase(Locale.ROOT)) {
                case "video/mp4": return ".mp4";
                case "image/gif": return ".gif";
                case "image/webp": return ".webp";
                case "application/octet-stream": break;
                default: throw new UnsupportedMediaException();
            }
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        for (String extension : new String[] {".mp4", ".gif", ".webp"}) {
            if (lowerName.endsWith(extension)) return extension;
        }
        throw new UnsupportedMediaException();
    }
}
