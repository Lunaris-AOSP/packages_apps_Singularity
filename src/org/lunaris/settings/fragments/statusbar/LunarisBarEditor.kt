/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.statusbar

import android.content.Context
import android.app.ActivityManager
import android.provider.Settings
import com.android.internal.util.lunaris.LunarisBarSettings

internal class LunarisBarEditor(val context: Context) {
    val userId = ActivityManager.getCurrentUser()

    fun isCurrentUser(): Boolean = userId == ActivityManager.getCurrentUser()

    private fun readConfig(): LunarisBarSettings {
        return LunarisBarSettings.read(context.contentResolver, userId)
            .forScreen(
                LunarisBarSettings.screenWidth(context),
                LunarisBarSettings.screenHeight(context),
            )
    }

    var config = readConfig()
        private set

    fun resetAll(): Boolean {
        if (!isCurrentUser()) return false
        val success = LunarisBarSettings.reset(context.contentResolver, userId)
        reload()
        return success
    }

    fun reload() {
        config = readConfig()
    }

    fun defaultValue(option: LunarisBarOption): Int =
        if (option.system) LunarisBarSettings.systemDefaults()[option.key] ?: option.default
        else option.default

    fun read(option: LunarisBarOption): Int {
        return if (option.system)
            Settings.System.getIntForUser(
                context.contentResolver,
                option.key,
                defaultValue(option),
                userId,
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

    fun set(option: LunarisBarOption, value: Int): Boolean {
        if (!isCurrentUser()) return false
        if (option.system) {
            return Settings.System.putIntForUser(
                context.contentResolver, option.key, value, userId,
            ).also { reload() }
        }
        val stored: Any = when {
            option.key.startsWith("feature_") && option.key.endsWith("_left_content") ->
                if (value == 1) 2 else 0
            option.kind == "switch" -> value != 0
            else -> option.values.getOrElse(value) { value }
        }
        return put(option.key to stored)
    }

    fun put(vararg changes: Pair<String, Any>): Boolean {
        if (!isCurrentUser()) return false
        var next = LunarisBarSettings.read(context.contentResolver, userId)
        for ((key, value) in changes) {
            next = next.withScreenValue(
                LunarisBarSettings.screenWidth(context), LunarisBarSettings.screenHeight(context),
                key, value,
            )
        }
        return next.write(context.contentResolver, userId).also { reload() }
    }

    fun screenProfile(enabled: Boolean): Boolean {
        if (!isCurrentUser()) return false
        return LunarisBarSettings.read(context.contentResolver, userId).withScreenProfile(
            LunarisBarSettings.screenWidth(context), LunarisBarSettings.screenHeight(context), enabled,
        ).write(context.contentResolver, userId).also { reload() }
    }

    fun cards(cards: List<LunarisBarSettings.Card>): Boolean {
        if (!isCurrentUser()) return false
        return LunarisBarSettings.read(context.contentResolver, userId).withCards(cards)
            .write(context.contentResolver, userId).also { reload() }
    }
}
