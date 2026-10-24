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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.internal.logging.nano.MetricsProto
import com.android.internal.util.lunaris.LunarisBarSettings
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.framework.theme.settingsBackground
import kotlin.math.roundToInt

class DynamicIslandSettings : SettingsPreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.lunaris_bar_title)
    }

    override fun getMetricsCategory() = MetricsProto.MetricsEvent.LUNARIS

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { SettingsTheme { LunarisBarSettingsPage() } }
        }
}

internal class LunarisBarEditor(val context: Context) {
    private fun readConfig(): LunarisBarSettings {
        return LunarisBarSettings.read(context.contentResolver)
            .forScreen(
                LunarisBarSettings.screenWidth(context),
                LunarisBarSettings.screenHeight(context),
            )
    }

    var config by mutableStateOf(readConfig())
        private set

    var revision by mutableIntStateOf(0)
        private set

    var failed by mutableStateOf(false)
        private set

    fun resetAll() {
        val userId = android.app.ActivityManager.getCurrentUser()
        failed = !LunarisBarSettings.reset(context.contentResolver, userId)
        reload()
    }

    fun reload() {
        config = readConfig()
        revision++
    }

    fun defaultValue(option: LunarisBarOption): Int =
        if (option.system) LunarisBarSettings.systemDefaults()[option.key] ?: option.default
        else option.default

    fun read(option: LunarisBarOption): Int {
        revision
        return if (option.system)
            Settings.System.getIntForUser(
                context.contentResolver,
                option.key,
                defaultValue(option),
                UserHandle.USER_CURRENT,
            )
        else if (option.kind == "switch") {
            if (config.enabled(option.key, option.default != 0)) 1 else 0
        } else if (option.key.startsWith("feature_") && option.key.endsWith("_left_content")) {
            if (config.integer(option.key, 0, 0, 2) == 2) 1 else 0
        } else {
            val value =
                config.integer(
                    option.key,
                    option.values.getOrElse(option.default) { option.default },
                    option.min,
                    option.values.maxOrNull()
                        ?: if (option.choices.isNotEmpty()) option.choices.lastIndex else option.max,
                )
            if (option.values.isEmpty()) value
            else option.values.indexOf(value).takeIf { it >= 0 } ?: option.default
        }
    }

    fun alternative(key: String): Boolean = read(LunarisBarOption(key, system = true)) != 0

    fun available(option: LunarisBarOption): Boolean {
        val key = option.key
        if (
            alternative(LunarisBarSettings.ALTERNATIVE_ANIMATION) &&
                key in listOf("animation_style", "animation_speed", "animation_bounce", "mini_fade")
        )
            return false
        if (
            config.isReducedMotion &&
                key in
                    listOf(
                        "animation_style",
                        "animation_speed",
                        "animation_bounce",
                        "mini_fade",
                        "rotate_artwork",
                    )
        )
            return false
        if (key == "corner_radius" && !config.enabled("custom_radius", true)) return false
        if (key in listOf("collapsed_opacity", "collapsed_border") && !config.isAlwaysVisible)
            return false
        if (
            alternative(LunarisBarSettings.ALTERNATIVE_MEDIA) &&
                key in
                    listOf(
                        "media_background",
                        "artwork_shape",
                        "media_button_shape",
                        "player_icon",
                        "media_slider",
                        "media_artwork_colors",
                        "media_haptics",
                    )
        )
            return false
        if (key == "media_hide_delay" && !config.enabled("media_hide_paused", false)) return false
        if (
            key in
                listOf(
                    "rotate_artwork",
                    "equalizer",
                    "media_thin_border",
                    "media_mini_custom",
                    "media_mini_left_shape",
                    "media_mini_right_shape",
                    "media_mini_right",
                    "media_mini_artwork_colors",
                ) && !config.enabled("media_mini", true)
        )
            return false
        if (
            key in listOf("media_mini_left_shape", "media_mini_right_shape") &&
                !config.enabled("media_mini_custom", false)
        )
            return false
        return true
    }

    fun set(option: LunarisBarOption, value: Int) {
        if (option.system) {
            failed =
                !Settings.System.putIntForUser(
                    context.contentResolver,
                    option.key,
                    value,
                    UserHandle.USER_CURRENT,
                )
            revision++
        } else if (option.key.startsWith("feature_") && option.key.endsWith("_left_content")) {
            put(option.key, if (value == 1) 2 else 0)
        } else
            put(
                option.key,
                if (option.kind == "switch") value != 0
                else option.values.getOrElse(value) { value },
            )
    }

    fun put(key: String, value: Any) {
        val next =
            LunarisBarSettings.read(context.contentResolver)
                .withScreenValue(
                    LunarisBarSettings.screenWidth(context),
                    LunarisBarSettings.screenHeight(context),
                    key,
                    value,
                )
        failed = !next.write(context.contentResolver, UserHandle.USER_CURRENT)
        if (!failed) config = readConfig()
    }

    fun screenProfile(enabled: Boolean) {
        val next =
            LunarisBarSettings.read(context.contentResolver)
                .withScreenProfile(
                    LunarisBarSettings.screenWidth(context),
                    LunarisBarSettings.screenHeight(context),
                    enabled,
                )
        failed = !next.write(context.contentResolver, UserHandle.USER_CURRENT)
        reload()
    }

    fun cards(cards: List<LunarisBarSettings.Card>) {
        val next = LunarisBarSettings.read(context.contentResolver).withCards(cards)
        failed = !next.write(context.contentResolver, UserHandle.USER_CURRENT)
        if (!failed) config = readConfig()
    }
}

@Composable
internal fun lunarisBarText(key: String): String {
    val context = LocalContext.current
    val baseKey = key.removeSuffix("_day").removeSuffix("_night")
    val labelKey =
        if (baseKey.startsWith("feature_")) {
            listOf(
                    "card_icon_shape",
                    "card_button_shape",
                    "left_background",
                    "left_foreground",
                    "right_background",
                    "right_foreground",
                    "progress_background",
                    "progress_foreground",
                    "track_background",
                    "track_foreground",
                    "show_battery",
                    "progress_palette",
                    "track_palette",
                    "left_palette",
                    "right_palette",
                    "left_content",
                    "right_content",
                    "mini_shape",
                    "right_shape",
                    "background",
                    "foreground",
                    "palette",
                    "haptics",
                )
                .firstOrNull { baseKey.endsWith("_$it") } ?: baseKey
        } else if (baseKey.startsWith("media")) baseKey else key
    val id =
        remember(labelKey, context) {
            context.resources.getIdentifier("lunaris_bar_$labelKey", "string", context.packageName)
        }
    return if (id != 0) stringResource(id) else labelKey.replace('_', ' ')
}

@Composable
private fun LunarisBarSettingsPage() {
    val context = LocalContext.current
    val editor = remember(context) { LunarisBarEditor(context) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val pages = listOf("appearance", "colors", "motion", "media", "gestures", "cards", "features")
    DisposableEffect(editor) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    editor.reload()
                }
            }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    editor.reload()
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
            UserHandle.USER_ALL,
        )
        context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_USER_SWITCHED),
            Context.RECEIVER_EXPORTED,
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.settingsBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .imePadding()
                    .nestedScroll(rememberNestedScrollInteropConnection()),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom =
                        WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 48.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "main_switch") {
                LunarisBarOptionRow(
                    editor,
                    LunarisBarOption("status_bar_show_dynamic_island", system = true),
                    prominent = true,
                )
            }
            item(key = "preview") { LunarisBarPreview(editor.config) }
            item(key = "customize") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lunarisBarText("customize"),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { confirmReset = true }) {
                        Text(lunarisBarText("reset_all"))
                    }
                }
            }
            item(key = "sections") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pages.forEachIndexed { index, name ->
                        FilterChip(
                            selected = page == index,
                            onClick = { page = index },
                            label = { Text(lunarisBarText(name)) },
                        )
                    }
                }
            }
            item {
                Text(
                    lunarisBarText(pages[page] + "_description"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (editor.failed)
                item {
                    Text(
                        lunarisBarText("save_error"),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            when (page) {
                0 -> {
                    item {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(lunarisBarText("screen_profile"))
                                Text(
                                    lunarisBarText("screen_profile_description"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "${LunarisBarSettings.screenWidth(context)} × ${LunarisBarSettings.screenHeight(context)} dp",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                editor.config.hasScreenProfile(
                                    LunarisBarSettings.screenWidth(context),
                                    LunarisBarSettings.screenHeight(context),
                                ),
                                onCheckedChange = editor::screenProfile,
                            )
                        }
                    }
                    for ((section, keys) in
                        listOf(
                            "appearance_theme" to listOf("theme", "mini_blur", "capsule_blur"),
                            "appearance_chip" to
                                listOf(
                                    "mini_shape",
                                    "mini_scale",
                                    "status_bar_dynamic_island_width_scale",
                                    "status_bar_dynamic_island_height_scale",
                                    "companion",
                                ),
                            "appearance_position" to
                                listOf(
                                    "mini_alignment",
                                    "mini_scale_alignment",
                                    "offset_x",
                                    "offset_y",
                                    "camera_width",
                                    "camera_height",
                                ),
                            "appearance_expanded" to
                                listOf(
                                    "expanded_mode",
                                    "expanded_width",
                                    "expanded_shape",
                                    "card_icon_shape",
                                    "card_button_shape",
                                ),
                            "appearance_edges" to
                                listOf(
                                    "custom_radius",
                                    "corner_radius",
                                    "outline_width",
                                    "shadow_elevation",
                                ),
                            "appearance_idle" to
                                listOf("always_visible", "collapsed_opacity", "collapsed_border"),
                        )) {
                        item { LunarisBarSectionTitle(section) }
                        items(
                            lunarisBarAppearanceOptions.filter {
                                it.key in keys && editor.available(it)
                            },
                            key = { it.key },
                        ) {
                            LunarisBarOptionRow(editor, it)
                        }
                    }
                }
                1 -> item { LunarisBarColorSettings(editor) }
                2 -> {
                    items(lunarisBarMotionOptions.filter(editor::available), key = { it.key }) {
                        LunarisBarOptionRow(editor, it)
                    }
                    item { LunarisBarSectionTitle("alternative_versions") }
                    item {
                        LunarisBarOptionRow(
                            editor,
                            LunarisBarOption(
                                LunarisBarSettings.ALTERNATIVE_ANIMATION,
                                default = 1,
                                system = true,
                            ),
                        )
                    }
                    item {
                        LunarisBarOptionRow(
                            editor,
                            LunarisBarOption(
                                LunarisBarSettings.ALTERNATIVE_MEDIA,
                                default = 1,
                                system = true,
                            ),
                        )
                    }
                    item {
                        LunarisBarOptionRow(
                            editor,
                            LunarisBarOption(
                                LunarisBarSettings.ALTERNATIVE_FLASHLIGHT,
                                default = 1,
                                system = true,
                            ),
                        )
                    }
                }
                3 -> {
                    items(lunarisBarMediaOptions.filter(editor::available), key = { it.key }) {
                        LunarisBarOptionRow(editor, it)
                    }
                    item { LunarisBarMediaColors(editor) }
                }
                4 -> item { LunarisBarGestureSettings(editor) }
                5 -> item { LunarisBarCardsSettings(editor) }
                6 -> {
                    items(lunarisBarFeatureOptions, key = { it.key }) {
                        LunarisBarOptionRow(editor, it)
                    }
                    item { LunarisBarFeatureStyles(editor) }
                }
            }
        }
    }
    if (confirmReset)
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(lunarisBarText("reset_all")) },
            text = { Text(lunarisBarText("reset_all_message")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        editor.resetAll()
                        confirmReset = false
                    }
                ) {
                    Text(lunarisBarText("reset"))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(lunarisBarText("cancel")) }
            },
        )
}

@Composable
internal fun LunarisBarSectionTitle(key: String) {
    Text(
        lunarisBarText(key),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
internal fun LunarisBarOptionRow(
    editor: LunarisBarEditor,
    option: LunarisBarOption,
    prominent: Boolean = false,
) {
    val effectiveOption = option.copy(default = editor.defaultValue(option))
    val value = editor.read(effectiveOption)
    var choosing by remember(option.key) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (prominent) 28.dp else 20.dp)
    Surface(
        shape = shape,
        color =
            if (prominent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.clickable(enabled = option.kind != "slider") {
                    if (option.kind == "switch") editor.set(option, if (value == 0) 1 else 0)
                    else choosing = true
                }
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        lunarisBarText(option.key),
                        style =
                            if (prominent) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.bodyLarge,
                    )
                    val description =
                        LocalContext.current.resources.getIdentifier(
                            "lunaris_bar_${option.key}_description",
                            "string",
                            LocalContext.current.packageName,
                        )
                    if (description != 0)
                        Text(
                            stringResource(description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    if (option.kind == "choice")
                        Text(
                            lunarisBarText(
                                option.choices.getOrElse(value) { option.choices.first() }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                }
                when (option.kind) {
                    "switch" ->
                        Switch(
                            checked = value != 0,
                            onCheckedChange = { editor.set(option, if (it) 1 else 0) },
                        )
                    "choice" -> Icon(Icons.Default.ChevronRight, contentDescription = null)
                    "slider" ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                lunarisBarValueText(option, value),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            LunarisBarSliderReset(effectiveOption, value) {
                                editor.set(effectiveOption, effectiveOption.default)
                            }
                        }
                }
            }
            if (option.kind == "slider") {
                var drag by remember(value, option.key) { mutableStateOf(value.toFloat()) }
                Slider(
                    value = drag.coerceIn(option.min.toFloat(), option.max.toFloat()),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { editor.set(option, drag.roundToInt()) },
                    valueRange = option.min.toFloat()..option.max.toFloat(),
                )
            }
        }
    }
    if (choosing)
        LunarisBarChoiceDialog(
            lunarisBarText(option.key),
            option.choices,
            value,
            onDismiss = { choosing = false },
            onSelect = {
                editor.set(option, it)
                choosing = false
            },
        )
}

private fun lunarisBarValueText(option: LunarisBarOption, value: Int): String =
    when {
        option.key == "animation_speed" -> "${value / 100f}×"
        option.key == "outline_width" -> "${value / 10f} dp"
        "scale" in option.key || "opacity" in option.key -> "$value%"
        option.key.endsWith("delay") -> "$value s"
        else -> "$value dp"
    }

@Composable
internal fun LunarisBarChoiceDialog(
    title: String,
    choices: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(choices.size) { index ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected == index, onClick = { onSelect(index) })
                        Text(
                            lunarisBarText(choices[index]),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(lunarisBarText("cancel")) } },
    )
}

@Composable
private fun LunarisBarColorSettings(editor: LunarisBarEditor) {
    var night by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                !night,
                onClick = { night = false },
                label = { Text(lunarisBarText("light")) },
            )
            FilterChip(night, onClick = { night = true }, label = { Text(lunarisBarText("dark")) })
        }
        val suffix = if (night) "night" else "day"
        for (prefix in listOf("mini", "capsule")) {
            LunarisBarSectionTitle(prefix)
            LunarisBarOptionRow(
                editor,
                LunarisBarOption(
                    "${prefix}_palette_$suffix",
                    "choice",
                    default = 4,
                    choices = lunarisBarPalettes,
                ),
            )
            LunarisBarOptionRow(
                editor,
                LunarisBarOption("${prefix}_opacity_$suffix", "slider", 100, 10, 100),
            )
            for (role in listOf("background", "foreground")) {
                val key = "${prefix}_${role}_$suffix"
                LunarisBarColorRow(
                    key,
                    Color(editor.config.color(prefix == "mini", night, role == "foreground")),
                ) {
                    editor.put(key, it.toArgb())
                    editor.put("${prefix}_palette_$suffix", 6)
                }
            }
        }
        LunarisBarSectionTitle("outline_and_shadow")
        LunarisBarOptionRow(
            editor,
            LunarisBarOption("outline_$suffix", default = if (night) 1 else 0),
        )
        LunarisBarOptionRow(
            editor,
            LunarisBarOption("shadow_$suffix", default = if (night) 0 else 1),
        )
    }
}

@Composable
internal fun LunarisBarColorRow(
    label: String,
    color: Color,
    defaultColor: Color = if ("background" in label) Color.Black else Color.White,
    onColor: (Color) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Surface(
        onClick = { editing = true },
        color = MaterialTheme.colorScheme.surfaceBright,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lunarisBarText(label), modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(color))
        }
    }
    if (editing)
        LunarisBarColorDialog(color, defaultColor, { editing = false }) {
            onColor(it)
            editing = false
        }
}

@Composable
private fun LunarisBarColorDialog(
    initial: Color,
    defaultColor: Color,
    dismiss: () -> Unit,
    save: (Color) -> Unit,
) {
    val initialHsv =
        remember(initial) {
            FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
        }
    val defaultHsv =
        remember(defaultColor) {
            FloatArray(3).also { android.graphics.Color.colorToHSV(defaultColor.toArgb(), it) }
        }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    val color = Color.hsv(hue, saturation, brightness, initial.alpha)
    var hex by remember { mutableStateOf("%06X".format(initial.toArgb() and 0xffffff)) }
    fun updateHex() {
        hex = "%06X".format(Color.hsv(hue, saturation, brightness).toArgb() and 0xffffff)
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(lunarisBarText("choose_color")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(color)
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it.removePrefix("#").take(6)
                        if (hex.length == 6)
                            runCatching {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(
                                    android.graphics.Color.parseColor("#$hex"),
                                    hsv,
                                )
                                hue = hsv[0]
                                saturation = hsv[1]
                                brightness = hsv[2]
                            }
                    },
                    label = { Text(lunarisBarText("hex_color")) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lunarisBarText("hue"), Modifier.weight(1f))
                    LunarisBarColorSliderReset("hue", hue, defaultHsv[0]) {
                        hue = defaultHsv[0]
                        updateHex()
                    }
                }
                Slider(
                    hue,
                    onValueChange = {
                        hue = it
                        hex =
                            "%06X"
                                .format(Color.hsv(it, saturation, brightness).toArgb() and 0xffffff)
                    },
                    valueRange = 0f..360f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lunarisBarText("saturation"), Modifier.weight(1f))
                    LunarisBarColorSliderReset("saturation", saturation, defaultHsv[1]) {
                        saturation = defaultHsv[1]
                        updateHex()
                    }
                }
                Slider(
                    saturation,
                    onValueChange = {
                        saturation = it
                        hex = "%06X".format(Color.hsv(hue, it, brightness).toArgb() and 0xffffff)
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lunarisBarText("brightness"), Modifier.weight(1f))
                    LunarisBarColorSliderReset("brightness", brightness, defaultHsv[2]) {
                        brightness = defaultHsv[2]
                        updateHex()
                    }
                }
                Slider(
                    brightness,
                    onValueChange = {
                        brightness = it
                        hex = "%06X".format(Color.hsv(hue, saturation, it).toArgb() and 0xffffff)
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = { save(color) }) { Text(lunarisBarText("save")) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(lunarisBarText("cancel")) } },
    )
}

@Composable
private fun LunarisBarGestureSettings(editor: LunarisBarEditor) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, fallback) in
            listOf(
                "gesture_tap" to "expand",
                "gesture_double_tap" to "none",
                "gesture_long_press" to "open_app",
                "gesture_swipe_left" to "next_card",
                "gesture_swipe_right" to "previous_card",
                "gesture_swipe_up" to "expand",
                "gesture_swipe_down" to "notifications",
            )) {
            var choosing by remember(key) { mutableStateOf(false) }
            var appPicker by remember(key) { mutableStateOf(false) }
            val action = editor.config.text(key, fallback)
            val cards = editor.config.cards.filter { it.isEnabled }
            val cardAction = cards.firstOrNull { "card:${it.id}" == action }
            Surface(
                onClick = { choosing = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceBright,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(lunarisBarText(key), style = MaterialTheme.typography.titleMedium)
                    Text(
                        cardAction?.title ?: lunarisBarText(action),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (choosing)
                LunarisBarChoiceDialog(
                    lunarisBarText(key),
                    lunarisBarGestureActions + "choose_app" + cards.map { it.title },
                    lunarisBarGestureActions.indexOf(action),
                    { choosing = false },
                ) {
                    when {
                        it == lunarisBarGestureActions.size -> appPicker = true
                        it > lunarisBarGestureActions.size ->
                            editor.put(
                                key,
                                "card:${cards[it - lunarisBarGestureActions.size - 1].id}",
                            )
                        else -> editor.put(key, lunarisBarGestureActions[it])
                    }
                    choosing = false
                }
            if (appPicker)
                LunarisBarAppPicker({ appPicker = false }) { _, launchAction ->
                    editor.put(key, launchAction)
                    appPicker = false
                }
        }
    }
}

@Composable
private fun LunarisBarPreview(config: LunarisBarSettings) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dark =
        when (config.theme) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }
    val lightScheme = dynamicLightColorScheme(context)
    val darkScheme = dynamicDarkColorScheme(context)
    val paletteScheme = if (dark) darkScheme else lightScheme
    val (background, foreground) =
        lunarisBarPreviewPalette(
            config.palette(!expanded, dark),
            paletteScheme,
            lightScheme,
            darkScheme,
            Color(config.color(!expanded, dark, false)) to
                Color(config.color(!expanded, dark, true)),
        )
    val surface = background.copy(alpha = config.opacity(!expanded, dark))
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(listOf(scheme.secondaryContainer, scheme.tertiaryContainer))
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            lunarisBarText("preview"),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSecondaryContainer,
        )
        Surface(
            onClick = { expanded = !expanded },
            color = surface,
            contentColor = foreground,
            shape =
                when {
                    !expanded -> LunarisBarPreviewSquircle(config.cornerRadius.dp)
                    config.expandedShape == 0 -> RoundedCornerShape(48.dp)
                    config.expandedShape == 2 -> LunarisBarPreviewSquircle(config.cornerRadius.dp)
                    else -> RoundedCornerShape(config.cornerRadius.dp)
                },
            border =
                if (config.outline(dark))
                    BorderStroke(config.outlineWidth.dp, foreground.copy(alpha = 0.2f))
                else null,
            shadowElevation = if (config.shadow(dark)) config.shadowElevation.dp else 0.dp,
            modifier =
                Modifier.animateContentSize(
                    spring(
                        dampingRatio =
                            if (config.bounce == 0 || config.isReducedMotion) 1f else 0.65f,
                        stiffness =
                            if (config.isReducedMotion) 100000f else 400f * config.animationSpeed,
                    )
                ),
        ) {
            Column(
                Modifier.padding(if (expanded) 18.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 28.dp),
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        Modifier.size(if (expanded) 40.dp else 24.dp)
                            .clip(
                                lunarisBarIconShape(
                                    config.integer(
                                        if (expanded) "artwork_shape" else "mini_shape",
                                        if (expanded) 4 else 2,
                                        0,
                                        31,
                                    )
                                )
                            )
                            .background(paletteScheme.secondaryContainer)
                            .padding(4.dp),
                    )
                    if (expanded)
                        Column(Modifier.weight(1f)) {
                            Text(lunarisBarText("title"), fontWeight = FontWeight.Bold)
                            Text(
                                lunarisBarText("preview_song"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    Icon(Icons.Default.Pause, null, Modifier.size(24.dp))
                }
                if (expanded && config.isSliderVisible)
                    Slider(
                        0.4f,
                        onValueChange = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
            }
        }
        Text(
            lunarisBarText("preview_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun LunarisBarThemeSelector(night: Boolean, onSelect: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !night,
            onClick = { onSelect(false) },
            label = { Text(lunarisBarText("day")) },
        )
        FilterChip(
            selected = night,
            onClick = { onSelect(true) },
            label = { Text(lunarisBarText("night")) },
        )
    }
}

@Composable
private fun LunarisBarMediaColors(editor: LunarisBarEditor) {
    var night by rememberSaveable { mutableStateOf(false) }
    val suffix = if (night) "night" else "day"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LunarisBarThemeSelector(night) { night = it }
        val parts = buildList {
            if (!editor.alternative(LunarisBarSettings.ALTERNATIVE_MEDIA)) add("media")
            if (
                editor.config.enabled("media_mini", true) &&
                    editor.config.enabled("media_mini_custom", false)
            ) {
                add("media_mini_left")
                add("media_mini_right")
            }
        }
        for (part in parts) {
            LunarisBarOptionRow(
                editor,
                LunarisBarOption(
                    "${part}_palette_$suffix",
                    "choice",
                    editor.config.integer("${part}_palette", if (part == "media") 4 else 0, 0, 20),
                    choices = lunarisBarPalettes,
                ),
            )
            val roles =
                if (part == "media")
                    listOf("background_color", "foreground_color") +
                        if (editor.config.mediaBackground in listOf(3, 4)) listOf("accent_color")
                        else emptyList()
                else listOf("background", "foreground")
            for (role in roles) {
                val key = "${part}_${role}_$suffix"
                val fallback =
                    if (role.startsWith("background")) Color.Black.toArgb()
                    else Color.White.toArgb()
                LunarisBarColorRow(
                    key,
                    Color(
                        editor.config.integer(
                            key,
                            editor.config.integer(
                                "${part}_$role",
                                fallback,
                                Int.MIN_VALUE,
                                Int.MAX_VALUE,
                            ),
                            Int.MIN_VALUE,
                            Int.MAX_VALUE,
                        )
                    ),
                ) {
                    editor.put(key, it.toArgb())
                    editor.put("${part}_palette_$suffix", 6)
                    if (part == "media") editor.put("media_artwork_colors", false)
                    else editor.put("media_mini_custom", true)
                }
            }
        }
    }
}

@Composable
private fun LunarisBarFeatureStyles(editor: LunarisBarEditor) {
    val features =
        listOf(
            "charging",
            "unlock",
            "bluetooth",
            "hotspot",
            "vpn",
            "ringer",
            "call",
            "notification",
            "ongoing",
            "timer",
            "recording",
            "nowplaying",
            "alarm",
            "stopwatch",
            "screenrecord",
            "flashlight",
            "livescore",
            "privacy",
            "screenshare",
        )
    var selected by rememberSaveable { mutableStateOf("notification") }
    var night by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val suffix = if (night) "night" else "day"
    val prefix = "feature_$selected"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LunarisBarSectionTitle("feature_appearance")
        TextButton(onClick = { choosing = true }) { Text(lunarisBarText(selected)) }
        val alternativeFlashlight =
            selected == "flashlight" &&
                editor.alternative(LunarisBarSettings.ALTERNATIVE_FLASHLIGHT)
        val hasProgress =
            selected in listOf("charging", "bluetooth", "notification", "ongoing", "timer")
        val shapes = buildList {
            add("mini_shape")
            if (editor.config.integer("${prefix}_right_content", 0, 0, 3) == 1) add("right_shape")
            if (!alternativeFlashlight) add("card_icon_shape")
            if (selected != "unlock") add("card_button_shape")
        }
        for (part in shapes) {
            LunarisBarOptionRow(
                editor,
                LunarisBarOption(
                    "${prefix}_$part",
                    "choice",
                    choices = listOf("inherit") + lunarisBarShapeNames,
                ),
            )
        }
        LunarisBarOptionRow(
            editor,
            LunarisBarOption(
                "${prefix}_left_content",
                "choice",
                choices = listOf("app_icon", "none"),
            ),
        )
        LunarisBarOptionRow(
            editor,
            LunarisBarOption(
                "${prefix}_right_content",
                "choice",
                choices =
                    if (hasProgress) listOf("text", "app_icon", "progress", "none")
                    else listOf("text", "app_icon", "none"),
                values = if (hasProgress) emptyList() else listOf(0, 1, 3),
            ),
        )
        LunarisBarOptionRow(editor, LunarisBarOption("${prefix}_haptics", default = 1))
        if (selected == "bluetooth")
            LunarisBarOptionRow(
                editor,
                LunarisBarOption("feature_bluetooth_show_battery", default = 1),
            )
        LunarisBarThemeSelector(night) { night = it }
        val parts = buildList {
            add("")
            if (editor.config.integer("${prefix}_left_content", 0, 0, 2) != 2) add("left_")
            if (editor.config.integer("${prefix}_right_content", 0, 0, 3) != 3) add("right_")
            if (hasProgress || (selected == "flashlight" && !alternativeFlashlight)) {
                add("progress_")
                add("track_")
            }
        }
        for (part in parts) {
            val inherits = part in listOf("", "left_", "right_")
            val palette = "${prefix}_${part}palette"
            val default =
                when (part) {
                    "progress_" -> 5
                    "track_" -> 2
                    else -> 0
                }
            LunarisBarOptionRow(
                editor,
                LunarisBarOption(
                    "${palette}_$suffix",
                    "choice",
                    editor.config.integer(palette, default, 0, if (inherits) 21 else 20),
                    choices =
                        (if (inherits) listOf("inherit") else emptyList()) + lunarisBarPalettes,
                ),
            )
            val roles =
                if (part in listOf("progress_", "track_")) listOf("background")
                else listOf("background", "foreground")
            for (role in roles) {
                val key = "${prefix}_${part}${role}_$suffix"
                val defaultColors =
                    lunarisBarPreviewPalette(
                        if (part == "progress_") 5 else if (part == "track_") 2 else 4,
                        MaterialTheme.colorScheme,
                    )
                val defaultColor =
                    if (role == "background") defaultColors.first else defaultColors.second
                val fallback = defaultColor.toArgb()
                LunarisBarColorRow(
                    key,
                    Color(
                        editor.config.integer(
                            key,
                            editor.config.integer(
                                "${prefix}_${part}$role",
                                fallback,
                                Int.MIN_VALUE,
                                Int.MAX_VALUE,
                            ),
                            Int.MIN_VALUE,
                            Int.MAX_VALUE,
                        )
                    ),
                    defaultColor = defaultColor,
                ) {
                    editor.put(key, it.toArgb())
                    editor.put("${palette}_$suffix", if (inherits) 7 else 6)
                }
            }
        }
    }
    if (choosing)
        LunarisBarChoiceDialog(
            lunarisBarText("feature_appearance"),
            features,
            features.indexOf(selected),
            { choosing = false },
        ) {
            selected = features[it]
            choosing = false
        }
}

internal fun lunarisBarPreviewPalette(
    index: Int,
    scheme: androidx.compose.material3.ColorScheme,
    light: androidx.compose.material3.ColorScheme = scheme,
    dark: androidx.compose.material3.ColorScheme = scheme,
    custom: Pair<Color, Color> = scheme.surface to scheme.onSurface,
): Pair<Color, Color> =
    when (index) {
        1 -> scheme.primaryContainer to scheme.onPrimaryContainer
        2 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        3 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        4 -> Color.Black to Color.White
        5 -> scheme.primary to scheme.onPrimary
        6 -> custom
        7 -> light.surfaceBright to light.onSurface
        8 -> dark.surfaceBright to dark.onSurface
        9 -> light.primaryContainer to light.onPrimaryContainer
        10 -> dark.primaryContainer to dark.onPrimaryContainer
        11 -> light.secondaryContainer to light.onSecondaryContainer
        12 -> dark.secondaryContainer to dark.onSecondaryContainer
        13 -> light.tertiaryContainer to light.onTertiaryContainer
        14 -> dark.tertiaryContainer to dark.onTertiaryContainer
        15 -> light.primary to light.onPrimary
        16 -> dark.primary to dark.onPrimary
        17 -> scheme.tertiary to scheme.onTertiary
        18 -> light.tertiary to light.onTertiary
        19 -> dark.tertiary to dark.onTertiary
        20 -> scheme.surfaceContainerHighest to scheme.primary
        else -> scheme.surfaceBright to scheme.onSurface
    }

@Composable
internal fun LunarisBarSliderReset(option: LunarisBarOption, value: Int, reset: () -> Unit) {
    IconButton(onClick = reset, enabled = value != option.default) {
        Icon(
            Icons.Default.RestartAlt,
            stringResource(
                R.string.lunaris_bar_reset_slider,
                lunarisBarText(option.key),
                lunarisBarValueText(option, option.default),
            ),
        )
    }
}

@Composable
private fun LunarisBarColorSliderReset(
    label: String,
    value: Float,
    default: Float,
    reset: () -> Unit,
) {
    IconButton(onClick = reset, enabled = value != default) {
        Icon(
            Icons.Default.RestartAlt,
            stringResource(
                R.string.lunaris_bar_reset_slider,
                lunarisBarText(label),
                default.toString(),
            ),
        )
    }
}
