/*
 * Copyright (C) 2023 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lunaris.settings.fragments.themes.fonts;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.android.settings.R;

import androidx.core.content.ContextCompat;

import java.util.List;

public class FontArrayAdapter extends ArrayAdapter<String> {
    private FontManager fontManager;
    private List<String> fontPackageNames;
    private List<Typeface> typefaces;
    private Context mContext;
    private boolean mIsNightMode;

    public FontArrayAdapter(Context context, int textViewResourceId,
            List<String> objects, FontManager fontManager, boolean nightMode) {
        super(context, textViewResourceId, objects);
        this.mContext = context;
        this.fontPackageNames = objects;
        this.fontManager = fontManager;
        this.typefaces = fontManager.getFonts();
        this.mIsNightMode = nightMode;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view = (TextView) super.getView(position, convertView, parent);
        Typeface typeface = getTypefaceForPosition(position);
        if (typeface != null) {
            view.setTypeface(typeface);
        }
        view.setText(getLabelForPosition(position));
        view.setTextColor(ContextCompat.getColor(mContext, mIsNightMode
                ? R.color.font_drop_down_bg_light
                : R.color.font_drop_down_bg_dark));
        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView view = (TextView) super.getDropDownView(position, convertView, parent);
        Typeface typeface = getTypefaceForPosition(position);
        if (typeface != null) {
            view.setTypeface(typeface);
        }
        view.setText(getLabelForPosition(position));
        view.setTextColor(ContextCompat.getColor(mContext, mIsNightMode
                ? R.color.font_drop_down_bg_light
                : R.color.font_drop_down_bg_dark));
        return view;
    }

    private String getLabelForPosition(int position) {
        return fontManager.getLabel(mContext, getFontPackageName(position));
    }

    private Typeface getTypefaceForPosition(int position) {
        return fontManager.getTypeface(getContext(), getFontPackageName(position));
    }

    private String getFontPackageName(int position) {
        return fontPackageNames.get(position);
    }
}
