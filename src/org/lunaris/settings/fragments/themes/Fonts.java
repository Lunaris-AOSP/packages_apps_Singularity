/*
 * Copyright (C) 2022-2025 crDroid Android Project
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

package org.lunaris.settings.fragments.themes;

import static com.android.internal.util.lunaris.ThemeUtils.FONT_KEY;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.util.lunaris.ThemeUtils;

import java.lang.ref.WeakReference;
import java.util.List;

public class Fonts extends SettingsPreferenceFragment {

    private static final String TAG = "FontsPicker";

    private RecyclerView mRecyclerView;
    private ThemeUtils mThemeUtils;
    private final String mCategory = FONT_KEY;
    private List<String> mPkgs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isAdded()) return;
        requireActivity().setTitle(R.string.theme_customization_font_title);
        mThemeUtils = new ThemeUtils(requireContext());
        mPkgs = mThemeUtils.getOverlayPackagesForCategory(mCategory, "android");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.item_view, container, false);
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 1));

        // Fix: safely assign adapter after layout inflation
        view.post(() -> {
            if (mRecyclerView != null) {
                mRecyclerView.setAdapter(
                    new Adapter(requireContext(), mPkgs, mThemeUtils, mCategory, mRecyclerView)
                );
            } else {
                Log.w(TAG, "RecyclerView is null — adapter not set");
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mRecyclerView != null) {
            mRecyclerView.setAdapter(null);
            mRecyclerView = null;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    public static class Adapter extends RecyclerView.Adapter<Adapter.CustomViewHolder> {
        private final WeakReference<Context> contextRef;
        private final List<String> mPkgs;
        private final ThemeUtils mThemeUtils;
        private final String mCategory;
        private final RecyclerView mRecyclerView;
        private final String mAppliedPkg;
        private String mSelectedPkg;

        public Adapter(Context context, List<String> pkgs, ThemeUtils themeUtils, String category, RecyclerView recyclerView) {
            this.contextRef = new WeakReference<>(context);
            this.mPkgs = pkgs;
            this.mThemeUtils = themeUtils;
            this.mCategory = category;
            this.mRecyclerView = recyclerView;

            mAppliedPkg = mThemeUtils.getOverlayInfos(mCategory).stream()
                    .filter(info -> info.isEnabled())
                    .map(info -> info.packageName)
                    .findFirst()
                    .orElse("android");

            mSelectedPkg = mAppliedPkg;
        }

        @NonNull
        @Override
        public CustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.fonts_option, parent, false);
            return new CustomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CustomViewHolder holder, int position) {
            Context context = contextRef.get();
            if (context == null) return;

            String pkg = mPkgs.get(position);
            String label = getLabel(context, pkg);
            Typeface typeface = getTypeface(context, pkg);

            holder.title.setText("android".equals(pkg) ? "Default" : label);
            holder.title.setTextSize(20);
            if (typeface != null) {
                holder.title.setTypeface(typeface);
            }
            holder.name.setVisibility(View.GONE);
            holder.itemView.setActivated(pkg.equals(mSelectedPkg));

            holder.itemView.setOnClickListener(view -> {
                if (!pkg.equals(mSelectedPkg)) {
                    String oldPkg = mSelectedPkg;
                    mSelectedPkg = pkg;
                    mThemeUtils.setOverlayEnabled(mCategory, pkg, "android");
                    updateActivatedStatus(oldPkg);
                    updateActivatedStatus(mSelectedPkg);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mPkgs.size();
        }

        private void updateActivatedStatus(String pkg) {
            int index = mPkgs.indexOf(pkg);
            if (index >= 0) {
                notifyItemChanged(index);
            }
        }

        public static class CustomViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            TextView title;
            public CustomViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.option_title);
                name = itemView.findViewById(R.id.option_label);
            }
        }

        private Typeface getTypeface(Context context, String pkg) {
            try {
                PackageManager pm = context.getPackageManager();
                Resources res = pkg.equals("android") ? Resources.getSystem()
                        : pm.getResourcesForApplication(pkg);
                int id = res.getIdentifier("config_bodyFontFamily", "string", pkg);
                if (id != 0) {
                    return Typeface.create(res.getString(id), Typeface.NORMAL);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Typeface load failed for pkg: " + pkg, e);
            }
            return null;
        }

        private String getLabel(Context context, String pkg) {
            PackageManager pm = context.getPackageManager();
            try {
                return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Label load failed for pkg: " + pkg, e);
            }
            return pkg;
        }
    }
}
