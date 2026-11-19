/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.fragments.buttons;

import android.app.AlertDialog;
import android.content.Context;
import android.os.UserHandle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.internal.util.lunaris.EdgeGoConfig;
import com.android.settings.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

final class EdgeGoCollectionEditor {
    private final EdgeGoSettings fragment;
    private final Context context;
    EdgeGoCollectionEditor(EdgeGoSettings fragment) { this.fragment = fragment; context = fragment.requireContext(); }
    private EdgeGoConfig read() { return new EdgeGoConfig(context.getContentResolver(), UserHandle.myUserId()); }

    void items(String key, boolean pie) { items(key, pie, -1); }
    private JSONArray array(String key, int parent) {
        JSONArray root = read().array(key);
        if (parent < 0) return root;
        JSONObject group = root.optJSONObject(parent);
        if (group == null) return new JSONArray();
        JSONArray children = group.optJSONArray("children"); return children == null ? new JSONArray() : children;
    }
    private void save(String key, int parent, JSONArray items) {
        if (parent < 0) fragment.write(key, items);
        else {
            JSONArray root = read().array(key); JSONObject group = root.optJSONObject(parent); if (group == null) return;
            EdgeGoConfig.put(group, "children", items); fragment.write(key, root);
        }
    }
    private void items(String key, boolean pie, int parent) {
        JSONArray items = array(key, parent);
        boolean copy = parent < 0 && key.endsWith("_items_2");
        int add = pie && parent < 0 ? 2 : 1;
        String[] labels = new String[items.length() + add + (copy ? 1 : 0)];
        for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); labels[i] = item == null ? "" : item.optString("label", item.optString("name", EdgeGoActionPicker.label(context, item.optString("actionId", "none")))); }
        labels[items.length()] = context.getString(R.string.edgego_add_item);
        if (add == 2) labels[items.length() + 1] = context.getString(R.string.edgego_add_group);
        if (copy) labels[labels.length - 1] = context.getString(R.string.edgego_copy_set_one);
        fragment.trackDialog(new AlertDialog.Builder(context).setTitle(R.string.edgego_items).setItems(labels, (dialog, index) -> {
            if (index < items.length()) editItem(key, pie, parent, index);
            else if (index == items.length()) {
                fragment.shortcutTarget(key, parent, -1, false);
                EdgeGoActionPicker.show(fragment, false, action -> {
                    JSONArray latest = array(key, parent); if (latest.length() >= (pie ? parent < 0 ? 24 : 8 : EdgeGoConfig.MAX_ITEMS)) return;
                    JSONObject item = new JSONObject(); EdgeGoConfig.put(item, "id", UUID.randomUUID().toString()); EdgeGoConfig.put(item, "type", "action");
                    EdgeGoConfig.put(item, "actionId", action); EdgeGoConfig.put(item, "label", EdgeGoActionPicker.label(context, action)); latest.put(item);
                    save(key, parent, latest); items(key, pie, parent);
                });
            } else if (copy && index == labels.length - 1) {
                fragment.write(key, read().array(key.substring(0, key.length() - 2))); items(key, pie, parent);
            } else fragment.name("", name -> {
                JSONArray latest = array(key, parent); if (latest.length() >= 24) return;
                JSONObject group = new JSONObject(); EdgeGoConfig.put(group, "id", UUID.randomUUID().toString());
                EdgeGoConfig.put(group, "type", "ring"); EdgeGoConfig.put(group, "name", name); EdgeGoConfig.put(group, "children", new JSONArray());
                latest.put(group); save(key, parent, latest); items(key, pie, parent);
            });
        }).setNegativeButton(android.R.string.cancel, null).show());
    }

    private void editItem(String key, boolean pie, int parent, int index) {
        JSONObject item = array(key, parent).optJSONObject(index); if (item == null) return;
        String[] choices = {context.getString(R.string.edgego_action), context.getString(R.string.edgego_hold_action), context.getString(R.string.edgego_rename),
                context.getString(R.string.edgego_move_up), context.getString(R.string.edgego_move_down), context.getString(R.string.edgego_group_items),
                context.getString(R.string.edgego_module), context.getString(R.string.edgego_delete), context.getString(R.string.edgego_move_ring)};
        if (!pie) choices = java.util.Arrays.copyOf(choices, 8);
        fragment.trackDialog(new AlertDialog.Builder(context).setTitle(item.optString("label", item.optString("name", ""))).setItems(choices, (dialog, which) -> {
            if (which < 2) {
                fragment.shortcutTarget(key, parent, index, which == 1);
                EdgeGoActionPicker.show(fragment, false, action -> {
                    EdgeGoConfig.put(item, which == 0 ? "actionId" : "holdActionId", action);
                    if (which == 0) {
                        EdgeGoConfig.put(item, "type", "action"); item.remove("children"); item.remove("moduleId"); item.remove("module");
                        item.remove("packageName"); item.remove("activityName"); item.remove("shortcutId");
                        item.remove("intentUri"); item.remove("iconBase64");
                        if (!item.optBoolean("customLabel")) EdgeGoConfig.put(item, "label", EdgeGoActionPicker.label(context, action));
                    }
                    replace(key, parent, index, item); items(key, pie, parent);
                });
            } else if (which == 2) fragment.name(item.optString("label", item.optString("name")), name -> {
                EdgeGoConfig.put(item, item.optString("type").equals("ring") ? "name" : "label", name);
                EdgeGoConfig.put(item, "customLabel", true); replace(key, parent, index, item); items(key, pie, parent);
            });
            else if (which == 5 && parent < 0 && item.optString("type").equals("ring")) items(key, pie, index);
            else if (which == 6) {
                String[] modules = {"volume", "brightness", "app_drawer", "recent_apps", "current_app", "now_playing"};
                fragment.trackDialog(new AlertDialog.Builder(context).setTitle(R.string.edgego_module).setItems(modules, (picker, value) -> {
                    EdgeGoConfig.put(item, "type", "module"); EdgeGoConfig.put(item, "moduleId", modules[value]); EdgeGoConfig.put(item, "label", modules[value].replace('_', ' '));
                    item.remove("children"); item.remove("actionId"); item.remove("packageName"); item.remove("activityName"); item.remove("shortcutId");
                    replace(key, parent, index, item); items(key, pie, parent);
                }).show());
            } else if (which == 8 && pie) {
                fragment.trackDialog(new AlertDialog.Builder(context).setTitle(R.string.edgego_move_ring).setItems(new String[]{
                        context.getString(R.string.edgego_ring_one), context.getString(R.string.edgego_ring_two), context.getString(R.string.edgego_ring_three)},
                        (picker, ring) -> { EdgeGoConfig.put(item, "layer", ring); replace(key, parent, index, item); items(key, pie, parent); }).show());
            } else {
                JSONArray current = array(key, parent), updated = new JSONArray(); int destination = which == 3 ? index - 1 : index + 1;
                for (int i = 0; i < current.length(); i++) {
                    if (which == 7 && i == index) continue;
                    if ((which == 3 || which == 4) && destination >= 0 && destination < current.length()) {
                        updated.put(current.opt(i == index ? destination : i == destination ? index : i));
                    } else updated.put(current.opt(i));
                }
                save(key, parent, updated); items(key, pie, parent);
            }
        }).show());
    }

    private void replace(String key, int parent, int index, JSONObject item) {
        JSONArray old = array(key, parent), updated = new JSONArray();
        for (int i = 0; i < old.length(); i++) updated.put(i == index ? item : old.opt(i));
        save(key, parent, updated);
    }

    void macros(PreferenceGroup screen) {
        JSONArray macros = read().array("macros_v2_json");
        for (int i = 0; i < macros.length(); i++) {
            JSONObject macro = macros.optJSONObject(i); if (macro == null) continue;
            Preference preference = new Preference(context); preference.setKey("macro_" + macro.optString("id")); preference.setTitle(macro.optString("name"));
            preference.setSummary(EdgeGoActionPicker.label(context, "launch_app:" + macro.optString("targetPackage")) + " · "
                    + (macro.optJSONArray("steps") == null ? 0 : macro.optJSONArray("steps").length()));
            preference.setOnPreferenceClickListener(p -> { editMacro(macro); return true; }); screen.addPreference(preference);
        }
        Preference add = new Preference(context); add.setKey("add_macro"); add.setTitle(R.string.edgego_add_macro);
        add.setOnPreferenceClickListener(p -> {
            fragment.name("", name -> EdgeGoActionPicker.pickApp(fragment, pkg -> {
                JSONObject macro = new JSONObject(); EdgeGoConfig.put(macro, "schemaVersion", 2); EdgeGoConfig.put(macro, "id", UUID.randomUUID().toString());
                EdgeGoConfig.put(macro, "name", name); EdgeGoConfig.put(macro, "targetPackage", pkg); EdgeGoConfig.put(macro, "steps", new JSONArray());
                JSONArray updated = read().array("macros_v2_json"); if (updated.length() < 32) { updated.put(macro); fragment.write("macros_v2_json", updated); }
            }));
            return true;
        }); screen.addPreference(add);
    }

    private void editMacro(JSONObject macro) {
        String[] choices = {context.getString(R.string.edgego_target_app), context.getString(R.string.edgego_record), context.getString(R.string.edgego_run),
                context.getString(R.string.edgego_rename), context.getString(R.string.edgego_steps), context.getString(R.string.edgego_delete)};
        fragment.trackDialog(new AlertDialog.Builder(context).setTitle(macro.optString("name")).setItems(choices, (dialog, which) -> {
            if (which == 0) EdgeGoActionPicker.pickApp(fragment, pkg -> { EdgeGoConfig.put(macro, "targetPackage", pkg); saveMacro(macro, false); });
            else if (which == 1 || which == 2) {
                if (!read().enabled) { Toast.makeText(context, R.string.edgego_enable_first, Toast.LENGTH_SHORT).show(); return; }
                fragment.command((which == 1 ? "record" : "run_macro") + "|" + macro.optString("id")
                        + (which == 1 ? "|" + fragment.requireActivity().getTaskId() : ""));
            } else if (which == 3) fragment.name(macro.optString("name"), name -> { EdgeGoConfig.put(macro, "name", name); saveMacro(macro, false); });
            else if (which == 4) steps(macro);
            else saveMacro(macro, true);
        }).show());
    }

    private void saveMacro(JSONObject macro, boolean remove) {
        JSONArray old = read().array("macros_v2_json"), updated = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject current = old.optJSONObject(i); if (current == null) continue;
            if (current.optString("id").equals(macro.optString("id"))) { if (!remove) updated.put(macro); }
            else updated.put(current);
        }
        fragment.write("macros_v2_json", updated);
    }

    private void steps(JSONObject macro) {
        JSONArray steps = macro.optJSONArray("steps"); if (steps == null) steps = new JSONArray();
        JSONArray current = steps; String[] labels = new String[current.length() + 1];
        for (int i = 0; i < current.length(); i++) { JSONObject step = current.optJSONObject(i); labels[i] = (i + 1) + ". " + (step == null ? "" : step.optString("type") + " · " + step.optLong("durationMs") + " ms"); }
        labels[current.length()] = context.getString(R.string.edgego_add_step);
        fragment.trackDialog(new AlertDialog.Builder(context).setTitle(R.string.edgego_steps).setItems(labels, (dialog, index) -> {
            if (index == current.length()) addStep(macro, current);
            else fragment.trackDialog(new AlertDialog.Builder(context).setTitle(labels[index]).setItems(new String[]{context.getString(R.string.edgego_move_up), context.getString(R.string.edgego_move_down), context.getString(R.string.edgego_delete)}, (d, which) -> {
                JSONArray updated = new JSONArray(); int destination = index + (which == 0 ? -1 : 1);
                for (int i = 0; i < current.length(); i++) {
                    if (which == 2 && i == index) continue;
                    updated.put(which != 2 && destination >= 0 && destination < current.length()
                            ? current.opt(i == index ? destination : i == destination ? index : i) : current.opt(i));
                }
                EdgeGoConfig.put(macro, "steps", updated); saveMacro(macro, false); steps(macro);
            }).show());
        }).show());
    }

    private void addStep(JSONObject macro, JSONArray steps) {
        if (steps.length() >= EdgeGoConfig.MAX_MACRO_STEPS) return;
        fragment.trackDialog(new AlertDialog.Builder(context).setTitle(R.string.edgego_add_step).setItems(new String[]{context.getString(R.string.edgego_wait), context.getString(R.string.edgego_tap), context.getString(R.string.edgego_swipe), context.getString(R.string.edgego_action)}, (dialog, which) -> {
            if (which == 3) EdgeGoActionPicker.showMacro(fragment, action -> {
                JSONObject step = new JSONObject(); EdgeGoConfig.put(step, "type", "action"); EdgeGoConfig.put(step, "actionId", action); EdgeGoConfig.put(step, "durationMs", 250);
                steps.put(step); EdgeGoConfig.put(macro, "steps", steps); saveMacro(macro, false);
            });
            else {
                EditText input = new EditText(context); input.setSingleLine(true); input.setText(which == 0 ? "500" : which == 1 ? "50,50,80" : "50,80,50,20,350");
                fragment.trackDialog(new AlertDialog.Builder(context).setTitle(which == 0 ? R.string.edgego_wait_hint : which == 1 ? R.string.edgego_tap_hint : R.string.edgego_swipe_hint).setView(input)
                        .setPositiveButton(android.R.string.ok, (d, button) -> {
                            try {
                                String[] values = input.getText().toString().split(","); JSONObject step = new JSONObject();
                                EdgeGoConfig.put(step, "type", which == 0 ? "delay" : which == 1 ? "tap" : "swipe");
                                if (which == 1) { EdgeGoConfig.put(step, "x", coordinate(values[0])); EdgeGoConfig.put(step, "y", coordinate(values[1])); }
                                else if (which == 2) { EdgeGoConfig.put(step, "startX", coordinate(values[0])); EdgeGoConfig.put(step, "startY", coordinate(values[1])); EdgeGoConfig.put(step, "endX", coordinate(values[2])); EdgeGoConfig.put(step, "endY", coordinate(values[3])); }
                                EdgeGoConfig.put(step, "durationMs", Math.max(40, Math.min(which == 0 ? 30000 : 5000, Long.parseLong(values[values.length - 1].trim()))));
                                steps.put(step); EdgeGoConfig.put(macro, "steps", steps); saveMacro(macro, false);
                            } catch (Exception e) { Toast.makeText(context, R.string.edgego_step_error, Toast.LENGTH_SHORT).show(); }
                        }).setNegativeButton(android.R.string.cancel, null).show());
            }
        }).show());
    }
    private double coordinate(String value) { double number = Double.parseDouble(value.trim()) / 100; return Double.isFinite(number) ? Math.max(0, Math.min(1, number)) : .5; }
}
