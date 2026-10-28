/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.statusbar

import android.app.AlertDialog
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.util.lunaris.LunarisBarSettings
import com.android.settings.R

class DynamicIslandSettings : LunarisBarPreferenceFragment() {
    private var page = "main"
    private var selectedFeature = "notification"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = arguments?.getString("page") ?: "main"
        selectedFeature = savedInstanceState?.getString("selected_feature") ?: "notification"
        addPreferencesFromResource(when (page) {
            "appearance" -> R.xml.lunaris_bar_appearance
            "colors" -> R.xml.lunaris_bar_colors
            "motion" -> R.xml.lunaris_bar_motion
            "media" -> R.xml.lunaris_bar_media
            "gestures" -> R.xml.lunaris_bar_gestures
            "features" -> R.xml.lunaris_bar_features
            "feature_style" -> R.xml.lunaris_bar_feature_style
            else -> R.xml.dynamic_island_settings
        })
        requireActivity().title = preferenceScreen.title
        refreshPreferences()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_feature", selectedFeature)
        super.onSaveInstanceState(outState)
    }

    override fun refreshPreferences() {
        val options = when (page) {
            "appearance" -> lunarisBarAppearanceOptions
            "motion" -> lunarisBarMotionOptions + listOf(
                LunarisBarOption(LunarisBarSettings.ALTERNATIVE_ANIMATION, default = 1, system = true),
                LunarisBarOption(LunarisBarSettings.ALTERNATIVE_MEDIA, default = 1, system = true),
                LunarisBarOption(LunarisBarSettings.ALTERNATIVE_FLASHLIGHT, default = 1, system = true),
            )
            "media" -> lunarisBarMediaOptions
            "features" -> lunarisBarFeatureOptions
            "main" -> listOf(LunarisBarOption("status_bar_show_dynamic_island", system = true))
            else -> emptyList()
        }
        for (option in options) {
            val preference = findPreference<Preference>(option.key) ?: continue
            preference.isVisible = editor.available(option)
            bindOption(preference, option.copy(default = editor.defaultValue(option)), editor.read(option)) {
                changed(editor.set(option, it))
            }
        }
        when (page) {
            "main" -> findPreference<Preference>("reset_all")?.setOnPreferenceClickListener {
                showDialog(AlertDialog.Builder(requireContext())
                    .setTitle(R.string.lunaris_bar_reset_all)
                    .setMessage(R.string.lunaris_bar_reset_all_message)
                    .setPositiveButton(R.string.lunaris_bar_reset) { _, _ -> changed(editor.resetAll()) }
                    .setNegativeButton(R.string.lunaris_bar_cancel, null))
                true
            }
            "appearance" -> {
                val preference = requireNotNull(findPreference<SwitchPreferenceCompat>("screen_profile"))
                val width = LunarisBarSettings.screenWidth(requireContext())
                val height = LunarisBarSettings.screenHeight(requireContext())
                preference.isChecked = editor.config.hasScreenProfile(width, height)
                preference.summary = getString(R.string.lunaris_bar_screen_profile_description) + "\n$width × $height dp"
                preference.setOnPreferenceChangeListener { _, next -> changed(editor.screenProfile(next as Boolean)) }
            }
            "colors" -> buildColors()
            "media" -> buildMediaColors()
            "gestures" -> bindGestures()
            "feature_style" -> buildFeatureStyle()
        }
    }

    private fun colorGroup(): PreferenceGroup = requireNotNull(findPreference<PreferenceGroup>("colors")).also { it.removeAll() }

    private fun buildColors() {
        val parent = colorGroup()
        for (night in listOf(false, true)) {
            val suffix = if (night) "night" else "day"
            val theme = category(parent, suffix, if (night) "dark" else "light")
            for (part in listOf("mini", "capsule")) {
                val group = category(theme, "${part}_$suffix", part)
                option(group, LunarisBarOption("${part}_palette_$suffix", "choice", 4, choices = lunarisBarPalettes))
                option(group, LunarisBarOption("${part}_opacity_$suffix", "slider", 100, 10, 100))
                for (role in listOf("background", "foreground")) {
                    val key = "${part}_${role}_$suffix"
                    val fallback = if (role == "background") Color.BLACK else Color.WHITE
                    color(group, key, role, editor.config.color(part == "mini", night, role == "foreground"), fallback) {
                        changed(editor.put(key to it, "${part}_palette_$suffix" to 6))
                    }
                }
            }
            option(theme, LunarisBarOption("outline_$suffix", default = if (night) 1 else 0))
            option(theme, LunarisBarOption("shadow_$suffix", default = if (night) 0 else 1))
        }
    }

    private fun buildMediaColors() {
        val parent = colorGroup()
        val parts = buildList {
            if (!editor.alternative(LunarisBarSettings.ALTERNATIVE_MEDIA)) add("media")
            if (editor.config.enabled("media_mini", true) && editor.config.enabled("media_mini_custom", false)) {
                add("media_mini_left")
                add("media_mini_right")
            }
        }
        parent.isVisible = parts.isNotEmpty()
        for (suffix in listOf("day", "night")) {
            val theme = category(parent, suffix, if (suffix == "day") "light" else "dark")
            for (part in parts) {
                val palette = "${part}_palette_$suffix"
                option(theme, LunarisBarOption(palette, "choice",
                    editor.config.integer("${part}_palette", if (part == "media") 4 else 0, 0, 20), choices = lunarisBarPalettes))
                val roles = if (part == "media") listOf("background_color", "foreground_color") +
                    (if (editor.config.mediaBackground in listOf(3, 4)) listOf("accent_color") else emptyList())
                    else listOf("background", "foreground")
                for (role in roles) {
                    val key = "${part}_${role}_$suffix"
                    val fallback = if (role.startsWith("background")) Color.BLACK else Color.WHITE
                    color(theme, key, key, configColor(key, "${part}_$role", fallback), fallback) {
                        val change = if (part == "media") "media_artwork_colors" to false else "media_mini_custom" to true
                        changed(editor.put(key to it, palette to 6, change))
                    }
                }
            }
        }
    }

    private fun configColor(key: String, legacy: String, fallback: Int): Int =
        editor.config.integer(key, editor.config.integer(legacy, fallback, Int.MIN_VALUE, Int.MAX_VALUE), Int.MIN_VALUE, Int.MAX_VALUE)

    private fun bindGestures() {
        for ((key, fallback) in listOf(
            "gesture_tap" to "expand", "gesture_double_tap" to "none",
            "gesture_long_press" to "open_app", "gesture_swipe_left" to "next_card",
            "gesture_swipe_right" to "previous_card", "gesture_swipe_up" to "expand",
            "gesture_swipe_down" to "notifications",
        )) {
            val preference = requireNotNull(findPreference<Preference>(key))
            val action = editor.config.text(key, fallback)
            val cards = editor.config.cards.filter { it.isEnabled }
            preference.summary = cards.firstOrNull { "card:${it.id}" == action }?.title ?: actionLabel(action)
            preference.setOnPreferenceClickListener {
                val actions = lunarisBarGestureActions + "choose_app" + cards.map { "card:${it.id}" }
                choose(label(key), lunarisBarGestureActions.map(::label) + label("choose_app") + cards.map { it.title }, actions.indexOf(action)) { index ->
                    if (actions[index] == "choose_app") chooseApp { _, value -> changed(editor.put(key to value)) }
                    else changed(editor.put(key to actions[index]))
                }
                true
            }
        }
    }

    private fun actionLabel(action: String): String {
        if (!action.startsWith("app:")) return label(action)
        val component = ComponentName.unflattenFromString(action.removePrefix("app:")) ?: return action
        return runCatching { requireContext().packageManager.getActivityInfo(component, 0).loadLabel(requireContext().packageManager).toString() }.getOrDefault(component.packageName)
    }

    private fun buildFeatureStyle() {
        val features = listOf("charging", "unlock", "bluetooth", "hotspot", "vpn", "ringer", "call",
            "notification", "ongoing", "timer", "recording", "nowplaying", "alarm", "stopwatch",
            "screenrecord", "flashlight", "livescore", "privacy", "screenshare")
        val selected = requireNotNull(findPreference<ListPreference>("selected_feature"))
        selected.entries = features.map(::label).toTypedArray()
        selected.entryValues = features.toTypedArray()
        selected.value = selectedFeature
        selected.summary = selected.entry
        selected.setOnPreferenceChangeListener { _, value -> selectedFeature = value.toString(); scheduleRefresh(); true }
        val parent = requireNotNull(findPreference<PreferenceGroup>("style"))
        parent.removeAll()
        val prefix = "feature_$selectedFeature"
        val alternativeFlashlight = selectedFeature == "flashlight" && editor.alternative(LunarisBarSettings.ALTERNATIVE_FLASHLIGHT)
        val hasProgress = selectedFeature in listOf("charging", "bluetooth", "notification", "ongoing", "timer")
        val shapes = buildList {
            add("mini_shape")
            if (editor.config.integer("${prefix}_right_content", 0, 0, 3) == 1) add("right_shape")
            if (!alternativeFlashlight) add("card_icon_shape")
            if (selectedFeature != "unlock") add("card_button_shape")
        }
        for (shape in shapes) option(parent, LunarisBarOption("${prefix}_$shape", "choice", choices = listOf("inherit") + lunarisBarShapeNames))
        option(parent, LunarisBarOption("${prefix}_left_content", "choice", choices = listOf("app_icon", "none")))
        option(parent, LunarisBarOption("${prefix}_right_content", "choice",
            choices = if (hasProgress) listOf("text", "app_icon", "progress", "none") else listOf("text", "app_icon", "none"),
            values = if (hasProgress) emptyList() else listOf(0, 1, 3)))
        option(parent, LunarisBarOption("${prefix}_haptics", default = 1))
        if (selectedFeature == "bluetooth") option(parent, LunarisBarOption("feature_bluetooth_show_battery", default = 1))
        val parts = buildList {
            add("")
            if (editor.config.integer("${prefix}_left_content", 0, 0, 2) != 2) add("left_")
            if (editor.config.integer("${prefix}_right_content", 0, 0, 3) != 3) add("right_")
            if (hasProgress || (selectedFeature == "flashlight" && !alternativeFlashlight)) {
                add("progress_")
                add("track_")
            }
        }
        val colors = colorGroup()
        for (suffix in listOf("day", "night")) {
            val theme = category(colors, suffix, if (suffix == "day") "light" else "dark")
            for (part in parts) {
                val inherits = part in listOf("", "left_", "right_")
                val palette = "${prefix}_${part}palette"
                val default = when (part) { "progress_" -> 5; "track_" -> 2; else -> 0 }
                option(theme, LunarisBarOption("${palette}_$suffix", "choice",
                    editor.config.integer(palette, default, 0, if (inherits) 21 else 20),
                    choices = (if (inherits) listOf("inherit") else emptyList()) + lunarisBarPalettes))
                val roles = if (part in listOf("progress_", "track_")) listOf("background") else listOf("background", "foreground")
                for (role in roles) {
                    val key = "${prefix}_${part}${role}_$suffix"
                    val fallback = paletteColor(if (part == "progress_") 5 else if (part == "track_") 2 else 4, role == "foreground")
                    color(theme, key, key, configColor(key, "${prefix}_${part}$role", fallback), fallback) {
                        changed(editor.put(key to it, "${palette}_$suffix" to if (inherits) 7 else 6))
                    }
                }
            }
        }
    }
}
