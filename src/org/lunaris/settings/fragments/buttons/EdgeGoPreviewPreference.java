/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 */
package org.lunaris.settings.fragments.buttons;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.FrameLayout;

import androidx.preference.PreferenceViewHolder;

import com.android.internal.util.lunaris.EdgeGoConfig;
import com.android.settings.R;
import com.android.settingslib.widget.LayoutPreference;

import java.util.function.IntConsumer;

final class EdgeGoPreviewPreference extends LayoutPreference {
    private EdgeGoConfig config;
    private final String zone;
    private final IntConsumer position;
    private View preview;
    EdgeGoPreviewPreference(Context context, EdgeGoConfig config, String zone, IntConsumer position) {
        super(context, R.layout.edgego_zone_preview);
        this.config = config; this.zone = zone; this.position = position;
        setKey("zone_preview"); setSelectable(false);
    }
    void update(EdgeGoConfig config) { this.config = config; if (preview != null) preview.invalidate(); }
    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        FrameLayout frame = findViewById(R.id.edgego_preview_host);
        frame.removeAllViews();
        preview = new View(getContext()) {
            final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            final RectF phone = new RectF();
            final RectF bounds = new RectF();
            int previewPosition = -1;
            boolean dragging;
            @Override protected void onDraw(Canvas canvas) {
                float dp = getResources().getDisplayMetrics().density;
                int accent = EdgeGoPreviewColors.accent(getContext(), config);
                int foreground = EdgeGoPreviewColors.text(getContext());
                float height = getHeight() - 48 * dp, width = Math.min(getWidth() - 48 * dp, height * .49f);
                phone.set((getWidth() - width) / 2, 12 * dp, (getWidth() + width) / 2, 12 * dp + height);
                paint.setColor(EdgeGoPreviewColors.surface(getContext(), config)); paint.setStyle(Paint.Style.FILL); canvas.drawRoundRect(phone, 24 * dp, 24 * dp, paint);
                paint.setColor(accent); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp); canvas.drawRoundRect(phone, 24 * dp, 24 * dp, paint); paint.setStyle(Paint.Style.FILL);
                for (String id : EdgeGoConfig.ZONES) {
                    if (!config.zoneEnabled(id) && !id.equals(zone)) continue;
                    String edge = id.substring(0, id.indexOf('_')); boolean side = edge.equals("left") || edge.equals("right");
                    float length = config.zoneInt(id, "length", side ? 25 : id.startsWith("bottom_navigation") ? 100 : 65, 5, 100) / 100f;
                    float position = id.equals(zone) && previewPosition >= 0 ? previewPosition / 100f
                            : config.zoneInt(id, "position", id.endsWith("z2") ? 88 : 50, 0, 100) / 100f;
                    float thickness = Math.max(3 * dp, config.zoneInt(id, "thickness", 16, 4, 40) * dp * .3f);
                    if (side) {
                        float top = phone.top + 28 * dp, available = phone.height() - 56 * dp;
                        float y = top + (available - available * length) * position;
                        float x = edge.equals("left") ? phone.left : phone.right - thickness;
                        bounds.set(x, y, x + thickness, y + available * length);
                    } else {
                        float available = phone.width() - 24 * dp;
                        float start = phone.left + 12 * dp;
                        if (id.startsWith("bottom_navigation")) {
                            available /= 3;
                            start += (id.charAt(id.length() - 1) - '1') * available;
                        }
                        float x = start + available * (1 - length) * position;
                        float y = edge.equals("top") ? phone.top + 16 * dp : phone.bottom - 16 * dp - thickness;
                        bounds.set(x, y, x + available * length, y + thickness);
                    }
                    paint.setColor(config.bool("material_you_sync_edges") ? accent : config.integer("overlay_color_" + edge + "_rgb") | 0xff000000); paint.setAlpha(id.equals(zone) ? 255 : 90);
                    canvas.drawRoundRect(bounds, 3 * dp, 3 * dp, paint);
                }
                paint.setColor(foreground); paint.setAlpha(255); paint.setTextSize(12 * dp * getResources().getConfiguration().fontScale);
                CharSequence hint = TextUtils.ellipsize(getContext().getString(R.string.edgego_preview_hint), paint,
                        Math.max(1, getWidth() - 24 * dp), TextUtils.TruncateAt.END);
                canvas.drawText(hint, 0, hint.length(), Math.max(8 * dp, (getWidth() - paint.measureText(hint, 0, hint.length())) / 2), getHeight() - 12 * dp, paint);
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { dragging = true; getParent().requestDisallowInterceptTouchEvent(true); }
                if (event.getPointerCount() != 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    dragging = false; previewPosition = -1; getParent().requestDisallowInterceptTouchEvent(false); invalidate(); return true;
                }
                if (!dragging) return true;
                if (phone.isEmpty()) return true;
                boolean side = zone.startsWith("left") || zone.startsWith("right");
                float dp = getResources().getDisplayMetrics().density;
                float start = side ? phone.top + 28 * dp : phone.left + 12 * dp;
                float available = side ? phone.height() - 56 * dp : phone.width() - 24 * dp;
                if (zone.startsWith("bottom_navigation")) {
                    available /= 3;
                    start += (zone.charAt(zone.length() - 1) - '1') * available;
                }
                float length = config.zoneInt(zone, "length", side ? 25 : zone.startsWith("bottom_navigation") ? 100 : 65, 5, 100) / 100f;
                float fraction = length == 1 ? config.zoneInt(zone, "position", zone.endsWith("z2") ? 88 : 50, 0, 100) / 100f
                        : ((side ? event.getY() : event.getX()) - start - available * length / 2) / Math.max(1, available * (1 - length));
                previewPosition = Math.round(Math.max(0, Math.min(1, fraction)) * 100);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    dragging = false; getParent().requestDisallowInterceptTouchEvent(false);
                    position.accept(previewPosition); previewPosition = -1;
                }
                invalidate(); return true;
            }
        };
        preview.setContentDescription(getContext().getString(R.string.edgego_preview_hint)); frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    }
}
