/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 */
package org.lunaris.settings.fragments.buttons;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.lunaris.EdgeGoConfig;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.search.SearchIndexableRaw;
import com.android.settingslib.utils.ThreadUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SearchIndexable
public final class EdgeGoSettings extends SettingsPreferenceFragment {
    private static final int EXPORT = 81, IMPORT = 82;
    static final String[] SECTIONS = {"controls", "appearance", "animation", "panels", "sliders", "sectors",
            "pie", "tools", "launcher", "media", "side_panel", "notifications", "touch_pad", "cursor",
            "scroll", "drawing", "dim", "notch", "presets", "macros", "pauses"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ContentObserver observer = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { if (isAdded()) refresh(); }
    };
    private EdgeGoConfig config;
    private String section = "", zone = "";
    private AlertDialog dialog;
    private EdgeGoPreviewPreference preview;
    private EdgeGoSectorPreference sectorPreview;
    private EdgeGoGuidePreference guidePreview;
    private EdgeGoCollectionEditor collectionEditor;
    private boolean observing;
    private java.util.function.Consumer<String> shortcutResult;
    private String shortcutKey = "";
    private int shortcutParent = -1, shortcutIndex = -1;
    private boolean shortcutHold;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        config = read();
        if (state != null) {
            shortcutKey = state.getString("shortcutKey", ""); shortcutParent = state.getInt("shortcutParent", -1);
            shortcutIndex = state.getInt("shortcutIndex", -1); shortcutHold = state.getBoolean("shortcutHold");
        }
        Bundle args = getArguments();
        if (args != null) {
            section = args.getString("section", ""); zone = args.getString("zone", "");
            String highlighted = args.getString(":settings:fragment_args_key", "");
            if (section.isEmpty() && EdgeGoConfig.option(highlighted) != null) section = EdgeGoConfig.option(highlighted).section;
            if (section.isEmpty() && highlighted.equals("edgego_help")) section = "guide";
            if (section.isEmpty() && highlighted.startsWith("edgego_guide_")
                    && EdgeGoGuide.known(highlighted.substring(13))) section = "guide_" + highlighted.substring(13);
        }
        if (!Arrays.asList(EdgeGoConfig.ZONES).contains(zone)) zone = "";
        if (section.isEmpty() && zone.isEmpty()) root();
        else {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
            setPreferenceScreen(screen);
            if (!zone.isEmpty()) zone(screen);
            else section(screen);
        }
    }

    private EdgeGoConfig read() { return new EdgeGoConfig(requireContext().getContentResolver(), UserHandle.myUserId()); }

    private void root() {
        addPreferencesFromResource(R.xml.edgego_settings);
        SwitchPreferenceCompat guard = findPreference("disable_in_gaming_mode");
        guard.setChecked(config.bool("disable_in_gaming_mode"));
        guard.setOnPreferenceChangeListener((preference, value) -> write("disable_in_gaming_mode", value));
        PreferenceGroup zones = findPreference("zones");
        for (String id : EdgeGoConfig.ZONES) {
            Preference entry = entry(zones, "zone_" + id, zoneTitle(id), () -> open("", id, zoneTitle(id)));
            entry.setSummary(config.zoneEnabled(id) ? R.string.edgego_zone_on : R.string.edgego_zone_off);
        }
        entry(zones, "global_size", getString(R.string.edgego_global_size), this::globalSize);
        PreferenceGroup sections = findPreference("sections");
        for (String id : SECTIONS) entry(sections, id, sectionTitle(id), () -> open(id, "", sectionTitle(id)));
        findPreference("edgego_help").setOnPreferenceClickListener(preference -> {
            open("guide", "", getString(R.string.edgego_help));
            return true;
        });
    }

    private void section(PreferenceGroup screen) {
        if (section.equals("guide") || section.startsWith("guide_")) {
            new EdgeGoGuide(this).show(screen, section.equals("guide") ? "" : section.substring(6));
            return;
        }
        for (EdgeGoConfig.Option option : EdgeGoConfig.options(section)) option(screen, option);
        switch (section) {
            case "controls":
                entry(screen, "lock_edges", getString(R.string.edgego_lock_edges), () -> pauseEdges("disabled_lock_screen_edges"));
                entry(screen, "home_edges", getString(R.string.edgego_home_edges), () -> pauseEdges("disabled_home_screen_edges"));
                break;
            case "animation": entry(screen, "haptic_test", getString(R.string.edgego_haptic_test), () -> command("haptic")); break;
            case "panels": entry(screen, "reset_positions", getString(R.string.edgego_reset_positions), () -> command("reset_positions")); break;
            case "pie":
                collection(screen, "quick_access_items", R.string.edgego_pie_set_one, true);
                collection(screen, "quick_access_items_2", R.string.edgego_pie_set_two, true);
                collection(screen, "quick_access_drawer_items", R.string.edgego_pie_drawer, false);
                break;
            case "tools":
                collection(screen, "quick_tools_items", R.string.edgego_tools_items, false);
                entry(screen, "tools_order", getString(R.string.edgego_tools_order), () -> order("quick_tools_section_order", new String[]{"volume", "brightness", "media", "actions"}));
                break;
            case "launcher":
                collection(screen, "app_launcher_pinned_items", R.string.edgego_launcher_pins, false);
                entry(screen, "icon_pack", getString(R.string.edgego_icon_pack), () -> EdgeGoActionPicker.pickIconPack(this,
                        pack -> write("app_icon_pack_package", pack)));
                break;
            case "side_panel": collection(screen, "side_panel_items", R.string.edgego_side_items, false); break;
            case "notch":
                collection(screen, "notch_apps_items", R.string.edgego_notch_set_one, false);
                collection(screen, "notch_apps_items_2", R.string.edgego_notch_set_two, false);
                break;
            case "cursor":
                entry(screen, "cursor_double", getString(R.string.edgego_cursor_double_action), () -> pickAction("remote_cursor_double_tap_bound_action_id", true));
                entry(screen, "cursor_hold", getString(R.string.edgego_cursor_hold_action), () -> pickAction("remote_cursor_hold_bound_action_id", true));
                entry(screen, "cursor_top", getString(R.string.edgego_cursor_top_action), () -> pickAction("remote_cursor_top_edge_action_id", true));
                break;
            case "sectors": {
                sectorPreview = new EdgeGoSectorPreference(requireContext(), config, values -> {
                    if (!EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(), current -> {
                        for (String key : new String[]{"custom_pull_sector_dividers", "custom_pull_upper_limit", "custom_pull_lower_limit"}) {
                            if (values.has(key)) EdgeGoConfig.put(current, key, values.opt(key));
                        }
                    })) Toast.makeText(requireContext(), R.string.edgego_save_failed, Toast.LENGTH_SHORT).show();
                });
                screen.addPreference(sectorPreview);
                break;
            }
            case "drawing": drawings(screen, null); break;
            case "presets": presets(screen); break;
            case "macros":
                collectionEditor = new EdgeGoCollectionEditor(this);
                collectionEditor.macros(screen);
                break;
            case "pauses": pauseRules(screen); break;
        }
    }

    private void zone(PreferenceGroup screen) {
        preview = new EdgeGoPreviewPreference(requireContext(), config, zone, value -> write("edge_" + zone + "_position", value));
        screen.addPreference(preview);
        SwitchPreferenceCompat enabled = toggle(screen, "edge_" + zone + "_enabled", getString(R.string.edgego_zone_enabled), config.zoneEnabled(zone));
        enabled.setOnPreferenceChangeListener((preference, value) -> EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(), values -> {
            EdgeGoConfig.put(values, preference.getKey(), value);
            if (Boolean.TRUE.equals(value) && zone.endsWith("z2")) EdgeGoConfig.put(values, "split_zones_enabled", true);
            if (Boolean.TRUE.equals(value) && zone.startsWith("bottom_navigation")) {
                EdgeGoConfig.put(values, "edge_bottom_navigation_replacement_enabled", true);
            }
        }));
        zoneRange(screen, "thickness", R.string.edgego_thickness, 16, 4, 40);
        int length = zone.startsWith("left") || zone.startsWith("right") ? 25
                : zone.startsWith("bottom_navigation") ? 100 : 65;
        zoneRange(screen, "length", R.string.edgego_length, length, 5, 100);
        zoneRange(screen, "landscape_length", R.string.edgego_landscape_length, length, 5, 100);
        zoneRange(screen, "position", R.string.edgego_position, zone.endsWith("z2") ? 88 : 50, 0, 100);
        ListPreference orientation = new ListPreference(requireContext());
        orientation.setKey("edge_" + zone + "_orientation_mode"); orientation.setTitle(R.string.edgego_orientation);
        orientation.setEntries(new String[]{getString(R.string.edgego_both), getString(R.string.edgego_portrait), getString(R.string.edgego_landscape)});
        orientation.setEntryValues(new String[]{"both", "portrait", "landscape"});
        orientation.setPersistent(false); orientation.setValue(config.zoneString(zone, "orientation_mode", "both"));
        orientation.setSummary(orientation.getEntry());
        orientation.setOnPreferenceChangeListener((preference, value) -> write(preference.getKey(), value)); screen.addPreference(orientation);
        toggle(screen, "edge_" + zone + "_indicator", getString(R.string.edgego_show_zone), config.zoneBoolean(zone, "indicator", true));
        toggle(screen, "edge_" + zone + "_vibrate", getString(R.string.edgego_zone_vibrate), config.zoneBoolean(zone, "vibrate", true));
        PreferenceCategory gestures = category(screen, "gestures", getString(R.string.edgego_gestures));
        for (String gesture : config.gestures(zone)) {
            String key = "gesture_" + zone + "_" + gesture;
            entry(gestures, key, gestureTitle(gesture), () -> pickAction(key, false)).setSummary(EdgeGoActionPicker.label(requireContext(), config.action(zone, gesture)));
        }
        drawings(screen, zone);
        entry(screen, "copy_actions", getString(R.string.edgego_copy_actions), () -> copyActions(zone));
    }

    private void option(PreferenceGroup screen, EdgeGoConfig.Option option) {
        String title = title(option.key, option.title);
        if (option.kind == EdgeGoConfig.Kind.SWITCH) {
            SwitchPreferenceCompat toggle = toggle(screen, option.key, title, config.bool(option.key));
            if (option.key.equals("continuous_swipe_enabled")) toggle.setSummary(R.string.edgego_continuous_swipe_summary);
            if (option.key.equals("custom_pull_angles_enabled")) toggle.setOnPreferenceChangeListener((preference, value) -> {
                if (Boolean.TRUE.equals(value)) return remapSectors(config.integer("custom_pull_angle_slot_count"), true);
                return write(option.key, value);
            });
        } else if (option.kind == EdgeGoConfig.Kind.CHOICE) {
            ListPreference choice = new ListPreference(requireContext());
            choice.setKey(option.key); choice.setTitle(title); choice.setPersistent(false);
            String[] values = option.choices.split("\\|");
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = option.key.equals("notification_carousel_snooze_minutes") && !values[i].equals("off")
                    ? getString(R.string.edgego_minutes_value, Integer.parseInt(values[i])) : choiceTitle(values[i]);
            choice.setEntries(labels); choice.setEntryValues(values); choice.setValue(config.string(option.key)); choice.setSummary(choice.getEntry());
            choice.setOnPreferenceChangeListener((preference, value) -> write(option.key, value)); screen.addPreference(choice);
        } else if (option.kind == EdgeGoConfig.Kind.RANGE) {
            Preference range = entry(screen, option.key, title, () -> range(title, config.integer(option.key), option.min, option.max,
                    value -> {
                        if (option.key.equals("custom_pull_angle_slot_count")) remapSectors(value, config.bool("custom_pull_angles_enabled"));
                        else write(option.key, value);
                    }));
            range.setSummary(option.key.equals("continuous_adjustment_interval_ms")
                    ? getString(R.string.edgego_continuous_adjustment_interval_summary, config.integer(option.key))
                    : value(option.key, config.integer(option.key)));
        } else if (option.kind == EdgeGoConfig.Kind.COLOR) {
            entry(screen, option.key, title, () -> color(option)).setSummary(String.format(LocaleHolder.ROOT, "#%08X", config.integer(option.key)));
        } else {
            entry(screen, option.key, title, () -> time(option)).setSummary(String.format(LocaleHolder.ROOT, "%02d:%02d", config.integer(option.key) / 60, config.integer(option.key) % 60));
        }
    }

    private SwitchPreferenceCompat toggle(PreferenceGroup screen, String key, String title, boolean checked) {
        SwitchPreferenceCompat preference = new SwitchPreferenceCompat(requireContext());
        preference.setKey(key); preference.setTitle(title); preference.setPersistent(false); preference.setChecked(checked);
        preference.setOnPreferenceChangeListener((p, value) -> write(key, value)); screen.addPreference(preference);
        return preference;
    }

    private void zoneRange(PreferenceGroup screen, String key, int title, int initial, int min, int max) {
        String setting = "edge_" + zone + "_" + key;
        entry(screen, setting, getString(title), () -> range(getString(title), config.zoneInt(zone, key, initial, min, max), min, max,
                value -> write(setting, value))).setSummary(value(key, config.zoneInt(zone, key, initial, min, max)));
    }

    private Preference entry(PreferenceGroup screen, String key, String title, Runnable click) {
        Preference preference = new Preference(requireContext());
        preference.setKey(key); preference.setTitle(title); preference.setPersistent(false);
        preference.setOnPreferenceClickListener(p -> { click.run(); return true; }); screen.addPreference(preference);
        return preference;
    }

    private PreferenceCategory category(PreferenceGroup screen, String key, String title) {
        PreferenceCategory category = new PreferenceCategory(requireContext()); category.setKey(key); category.setTitle(title); screen.addPreference(category); return category;
    }

    private void collection(PreferenceGroup screen, String key, int title, boolean pie) {
        entry(screen, key, getString(title), () -> {
            collectionEditor = new EdgeGoCollectionEditor(this); collectionEditor.items(key, pie);
        }).setSummary(getString(R.string.edgego_item_count, config.array(key).length()));
    }

    private void open(String section, String zone, String title) {
        Bundle args = new Bundle(); args.putString("section", section); args.putString("zone", zone);
        new SubSettingLauncher(requireContext()).setDestination(getClass().getName()).setArguments(args)
                .setSourceMetricsCategory(getMetricsCategory()).setTitleText(title).launch();
    }

    boolean write(String key, Object value) {
        String resolved = key;
        if (key.startsWith("gesture_")) {
            EdgeGoConfig current = read();
            for (String id : EdgeGoConfig.ZONES) if (key.startsWith("gesture_" + id + "_")) {
                resolved = "gesture_" + current.actionZone(id) + key.substring(("gesture_" + id).length());
                break;
            }
        }
        String setting = resolved;
        boolean saved = EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(), values -> EdgeGoConfig.put(values, setting, value));
        if (!saved) Toast.makeText(requireContext(), R.string.edgego_save_failed, Toast.LENGTH_SHORT).show();
        return saved;
    }

    EdgeGoConfig configuration() { return config; }
    void setGuidePreview(EdgeGoGuidePreference preview) { guidePreview = preview; }
    void trackDialog(AlertDialog value) {
        if (dialog != null && dialog != value && dialog.isShowing()) dialog.dismiss();
        dialog = value;
    }
    void command(String command) { Settings.Secure.putStringForUser(requireContext().getContentResolver(), EdgeGoConfig.COMMAND,
            System.currentTimeMillis() + "|" + command, UserHandle.myUserId()); }

    private void pickAction(String key, boolean safe) {
        shortcutKey = key; shortcutParent = shortcutIndex = -1; shortcutHold = false;
        EdgeGoActionPicker.show(this, safe, action -> write(key, action));
    }

    void shortcutTarget(String key, int parent, int index, boolean hold) {
        shortcutKey = key; shortcutParent = parent; shortcutIndex = index; shortcutHold = hold;
    }
    void createShortcut(Intent intent, java.util.function.Consumer<String> result) {
        shortcutResult = result;
        startActivityForResult(intent, 84);
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("shortcutKey", shortcutKey);
        state.putInt("shortcutParent", shortcutParent); state.putInt("shortcutIndex", shortcutIndex); state.putBoolean("shortcutHold", shortcutHold);
    }

    private void range(String title, int initial, int min, int max, java.util.function.IntConsumer save) {
        android.widget.LinearLayout view = new android.widget.LinearLayout(requireContext()); view.setOrientation(1); view.setPadding(32, 16, 32, 16);
        android.widget.TextView label = new android.widget.TextView(requireContext()); label.setText(String.valueOf(initial)); view.addView(label);
        SeekBar bar = new SeekBar(requireContext()); bar.setMax(max - min); bar.setProgress(initial - min); view.addView(bar);
        int[] pending = {initial}, applied = {initial};
        Runnable persist = () -> { if (pending[0] != applied[0]) { applied[0] = pending[0]; save.accept(pending[0]); } };
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                label.setText(String.valueOf(min + value));
                if (fromUser) { pending[0] = min + value; handler.removeCallbacks(persist); handler.postDelayed(persist, 100); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { handler.removeCallbacks(persist); persist.run(); }
        });
        dialog = new AlertDialog.Builder(requireContext()).setTitle(title).setView(view).setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnDismissListener(d -> { handler.removeCallbacks(persist); persist.run(); });
        dialog.show();
    }

    private void color(EdgeGoConfig.Option option) {
        EditText input = new EditText(requireContext()); input.setSingleLine(true); input.setText(String.format(LocaleHolder.ROOT, "#%08X", config.integer(option.key)));
        dialog = new AlertDialog.Builder(requireContext()).setTitle(title(option.key, option.title)).setView(input)
                .setNegativeButton(android.R.string.cancel, null).setNeutralButton(R.string.edgego_reset, (d, which) -> write(option.key, option.initial))
                .setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { if (write(option.key, Color.parseColor(input.getText().toString()))) dialog.dismiss(); }
            catch (IllegalArgumentException e) { input.setError(getString(R.string.edgego_color_error)); }
        }));
        dialog.show();
    }

    private void time(EdgeGoConfig.Option option) {
        TimePicker picker = new TimePicker(requireContext()); picker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(requireContext()));
        picker.setHour(config.integer(option.key) / 60); picker.setMinute(config.integer(option.key) % 60);
        dialog = new AlertDialog.Builder(requireContext()).setTitle(title(option.key, option.title)).setView(picker)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok,
                        (d, which) -> write(option.key, picker.getHour() * 60 + picker.getMinute())).show();
    }

    private void pauseEdges(String key) {
        String[] edges = {"left", "right", "top", "bottom", "bottom_navigation_s1", "bottom_navigation_s2", "bottom_navigation_s3"};
        String[] labels = {getString(R.string.edgego_left), getString(R.string.edgego_right), getString(R.string.edgego_top),
                getString(R.string.edgego_bottom), zoneTitle("bottom_navigation_s1"), zoneTitle("bottom_navigation_s2"), zoneTitle("bottom_navigation_s3")};
        JSONArray current = config.array(key); boolean[] checked = new boolean[edges.length];
        for (int i = 0; i < edges.length; i++) {
            checked[i] = !config.contains(key);
            for (int j = 0; j < current.length(); j++) checked[i] |= current.optString(j).equals("all")
                    || edges[i].equals(current.optString(j)) || edges[i].startsWith(current.optString(j) + "_");
        }
        dialog = new AlertDialog.Builder(requireContext()).setTitle(R.string.edgego_pause_edges).setMultiChoiceItems(labels, checked, (d, index, value) -> checked[index] = value)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    JSONArray next = new JSONArray(); for (int i = 0; i < checked.length; i++) if (checked[i]) next.put(edges[i]);
                    EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(), values -> {
                        EdgeGoConfig.put(values, key, next);
                        if (next.length() == 0) EdgeGoConfig.put(values, key.equals("disabled_home_screen_edges")
                                ? "disable_on_home_screen" : "disable_on_lock_screen", false);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void pauseRules(PreferenceGroup screen) {
        JSONArray rules = config.array("disabled_app_edge_rules");
        java.util.HashSet<String> listed = new java.util.HashSet<>();
        for (int i = 0; i < Math.min(rules.length(), 256); i++) {
            JSONObject rule = rules.optJSONObject(i); if (rule == null) continue;
            String pkg = rule.optString("packageName");
            listed.add(pkg);
            entry(screen, "pause_" + i, EdgeGoActionPicker.label(requireContext(), "launch_app:" + pkg), () -> editPauseRule(pkg)).setSummary(pausedZones(pkg));
        }
        JSONArray packages = config.array("disabled_app_packages");
        for (int i = 0; i < packages.length(); i++) {
            String pkg = packages.optString(i);
            if (listed.add(pkg)) entry(screen, "pause_legacy_" + i, EdgeGoActionPicker.label(requireContext(), "launch_app:" + pkg),
                    () -> editPauseRule(pkg)).setSummary(pausedZones(pkg));
        }
        entry(screen, "add_pause_rule", getString(R.string.edgego_add_pause_rule), () -> EdgeGoActionPicker.pickApp(this, pkg -> editPauseRule(pkg)));
    }

    private String pausedZones(String pkg) {
        List<String> zones = new ArrayList<>();
        for (String id : EdgeGoConfig.ZONES) if (config.paused(pkg, id, false, false)) zones.add(zoneTitle(id));
        return String.join(", ", zones);
    }

    private void editPauseRule(String pkg) {
        String[] edges = EdgeGoConfig.ZONES;
        String[] labels = new String[edges.length]; boolean[] selected = new boolean[edges.length];
        JSONArray existing = config.array("disabled_app_edge_rules"), disabled = config.array("disabled_app_packages");
        for (int i = 0; i < edges.length; i++) {
            labels[i] = zoneTitle(edges[i]);
            for (int j = 0; j < disabled.length(); j++) selected[i] |= pkg.equals(disabled.optString(j));
            for (int j = 0; j < existing.length(); j++) {
                JSONObject rule = existing.optJSONObject(j);
                if (rule == null || !pkg.equals(rule.optString("packageName"))) continue;
                JSONArray paused = rule.optJSONArray("edges");
                if (paused != null) for (int k = 0; k < paused.length(); k++) selected[i] |= paused.optString(k).equals("all")
                        || edges[i].equals(paused.optString(k)) || edges[i].startsWith(paused.optString(k) + "_");
            }
        }
        dialog = new AlertDialog.Builder(requireContext()).setTitle(pkg).setMultiChoiceItems(labels, selected,
                (d, index, value) -> selected[index] = value).setPositiveButton(android.R.string.ok, (d, which) -> {
                    JSONArray rules = new JSONArray(); JSONArray previous = read().array("disabled_app_edge_rules");
                    for (int i = 0; i < previous.length(); i++) if (previous.optJSONObject(i) != null && !pkg.equals(previous.optJSONObject(i).optString("packageName"))) rules.put(previous.optJSONObject(i));
                    JSONArray paused = new JSONArray(); for (int i = 0; i < edges.length; i++) if (selected[i]) paused.put(edges[i]);
                    if (paused.length() > 0) { JSONObject rule = new JSONObject(); EdgeGoConfig.put(rule, "packageName", pkg); EdgeGoConfig.put(rule, "edges", paused); rules.put(rule); }
                    EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(), values -> {
                        EdgeGoConfig.put(values, "disabled_app_edge_rules", rules);
                        JSONArray packages = read().array("disabled_app_packages"), updated = new JSONArray();
                        for (int i = 0; i < packages.length(); i++) if (!pkg.equals(packages.optString(i))) updated.put(packages.optString(i));
                        EdgeGoConfig.put(values, "disabled_app_packages", updated);
                    });
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void globalSize() {
        dialog = new AlertDialog.Builder(requireContext()).setTitle(R.string.edgego_global_size)
                .setItems(new String[]{getString(R.string.edgego_thickness), getString(R.string.edgego_length), getString(R.string.edgego_orientation)}, (d, index) -> {
                    if (index < 2) range(getString(index == 0 ? R.string.edgego_thickness : R.string.edgego_length), index == 0 ? 16 : 25,
                            index == 0 ? 4 : 5, index == 0 ? 40 : 100, value -> EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(),
                                    values -> { for (String zone : EdgeGoConfig.ZONES) EdgeGoConfig.put(values, "edge_" + zone + "_" + (index == 0 ? "thickness" : "length"), value); }));
                    else dialog = new AlertDialog.Builder(requireContext()).setTitle(R.string.edgego_orientation).setItems(new String[]{getString(R.string.edgego_both), getString(R.string.edgego_portrait), getString(R.string.edgego_landscape)},
                            (picker, choice) -> EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(), values -> {
                                for (String zone : EdgeGoConfig.ZONES) EdgeGoConfig.put(values, "edge_" + zone + "_orientation_mode", new String[]{"both", "portrait", "landscape"}[choice]);
                            })).show();
                }).show();
    }

    private void copyActions(String from) {
        String[] names = new String[EdgeGoConfig.ZONES.length]; for (int i = 0; i < names.length; i++) names[i] = zoneTitle(EdgeGoConfig.ZONES[i]);
        dialog = new AlertDialog.Builder(requireContext()).setTitle(R.string.edgego_copy_actions).setItems(names, (d, index) -> {
            String target = EdgeGoConfig.ZONES[index]; EdgeGoConfig source = read();
            try {
                JSONObject copied = source.copiedActions(from, target);
                if (!EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(), values -> {
                    java.util.Iterator<String> keys = copied.keys();
                    while (keys.hasNext()) { String key = keys.next(); EdgeGoConfig.put(values, key, copied.opt(key)); }
                })) Toast.makeText(requireContext(), R.string.edgego_save_failed, Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) { Toast.makeText(requireContext(), R.string.edgego_save_failed, Toast.LENGTH_SHORT).show(); }
        }).show();
    }

    private boolean remapSectors(int count, boolean enabled) {
        EdgeGoConfig old = read();
        JSONObject source = old.snapshot();
        boolean custom = old.bool("custom_pull_angles_enabled");
        for (java.util.Iterator<String> keys = source.keys(); keys.hasNext();) {
            String key = keys.next();
            custom |= key.startsWith("gesture_") && key.contains("_custom_pull_");
        }
        if (custom && count == old.integer("custom_pull_angle_slot_count")) {
            return write("custom_pull_angles_enabled", enabled);
        }
        EdgeGoConfig.put(source, "custom_pull_angles_enabled", custom);
        EdgeGoConfig priorConfig = new EdgeGoConfig(old.enabled, EdgeGoConfig.encode(source));
        double[] prior = priorConfig.sectorDividers();
        boolean saved = EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(), values -> {
            EdgeGoConfig.put(values, "custom_pull_angle_slot_count", count);
            EdgeGoConfig.put(values, "custom_pull_angles_enabled", enabled);
            values.remove("custom_pull_sector_dividers");
            JSONObject geometry = EdgeGoConfig.object(values.toString());
            EdgeGoConfig.put(geometry, "custom_pull_angles_enabled", true);
            EdgeGoConfig updated = new EdgeGoConfig(true, EdgeGoConfig.encode(geometry)); double[] next = updated.sectorDividers();
            for (String zone : EdgeGoConfig.ZONES) if (zone.startsWith("left") || zone.startsWith("right")) {
                for (int i = 0; i < count; i++) {
                    double center = (next[i] + next[i + 1]) * .5, best = Double.MAX_VALUE; int closest = 0;
                    for (int j = 0; j < prior.length - 1; j++) { double distance = Math.abs(center - (prior[j] + prior[j + 1]) * .5); if (distance < best) { best = distance; closest = j; } }
                    String before = priorConfig.bool("custom_pull_angles_enabled") ? "custom_pull_" + (closest + 1) : new String[]{"pull_diagonal_up", "pull_inward", "pull_diagonal_down"}[closest];
                    for (String suffix : new String[]{"", "_and_hold", "_extended"}) EdgeGoConfig.put(values, "gesture_" + zone + "_custom_pull_" + (i + 1) + suffix, old.action(zone, before + suffix));
                }
            }
        });
        if (!saved) Toast.makeText(requireContext(), R.string.edgego_save_failed, Toast.LENGTH_SHORT).show();
        return saved;
    }

    private void order(String key, String[] defaults) {
        List<String> values = new ArrayList<>(); JSONArray stored = config.array(key);
        for (int i = 0; i < stored.length(); i++) values.add(stored.optString(i)); if (values.isEmpty()) values.addAll(Arrays.asList(defaults));
        String[] labels = new String[values.size()]; for (int i = 0; i < labels.length; i++) labels[i] = choiceTitle(values.get(i));
        dialog = new AlertDialog.Builder(requireContext()).setTitle(R.string.edgego_tools_order).setItems(labels, (d, index) -> {
            if (index > 0) java.util.Collections.swap(values, index, index - 1); write(key, new JSONArray(values));
        }).show();
    }

    private void drawings(PreferenceGroup screen, String zone) {
        PreferenceCategory category = category(screen, "drawings", getString(R.string.edgego_drawings));
        for (JSONObject drawing : config.drawings(zone)) {
            String id = drawing.optString("id"); String key = zone == null ? "custom_gestures_json" : "custom_gestures_direct_json";
            entry(category, "drawn_" + id, drawing.optString("name", getString(R.string.edgego_drawing)), () -> {
                dialog = new AlertDialog.Builder(requireContext()).setTitle(drawing.optString("name")).setItems(new String[]{getString(R.string.edgego_action), getString(R.string.edgego_rename), getString(R.string.edgego_redraw),
                        getString(drawing.optBoolean("holdAtEnd") ? R.string.edgego_drawing_hold_off : R.string.edgego_drawing_hold_on), getString(R.string.edgego_delete)}, (d, which) -> {
                    if (which == 0) EdgeGoActionPicker.show(this, false, action -> {
                        if (zone == null) updateDrawing(key, id, value -> EdgeGoConfig.put(value, "actionId", action));
                        else write("gesture_" + zone + "_drawn_" + id, action);
                    });
                    else if (which == 1) name(drawing.optString("name"), value -> updateDrawing(key, id, item -> EdgeGoConfig.put(item, "name", value)));
                    else if (which == 2) EdgeGoDrawingEditor.show(this, zone, id, sample -> updateDrawing(key, id, item -> {
                        EdgeGoConfig.put(item, "samples", sample.optJSONArray("samples")); EdgeGoConfig.put(item, "fingers", sample.optInt("fingers", 1));
                    }));
                    else if (which == 3) updateDrawing(key, id, item -> EdgeGoConfig.put(item, "holdAtEnd", !item.optBoolean("holdAtEnd")));
                    else updateDrawing(key, id, null);
                }).show();
            }).setSummary(drawingSummary(drawing, zone));
        }
        entry(category, "add_drawing", getString(R.string.edgego_add_drawing), () -> EdgeGoDrawingEditor.show(this, zone, drawing -> {
            String key = zone == null ? "custom_gestures_json" : "custom_gestures_direct_json";
            JSONArray entries = read().array(key); if (entries.length() >= 32) return;
            entries.put(drawing); write(key, entries);
        }));
    }

    private void updateDrawing(String key, String id, java.util.function.Consumer<JSONObject> update) {
        JSONArray items = read().array(key), result = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i); if (item == null) continue;
            if (id.equals(item.optString("id"))) { if (update == null) continue; update.accept(item); }
            result.put(item);
        }
        write(key, result);
    }

    private String drawingSummary(JSONObject drawing, String zone) {
        String action = config.drawingAction(zone, drawing);
        return getString(R.string.edgego_drawing_details, drawing.optInt("fingers", drawing.optInt("fingerCount", 1)),
                EdgeGoActionPicker.label(requireContext(), action));
    }

    private void presets(PreferenceGroup screen) {
        JSONArray presets = presetStore().optJSONArray("presets");
        if (presets != null) for (int i = 0; i < presets.length(); i++) {
            JSONObject preset = presets.optJSONObject(i); if (preset == null) continue;
            entry(screen, "preset_" + preset.optString("id"), preset.optString("name"), () -> {
                dialog = new AlertDialog.Builder(requireContext()).setTitle(preset.optString("name")).setItems(new String[]{getString(R.string.edgego_apply), getString(R.string.edgego_rename), getString(R.string.edgego_update), getString(R.string.edgego_delete)},
                        (d, which) -> updatePreset(preset.optString("id"), which)).show();
            });
        }
        entry(screen, "save_preset", getString(R.string.edgego_save_preset), () -> name("", name -> {
            JSONObject store = presetStore(); JSONArray items = store.optJSONArray("presets"); if (items == null) items = new JSONArray(); if (items.length() >= 24) return;
            JSONObject preset = new JSONObject(); EdgeGoConfig.put(preset, "id", UUID.randomUUID().toString()); EdgeGoConfig.put(preset, "name", name); EdgeGoConfig.put(preset, "values", read().snapshot()); items.put(preset);
            EdgeGoConfig.put(store, "presets", items); savePresetStore(store);
        }));
        entry(screen, "export", getString(R.string.edgego_export), () -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "EdgeGo.json"), EXPORT));
        entry(screen, "import", getString(R.string.edgego_import), () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT));
    }

    private JSONObject presetStore() { return EdgeGoConfig.presetStore(requireContext().getContentResolver(), UserHandle.myUserId()); }
    private void savePresetStore(JSONObject store) {
        String value = store.toString();
        if (value.length() > EdgeGoConfig.MAX_JSON_LENGTH || !Settings.Secure.putStringForUser(
                requireContext().getContentResolver(), EdgeGoConfig.PRESETS, value, UserHandle.myUserId())) {
            Toast.makeText(requireContext(), R.string.edgego_save_failed, Toast.LENGTH_SHORT).show();
        }
        refresh();
    }
    private void updatePreset(String id, int operation) {
        if (operation == 1) { name("", name -> updatePresetValue(id, item -> EdgeGoConfig.put(item, "name", name))); return; }
        if (operation == 2) { updatePresetValue(id, item -> EdgeGoConfig.put(item, "values", read().snapshot())); return; }
        JSONObject store = presetStore(); JSONArray items = store.optJSONArray("presets"), next = new JSONArray(); if (items == null) return;
        for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item == null) continue;
            if (item.optString("id").equals(id)) {
                if (operation == 0 && item.optJSONObject("values") != null) {
                    EdgeGoConfig.edit(requireContext().getContentResolver(), UserHandle.myUserId(), values -> {
                        for (java.util.Iterator<String> keys = values.keys(); keys.hasNext();) { keys.next(); keys.remove(); }
                        JSONObject saved = item.optJSONObject("values");
                        for (java.util.Iterator<String> keys = saved.keys(); keys.hasNext();) {
                            String key = keys.next(); EdgeGoConfig.put(values, key, saved.opt(key));
                        }
                    });
                } else if (operation != 0) continue;
            }
            next.put(item);
        }
        if (operation == 3) { EdgeGoConfig.put(store, "presets", next); savePresetStore(store); }
    }
    private void updatePresetValue(String id, java.util.function.Consumer<JSONObject> update) {
        JSONObject store = presetStore(); JSONArray items = store.optJSONArray("presets"); if (items == null) return;
        for (int i = 0; i < items.length(); i++) if (items.optJSONObject(i) != null && id.equals(items.optJSONObject(i).optString("id"))) update.accept(items.optJSONObject(i));
        savePresetStore(store);
    }

    void name(String initial, java.util.function.Consumer<String> save) {
        EditText input = new EditText(requireContext()); input.setSingleLine(true); input.setText(initial); input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        dialog = new AlertDialog.Builder(requireContext()).setTitle(R.string.edgego_name).setView(input).setPositiveButton(android.R.string.ok, (d, which) -> {
            String value = input.getText().toString().trim(); if (!value.isEmpty()) save.accept(value);
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 84) {
            if (result == android.app.Activity.RESULT_OK && data != null) {
                Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent.class);
                if (intent != null) {
                    String action = "intent:" + Uri.encode(intent.toUri(Intent.URI_INTENT_SCHEME));
                    if (shortcutResult != null) shortcutResult.accept(action);
                    else if (shortcutKey.startsWith("gesture_") || shortcutKey.startsWith("remote_cursor_")) write(shortcutKey, action);
                    else if (!shortcutKey.isEmpty()) {
                        JSONArray root = read().array(shortcutKey), items = root;
                        JSONObject parent = shortcutParent < 0 ? null : root.optJSONObject(shortcutParent);
                        if (parent != null) { items = parent.optJSONArray("children"); if (items == null) items = new JSONArray(); }
                        JSONObject item = shortcutIndex < 0 ? new JSONObject() : items.optJSONObject(shortcutIndex);
                        if (item != null) {
                            if (!shortcutHold) {
                                EdgeGoConfig.put(item, "type", "action"); item.remove("children"); item.remove("moduleId"); item.remove("module");
                            }
                            EdgeGoConfig.put(item, shortcutHold ? "holdActionId" : "actionId", action);
                            if (shortcutIndex < 0) {
                                int limit = shortcutKey.startsWith("quick_access") ? shortcutParent < 0 ? 24 : 8 : EdgeGoConfig.MAX_ITEMS;
                                if (items.length() >= limit) return;
                                EdgeGoConfig.put(item, "id", UUID.randomUUID().toString());
                                EdgeGoConfig.put(item, "label", data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)); items.put(item);
                            }
                            if (parent != null) EdgeGoConfig.put(parent, "children", items);
                            write(shortcutKey, root);
                        }
                    }
                }
            }
            shortcutResult = null; shortcutKey = ""; return;
        }
        if (result != android.app.Activity.RESULT_OK || data == null || data.getData() == null || request != EXPORT && request != IMPORT) return;
        Context context = requireContext(); int user = UserHandle.myUserId(); Uri uri = data.getData();
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                if (request == EXPORT) {
                    JSONObject backup = new JSONObject(); EdgeGoConfig.put(backup, "version", EdgeGoConfig.VERSION); EdgeGoConfig.put(backup, "values", new EdgeGoConfig(context.getContentResolver(), user).snapshot());
                    EdgeGoConfig.put(backup, "presets", EdgeGoConfig.object(Settings.Secure.getStringForUser(context.getContentResolver(), EdgeGoConfig.PRESETS, user)).optJSONArray("presets"));
                    try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) { if (output == null) throw new IllegalArgumentException(); output.write(backup.toString().getBytes(StandardCharsets.UTF_8)); }
                } else {
                    String raw;
                    try (InputStream input = context.getContentResolver().openInputStream(uri); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                        if (input == null) throw new IllegalArgumentException(); byte[] chunk = new byte[8192]; int size;
                        while ((size = input.read(chunk)) != -1) { if (bytes.size() + size > EdgeGoConfig.MAX_BACKUP_BYTES) throw new IllegalArgumentException(); bytes.write(chunk, 0, size); }
                        raw = bytes.toString(StandardCharsets.UTF_8.name());
                    }
                    JSONObject values = EdgeGoConfig.importValues(raw);
                    JSONObject root = new JSONObject(raw); JSONArray presets = root.optJSONArray("presets");
                    JSONObject store = presets == null ? null : EdgeGoConfig.importPresets(presets);
                    if (!Settings.Secure.putStringForUser(context.getContentResolver(), EdgeGoConfig.CONFIG, EdgeGoConfig.encode(values), user)) {
                        throw new IllegalArgumentException();
                    }
                    if (store != null && !Settings.Secure.putStringForUser(context.getContentResolver(), EdgeGoConfig.PRESETS, store.toString(), user)) {
                        throw new IllegalArgumentException();
                    }
                }
                ThreadUtils.postOnMainThread(() -> { if (isAdded()) { Toast.makeText(context, request == EXPORT ? R.string.edgego_exported : R.string.edgego_imported, Toast.LENGTH_SHORT).show(); refresh(); } });
            } catch (Exception e) { ThreadUtils.postOnMainThread(() -> { if (isAdded()) Toast.makeText(context, R.string.edgego_backup_error, Toast.LENGTH_SHORT).show(); }); }
        });
    }

    private void refresh() {
        EdgeGoConfig old = config;
        config = read();
        if (preview != null) preview.update(config);
        if (sectorPreview != null) sectorPreview.update(config);
        if (guidePreview != null) guidePreview.update(config);
        if (!zone.isEmpty() && (old.integer("custom_pull_angle_slot_count") != config.integer("custom_pull_angle_slot_count")
                || old.bool("custom_pull_angles_enabled") != config.bool("custom_pull_angles_enabled")
                || !old.drawingLayout(zone).equals(config.drawingLayout(zone)))) {
            getPreferenceScreen().removeAll(); zone(getPreferenceScreen()); return;
        }
        if (section.equals("drawing") || section.equals("presets") || section.equals("macros") || section.equals("pauses")) {
            getPreferenceScreen().removeAll(); section(getPreferenceScreen()); return;
        }
        refresh(getPreferenceScreen());
    }
    private void refresh(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i); String key = preference.getKey(); if (key == null) continue;
            if (preference instanceof PreferenceGroup) { refresh((PreferenceGroup) preference); continue; }
            if (preference instanceof TwoStatePreference) {
                boolean checked = key.equals(EdgeGoConfig.ENABLED) ? config.enabled : EdgeGoConfig.option(key) != null
                        ? config.bool(key) : key.equals("edge_" + zone + "_enabled")
                        ? config.zoneEnabled(zone) : !zone.isEmpty() && key.startsWith("edge_" + zone + "_")
                        ? config.zoneBoolean(zone, key.substring(("edge_" + zone + "_").length()), true) : config.bool(key);
                ((TwoStatePreference) preference).setChecked(checked);
            } else if (key.startsWith("zone_")) preference.setSummary(config.zoneEnabled(key.substring(5)) ? R.string.edgego_zone_on : R.string.edgego_zone_off);
            else if (!zone.isEmpty() && key.startsWith("gesture_" + zone + "_")) {
                String gesture = key.substring(("gesture_" + zone + "_").length()); preference.setSummary(EdgeGoActionPicker.label(requireContext(), config.action(zone, gesture)));
            } else if (preference instanceof ListPreference) {
                ListPreference list = (ListPreference) preference;
                list.setValue(EdgeGoConfig.option(key) != null ? config.string(key)
                        : config.zoneString(zone, "orientation_mode", "both")); list.setSummary(list.getEntry());
            } else if (EdgeGoConfig.option(key) != null) {
                EdgeGoConfig.Option option = EdgeGoConfig.option(key);
                if (option.kind == EdgeGoConfig.Kind.RANGE) preference.setSummary(key.equals("continuous_adjustment_interval_ms")
                        ? getString(R.string.edgego_continuous_adjustment_interval_summary, config.integer(key)) : value(key, config.integer(key)));
                else if (option.kind == EdgeGoConfig.Kind.COLOR) preference.setSummary(String.format(LocaleHolder.ROOT, "#%08X", config.integer(key)));
                else if (option.kind == EdgeGoConfig.Kind.TIME) preference.setSummary(String.format(LocaleHolder.ROOT, "%02d:%02d", config.integer(key) / 60, config.integer(key) % 60));
            } else if (key.startsWith("edge_" + zone + "_")) {
                String suffix = key.substring(("edge_" + zone + "_").length());
                int initial = suffix.equals("thickness") ? 16 : suffix.equals("position") ? zone.endsWith("z2") ? 88 : 50
                        : zone.startsWith("left") || zone.startsWith("right") ? 25 : zone.startsWith("bottom_navigation") ? 100 : 65;
                preference.setSummary(value(suffix, config.zoneInt(zone, suffix, initial, 0, 100)));
            } else if (key.endsWith("items") || key.endsWith("items_2")) {
                preference.setSummary(getString(R.string.edgego_item_count, config.array(key).length()));
            } else if (key.startsWith("drawn_")) {
                for (JSONObject drawing : config.drawings(zone.isEmpty() ? null : zone)) if (key.substring(6).equals(drawing.optString("id"))) {
                    preference.setSummary(drawingSummary(drawing, zone.isEmpty() ? null : zone)); break;
                }
            }
        }
    }

    String title(String key, String fallback) { int id = getResources().getIdentifier("edgego_" + key + "_title", "string", requireContext().getPackageName()); return id == 0 ? fallback : getString(id); }
    private String sectionTitle(String section) { return title("section_" + section, section); }
    private String zoneTitle(String zone) { return title("zone_" + zone, zone); }
    private String gestureTitle(String gesture) { return title("gesture_" + gesture, gesture.replace('_', ' ')); }
    private String choiceTitle(String value) { return title("choice_" + value, value.replace('_', ' ')); }
    private String value(String key, int value) { return String.valueOf(value) + (key.endsWith("_ms") || key.contains("delay") ? " ms" : key.endsWith("_dp") || key.equals("thickness") ? " dp" : key.contains("opacity") || key.contains("percent") || key.contains("length") || key.contains("position") || key.contains("sensitivity") || key.contains("speed") ? "%" : ""); }

    @Override public void onResume() {
        super.onResume();
        for (String key : new String[]{EdgeGoConfig.ENABLED, EdgeGoConfig.CONFIG, EdgeGoConfig.PRESETS}) requireContext().getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, observer, UserHandle.myUserId());
        observing = true; refresh();
    }
    @Override public void onPause() {
        if (observing) requireContext().getContentResolver().unregisterContentObserver(observer); observing = false;
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        if (guidePreview != null) guidePreview.stop();
        super.onPause();
    }
    @Override public int getMetricsCategory() { return MetricsProto.MetricsEvent.LUNARIS; }
    private static final class LocaleHolder { static final java.util.Locale ROOT = java.util.Locale.ROOT; }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider(R.xml.edgego_settings) {
        @Override public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> rows = new ArrayList<>();
            for (EdgeGoConfig.Option option : EdgeGoConfig.options(null)) {
                SearchIndexableRaw row = new SearchIndexableRaw(context); int id = context.getResources().getIdentifier("edgego_" + option.key + "_title", "string", context.getPackageName());
                row.title = id == 0 ? option.title : context.getString(id); row.screenTitle = context.getString(R.string.edgego_title);
                row.key = option.key; row.className = EdgeGoSettings.class.getName(); row.keywords = "EdgeGo " + option.section; rows.add(row);
            }
            EdgeGoGuide.addSearchRows(context, rows);
            return rows;
        }
    };
}
