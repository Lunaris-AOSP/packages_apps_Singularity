/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 */
package org.lunaris.settings.fragments.buttons;

import android.app.AlertDialog;
import android.content.Context;
import android.gesture.Gesture;
import android.gesture.GesturePoint;
import android.gesture.GestureStroke;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.util.lunaris.EdgeGoConfig;
import com.android.internal.util.lunaris.EdgeGoDrawings;
import com.android.settings.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class EdgeGoDrawingEditor {
    static void show(EdgeGoSettings fragment, String zone, Consumer<JSONObject> saved) {
        show(fragment, zone, "", saved);
    }

    static void show(EdgeGoSettings fragment, String zone, String existingId, Consumer<JSONObject> saved) {
        Context context = fragment.requireContext(); JSONObject drawing = new JSONObject(); JSONArray samples = new JSONArray();
        EdgeGoConfig.put(drawing, "id", UUID.randomUUID().toString()); EdgeGoConfig.put(drawing, "name", context.getString(R.string.edgego_drawing));
        EdgeGoConfig.put(drawing, "actionId", "none");
        if (zone != null) {
            EdgeGoConfig.put(drawing, "edge", zone.substring(0, zone.indexOf('_')));
        }
        EdgeGoConfig.put(drawing, "samples", samples);
        LinearLayout body = new LinearLayout(context); body.setOrientation(1);
        float dp = context.getResources().getDisplayMetrics().density;
        TextView hint = new TextView(context); hint.setText(R.string.edgego_training_hint);
        hint.setTextColor(EdgeGoPreviewColors.text(context));
        hint.setPadding((int) (24 * dp), (int) (16 * dp), (int) (24 * dp), (int) (16 * dp)); body.addView(hint);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.edgego_add_drawing).setView(body).setNegativeButton(android.R.string.cancel, null).create();
        View pad = new View(context) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Map<Integer, ArrayList<GesturePoint>> strokes = new LinkedHashMap<>();
            boolean canceled;
            @Override public boolean onTouchEvent(MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) { strokes.clear(); canceled = false; }
                if (event.getPointerCount() > (zone == null ? 3 : 1) || action == MotionEvent.ACTION_CANCEL) {
                    canceled = true; strokes.clear(); invalidate(); return true;
                }
                if (canceled) return true;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    ArrayList<GesturePoint> points = strokes.computeIfAbsent(event.getPointerId(i), id -> new ArrayList<>());
                    if (points.size() >= 256) for (int j = points.size() - 2; j > 0; j -= 2) points.remove(j);
                    points.add(new GesturePoint(event.getX(i), event.getY(i), event.getEventTime()));
                }
                if (strokes.size() > (zone == null ? 3 : 1)) {
                    canceled = true; strokes.clear(); hint.setText(R.string.edgego_training_fingers); invalidate(); return true;
                }
                if (action == MotionEvent.ACTION_UP) {
                    Gesture gesture = new Gesture(); for (ArrayList<GesturePoint> points : strokes.values()) if (points.size() > 1) gesture.addStroke(new GestureStroke(points));
                    boolean straight = false;
                    if (gesture.getStrokesCount() == 1) {
                        float[] points = gesture.getStrokes().get(0).points;
                        straight = gesture.getLength() < Math.hypot(points[0] - points[points.length - 2], points[1] - points[points.length - 1]) * 1.3;
                    }
                    if (gesture.getLength() < 48 * getResources().getDisplayMetrics().density || straight || gesture.getStrokesCount() == 0) {
                        hint.setText(R.string.edgego_training_too_simple); strokes.clear(); invalidate(); return true;
                    }
                    if (samples.length() > 0 && drawing.optInt("fingers") != gesture.getStrokesCount()) { hint.setText(R.string.edgego_training_fingers); return true; }
                    boolean sensitive = fragment.configuration().bool("drawing_orientation_sensitive");
                    if (samples.length() > 0 && new EdgeGoDrawings(List.of(drawing), sensitive, 50).match(gesture) == null) { hint.setText(R.string.edgego_training_inconsistent); return true; }
                    List<JSONObject> existing = new ArrayList<>(fragment.configuration().drawings(zone));
                    existing.removeIf(item -> existingId.equals(item.optString("id")));
                    if (samples.length() == 0 && new EdgeGoDrawings(existing, sensitive, 75, drawing.optString("edge")).match(gesture) != null) { hint.setText(R.string.edgego_training_collision); return true; }
                    EdgeGoConfig.put(drawing, "fingers", gesture.getStrokesCount()); samples.put(EdgeGoDrawings.encode(gesture));
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                    hint.setText(context.getString(R.string.edgego_training_progress, samples.length()));
                    strokes.clear();
                    if (samples.length() == 3) {
                        dialog.dismiss();
                        String mapped = zone == null ? "" : fragment.configuration().actionZone(zone);
                        saved.accept(zone == null ? drawing : EdgeGoDrawings.reorientedDrawing(
                                drawing, mapped.substring(0, mapped.indexOf('_'))));
                    }
                }
                invalidate(); return true;
            }
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(EdgeGoPreviewColors.surface(getContext(), fragment.configuration()));
                paint.setColor(EdgeGoPreviewColors.accent(getContext(), fragment.configuration())); paint.setStrokeWidth(4 * getResources().getDisplayMetrics().density);
                paint.setStrokeCap(Paint.Cap.ROUND);
                for (ArrayList<GesturePoint> stroke : strokes.values()) for (int i = 1; i < stroke.size(); i++) canvas.drawLine(stroke.get(i - 1).x, stroke.get(i - 1).y, stroke.get(i).x, stroke.get(i).y, paint);
            }
        };
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(EdgeGoPreviewColors.surface(context, fragment.configuration())); surface.setCornerRadius(24 * dp);
        pad.setBackground(surface); pad.setClipToOutline(true);
        pad.setContentDescription(context.getString(R.string.edgego_training_hint));
        pad.setHapticFeedbackEnabled(fragment.configuration().bool("haptic_feedback"));
        body.addView(pad, new LinearLayout.LayoutParams(-1, (int) (300 * context.getResources().getDisplayMetrics().density)));
        fragment.trackDialog(dialog);
        fragment.command("training_on"); dialog.setOnDismissListener(d -> fragment.command("training_off")); dialog.show();
    }
}
