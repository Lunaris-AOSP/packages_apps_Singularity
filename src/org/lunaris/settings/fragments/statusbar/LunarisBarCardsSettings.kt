/*
 * Copyright (C) 2024-2026 Lunaris AOSP
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

package org.lunaris.settings.fragments.statusbar

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.internal.util.lunaris.LunarisBarSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun LunarisBarCardsSettings(editor: LunarisBarEditor) {
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LunarisBarSettings.Card?>(null) }
    var widgets by remember { mutableStateOf(false) }
    val cards = editor.config.cards
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { add = true },
            enabled = cards.size < LunarisBarSettings.MAX_CARDS,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null)
            Text(lunarisBarText("add_card"), modifier = Modifier.padding(start = 8.dp))
        }
        cards.forEachIndexed { index, card ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceBright,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(card.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                lunarisBarText(card.type),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            card.isEnabled,
                            onCheckedChange = { enabled ->
                                editor.cards(
                                    cards.map {
                                        if (it.id == card.id) it.with("enabled", enabled) else it
                                    }
                                )
                            },
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(
                            onClick = {
                                val next = cards.toMutableList()
                                java.util.Collections.swap(next, index, index - 1)
                                editor.cards(next)
                            },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Default.ArrowUpward, lunarisBarText("move_up"))
                        }
                        IconButton(
                            onClick = {
                                val next = cards.toMutableList()
                                java.util.Collections.swap(next, index, index + 1)
                                editor.cards(next)
                            },
                            enabled = index < cards.lastIndex,
                        ) {
                            Icon(Icons.Default.ArrowDownward, lunarisBarText("move_down"))
                        }
                        IconButton(onClick = { editing = card }) {
                            Icon(Icons.Default.Edit, lunarisBarText("edit"))
                        }
                        IconButton(
                            onClick = { editor.cards(cards.filterNot { it.id == card.id }) }
                        ) {
                            Icon(Icons.Default.Delete, lunarisBarText("delete"))
                        }
                    }
                }
            }
        }
        if (cards.isEmpty()) Text(lunarisBarText("cards_empty"), modifier = Modifier.padding(20.dp))
    }
    if (add)
        LunarisBarChoiceDialog(
            lunarisBarText("add_card"),
            LunarisBarSettings.Card.TYPES.toList(),
            -1,
            { add = false },
        ) { index ->
            val type = LunarisBarSettings.Card.TYPES[index]
            add = false
            if (type == "widget") widgets = true
            else {
                val id =
                    editor.context.resources.getIdentifier(
                        "lunaris_bar_$type",
                        "string",
                        editor.context.packageName,
                    )
                editing = LunarisBarSettings.Card.create(type, editor.context.getString(id))
            }
        }
    editing?.let { card ->
        LunarisBarCardEditor(card, dismiss = { editing = null }) { updated ->
            val latest = editor.config.cards
            editor.cards(
                if (latest.any { it.id == updated.id })
                    latest.map { if (it.id == updated.id) updated else it }
                else latest + updated
            )
            editing = null
        }
    }
    if (widgets) LunarisBarWidgetPicker { widgets = false }
}

@Composable
private fun LunarisBarCardEditor(
    initial: LunarisBarSettings.Card,
    dismiss: () -> Unit,
    save: (LunarisBarSettings.Card) -> Unit,
) {
    var card by remember(initial) { mutableStateOf(initial) }
    var appPicker by remember { mutableStateOf(false) }
    var customShortcut by remember { mutableStateOf(false) }
    var toggleActionPicker by remember { mutableStateOf(false) }
    var toggleAppPicker by remember { mutableStateOf(false) }
    var nightColors by remember { mutableStateOf(false) }
    val colorSuffix = if (nightColors) "night" else "day"
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(lunarisBarText("edit_card")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    card.title,
                    onValueChange = { card = card.with("title", it.take(80)) },
                    label = { Text(lunarisBarText("card_name")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LunarisBarCardOption(card, LunarisBarOption("lockscreen")) { card = it }
                LunarisBarCardOption(card, LunarisBarOption("mini_enabled", default = 1)) {
                    card = it
                }
                LunarisBarCardOption(card, LunarisBarOption("card_haptics", default = 1)) {
                    card = it
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lunarisBarText("night_colors"), modifier = Modifier.weight(1f))
                    Switch(nightColors, onCheckedChange = { nightColors = it })
                }
                LunarisBarCardOption(
                    card,
                    LunarisBarOption(
                        "palette_$colorSuffix",
                        "choice",
                        0,
                        choices = listOf("inherit") + lunarisBarPalettes,
                    ),
                ) {
                    card = it.with("custom_colors_$colorSuffix", false)
                }
                LunarisBarColorRow(
                    "card_foreground",
                    Color(
                        card.integer(
                            "foreground_$colorSuffix",
                            Color.White.toArgb(),
                            Int.MIN_VALUE,
                            Int.MAX_VALUE,
                        )
                    ),
                ) {
                    card =
                        card
                            .with("foreground_$colorSuffix", it.toArgb())
                            .with("custom_colors_$colorSuffix", true)
                }
                LunarisBarColorRow(
                    "card_background",
                    Color(
                        card.integer(
                            "background_$colorSuffix",
                            Color.Black.toArgb(),
                            Int.MIN_VALUE,
                            Int.MAX_VALUE,
                        )
                    ),
                ) {
                    card =
                        card
                            .with("background_$colorSuffix", it.toArgb())
                            .with("custom_colors_$colorSuffix", true)
                }
                when (card.type) {
                    "shortcuts" -> {
                        val entries =
                            runCatching { JSONArray(card.text("actions", "[]")) }
                                .getOrDefault(JSONArray())
                        for (i in 0 until entries.length()) {
                            val entry = entries.optJSONObject(i) ?: continue
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.optString("label"), modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        val array = JSONArray()
                                        for (j in 0 until entries.length()) if (j != i)
                                            array.put(entries.get(j))
                                        card = card.with("actions", array.toString())
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, lunarisBarText("delete"))
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { appPicker = true },
                            enabled = entries.length() < 24,
                        ) {
                            Text(lunarisBarText("add_app"))
                        }
                        TextButton(
                            onClick = { customShortcut = true },
                            enabled = entries.length() < 24,
                        ) {
                            Text(lunarisBarText("add_contact_action"))
                        }
                        for (option in
                            listOf(
                                LunarisBarOption("columns", "slider", 4, 2, 6),
                                LunarisBarOption("icon_scale", "slider", 100, 70, 150),
                                LunarisBarOption(
                                    "icon_shape",
                                    "choice",
                                    2,
                                    choices = lunarisBarShapeNames,
                                ),
                                LunarisBarOption("themed_icons"),
                                LunarisBarOption("close_on_tap", default = 1),
                            )) {
                            LunarisBarCardOption(card, option) { card = it }
                        }
                    }
                    "barcode" -> {
                        LunarisBarCardText(card, "value", "barcode_value") { card = it }
                        LunarisBarCardText(card, "label", "barcode_label") { card = it }
                        val formats =
                            listOf(
                                "QR_CODE",
                                "CODE_128",
                                "CODE_39",
                                "CODE_93",
                                "CODABAR",
                                "EAN_13",
                                "EAN_8",
                                "ITF",
                                "UPC_A",
                                "UPC_E",
                                "AZTEC",
                                "DATA_MATRIX",
                                "PDF_417",
                            )
                        var picking by remember { mutableStateOf(false) }
                        TextButton(onClick = { picking = true }) {
                            Text(card.text("format", "QR_CODE"))
                        }
                        if (picking)
                            LunarisBarChoiceDialog(
                                lunarisBarText("barcode_format"),
                                formats,
                                formats.indexOf(card.text("format", "QR_CODE")),
                                { picking = false },
                            ) {
                                card = card.with("format", formats[it])
                                picking = false
                            }
                        LunarisBarCardOption(card, LunarisBarOption("rotate")) { card = it }
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption("height", "slider", 180, 64, 320),
                        ) {
                            card = it
                        }
                    }
                    "clock" -> {
                        LunarisBarCardText(card, "timezone", "timezone") { card = it }
                        LunarisBarCardOption(card, LunarisBarOption("seconds")) { card = it }
                    }
                    "timer" ->
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption("minutes", "slider", 5, 1, 180),
                        ) {
                            card = it
                        }
                    "battery" -> {
                        LunarisBarCardOption(card, LunarisBarOption("fahrenheit")) { card = it }
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption(
                                "battery_left",
                                "choice",
                                choices = listOf("battery_icon", "percentage", "temperature"),
                            ),
                        ) {
                            card = it
                        }
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption(
                                "battery_right",
                                "choice",
                                1,
                                choices = listOf("battery_icon", "percentage", "temperature"),
                            ),
                        ) {
                            card = it
                        }
                    }
                    "widget" -> {
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption("width", "slider", 360, 64, 480),
                        ) {
                            card = it
                        }
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption("height", "slider", 180, 64, 480),
                        ) {
                            card = it
                        }
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption(
                                "widget_theme",
                                "choice",
                                choices = listOf("follow_system", "light", "dark"),
                            ),
                        ) {
                            card = it
                        }
                        LunarisBarCardOption(card, LunarisBarOption("close_on_tap")) { card = it }
                    }
                    "volume",
                    "brightness",
                    "flashlight" -> {
                        if (card.type == "volume")
                            LunarisBarCardOption(
                                card,
                                LunarisBarOption(
                                    "stream",
                                    "choice",
                                    choices =
                                        listOf(
                                            "media_volume",
                                            "ring_volume",
                                            "notification_volume",
                                            "alarm_volume",
                                            "call_volume",
                                        ),
                                ),
                            ) {
                                card = it
                            }
                        if (card.type != "flashlight")
                            LunarisBarCardOption(card, LunarisBarOption("hide_value")) { card = it }
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption(
                                "slider_style",
                                "choice",
                                choices = listOf("standard", "expressive", "vertical"),
                            ),
                        ) {
                            card = it
                        }
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption(
                                "toggle_shape",
                                "choice",
                                2,
                                choices = lunarisBarShapeNames,
                            ),
                        ) {
                            card = it
                        }
                        TextButton(onClick = { toggleActionPicker = true }) {
                            Text(lunarisBarText("toggle_action"))
                        }
                        LunarisBarCardText(card, "toggle_label", "toggle_label") { card = it }
                        LunarisBarCardOption(
                            card,
                            LunarisBarOption("hide_after", "slider", 0, 0, 60),
                        ) {
                            card = it
                        }
                    }
                }
                val parts =
                    when (card.type) {
                        "shortcuts" -> listOf("icon" to 2)
                        "barcode" -> listOf("barcode" to 6)
                        "clock" -> listOf("time" to 5, "date" to 0)
                        "timer" -> listOf("time" to 5, "slider" to 5, "track" to 2, "button" to 5)
                        "battery" -> listOf("battery" to 5, "details" to 2, "button" to 5)
                        "volume",
                        "brightness",
                        "flashlight" -> listOf("slider" to 5, "track" to 2, "toggle" to 2)
                        else -> emptyList()
                    }
                for ((part, fallback) in parts) {
                    LunarisBarSectionTitle(part)
                    LunarisBarCardOption(
                        card,
                        LunarisBarOption(
                            "${part}_palette_$colorSuffix",
                            "choice",
                            card.integer("${part}_palette", fallback, 0, 20),
                            choices = lunarisBarPalettes,
                        ),
                    ) {
                        card = it
                    }
                    val roles =
                        if (part in listOf("slider", "track")) listOf("background")
                        else listOf("background", "foreground")
                    for (role in roles) {
                        val key = "${part}_${role}_$colorSuffix"
                        val fallbackColor =
                            if (part == "barcode") {
                                if (role == "background") Color.White else Color.Black
                            } else {
                                val defaults =
                                    lunarisBarPreviewPalette(
                                        fallback,
                                        colors,
                                        custom = Color.Black to Color.White,
                                    )
                                if (role == "background") defaults.first else defaults.second
                            }
                        LunarisBarColorRow(
                            role,
                            Color(
                                card.integer(
                                    key,
                                    fallbackColor.toArgb(),
                                    Int.MIN_VALUE,
                                    Int.MAX_VALUE,
                                )
                            ),
                            defaultColor = fallbackColor,
                        ) {
                            card =
                                card.with(key, it.toArgb()).with("${part}_palette_$colorSuffix", 6)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { save(card) },
                enabled =
                    card.title.isNotBlank() &&
                        (card.type != "barcode" || card.text("value", "").isNotBlank()),
            ) {
                Text(lunarisBarText("save"))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(lunarisBarText("cancel")) } },
    )
    if (appPicker)
        LunarisBarAppPicker({ appPicker = false }) { label, action ->
            val array =
                runCatching { JSONArray(card.text("actions", "[]")) }.getOrDefault(JSONArray())
            array.put(JSONObject().put("label", label).put("action", action))
            card = card.with("actions", array.toString())
            appPicker = false
        }
    if (toggleActionPicker)
        LunarisBarChoiceDialog(
            lunarisBarText("toggle_action"),
            lunarisBarGestureActions.filterNot {
                it in listOf("expand", "next_card", "previous_card", "open_app")
            } + "choose_app",
            -1,
            { toggleActionPicker = false },
        ) { index ->
            val choices =
                lunarisBarGestureActions.filterNot {
                    it in listOf("expand", "next_card", "previous_card", "open_app")
                }
            if (index == choices.size) toggleAppPicker = true
            else card = card.with("toggle_action", choices[index])
            toggleActionPicker = false
        }
    if (toggleAppPicker)
        LunarisBarAppPicker({ toggleAppPicker = false }) { label, action ->
            card = card.with("toggle_action", action).with("toggle_label", label)
            toggleAppPicker = false
        }
    if (customShortcut)
        LunarisBarShortcutEditor({ customShortcut = false }) { label, action ->
            val array =
                runCatching { JSONArray(card.text("actions", "[]")) }.getOrDefault(JSONArray())
            array.put(JSONObject().put("label", label).put("action", action))
            card = card.with("actions", array.toString())
            customShortcut = false
        }
}

@Composable
private fun LunarisBarCardText(
    card: LunarisBarSettings.Card,
    key: String,
    label: String,
    change: (LunarisBarSettings.Card) -> Unit,
) {
    OutlinedTextField(
        card.text(key, ""),
        onValueChange = { change(card.with(key, it.take(8192))) },
        label = { Text(lunarisBarText(label)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LunarisBarCardOption(
    card: LunarisBarSettings.Card,
    option: LunarisBarOption,
    change: (LunarisBarSettings.Card) -> Unit,
) {
    var choosing by remember(option.key) { mutableStateOf(false) }
    val value =
        if (option.kind == "switch") {
            if (card.enabled(option.key, option.default != 0)) 1 else 0
        } else
            card.integer(
                option.key,
                option.default,
                option.min,
                if (option.choices.isNotEmpty()) option.choices.lastIndex else option.max,
            )
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth().clickable(enabled = option.kind == "choice") {
                    choosing = true
                },
        ) {
            Text(lunarisBarText(option.key), modifier = Modifier.weight(1f))
            if (option.kind == "switch")
                Switch(value != 0, onCheckedChange = { change(card.with(option.key, it)) })
            else if (option.kind == "choice")
                Text(
                    lunarisBarText(option.choices[value]),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            else {
                Text(value.toString())
                LunarisBarSliderReset(option, value) {
                    change(card.with(option.key, option.default))
                }
            }
        }
        if (option.kind == "slider")
            Slider(
                value.toFloat(),
                onValueChange = { change(card.with(option.key, it.roundToInt())) },
                valueRange = option.min.toFloat()..option.max.toFloat(),
            )
    }
    if (choosing)
        LunarisBarChoiceDialog(
            lunarisBarText(option.key),
            option.choices,
            value,
            { choosing = false },
        ) {
            change(card.with(option.key, it))
            choosing = false
        }
}

@Composable
internal fun LunarisBarAppPicker(dismiss: () -> Unit, select: (String, String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<ResolveInfo>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(context) {
        apps =
            withContext(Dispatchers.IO) {
                context.packageManager
                    .queryIntentActivities(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                        0,
                    )
                    .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
            }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(lunarisBarText("add_app")) },
        text = {
            Column {
                OutlinedTextField(
                    search,
                    onValueChange = { search = it },
                    label = { Text(lunarisBarText("search")) },
                )
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(
                        apps.filter { it.loadLabel(context.packageManager).contains(search, true) }
                    ) { app ->
                        val label = app.loadLabel(context.packageManager).toString()
                        Text(
                            label,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        select(
                                            label,
                                            "app:" +
                                                ComponentName(
                                                        app.activityInfo.packageName,
                                                        app.activityInfo.name,
                                                    )
                                                    .flattenToString(),
                                        )
                                    }
                                    .padding(vertical = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(lunarisBarText("cancel")) } },
    )
}

@Composable
private fun LunarisBarShortcutEditor(dismiss: () -> Unit, save: (String, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(lunarisBarText("add_contact_action")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lunarisBarText("contact"), modifier = Modifier.weight(1f))
                    Switch(contact, onCheckedChange = { contact = it })
                }
                OutlinedTextField(
                    label,
                    onValueChange = { label = it },
                    label = { Text(lunarisBarText("label")) },
                )
                OutlinedTextField(
                    value,
                    onValueChange = { value = it },
                    label = { Text(lunarisBarText(if (contact) "phone_number" else "intent_uri")) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && value.isNotBlank(),
                onClick = { save(label, (if (contact) "contact:" else "intent:") + value) },
            ) {
                Text(lunarisBarText("save"))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(lunarisBarText("cancel")) } },
    )
}

@Composable
private fun LunarisBarWidgetPicker(dismiss: () -> Unit) {
    val context = LocalContext.current
    val providers = remember {
        AppWidgetManager.getInstance(context).installedProviders.sortedBy {
            it.loadLabel(context.packageManager).lowercase()
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(lunarisBarText("widget")) },
        text = {
            LazyColumn {
                items(providers) { provider ->
                    Text(
                        provider.loadLabel(context.packageManager),
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(LunarisBarSettings.WIDGET_PICKER_ACTION)
                                            .setPackage("com.android.systemui")
                                            .putExtra(
                                                "provider",
                                                provider.provider.flattenToString(),
                                            )
                                    )
                                    dismiss()
                                }
                                .padding(vertical = 16.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(lunarisBarText("cancel")) } },
    )
}
