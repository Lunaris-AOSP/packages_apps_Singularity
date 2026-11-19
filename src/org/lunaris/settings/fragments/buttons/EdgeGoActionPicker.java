/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.fragments.buttons;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.Insets;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.internal.util.lunaris.EdgeGoConfig;
import com.android.settings.R;
import com.android.settingslib.utils.ThreadUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class EdgeGoActionPicker {
    private static final class Item {
        final String title, value;
        Item(String title, String value) { this.title = title; this.value = value; }
    }

    private static final class ItemAdapter extends ArrayAdapter<Item> {
        private final LayoutInflater inflater;

        ItemAdapter(Context context, List<Item> items) {
            super(context, 0, items);
            inflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : inflater.inflate(R.layout.edgego_picker_item, parent, false);
            String[] lines = getItem(position).title.split("\n", 2);
            TextView title = row.findViewById(android.R.id.title);
            TextView summary = row.findViewById(android.R.id.summary);
            title.setText(lines[0]);
            summary.setText(lines.length > 1 ? lines[1] : "");
            summary.setVisibility(lines.length > 1 ? View.VISIBLE : View.GONE);
            return row;
        }
    }

    static void show(EdgeGoSettings fragment, boolean safe, Consumer<String> selected) {
        show(fragment, safe, false, selected);
    }

    static void showMacro(EdgeGoSettings fragment, Consumer<String> selected) { show(fragment, true, true, selected); }

    private static void show(EdgeGoSettings fragment, boolean safe, boolean forMacro, Consumer<String> selected) {
        List<Item> items = new ArrayList<>();
        String[] labels = fragment.getResources().getStringArray(R.array.edgego_action_labels);
        for (int i = 0; i < EdgeGoConfig.ACTIONS.length; i++) {
            String id = EdgeGoConfig.ACTIONS[i];
            if (forMacro && !EdgeGoConfig.macroAction(id)) continue;
            if (safe && Arrays.asList("remote_cursor", "touch_pad", "drawing_pad", "text_picker", "page_scroll",
                    "show_quick_access", "app_launcher", "notch_apps", "preset_picker", "notification_carousel").contains(id)) continue;
            items.add(new Item(labels[i], id));
            if (id.equals("show_quick_access") || id.equals("notch_apps")) items.add(new Item(labels[i] + " · " + fragment.getString(R.string.edgego_set_two), id + ":2"));
            if (id.equals("page_scroll")) items.add(new Item(labels[i] + " · " + fragment.getString(R.string.edgego_scroll_up), "page_scroll:up"));
        }
        if (!safe) {
            items.add(new Item(fragment.getString(R.string.edgego_launch_app), "picker_app"));
            items.add(new Item(fragment.getString(R.string.edgego_app_shortcut), "picker_shortcut"));
            JSONArray macros = fragment.configuration().array("macros_v2_json");
            for (int i = 0; i < macros.length(); i++) { JSONObject macro = macros.optJSONObject(i); if (macro != null) items.add(new Item(
                    fragment.getString(R.string.edgego_macro) + " · " + macro.optString("name"), "run_macro:" + macro.optString("id"))); }
        }
        dialog(fragment, fragment.getString(R.string.edgego_choose_action), items, value -> {
            if (value.equals("picker_app")) pickActivity(fragment, info -> selected.accept("launch_app_activity:"
                    + info.getComponentName().getPackageName() + "|" + info.getComponentName().getClassName()));
            else if (value.equals("picker_shortcut")) shortcuts(fragment, selected);
            else selected.accept(value);
        });
    }

    static String label(Context context, String action) {
        String base = action.split(":", 2)[0]; int index = Arrays.asList(EdgeGoConfig.ACTIONS).indexOf(base);
        if (index >= 0) return context.getResources().getStringArray(R.array.edgego_action_labels)[index];
        if (base.startsWith("launch_app") || base.equals("shortcut")) {
            String pkg = action.substring(action.indexOf(':') + 1).split("\\|", 2)[0];
            try { return context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
            catch (Exception ignored) { return pkg; }
        }
        return base.equals("run_macro") ? context.getString(R.string.edgego_macro) : context.getString(R.string.edgego_shortcut);
    }

    static void pickApp(EdgeGoSettings fragment, Consumer<String> selected) {
        pickActivity(fragment, info -> selected.accept(info.getComponentName().getPackageName()));
    }

    private static void pickActivity(EdgeGoSettings fragment, Consumer<LauncherActivityInfo> selected) {
        Context context = fragment.requireContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            List<LauncherActivityInfo> apps = new ArrayList<>(context.getSystemService(LauncherApps.class).getActivityList(null, UserHandle.of(UserHandle.myUserId())));
            Collator collator = Collator.getInstance(); apps.sort((a, b) -> collator.compare(a.getLabel().toString(), b.getLabel().toString()));
            List<Item> items = new ArrayList<>(); for (int i = 0; i < apps.size(); i++) items.add(new Item(apps.get(i).getLabel() + "\n" + apps.get(i).getComponentName().getPackageName(), String.valueOf(i)));
            ThreadUtils.postOnMainThread(() -> { if (fragment.isAdded() && fragment.isResumed()) dialog(fragment, context.getString(R.string.edgego_choose_app), items,
                    index -> selected.accept(apps.get(Integer.parseInt(index)))); });
        });
    }

    private static void shortcuts(EdgeGoSettings fragment, Consumer<String> selected) {
        pickActivity(fragment, app -> {
            Context context = fragment.requireContext();
            ThreadUtils.postOnBackgroundThread(() -> {
                List<Item> items = new ArrayList<>();
                try {
                    LauncherApps launcher = context.getSystemService(LauncherApps.class);
                    LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery().setPackage(app.getComponentName().getPackageName())
                            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
                    List<ShortcutInfo> shortcuts = launcher.getShortcuts(query, UserHandle.of(UserHandle.myUserId()));
                    if (shortcuts != null) for (ShortcutInfo shortcut : shortcuts) if (shortcut.isEnabled()) items.add(new Item(String.valueOf(shortcut.getShortLabel()),
                            "shortcut:" + shortcut.getPackage() + "|" + shortcut.getId()));
                } catch (SecurityException ignored) { }
                List<ResolveInfo> providers = context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(app.getComponentName().getPackageName()), 0);
                for (int i = 0; i < providers.size(); i++) if (providers.get(i).activityInfo.exported) items.add(new Item(providers.get(i).loadLabel(context.getPackageManager()).toString(), "legacy:" + i));
                ThreadUtils.postOnMainThread(() -> {
                    if (!fragment.isAdded() || !fragment.isResumed()) return;
                    dialog(fragment, context.getString(R.string.edgego_app_shortcut), items, value -> {
                        if (value.startsWith("legacy:")) {
                            ResolveInfo provider = providers.get(Integer.parseInt(value.substring(7)));
                            fragment.createShortcut(new Intent(Intent.ACTION_CREATE_SHORTCUT).setClassName(provider.activityInfo.packageName, provider.activityInfo.name), selected);
                        } else selected.accept(value);
                    });
                });
            });
        });
    }

    static void pickIconPack(EdgeGoSettings fragment, Consumer<String> selected) {
        Context context = fragment.requireContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            List<Item> packs = new ArrayList<>(); packs.add(new Item(context.getString(R.string.edgego_system_icons), ""));
            List<String> packages = new ArrayList<>();
            for (String action : new String[]{"org.adw.launcher.THEMES", "com.novalauncher.THEME", "app.lawnchair.icons.THEMED_ICON", "ch.deletescape.lawnchair.ICONPACK"}) {
                for (ResolveInfo info : context.getPackageManager().queryIntentActivities(new Intent(action), 0)) {
                    if (!packages.contains(info.activityInfo.packageName)) { packages.add(info.activityInfo.packageName); packs.add(new Item(info.loadLabel(context.getPackageManager()).toString(), info.activityInfo.packageName)); }
                }
            }
            ThreadUtils.postOnMainThread(() -> { if (fragment.isAdded() && fragment.isResumed()) dialog(fragment, context.getString(R.string.edgego_icon_pack), packs, selected); });
        });
    }

    private static void dialog(EdgeGoSettings fragment, String title, List<Item> items, Consumer<String> selected) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.requireContext());
        Context context = builder.getContext();
        View body = LayoutInflater.from(context).inflate(R.layout.edgego_action_picker, null);
        EditText search = body.findViewById(R.id.edgego_picker_search);
        ImageButton clearSearch = body.findViewById(R.id.edgego_picker_clear_search);
        ListView list = body.findViewById(android.R.id.list);
        TextView empty = body.findViewById(android.R.id.empty);
        empty.setText(items.isEmpty() ? R.string.edgego_picker_empty
                : R.string.edgego_picker_no_results);
        list.setEmptyView(empty);

        WindowMetrics metrics = fragment.requireActivity().getWindowManager()
                .getCurrentWindowMetrics();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int availableWidth = metrics.getBounds().width() - insets.left - insets.right;
        int availableHeight = metrics.getBounds().height() - insets.top - insets.bottom;
        View results = body.findViewById(R.id.edgego_picker_results);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) results.getLayoutParams();
        // Keep the search and buttons visible when the dialog is resized for the keyboard.
        params.height = Math.min(dp(context, 360), availableHeight / 2);
        results.setLayoutParams(params);

        List<Item> visible = new ArrayList<>(items);
        ItemAdapter adapter = new ItemAdapter(context, visible);
        list.setAdapter(adapter);
        AlertDialog dialog = builder.setTitle(title).setView(body)
                .setNegativeButton(android.R.string.cancel, null).create();
        list.setOnItemClickListener((parent, view, index, id) -> {
            String value = adapter.getItem(index).value;
            dialog.dismiss();
            if (fragment.isAdded()) selected.accept(value);
        });
        clearSearch.setOnClickListener(view -> search.getText().clear());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                visible.clear();
                String query = s.toString().trim().toLowerCase(Locale.getDefault());
                boolean packages = fragment.configuration().bool("include_package_in_picker_search");
                for (Item item : items) {
                    String title = packages ? item.title : item.title.split("\n", 2)[0];
                    boolean value = packages || !item.value.startsWith("launch_app") && !item.value.startsWith("shortcut:");
                    if (title.toLowerCase(Locale.getDefault()).contains(query) || value && item.value.toLowerCase(Locale.ROOT).contains(query)) {
                        visible.add(item);
                    }
                }
                clearSearch.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
                adapter.notifyDataSetChanged();
                list.setSelection(0);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        fragment.trackDialog(dialog);
        Window window = dialog.getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.show();
        window.setLayout(Math.min(dp(context, 560), availableWidth - dp(context, 32)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        body.requestFocus();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
