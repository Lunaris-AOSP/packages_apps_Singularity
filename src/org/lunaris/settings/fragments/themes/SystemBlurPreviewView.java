/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.themes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.graphics.drawable.AxBlurCustomization;
import com.android.internal.graphics.drawable.AxBlurRimPainter;

public class SystemBlurPreviewView extends View {
    private static final float STAGE_CORNER_DP = 20f;
    private static final float DIALOG_CORNER_DP = 24f;
    private static final float TILE_CORNER_DP = 20f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mStagePath = new Path();
    private final Path mSurfacePath = new Path();
    private final RectF mStageRect = new RectF();
    private final RectF mDialogRect = new RectF();
    private final RectF mTileRect = new RectF();
    private final RectF mPillRect = new RectF();
    private final RectF mWorkRect = new RectF();

    private final RenderNode mBackdropNode = new RenderNode("SystemBlurPreviewBackdrop");
    private final RenderNode mBlurredNode = new RenderNode("SystemBlurPreviewBlurred");

    private final AxBlurRimPainter mDialogRimPainter = new AxBlurRimPainter();
    private final AxBlurRimPainter mTileRimPainter = new AxBlurRimPainter();
    private final AxBlurRimPainter mPillRimPainter = new AxBlurRimPainter();

    private AxBlurCustomization.State mState;
    private float mBlurRadiusPx;
    private boolean mLightBackdrop;
    private int mRecordedWidth = -1;
    private int mRecordedHeight = -1;
    private boolean mRecordedLight;
    private float mBlurEffectRadius = -1f;

    public SystemBlurPreviewView(Context context) {
        this(context, null);
    }

    public SystemBlurPreviewView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SystemBlurPreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mState = AxBlurCustomization.getInstance(context).getState();
    }

    public void setPreviewState(AxBlurCustomization.State state, float effectiveBlurRadiusPx) {
        mState = state;
        final float clamped = Float.isFinite(effectiveBlurRadiusPx)
                ? Math.max(0f, effectiveBlurRadiusPx) : 0f;
        if (mBlurRadiusPx != clamped) {
            mBlurRadiusPx = clamped;
            mBlurEffectRadius = -1f;
        }
        invalidate();
    }

    public void setLightBackdrop(boolean light) {
        if (mLightBackdrop == light) return;
        mLightBackdrop = light;
        mRecordedWidth = -1;
        invalidate();
    }

    public boolean isLightBackdrop() {
        return mLightBackdrop;
    }

    @Override
    protected void onDetachedFromWindow() {
        mBackdropNode.discardDisplayList();
        mBlurredNode.discardDisplayList();
        mRecordedWidth = -1;
        mRecordedHeight = -1;
        mBlurEffectRadius = -1f;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        final float d = getResources().getDisplayMetrics().density;
        final float stageCorner = STAGE_CORNER_DP * d;
        mStageRect.set(0f, 0f, w, h);
        mStagePath.rewind();
        mStagePath.addRoundRect(mStageRect, stageCorner, stageCorner, Path.Direction.CW);

        final int stageSave = canvas.save();
        canvas.clipPath(mStagePath);

        final boolean hw = canvas.isHardwareAccelerated();
        if (hw) {
            ensureBackdropNodes(w, h, d);
            canvas.drawRenderNode(mBackdropNode);
        } else {
            drawBackdropContent(canvas, w, h, d);
        }

        final float pad = 12f * d;
        final float gap = 10f * d;
        final float usableW = Math.max(0f, w - pad * 2f - gap);
        final float dialogW = usableW * 0.64f;
        final float rightLeft = pad + dialogW + gap;
        final float rightRight = w - pad;
        final float rightUsableH = Math.max(0f, h - pad * 2f - gap);
        final float tileH = rightUsableH * 0.60f;

        mDialogRect.set(pad, pad, pad + dialogW, h - pad);
        mTileRect.set(rightLeft, pad, rightRight, pad + tileH);
        mPillRect.set(rightLeft, pad + tileH + gap, rightRight, h - pad);

        final int surfaceTint = resolveSurfaceTint();
        final AxBlurCustomization.Rim rim = mState != null ? mState.rim : AxBlurCustomization.Rim.NONE;

        drawGlassSurface(canvas, hw, mDialogRect, DIALOG_CORNER_DP * d, surfaceTint, rim,
                mDialogRimPainter, d);
        drawDialogSkeleton(canvas, mDialogRect, d);

        drawGlassSurface(canvas, hw, mTileRect, TILE_CORNER_DP * d, surfaceTint, rim,
                mTileRimPainter, d);
        drawTileSkeleton(canvas, mTileRect, d);

        final float pillCorner = Math.min(mPillRect.width(), mPillRect.height()) * 0.5f;
        drawGlassSurface(canvas, hw, mPillRect, pillCorner, surfaceTint, rim,
                mPillRimPainter, d);
        drawPillSkeleton(canvas, mPillRect, d);

        canvas.restoreToCount(stageSave);

        // Subtle outer border for the preview stage itself
        mPaint.reset();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(d);
        mPaint.setColor(mLightBackdrop ? 0x24000000 : 0x26FFFFFF);
        canvas.drawRoundRect(mStageRect, stageCorner, stageCorner, mPaint);
    }

    private void ensureBackdropNodes(int w, int h, float d) {
        if (mRecordedWidth != w || mRecordedHeight != h || mRecordedLight != mLightBackdrop
                || !mBackdropNode.hasDisplayList()) {
            mBackdropNode.setPosition(0, 0, w, h);
            final RecordingCanvas rc = mBackdropNode.beginRecording(w, h);
            try {
                drawBackdropContent(rc, w, h, d);
            } finally {
                mBackdropNode.endRecording();
            }
            mRecordedWidth = w;
            mRecordedHeight = h;
            mRecordedLight = mLightBackdrop;
            mBlurEffectRadius = -1f;
        }
        if (mBlurEffectRadius != mBlurRadiusPx || !mBlurredNode.hasDisplayList()) {
            mBlurredNode.setPosition(0, 0, w, h);
            if (mBlurRadiusPx > 0.5f) {
                mBlurredNode.setRenderEffect(RenderEffect.createBlurEffect(
                        mBlurRadiusPx, mBlurRadiusPx, Shader.TileMode.CLAMP));
            } else {
                mBlurredNode.setRenderEffect(null);
            }
            final RecordingCanvas rc = mBlurredNode.beginRecording(w, h);
            try {
                rc.drawRenderNode(mBackdropNode);
            } finally {
                mBlurredNode.endRecording();
            }
            mBlurEffectRadius = mBlurRadiusPx;
        }
    }

    private void drawBackdropContent(Canvas canvas, int w, int h, float d) {
        mPaint.reset();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        final int[] baseColors = mLightBackdrop
                ? new int[]{0xFFD7E6FF, 0xFFF1E3FA, 0xFFD2F2EC}
                : new int[]{0xFF0C1220, 0xFF1A1633, 0xFF0E1D28};
        mPaint.setShader(new LinearGradient(0f, 0f, w, h, baseColors, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, w, h, mPaint);

        drawOrb(canvas, w * 0.16f, h * 0.22f, h * 0.68f,
                mLightBackdrop ? 0xCC3B82F6 : 0xCC4C8DFF);
        drawOrb(canvas, w * 0.52f, h * 0.85f, h * 0.72f,
                mLightBackdrop ? 0xCCF472B6 : 0xCCD946EF);
        drawOrb(canvas, w * 0.88f, h * 0.28f, h * 0.62f,
                mLightBackdrop ? 0xCCFB923C : 0xCCFF6B6B);
        drawOrb(canvas, w * 0.72f, h * 0.82f, h * 0.58f,
                mLightBackdrop ? 0xCC10B981 : 0xCC22D3EE);

        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2.2f * d);
        mPaint.setColor(mLightBackdrop ? 0x441E293B : 0x44FFFFFF);
        canvas.drawCircle(w * 0.30f, h * 0.52f, h * 0.30f, mPaint);
        canvas.drawLine(w * 0.08f, h * 0.86f, w * 0.54f, h * 0.14f, mPaint);
        canvas.drawLine(w * 0.58f, h * 0.90f, w * 0.94f, h * 0.12f, mPaint);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mLightBackdrop ? 0x55FFFFFF : 0x33FFFFFF);
        canvas.drawCircle(w * 0.24f, h * 0.36f, 8f * d, mPaint);
        canvas.drawCircle(w * 0.44f, h * 0.66f, 6f * d, mPaint);
        canvas.drawCircle(w * 0.80f, h * 0.48f, 7f * d, mPaint);
    }

    private void drawOrb(Canvas canvas, float cx, float cy, float radius, int color) {
        mPaint.setShader(new RadialGradient(
                cx, cy, Math.max(1f, radius),
                color, color & 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, mPaint);
    }

    private void drawGlassSurface(
            Canvas canvas,
            boolean hw,
            RectF rect,
            float cornerRadius,
            int tintColor,
            AxBlurCustomization.Rim rim,
            AxBlurRimPainter rimPainter,
            float density) {
        if (rect.isEmpty()) return;
        mSurfacePath.rewind();
        mSurfacePath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);

        final int save = canvas.save();
        canvas.clipPath(mSurfacePath);
        if (hw) {
            canvas.drawRenderNode(mBlurredNode);
        }
        mPaint.reset();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(tintColor);
        canvas.drawRect(rect, mPaint);
        canvas.restoreToCount(save);

        if (rim != null && rim.enabled) {
            rimPainter.draw(
                    canvas,
                    rim,
                    density,
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    cornerRadius,
                    null,
                    255);
        }
    }

    private void drawDialogSkeleton(Canvas canvas, RectF rect, float d) {
        if (rect.width() < 48f * d || rect.height() < 48f * d) return;
        final boolean darkText = isSurfaceLight();
        final int primary = darkText ? 0xB316181F : 0xD8FFFFFF;
        final int secondary = darkText ? 0x5516181F : 0x66FFFFFF;
        final int buttonBg = darkText ? 0x3016181F : 0x38FFFFFF;

        mPaint.reset();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);

        final float pad = 14f * d;
        final float left = rect.left + pad;
        final float top = rect.top + pad;
        final float innerW = Math.max(0f, rect.width() - pad * 2f);

        mPaint.setColor(primary);
        mWorkRect.set(left, top, left + innerW * 0.52f, top + 7f * d);
        canvas.drawRoundRect(mWorkRect, 3.5f * d, 3.5f * d, mPaint);

        mPaint.setColor(secondary);
        mWorkRect.set(left, top + 14f * d, left + innerW * 0.90f, top + 19f * d);
        canvas.drawRoundRect(mWorkRect, 2.5f * d, 2.5f * d, mPaint);

        mWorkRect.set(left, top + 24f * d, left + innerW * 0.70f, top + 29f * d);
        canvas.drawRoundRect(mWorkRect, 2.5f * d, 2.5f * d, mPaint);

        final float btnH = 14f * d;
        final float btnY = rect.bottom - pad - btnH;
        if (btnY > top + 34f * d) {
            final float btnW = Math.min(44f * d, innerW * 0.34f);
            mPaint.setColor(buttonBg);
            mWorkRect.set(rect.right - pad - btnW, btnY, rect.right - pad, btnY + btnH);
            canvas.drawRoundRect(mWorkRect, btnH * 0.5f, btnH * 0.5f, mPaint);

            mWorkRect.set(rect.right - pad - btnW * 2f - 6f * d, btnY,
                    rect.right - pad - btnW - 6f * d, btnY + btnH);
            canvas.drawRoundRect(mWorkRect, btnH * 0.5f, btnH * 0.5f, mPaint);
        }
    }

    private void drawTileSkeleton(Canvas canvas, RectF rect, float d) {
        if (rect.width() < 28f * d || rect.height() < 28f * d) return;
        final boolean darkText = isSurfaceLight();
        mPaint.reset();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(darkText ? 0xA816181F : 0xCCFFFFFF);
        final float pad = 11f * d;
        final float dotR = 7f * d;
        canvas.drawCircle(rect.left + pad + dotR, rect.top + pad + dotR, dotR, mPaint);

        mPaint.setColor(darkText ? 0x6016181F : 0x70FFFFFF);
        final float barY = rect.bottom - pad - 5f * d;
        if (barY > rect.top + pad + dotR * 2f + 4f * d) {
            mWorkRect.set(rect.left + pad, barY,
                    rect.left + pad + (rect.width() - pad * 2f) * 0.72f, barY + 5f * d);
            canvas.drawRoundRect(mWorkRect, 2.5f * d, 2.5f * d, mPaint);
        }
    }

    private void drawPillSkeleton(Canvas canvas, RectF rect, float d) {
        if (rect.width() < 24f * d || rect.height() < 16f * d) return;
        final boolean darkText = isSurfaceLight();
        mPaint.reset();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(darkText ? 0x7816181F : 0x99FFFFFF);
        final float barW = Math.min(rect.width() * 0.52f, 44f * d);
        final float barH = 4.5f * d;
        mWorkRect.set(
                rect.centerX() - barW * 0.5f,
                rect.centerY() - barH * 0.5f,
                rect.centerX() + barW * 0.5f,
                rect.centerY() + barH * 0.5f);
        canvas.drawRoundRect(mWorkRect, barH * 0.5f, barH * 0.5f, mPaint);
    }

    private int resolveSurfaceTint() {
        if (mState != null && mState.tintEnabled) {
            return mState.resolveTint(getContext(), 0);
        }
        return mLightBackdrop ? 0x8AF4F6FA : 0x8A1E222B;
    }

    private boolean isSurfaceLight() {
        final int tint = resolveSurfaceTint();
        final float alpha = Color.alpha(tint) / 255f;
        if (alpha < 0.2f) return mLightBackdrop;
        final double lum = (0.299 * Color.red(tint)
                + 0.587 * Color.green(tint)
                + 0.114 * Color.blue(tint)) / 255.0;
        return lum > 0.58;
    }
}
