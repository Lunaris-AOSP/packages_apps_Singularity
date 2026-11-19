/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 */
package org.lunaris.settings.fragments.buttons;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceViewHolder;

import com.android.internal.util.lunaris.EdgeGoConfig;
import com.android.settings.R;
import com.android.settingslib.widget.LayoutPreference;

import java.text.NumberFormat;

final class EdgeGoGuidePreference extends LayoutPreference {
    private static final int ZONES = 0, TAPS = 1, SWIPE = 2, HOLD = 3, EXTENDED = 4,
            CANCEL = 5, SLIDER = 6, MEDIA = 7, PIE = 8, PANEL = 9, CURSOR = 10,
            SCROLL = 11, DRAWING = 12, MACRO = 13, PAUSE = 14;
    private final String topic, title, summary;
    private final int scene;
    private EdgeGoConfig config;
    private GuideView preview;

    EdgeGoGuidePreference(Context context, EdgeGoConfig config, String topic, String title, String summary) {
        super(context, R.layout.edgego_guide_preview);
        this.config = config;
        this.topic = topic;
        this.title = title;
        this.summary = summary;
        scene = scene(topic);
        setKey("edgego_guide_preview");
        setSelectable(false);
    }

    void update(EdgeGoConfig config) {
        this.config = config;
        if (preview != null) { preview.stop(); preview.colors(); preview.invalidate(); }
    }

    void stop() { if (preview != null) preview.stop(); }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        stop();
        TextView heading = findViewById(R.id.edgego_guide_preview_title);
        TextView description = findViewById(R.id.edgego_guide_preview_summary);
        heading.setText(title);
        description.setText(summary);
        FrameLayout host = findViewById(R.id.edgego_guide_preview_host);
        host.removeAllViews();
        preview = new GuideView(getContext(), findViewById(R.id.edgego_guide_preview_status));
        preview.setContentDescription(title + ". " + summary);
        host.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        findViewById(R.id.edgego_guide_preview_replay).setOnClickListener(view -> preview.play());
    }

    private static int scene(String topic) {
        switch (topic) {
            case "zones": case "appearance": return ZONES;
            case "taps": return TAPS;
            case "holds": return HOLD;
            case "extended": return EXTENDED;
            case "cancel": return CANCEL;
            case "sliders": case "dim": return SLIDER;
            case "media": return MEDIA;
            case "pie": case "sectors": case "presets": return PIE;
            case "cursor": case "touch_pad": return CURSOR;
            case "scroll": return SCROLL;
            case "drawing": return DRAWING;
            case "macros": return MACRO;
            case "pauses": return PAUSE;
            case "swipes": case "controls": case "animation": return SWIPE;
            default: return PANEL;
        }
    }

    private final class GuideView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF phone = new RectF(), zone = new RectF(), card = new RectF();
        private final Path trace = new Path(), cursor = new Path(), trial = new Path();
        private final PathMeasure measure = new PathMeasure();
        private final float[] position = new float[2], tangent = new float[2];
        private final TextView status;
        private final NumberFormat percent = NumberFormat.getPercentInstance();
        private final float dp;
        private final Runnable holdReady = this::holdReady;
        private final Runnable finishTaps = this::finishTaps;
        private final Runnable tick = this::tick;
        private ValueAnimator animation;
        private int accent, foreground, surface, selected = 1, taps, steps;
        private float phase = .65f, level = .5f, x, y, startX, startY, lastX, lastY, maxDistance;
        private float pointerX = .5f, pointerY = .35f, sign = 1;
        private long downTime, lastFrame, lastMovement;
        private boolean touching, held, playing;

        GuideView(Context context, TextView status) {
            super(context);
            this.status = status;
            dp = getResources().getDisplayMetrics().density;
            setClickable(true);
            colors();
        }

        void colors() {
            accent = EdgeGoPreviewColors.accent(getContext(), config);
            foreground = EdgeGoPreviewColors.text(getContext());
            surface = EdgeGoPreviewColors.surface(getContext(), config);
        }

        private void holdReady() {
            if (!touching) return;
            held = true;
            phase = 1;
            status.setText(R.string.edgego_guide_hold_ready);
            invalidate();
        }

        void play() {
            stop();
            phase = 0;
            trial.reset();
            playing = true;
            if (!ValueAnimator.areAnimatorsEnabled()) { phase = 1; invalidate(); return; }
            animation = ValueAnimator.ofFloat(0, 1);
            animation.setDuration(scene == HOLD || scene == SLIDER || scene == MACRO ? 2800 : 1800);
            animation.setInterpolator(new android.view.animation.LinearInterpolator());
            animation.addUpdateListener(value -> {
                phase = (float) value.getAnimatedValue();
                if (scene == SLIDER) level = .15f + phase * .7f;
                invalidate();
            });
            animation.start();
        }

        void stop() {
            if (animation != null) { animation.cancel(); animation = null; }
            removeCallbacks(holdReady);
            removeCallbacks(finishTaps);
            removeCallbacks(tick);
            touching = held = false;
            taps = 0;
            setPressed(false);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }

        private void tick() {
            if (!touching) return;
            long now = SystemClock.uptimeMillis();
            if (scene == SLIDER) level = clamp(level + sign * Math.min(100, now - lastFrame)
                    / (float) config.integer("continuous_adjustment_interval_ms") * config.integer("brightness_step") / 100f);
            if (ValueAnimator.areAnimatorsEnabled()) phase = scene == HOLD
                    ? Math.min(1, (now - lastMovement) / (float) config.integer("hold_timeout_ms"))
                    : (now - downTime) % 1600 / 1600f;
            lastFrame = now;
            invalidate();
            if (scene == SLIDER || ValueAnimator.areAnimatorsEnabled()) postOnAnimation(tick);
        }

        @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

        @Override protected void onDraw(Canvas canvas) {
            float height = getHeight() - 24 * dp;
            float width = Math.min(getWidth() - 64 * dp, height * .48f);
            phone.set((getWidth() - width) / 2, 12 * dp, (getWidth() + width) / 2, 12 * dp + height);
            round(canvas, phone, 22 * dp, ColorUtils.blendARGB(surface, accent, .08f));
            paint.setColor(ColorUtils.blendARGB(foreground, surface, .65f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * dp);
            canvas.drawRoundRect(phone, 22 * dp, 22 * dp, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(foreground);
            canvas.drawRoundRect(phone.centerX() - 10 * dp, phone.top + 8 * dp,
                    phone.centerX() + 10 * dp, phone.top + 11 * dp, 2 * dp, 2 * dp, paint);
            canvas.drawRoundRect(phone.centerX() - 15 * dp, phone.bottom - 10 * dp,
                    phone.centerX() + 15 * dp, phone.bottom - 7 * dp, 2 * dp, 2 * dp, paint);
            int save = canvas.save();
            canvas.clipRect(phone.left + 9 * dp, phone.top + 24 * dp, phone.right - 9 * dp, phone.bottom - 20 * dp);
            for (int i = 0; i < 5; i++) {
                float top = phone.top + 32 * dp + i * 33 * dp - (scene == SCROLL ? phase * 30 * dp : 0);
                card.set(phone.left + 14 * dp, top, phone.right - 14 * dp, top + 22 * dp);
                round(canvas, card, 6 * dp, ColorUtils.blendARGB(surface, foreground, .08f));
            }
            canvas.restoreToCount(save);
            zone.set(phone.right - 6 * dp, phone.centerY() - 32 * dp, phone.right, phone.centerY() + 32 * dp);
            round(canvas, zone, 3 * dp, scene == PAUSE ? ColorUtils.blendARGB(accent, surface, .75f) : accent);
            if (scene == ZONES) {
                paint.setColor(accent);
                canvas.drawRoundRect(phone.left, phone.centerY() - 32 * dp, phone.left + 6 * dp,
                        phone.centerY() + 32 * dp, 3 * dp, 3 * dp, paint);
                canvas.drawRoundRect(phone.centerX() - 25 * dp, phone.top + 18 * dp,
                        phone.centerX() + 25 * dp, phone.top + 23 * dp, 3 * dp, 3 * dp, paint);
                float segment = (phone.width() - 24 * dp) / 3;
                for (int i = 0; i < 3; i++) canvas.drawRoundRect(phone.left + 12 * dp + i * segment,
                        phone.bottom - 23 * dp, phone.left + 8 * dp + (i + 1) * segment, phone.bottom - 18 * dp, 3 * dp, 3 * dp, paint);
            } else if (scene == SLIDER) drawSlider(canvas);
            else if (scene == MEDIA || scene == PANEL || scene == MACRO) drawPanel(canvas);
            else if (scene == PIE) drawPie(canvas);
            else if (scene == CURSOR) drawCursor(canvas);
            else if (scene == PAUSE) {
                paint.setColor(accent);
                canvas.drawRoundRect(phone.centerX() - 12 * dp, phone.centerY() - 16 * dp,
                        phone.centerX() - 4 * dp, phone.centerY() + 16 * dp, 3 * dp, 3 * dp, paint);
                canvas.drawRoundRect(phone.centerX() + 4 * dp, phone.centerY() - 16 * dp,
                        phone.centerX() + 12 * dp, phone.centerY() + 16 * dp, 3 * dp, 3 * dp, paint);
            } else drawGesture(canvas);
            if (touching) finger(canvas, x, y, 1);
        }

        private void drawGesture(Canvas canvas) {
            float sx = zone.centerX(), sy = zone.centerY();
            if (scene == SWIPE) {
                arrow(canvas, sx, sy, sx, sy - 70 * dp);
                arrow(canvas, sx, sy, sx, sy + 70 * dp);
                arrow(canvas, sx, sy, phone.centerX(), sy);
                arrow(canvas, sx, sy, phone.centerX(), sy - 40 * dp);
                arrow(canvas, sx, sy, phone.centerX(), sy + 40 * dp);
            }
            trace.reset();
            trace.moveTo(sx, sy);
            if (scene == SWIPE || scene == SCROLL) trace.lineTo(sx, sy - 70 * dp);
            else if (scene == DRAWING) trace.cubicTo(phone.left + 10 * dp, phone.top + 25 * dp,
                    phone.left + 10 * dp, phone.bottom - 25 * dp, phone.right - 24 * dp, phone.centerY() + 35 * dp);
            else if (scene == CANCEL) { trace.lineTo(phone.left + 24 * dp, sy); trace.lineTo(sx, sy); }
            else if (scene == TAPS) {
                finger(canvas, sx, sy, .6f + (phase * 3 % 1) * .4f);
                paint.setColor(accent);
                for (int i = 0; i < 3; i++) canvas.drawCircle(phone.centerX() + (i - 1) * 18 * dp,
                        phone.bottom - 34 * dp, (i < Math.max(taps, (int) Math.ceil(phase * 3)) ? 5 : 3) * dp, paint);
                return;
            } else trace.lineTo(scene == EXTENDED ? phone.left + 14 * dp : phone.centerX(), sy);
            paint.setColor(accent);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * dp);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawPath(trace, paint);
            if (scene == DRAWING && !trial.isEmpty()) canvas.drawPath(trial, paint);
            measure.setPath(trace, false);
            float travel = scene == HOLD ? Math.min(1, phase * 3) : phase;
            measure.getPosTan(measure.getLength() * travel, position, tangent);
            paint.setStyle(Paint.Style.FILL);
            if (!touching) finger(canvas, position[0], position[1], 1);
            if (scene == HOLD) {
                float clockX = touching ? x : phone.centerX(), clockY = touching ? y : sy;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * dp);
                canvas.drawArc(clockX - 13 * dp, clockY - 13 * dp, clockX + 13 * dp,
                        clockY + 13 * dp, -90, phase * 360, false, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (scene == EXTENDED) {
                paint.setTextSize(12 * dp);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("1", phone.centerX(), sy + 24 * dp, paint);
                canvas.drawText("2", phone.left + 16 * dp, sy + 24 * dp, paint);
            }
        }

        private void arrow(Canvas canvas, float sx, float sy, float ex, float ey) {
            float distance = Math.max(1, (float) Math.hypot(ex - sx, ey - sy));
            float dx = (ex - sx) / distance, dy = (ey - sy) / distance;
            paint.setColor(accent); paint.setAlpha(100); paint.setStrokeWidth(2 * dp);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(sx, sy, ex, ey, paint);
            canvas.drawLine(ex, ey, ex - (dx * 5 - dy * 4) * dp, ey - (dy * 5 + dx * 4) * dp, paint);
            canvas.drawLine(ex, ey, ex - (dx * 5 + dy * 4) * dp, ey - (dy * 5 - dx * 4) * dp, paint);
        }

        private void drawSlider(Canvas canvas) {
            card.set(phone.centerX() - 16 * dp, phone.top + 45 * dp, phone.centerX() + 16 * dp, phone.bottom - 45 * dp);
            round(canvas, card, 16 * dp, ColorUtils.blendARGB(surface, accent, .2f));
            int save = canvas.save();
            canvas.clipRect(card.left, card.bottom - card.height() * level, card.right, card.bottom);
            round(canvas, card, 16 * dp, accent);
            canvas.restoreToCount(save);
            paint.setColor(foreground);
            paint.setTextSize(14 * dp);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(percent.format(level), phone.centerX(), phone.bottom - 28 * dp, paint);
        }

        private void drawPanel(Canvas canvas) {
            boolean macro = scene == MACRO;
            float top = macro || topic.equals("notch") ? phone.top + 24 * dp : phone.centerY() - 48 * dp;
            if (topic.equals("notifications")) {
                card.set(phone.left + 15 * dp, top - 6 * dp, phone.right - 15 * dp, top + 100 * dp);
                round(canvas, card, 16 * dp, ColorUtils.blendARGB(surface, accent, .12f));
            }
            card.set(phone.left + 9 * dp, top, phone.right - 9 * dp, top + (macro ? 42 : 112) * dp);
            round(canvas, card, 16 * dp, ColorUtils.blendARGB(surface, accent, .22f));
            paint.setColor(accent);
            canvas.drawCircle(card.left + 15 * dp, top + 15 * dp, 5 * dp, paint);
            paint.setColor(foreground);
            canvas.drawRoundRect(card.left + 27 * dp, top + 11 * dp, card.right - 12 * dp,
                    top + 15 * dp, 2 * dp, 2 * dp, paint);
            if (macro) {
                for (int i = 0; i < 3; i++) canvas.drawRoundRect(card.left + (12 + i * 30) * dp,
                        top + 25 * dp, card.left + (33 + i * 30) * dp, top + 33 * dp, 4 * dp, 4 * dp, paint);
                finger(canvas, phone.centerX(), phone.bottom - (45 + phase * 85) * dp, 1);
                return;
            }
            if (topic.equals("launcher") || topic.equals("actions")) {
                for (int row = 0; row < 2; row++) for (int column = 0; column < 3; column++) {
                    paint.setColor(column == selected ? accent : ColorUtils.blendARGB(surface, accent, .5f));
                    canvas.drawCircle(card.left + (20 + column * 30) * dp, top + (45 + row * 38) * dp, 10 * dp, paint);
                }
                return;
            }
            if (topic.equals("notifications") || topic.equals("side_panel")) {
                paint.setColor(foreground);
                for (int i = 0; i < 3; i++) canvas.drawRoundRect(card.left + 13 * dp, top + (35 + i * 12) * dp,
                        card.right - (i == 2 ? 32 : 13) * dp, top + (39 + i * 12) * dp, 2 * dp, 2 * dp, paint);
            }
            if (topic.equals("tools")) {
                for (int i = 0; i < 2; i++) {
                    paint.setColor(accent);
                    canvas.drawRoundRect(card.left + 14 * dp, top + (34 + i * 18) * dp,
                            card.right - 14 * dp, top + (43 + i * 18) * dp, 5 * dp, 5 * dp, paint);
                }
            }
            paint.setColor(accent);
            for (int i = 0; i < 3; i++) canvas.drawRoundRect(card.left + (12 + i * 30) * dp,
                    top + (scene == MEDIA ? 42 : 74) * dp, card.left + (35 + i * 30) * dp, top + (scene == MEDIA ? 70 : 96) * dp,
                    (scene == MEDIA && i == 1 && playing ? 14 : 8) * dp, 12 * dp, paint);
            paint.setColor(foreground);
            canvas.drawRoundRect(card.left + 14 * dp, top + 86 * dp, card.right - 14 * dp,
                    top + 94 * dp, 4 * dp, 4 * dp, paint);
        }

        private void drawPie(Canvas canvas) {
            float radius = phone.width() * .64f;
            card.set(zone.centerX() - radius, zone.centerY() - radius, zone.centerX() + radius, zone.centerY() + radius);
            paint.setColor(ColorUtils.blendARGB(surface, accent, .18f));
            canvas.drawArc(card, 90, 180, true, paint);
            for (int i = 0; i < 3; i++) {
                double angle = Math.toRadians(120 + i * 60);
                float px = zone.centerX() + (float) Math.cos(angle) * radius * .68f;
                float py = zone.centerY() + (float) Math.sin(angle) * radius * .68f;
                paint.setColor(i == selected ? accent : ColorUtils.blendARGB(foreground, surface, .45f));
                canvas.drawCircle(px, py, (i == selected ? 13 : 10) * dp, paint);
            }
        }

        private void drawCursor(Canvas canvas) {
            card.set(phone.right - 46 * dp, phone.bottom - 66 * dp, phone.right - 12 * dp, phone.bottom - 32 * dp);
            round(canvas, card, 12 * dp, accent);
            float px = phone.left + pointerX * phone.width(), py = phone.top + pointerY * phone.height();
            cursor.reset();
            cursor.moveTo(px, py);
            cursor.lineTo(px + 14 * dp, py + 14 * dp);
            cursor.lineTo(px + 6 * dp, py + 16 * dp);
            cursor.close();
            paint.setColor(foreground);
            canvas.drawPath(cursor, paint);
        }

        private void round(Canvas canvas, RectF rect, float radius, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        private void finger(Canvas canvas, float x, float y, float amount) {
            paint.setColor(ColorUtils.blendARGB(accent, surface, .5f));
            canvas.drawCircle(x, y, 12 * dp * amount, paint);
            paint.setColor(accent);
            canvas.drawCircle(x, y, 5 * dp, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (animation != null) animation.cancel();
                x = startX = lastX = event.getX();
                y = startY = lastY = event.getY();
                boolean edgeGesture = scene >= TAPS && scene <= CANCEL || scene == DRAWING;
                if (edgeGesture && (Math.abs(x - zone.centerX()) > 22 * dp || Math.abs(y - zone.centerY()) > 45 * dp)) {
                    status.setText(R.string.edgego_guide_start_edge);
                    return true;
                }
                removeCallbacks(finishTaps);
                touching = true;
                held = false;
                maxDistance = 0;
                downTime = lastFrame = lastMovement = SystemClock.uptimeMillis();
                trial.reset(); trial.moveTo(x, y);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (scene == HOLD) {
                    phase = 0;
                    postDelayed(holdReady, config.integer("hold_timeout_ms"));
                    if (ValueAnimator.areAnimatorsEnabled()) postOnAnimation(tick);
                }
                if (scene == SLIDER) postOnAnimation(tick);
                setPressed(true);
            }
            if (!touching) return super.onTouchEvent(event);
            if (event.getPointerCount() != 1 || action == MotionEvent.ACTION_CANCEL) {
                if (event.getPointerCount() != 1) status.setText(R.string.edgego_guide_one_finger);
                stop(); invalidate(); return true;
            }
            x = event.getX(); y = event.getY();
            maxDistance = Math.max(maxDistance, (float) Math.hypot(x - startX, y - startY));
            if (scene == TAPS && maxDistance >= 12 * dp) taps = 0;
            if (action == MotionEvent.ACTION_MOVE) {
                trial.lineTo(x, y);
                if (scene == SLIDER) {
                    if (Math.abs(y - lastY) > dp) sign = y < lastY ? 1 : -1;
                    level = clamp(level - (y - lastY) / Math.max(1, phone.height()));
                } else if (scene == CURSOR) {
                    pointerX = Math.max(.1f, Math.min(.8f, pointerX + (x - lastX) / Math.max(1, phone.width())));
                    pointerY = Math.max(.15f, Math.min(.8f, pointerY + (y - lastY) / Math.max(1, phone.height())));
                } else if (scene == PIE) {
                    double angle = Math.toDegrees(Math.atan2(y - zone.centerY(), x - zone.centerX()));
                    if (angle < 0) angle += 360;
                    selected = EdgeGoConfig.clamp((int) ((angle - 90) / 60), 0, 2);
                } else if (scene == HOLD && Math.hypot(x - lastX, y - lastY) > dp) {
                    held = false;
                    lastMovement = SystemClock.uptimeMillis();
                    removeCallbacks(holdReady);
                    postDelayed(holdReady, config.integer("hold_timeout_ms"));
                }
                lastX = x; lastY = y;
            }
            if (action == MotionEvent.ACTION_UP) {
                touching = false;
                removeCallbacks(holdReady);
                removeCallbacks(tick);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (scene == TAPS) {
                    if (maxDistance < 12 * dp
                            && SystemClock.uptimeMillis() - downTime < config.integer("hold_timeout_ms")) {
                        taps++;
                        if (taps >= 3) finishTaps();
                        else { status.setText(R.string.edgego_guide_wait_tap); postDelayed(finishTaps, config.integer("multi_tap_interval_ms")); }
                    } else if (taps > 0) finishTaps();
                    else status.setText(summary);
                } else if (scene == SLIDER) {
                    status.setText(getContext().getString(R.string.edgego_guide_value, Math.round(level * 100)));
                    setStateDescription(percent.format(level));
                } else if (scene == MACRO) status.setText(getContext().getString(R.string.edgego_guide_macro_count, ++steps));
                else if (scene == MEDIA) { playing = !playing; status.setText(playing ? R.string.edgego_guide_media_playing : R.string.edgego_guide_media_paused); }
                else if (scene == CANCEL) status.setText(maxDistance > 24 * dp && Math.hypot(x - startX, y - startY) < 16 * dp
                        ? R.string.edgego_guide_canceled : R.string.edgego_guide_return_hint);
                else if (scene == DRAWING) status.setText(R.string.edgego_guide_stroke_done);
                else if (scene == SWIPE || scene == HOLD || scene == EXTENDED) finishGesture();
                else status.setText(R.string.edgego_guide_complete);
                setPressed(false);
                performClick();
            }
            invalidate();
            return true;
        }

        private void finishTaps() {
            String gesture = taps >= 3 ? "triple_tap" : taps == 2 ? "double_tap" : "tap";
            int name = getResources().getIdentifier("edgego_gesture_" + gesture + "_title", "string", getContext().getPackageName());
            status.setText(getContext().getString(R.string.edgego_guide_tap_result, name == 0 ? title : getContext().getString(name)));
            taps = 0;
            invalidate();
        }

        private void finishGesture() {
            float dx = x - startX, dy = y - startY;
            String gesture;
            if (maxDistance < 8 * dp) gesture = held ? "long_press" : "tap";
            else if (Math.abs(dy) > Math.abs(dx) * 1.4f) gesture = dy < 0 ? "swipe_up" : "swipe_down";
            else if (dx < -8 * dp) gesture = Math.abs(dy) < Math.abs(dx) * .5f ? "pull_inward"
                    : dy < 0 ? "pull_diagonal_up" : "pull_diagonal_down";
            else { status.setText(R.string.edgego_guide_start_edge); return; }
            if (maxDistance >= 8 * dp) {
                if (held) gesture += "_and_hold";
                else if (scene == EXTENDED && maxDistance >= phone.width() * .6f) gesture += "_extended";
            }
            int name = getResources().getIdentifier("edgego_gesture_" + gesture + "_title", "string", getContext().getPackageName());
            status.setText(getContext().getString(R.string.edgego_guide_tap_result, name == 0 ? title : getContext().getString(name)));
        }

        private float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    }
}
