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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.function.Consumer;

final class EdgeGoSectorPreference extends LayoutPreference {
    private EdgeGoConfig config;
    private final Consumer<JSONObject> save;
    private View preview;
    EdgeGoSectorPreference(Context context, EdgeGoConfig config, Consumer<JSONObject> save) {
        super(context, R.layout.edgego_zone_preview);
        this.config = config; this.save = save; setKey("sector_preview");
        setSelectable(false);
    }
    void update(EdgeGoConfig config) { this.config = config; if (preview != null) preview.invalidate(); }
    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        FrameLayout frame = findViewById(R.id.edgego_preview_host);
        frame.removeAllViews();
        preview = new View(getContext()) {
            final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            final RectF oval = new RectF();
            double[] angles = config.sectorDividers();
            int selected = -1;
            float centerX, centerY, radius;
            @Override protected void onDraw(Canvas canvas) {
                if (selected < 0) angles = config.sectorDividers();
                float dp = getResources().getDisplayMetrics().density;
                int accent = EdgeGoPreviewColors.accent(getContext(), config);
                int foreground = EdgeGoPreviewColors.text(getContext());
                centerX = getWidth() * .22f; centerY = getHeight() * .52f; radius = Math.min(getWidth() * .65f, getHeight() * .42f);
                oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
                for (int i = 0; i < angles.length - 1; i++) {
                    paint.setColor(config.integer("custom_pull_sector_" + (i + 1) + "_color_rgb")); paint.setAlpha(90); paint.setStyle(Paint.Style.FILL);
                    canvas.drawArc(oval, (float) angles[i], (float) (angles[i + 1] - angles[i]), true, paint);
                    if (config.bool("custom_pull_show_sector_number")) {
                        double middle = Math.toRadians((angles[i] + angles[i + 1]) / 2);
                        paint.setColor(foreground); paint.setTextSize(12 * dp); paint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText(Integer.toString(i + 1), centerX + radius * .65f * (float) Math.cos(middle),
                                centerY + radius * .65f * (float) Math.sin(middle) + 4 * dp, paint);
                        paint.setTextAlign(Paint.Align.LEFT);
                    }
                }
                for (int i = 0; i < angles.length; i++) {
                    float x = centerX + radius * (float) Math.cos(Math.toRadians(angles[i])), y = centerY + radius * (float) Math.sin(Math.toRadians(angles[i]));
                    paint.setColor(accent); paint.setAlpha(255); paint.setStrokeWidth(2 * dp); paint.setStrokeCap(Paint.Cap.ROUND); canvas.drawLine(centerX, centerY, x, y, paint);
                    canvas.drawCircle(x, y, (selected == i ? 10 : 7) * dp, paint);
                }
                paint.setTextSize(12 * dp * getResources().getConfiguration().fontScale); paint.setColor(foreground);
                CharSequence hint = TextUtils.ellipsize(getContext().getString(R.string.edgego_sector_hint), paint,
                        Math.max(1, getWidth() - 24 * dp), TextUtils.TruncateAt.END);
                canvas.drawText(hint, 0, hint.length(), 12 * dp, getHeight() - 12 * dp, paint);
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getPointerCount() != 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    selected = -1; getParent().requestDisallowInterceptTouchEvent(false); invalidate(); return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    selected = -1; float nearest = 48 * getResources().getDisplayMetrics().density;
                    for (int i = 0; i < angles.length; i++) {
                        float x = centerX + radius * (float) Math.cos(Math.toRadians(angles[i])), y = centerY + radius * (float) Math.sin(Math.toRadians(angles[i]));
                        float distance = (float) Math.hypot(event.getX() - x, event.getY() - y); if (distance < nearest) { selected = i; nearest = distance; }
                    }
                    if (selected >= 0) { getParent().requestDisallowInterceptTouchEvent(true); performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); }
                }
                if (selected >= 0) {
                    double angle = Math.toDegrees(Math.atan2(event.getY() - centerY, Math.max(1, event.getX() - centerX)));
                    double gap = Math.min(4, (angles[angles.length - 1] - angles[0]) / angles.length);
                    double min = selected == 0 ? -85 : angles[selected - 1] + gap;
                    double max = selected == angles.length - 1 ? 85 : angles[selected + 1] - gap;
                    if (selected == 0) max = Math.min(-10, max);
                    if (selected == angles.length - 1) min = Math.max(10, min);
                    angles[selected] = Math.max(min, Math.min(max, angle));
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        JSONObject values = new JSONObject(); JSONArray dividers = new JSONArray();
                        for (int i = 1; i < angles.length - 1; i++) dividers.put((Object) angles[i]);
                        EdgeGoConfig.put(values, "custom_pull_sector_dividers", dividers); EdgeGoConfig.put(values, "custom_pull_upper_limit", (int) Math.max(10, -angles[0]));
                        EdgeGoConfig.put(values, "custom_pull_lower_limit", (int) Math.max(10, angles[angles.length - 1])); save.accept(values);
                    }
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) { selected = -1; getParent().requestDisallowInterceptTouchEvent(false); }
                invalidate(); return true;
            }
        };
        preview.setContentDescription(getContext().getString(R.string.edgego_sector_hint));
        preview.setHapticFeedbackEnabled(config.bool("haptic_feedback")); frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    }
}
