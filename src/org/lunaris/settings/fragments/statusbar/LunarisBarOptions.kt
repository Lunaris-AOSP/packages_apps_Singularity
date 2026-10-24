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

internal data class LunarisBarOption(
    val key: String,
    val kind: String = "switch",
    val default: Int = 0,
    val min: Int = 0,
    val max: Int = 100,
    val choices: List<String> = emptyList(),
    val system: Boolean = false,
    val values: List<Int> = emptyList(),
)

internal val lunarisBarPalettes =
    listOf(
        "surface",
        "primary_container",
        "secondary_container",
        "tertiary_container",
        "black",
        "primary",
        "custom",
        "surface_light",
        "surface_dark",
        "primary_container_light",
        "primary_container_dark",
        "secondary_container_light",
        "secondary_container_dark",
        "tertiary_container_light",
        "tertiary_container_dark",
        "primary_light",
        "primary_dark",
        "tertiary",
        "tertiary_light",
        "tertiary_dark",
        "tonal",
    )

internal val lunarisBarShapeNames =
    listOf(
        "none",
        "system",
        "circle",
        "star",
        "rounded_corner",
        "square",
        "hexagon",
        "four_corner",
        "rhombus",
        "triangle",
        "triangle_round",
        "rectangle",
        "arrow",
        "sunny",
        "very_sunny",
        "diamond",
        "pentagon",
        "semicircle",
        "arch",
        "ghost",
        "pill",
        "burst",
        "soft_burst",
        "clover8",
        "clover4",
        "flower",
        "clamshell",
        "cookie",
        "fan",
        "pixel_circle",
        "bun",
        "heart",
    )

internal val lunarisBarGestureActions =
    listOf(
        "none",
        "expand",
        "open_app",
        "next_card",
        "previous_card",
        "play_pause",
        "next_track",
        "previous_track",
        "flashlight",
        "notifications",
        "quick_settings",
        "home",
        "back",
        "recents",
        "lock",
        "screenshot",
        "settings",
        "brightness_settings",
        "volume_settings",
        "wifi_settings",
        "bluetooth_settings",
        "internet_settings",
        "location_settings",
        "mute",
        "auto_brightness",
    )

internal val lunarisBarAppearanceOptions =
    listOf(
        LunarisBarOption("theme", "choice", choices = listOf("follow_system", "light", "dark")),
        LunarisBarOption("mini_blur"),
        LunarisBarOption("capsule_blur"),
        LunarisBarOption("mini_shape", "choice", 2, choices = lunarisBarShapeNames),
        LunarisBarOption("card_icon_shape", "choice", 2, choices = lunarisBarShapeNames),
        LunarisBarOption("card_button_shape", "choice", 20, choices = lunarisBarShapeNames),
        LunarisBarOption("mini_alignment", "choice", choices = listOf("center", "left", "right")),
        LunarisBarOption("mini_scale", "slider", 100, 70, 200),
        LunarisBarOption(
            "mini_scale_alignment",
            "choice",
            choices = listOf("center", "top", "bottom"),
        ),
        LunarisBarOption(
            "status_bar_dynamic_island_width_scale",
            "slider",
            100,
            70,
            140,
            system = true,
        ),
        LunarisBarOption(
            "status_bar_dynamic_island_height_scale",
            "slider",
            100,
            70,
            140,
            system = true,
        ),
        LunarisBarOption("offset_x", "slider", 0, -200, 200),
        LunarisBarOption("offset_y", "slider", 0, -24, 100),
        LunarisBarOption(
            "expanded_mode",
            "choice",
            default = 1,
            choices = listOf("camera_attached", "independent"),
        ),
        LunarisBarOption(
            "expanded_width",
            "choice",
            default = 1,
            choices = listOf("full_width", "content_width"),
        ),
        LunarisBarOption(
            "expanded_shape",
            "choice",
            default = 1,
            choices = listOf("pill", "rounded_corner", "squircle"),
        ),
        LunarisBarOption("custom_radius", default = 1),
        LunarisBarOption("camera_width", "slider", 25, 0, 120),
        LunarisBarOption("camera_height", "slider", 24, 8, 64),
        LunarisBarOption("corner_radius", "slider", 32, 0, 64),
        LunarisBarOption("outline_width", "slider", 10, 0, 30),
        LunarisBarOption("shadow_elevation", "slider", 12, 0, 32),
        LunarisBarOption("collapsed_opacity", "slider", 0, 0, 100),
        LunarisBarOption(
            "collapsed_border",
            "choice",
            choices = listOf("filled", "one_dp", "two_dp", "three_dp"),
        ),
        LunarisBarOption("companion", default = 1),
        LunarisBarOption("always_visible"),
    )

internal val lunarisBarMotionOptions =
    listOf(
        LunarisBarOption("animation_style", "choice", choices = listOf("fruit", "robot")),
        LunarisBarOption("animation_speed", "slider", 100, 50, 200),
        LunarisBarOption(
            "animation_bounce",
            "choice",
            1,
            choices = listOf("no_bounce", "normal_bounce", "high_bounce"),
        ),
        LunarisBarOption("reduce_motion"),
        LunarisBarOption("haptics", default = 1),
        LunarisBarOption("mini_fade", default = 1),
    )

internal val lunarisBarMediaOptions =
    listOf(
        LunarisBarOption(
            "media_background",
            "choice",
            default = 2,
            choices = listOf("none", "cover", "blurred_cover", "waves", "gradient"),
        ),
        LunarisBarOption("artwork_shape", "choice", 4, choices = lunarisBarShapeNames),
        LunarisBarOption("media_button_shape", "choice", 2, choices = lunarisBarShapeNames),
        LunarisBarOption("player_icon", "choice", choices = listOf("app_icon", "app_name", "none")),
        LunarisBarOption("media_haptics", default = 1),
        LunarisBarOption("media_artwork_colors"),
        LunarisBarOption("rotate_artwork", default = 1),
        LunarisBarOption("equalizer", default = 1),
        LunarisBarOption("media_slider", default = 1),
        LunarisBarOption("media_thin_border", default = 1),
        LunarisBarOption("media_hide_paused"),
        LunarisBarOption("media_hide_delay", "slider", 0, 0, 300),
        LunarisBarOption("media_mini", default = 1),
        LunarisBarOption("media_mini_custom"),
        LunarisBarOption("media_mini_left_shape", "choice", 2, choices = lunarisBarShapeNames),
        LunarisBarOption("media_mini_right_shape", "choice", 2, choices = lunarisBarShapeNames),
        LunarisBarOption(
            "media_mini_right",
            "choice",
            1,
            choices = listOf("app_icon", "equalizer"),
        ),
        LunarisBarOption("media_mini_artwork_colors"),
        LunarisBarOption("status_bar_dynamic_island_lyrics", system = true),
    )

internal val lunarisBarFeatureKeys =
    listOf(
        "battery",
        "unlock",
        "bluetooth",
        "calls",
        "notifications",
        "ongoing_activities",
        "hotspot",
        "vpn",
        "ringer",
        "timer",
        "audio_recording",
        "now_playing",
        "media_controls",
        "screen_recording",
        "alarms",
        "flashlight",
        "stopwatch",
        "live_scores",
        "lockscreen",
        "disable_in_landscape",
    )

internal val lunarisBarFeatureOptions =
    lunarisBarFeatureKeys.map {
        LunarisBarOption(
            "status_bar_dynamic_island_$it",
            default =
                if (
                    it in
                        listOf(
                            "media_controls",
                            "screen_recording",
                            "alarms",
                            "flashlight",
                            "stopwatch",
                            "live_scores",
                        )
                )
                    1
                else 0,
            system = true,
        )
    }
