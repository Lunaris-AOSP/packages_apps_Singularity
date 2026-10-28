/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.statusbar

import android.app.ActivityManager
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Switch
import androidx.preference.PreferenceGroup
import androidx.preference.Preference
import com.android.internal.util.lunaris.LunarisBarSettings
import com.android.settings.R
import org.json.JSONArray
import org.json.JSONObject

class LunarisBarCardSettings : LunarisBarPreferenceFragment() {
    private lateinit var card: LunarisBarSettings.Card
    private var ownerUser = 0
    private var existing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.lunaris_bar_card)
        requireActivity().title = preferenceScreen.title
        ownerUser = savedInstanceState?.getInt("owner_user") ?: ActivityManager.getCurrentUser()
        existing = arguments?.containsKey("card_id") == true
        val restored = savedInstanceState?.getString("card")?.let {
            LunarisBarSettings.parse("{\"cards\":[$it]}").cards.firstOrNull()
        }
        val stored = editor.config.cards.firstOrNull { it.id == arguments?.getString("card_id") }
        val type = arguments?.getString("type") ?: "shortcuts"
        card = restored ?: stored ?: LunarisBarSettings.Card.create(type, label(type))
        if (existing && stored == null) finish()
        refreshPreferences()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("card", card.toString())
        outState.putInt("owner_user", ownerUser)
        super.onSaveInstanceState(outState)
    }

    override fun refreshPreferences() {
        if (!::card.isInitialized) return
        if (ownerUser != ActivityManager.getCurrentUser() ||
            (existing && editor.config.cards.none { it.id == card.id })) {
            finish()
            return
        }
        val parent = requireNotNull(findPreference<PreferenceGroup>("card"))
        parent.removeAll()
        textPreference(parent, "title", "card_name", card.title, 80) { card = card.with("title", it) }
        cardOption(parent, LunarisBarOption("enabled", default = 1)).title = label("card_enabled")
        cardOption(parent, LunarisBarOption("lockscreen"))
        cardOption(parent, LunarisBarOption("mini_enabled", default = 1))
        cardOption(parent, LunarisBarOption("card_haptics", default = 1))
        when (card.type) {
            "shortcuts" -> {
                val entries = shortcuts()
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    action(parent, "shortcut_$index", "delete") {
                        val latest = shortcuts()
                        latest.remove(index)
                        card = card.with("actions", latest.toString())
                        scheduleRefresh()
                    }.apply {
                        title = entry.optString("label")
                        summary = label("delete")
                    }
                }
                action(parent, "add_app") { chooseApp(::addShortcut) }.isEnabled = entries.length() < 24
                action(parent, "add_contact_action") { shortcutDialog() }.isEnabled = entries.length() < 24
                cardOption(parent, LunarisBarOption("columns", "slider", 4, 2, 6))
                cardOption(parent, LunarisBarOption("icon_scale", "slider", 100, 70, 150))
                cardOption(parent, LunarisBarOption("icon_shape", "choice", 2, choices = lunarisBarShapeNames))
                cardOption(parent, LunarisBarOption("themed_icons"))
                cardOption(parent, LunarisBarOption("close_on_tap", default = 1))
            }
            "barcode" -> {
                cardText(parent, "value", "barcode_value")
                cardText(parent, "label", "barcode_label")
                val formats = listOf("QR_CODE", "CODE_128", "CODE_39", "CODE_93", "CODABAR", "EAN_13",
                    "EAN_8", "ITF", "UPC_A", "UPC_E", "AZTEC", "DATA_MATRIX", "PDF_417")
                val preference = template(parent, "choice", "format", "barcode_format")
                bindOption(preference, LunarisBarOption("format", "choice", choices = formats),
                    formats.indexOf(card.text("format", "QR_CODE")).coerceAtLeast(0)) {
                    card = card.with("format", formats[it]); scheduleRefresh(); true
                }
                cardOption(parent, LunarisBarOption("rotate"))
                cardOption(parent, LunarisBarOption("height", "slider", 180, 64, 320))
            }
            "clock" -> {
                cardText(parent, "timezone", "timezone")
                cardOption(parent, LunarisBarOption("seconds"))
            }
            "timer" -> cardOption(parent, LunarisBarOption("minutes", "slider", 5, 1, 180))
            "battery" -> {
                cardOption(parent, LunarisBarOption("fahrenheit"))
                cardOption(parent, LunarisBarOption("battery_left", "choice", choices = listOf("battery_icon", "percentage", "temperature")))
                cardOption(parent, LunarisBarOption("battery_right", "choice", 1, choices = listOf("battery_icon", "percentage", "temperature")))
            }
            "widget" -> {
                cardOption(parent, LunarisBarOption("width", "slider", 360, 64, 480))
                cardOption(parent, LunarisBarOption("height", "slider", 180, 64, 480))
                cardOption(parent, LunarisBarOption("widget_theme", "choice", choices = listOf("follow_system", "light", "dark")))
                cardOption(parent, LunarisBarOption("close_on_tap"))
            }
            "volume", "brightness", "flashlight" -> {
                if (card.type == "volume") cardOption(parent, LunarisBarOption("stream", "choice",
                    choices = listOf("media_volume", "ring_volume", "notification_volume", "alarm_volume", "call_volume")))
                if (card.type != "flashlight") cardOption(parent, LunarisBarOption("hide_value"))
                cardOption(parent, LunarisBarOption("slider_style", "choice", choices = listOf("standard", "expressive", "vertical")))
                cardOption(parent, LunarisBarOption("toggle_shape", "choice", 2, choices = lunarisBarShapeNames))
                action(parent, "toggle_action") { chooseToggleAction() }.summary = label(card.text("toggle_action", "none"))
                cardText(parent, "toggle_label", "toggle_label")
                cardOption(parent, LunarisBarOption("hide_after", "slider", 0, 0, 60))
            }
        }
        buildColors()
        val actions = requireNotNull(findPreference<PreferenceGroup>("actions"))
        actions.removeAll()
        action(actions, "save") { saveCard() }.isEnabled = card.title.isNotBlank() &&
            (card.type != "barcode" || card.text("value", "").isNotBlank())
        action(actions, "cancel") { finish() }
        if (existing) {
            val index = editor.config.cards.indexOfFirst { it.id == card.id }
            action(actions, "move_up") { moveCard(-1) }.isEnabled = index > 0
            action(actions, "move_down") { moveCard(1) }.isEnabled = index < editor.config.cards.lastIndex
            action(actions, "delete") {
                editor.reload()
                if (changed(editor.cards(editor.config.cards.filterNot { it.id == card.id }))) finish()
            }
        }
    }

    private fun cardOption(parent: PreferenceGroup, option: LunarisBarOption, after: () -> Unit = {}): Preference {
        val value = if (option.kind == "switch") {
            if (card.enabled(option.key, option.default != 0)) 1 else 0
        } else card.integer(option.key, option.default, option.min,
            if (option.choices.isEmpty()) option.max else option.choices.lastIndex)
        return this.option(parent, option, value) {
            card = card.with(option.key, if (option.kind == "switch") it != 0 else it)
            after()
            scheduleRefresh()
            true
        }
    }

    private fun cardText(parent: PreferenceGroup, key: String, title: String) {
        textPreference(parent, key, title, card.text(key, "")) { card = card.with(key, it) }
    }

    private fun buildColors() {
        val parent = requireNotNull(findPreference<PreferenceGroup>("colors"))
        parent.removeAll()
        val parts = when (card.type) {
            "shortcuts" -> listOf("icon" to 2)
            "barcode" -> listOf("barcode" to 6)
            "clock" -> listOf("time" to 5, "date" to 0)
            "timer" -> listOf("time" to 5, "slider" to 5, "track" to 2, "button" to 5)
            "battery" -> listOf("battery" to 5, "details" to 2, "button" to 5)
            "volume", "brightness", "flashlight" -> listOf("slider" to 5, "track" to 2, "toggle" to 2)
            else -> emptyList()
        }
        for (suffix in listOf("day", "night")) {
            val theme = category(parent, suffix, if (suffix == "day") "light" else "dark")
            cardOption(theme, LunarisBarOption("palette_$suffix", "choice", choices = listOf("inherit") + lunarisBarPalettes)) {
                card = card.with("custom_colors_$suffix", false)
            }
            for (role in listOf("foreground", "background")) {
                val key = "${role}_$suffix"
                val fallback = if (role == "background") Color.BLACK else Color.WHITE
                color(theme, key, "card_$role", card.integer(key, fallback, Int.MIN_VALUE, Int.MAX_VALUE), fallback) {
                    card = card.with(key, it).with("custom_colors_$suffix", true)
                    scheduleRefresh(); true
                }
            }
            for ((part, fallbackPalette) in parts) {
                val group = category(theme, "${part}_$suffix", part)
                val paletteKey = "${part}_palette_$suffix"
                cardOption(group, LunarisBarOption(paletteKey, "choice",
                    card.integer("${part}_palette", fallbackPalette, 0, 20), choices = lunarisBarPalettes)).title = label("palette")
                val roles = if (part in listOf("slider", "track")) listOf("background") else listOf("background", "foreground")
                for (role in roles) {
                    val key = "${part}_${role}_$suffix"
                    val fallback = if (part == "barcode") {
                        if (role == "background") Color.WHITE else Color.BLACK
                    } else paletteColor(fallbackPalette, role == "foreground")
                    color(group, key, role, card.integer(key, fallback, Int.MIN_VALUE, Int.MAX_VALUE), fallback) {
                        card = card.with(key, it).with(paletteKey, 6)
                        scheduleRefresh(); true
                    }
                }
            }
        }
    }

    private fun saveCard() {
        if (ownerUser != ActivityManager.getCurrentUser()) { finish(); return }
        editor.reload()
        val latest = editor.config.cards
        if (existing && latest.none { it.id == card.id }) { finish(); return }
        if (!existing && latest.size >= LunarisBarSettings.MAX_CARDS) { changed(false); return }
        if (changed(editor.cards(if (existing) latest.map { if (it.id == card.id) card else it } else latest + card))) finish()
    }

    private fun moveCard(direction: Int) {
        editor.reload()
        val latest = editor.config.cards.toMutableList()
        val index = latest.indexOfFirst { it.id == card.id }
        if (index < 0 || index + direction !in latest.indices) return
        java.util.Collections.swap(latest, index, index + direction)
        changed(editor.cards(latest))
    }

    private fun shortcuts(): JSONArray = runCatching { JSONArray(card.text("actions", "[]")) }.getOrDefault(JSONArray())

    private fun addShortcut(label: String, action: String) {
        val entries = shortcuts()
        if (entries.length() >= 24) return
        entries.put(JSONObject().put("label", label).put("action", action))
        card = card.with("actions", entries.toString())
        scheduleRefresh()
    }

    private fun chooseToggleAction() {
        val actions = lunarisBarGestureActions.filterNot { it in listOf("expand", "next_card", "previous_card", "open_app") }
        choose(label("toggle_action"), (actions + "choose_app").map(::label), actions.indexOf(card.text("toggle_action", "none"))) { index ->
            if (index == actions.size) chooseApp { label, action ->
                card = card.with("toggle_action", action).with("toggle_label", label)
                scheduleRefresh()
            } else {
                card = card.with("toggle_action", actions[index])
                scheduleRefresh()
            }
        }
    }

    private fun shortcutDialog() {
        val view = layoutInflater.inflate(R.layout.lunaris_bar_shortcut_editor, null)
        val contact = view.findViewById<Switch>(R.id.lunaris_bar_contact)
        val title = view.findViewById<EditText>(R.id.lunaris_bar_shortcut_label)
        val value = view.findViewById<EditText>(R.id.lunaris_bar_shortcut_value)
        contact.setOnCheckedChangeListener { _, checked ->
            value.setHint(if (checked) R.string.lunaris_bar_phone_number else R.string.lunaris_bar_intent_uri)
        }
        val dialog = showDialog(AlertDialog.Builder(requireContext())
            .setTitle(R.string.lunaris_bar_add_contact_action).setView(view)
            .setPositiveButton(R.string.lunaris_bar_save) { _, _ ->
                addShortcut(title.text.toString(), (if (contact.isChecked) "contact:" else "intent:") + value.text.toString())
            }.setNegativeButton(R.string.lunaris_bar_cancel, null))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = title.text.isNotBlank() && value.text.isNotBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        title.addTextChangedListener(watcher)
        value.addTextChangedListener(watcher)
    }
}
