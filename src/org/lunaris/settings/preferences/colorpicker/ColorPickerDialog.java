/*
 * Copyright (C) 2010 Daniel Nilsson
 *               2013 Slimroms
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.preferences.colorpicker;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.android.settings.R;

public class ColorPickerDialog extends AlertDialog implements ColorPickerView.OnColorChangedListener, View.OnClickListener {

    private ColorPickerView mColorPicker;
    private ColorPickerPanelView mOldColor;
    private ColorPickerPanelView mNewColor;
    private EditText mHex;

    private ColorPickerPanelView mWhite;
    private ColorPickerPanelView mBlack;
    private ColorPickerPanelView mCyan;
    private ColorPickerPanelView mRed;
    private ColorPickerPanelView mGreen;
    private ColorPickerPanelView mYellow;

    private OnColorChangedListener mListener;

    public interface OnColorChangedListener {
        public void onColorChanged(int color);
    }

    public ColorPickerDialog(Context context, int initialColor) {
        super(context);
        init(initialColor);
    }

    private void init(int color) {
        if (getWindow() != null) {
            getWindow().setFormat(PixelFormat.RGBA_8888);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setUp(color);
        }
    }

    private void setUp(int color) {

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        assert inflater != null;
        View layout = inflater.inflate(R.layout.dialog_color_picker, null);

        mColorPicker = layout.findViewById(R.id.color_picker_view);
        mOldColor = layout.findViewById(R.id.old_color_panel);
        mNewColor = layout.findViewById(R.id.new_color_panel);

        mWhite = layout.findViewById(R.id.white_panel);
        mBlack = layout.findViewById(R.id.black_panel);
        mCyan = layout.findViewById(R.id.cyan_panel);
        mRed = layout.findViewById(R.id.red_panel);
        mGreen = layout.findViewById(R.id.green_panel);
        mYellow = layout.findViewById(R.id.yellow_panel);

        mHex = layout.findViewById(R.id.hex);
        ImageButton mSetButton = layout.findViewById(R.id.enter);

        ((LinearLayout) mOldColor.getParent()).setPadding(Math.round(mColorPicker.getDrawingOffset()),
                0, Math.round(mColorPicker.getDrawingOffset()), 0);

        mOldColor.setOnClickListener(this);
        mNewColor.setOnClickListener(this);
        mColorPicker.setOnColorChangedListener(this);
        mOldColor.setColor(color);
        mColorPicker.setColor(color, true);

        setColorAndClickAction(mWhite, Color.WHITE);
        setColorAndClickAction(mBlack, Color.BLACK);
        setColorAndClickAction(mCyan, 0xff24b7d6);
        setColorAndClickAction(mRed, 0xfff90028);
        setColorAndClickAction(mGreen, 0xff76c124);
        setColorAndClickAction(mYellow, 0xffffc90f);

        if (mHex != null) {
            mHex.setText(ColorPickerPreference.convertToARGB(color));
        }
        if (mSetButton != null) {
            mSetButton.setOnClickListener(v -> {
                String text = mHex.getText().toString();
                try {
                    int newColor = ColorPickerPreference.convertToColorInt(text);
                    mColorPicker.setColor(newColor, true);
                } catch (Exception e) {
                }
            });
        }

        setView(layout);
    }

    @Override
    public void onColorChanged(int color) {

        mNewColor.setColor(color);
        try {
            if (mHex != null) {
                mHex.setText(ColorPickerPreference.convertToARGB(color));
            }
        } catch (Exception e) {
        }
    }

    public void setAlphaSliderVisible(boolean visible) {
        mColorPicker.setAlphaSliderVisible(visible);
    }

    public void setColorAndClickAction(ColorPickerPanelView previewRect, final int color) {
        if (previewRect != null) {
            previewRect.setColor(color);
            previewRect.setOnClickListener(v -> {
                try {
                    mColorPicker.setColor(color, true);
                } catch (Exception e) {
                }
            });
        }
    }

    /**
     * Set a OnColorChangedListener to get notified when the color selected by the user has changed.
     *
     * @param listener
     */
    public void setOnColorChangedListener(OnColorChangedListener listener) {
        mListener = listener;
    }

    public int getColor() {
        return mColorPicker.getColor();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.new_color_panel) {
            if (mListener != null) {
                mListener.onColorChanged(mNewColor.getColor());
            }
        }
        dismiss();
    }

    @NonNull
    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt("old_color", mOldColor.getColor());
        state.putInt("new_color", mNewColor.getColor());
        dismiss();
        return state;
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mOldColor.setColor(savedInstanceState.getInt("old_color"));
        mColorPicker.setColor(savedInstanceState.getInt("new_color"), true);
    }
}
