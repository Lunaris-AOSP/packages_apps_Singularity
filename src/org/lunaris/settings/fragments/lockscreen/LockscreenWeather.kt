/*
 * Copyright (C) 2024-2025 Lunaris AOSP
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
package org.lunaris.settings.fragments.lockscreen

import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto
import com.android.internal.util.android.VibrationUtils
import com.android.internal.util.crdroid.OmniJawsClient
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import org.lunaris.settings.utils.SystemRestartUtils

class LockscreenWeather : SettingsPreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val TAG = "LockscreenWeather"
        private const val KEY_SMARTSPACE_ENABLED = "lockscreen_smartspace_enabled"
        private const val KEY_QUICKSPACE_PSA = "quickspace_psa_enabled"
        private const val KEY_WEATHER_CATEGORY = "lockscreen_weather_category"
        private const val KEY_WEATHER_ENABLED = "lockscreen_weather_enabled"
    }

    private var mSmartspaceEnabled: SwitchPreferenceCompat? = null
    private var mQuickspacePsa: SwitchPreferenceCompat? = null
    private var mWeatherCategory: PreferenceCategory? = null
    private var mWeatherEnabled: SwitchPreferenceCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.lockscreen_weather)

        mSmartspaceEnabled = findPreference(KEY_SMARTSPACE_ENABLED)
        mQuickspacePsa = findPreference(KEY_QUICKSPACE_PSA)
        mWeatherCategory = findPreference(KEY_WEATHER_CATEGORY)
        mWeatherEnabled = findPreference(KEY_WEATHER_ENABLED)

        mSmartspaceEnabled?.onPreferenceChangeListener = this
        mWeatherEnabled?.onPreferenceChangeListener = this

        updatePreferencesState()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        when (preference) {
            mSmartspaceEnabled -> {
                val enabled = newValue as Boolean
                updatePreferencesState(enabled)
                SystemRestartUtils.restartSystemUI(context)
                return true
            }
            mWeatherEnabled -> {
                SystemRestartUtils.restartSystemUI(context)
                return true
            }
        }
        return false
    }

    private fun updatePreferencesState(smartspaceOverride: Boolean? = null) {
        if (mSmartspaceEnabled == null || mWeatherCategory == null || 
            mQuickspacePsa == null || mWeatherEnabled == null) {
            return
        }

        val context = context ?: return
        val isSmartspaceEnabled = smartspaceOverride ?: (Settings.Secure.getIntForUser(
            context.contentResolver,
            Settings.Secure.LOCKSCREEN_SMARTSPACE_ENABLED,
            1,
            UserHandle.USER_CURRENT
        ) == 1)

        val weatherServiceEnabled = OmniJawsClient.get().isOmniJawsEnabled(context)
        mWeatherCategory?.isVisible = !isSmartspaceEnabled
        mQuickspacePsa?.isVisible = isSmartspaceEnabled

        mWeatherEnabled?.apply {
            isEnabled = !isSmartspaceEnabled && weatherServiceEnabled
            
            summary = when {
                !weatherServiceEnabled -> getString(R.string.lockscreen_weather_enabled_info)
                isSmartspaceEnabled -> getString(R.string.lockscreen_weather_smartspace_enabled_info)
                else -> getString(R.string.lockscreen_weather_summary)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePreferencesState()
    }

    override fun getMetricsCategory(): Int {
        return MetricsProto.MetricsEvent.LUNARIS
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        preference.key?.let {
            VibrationUtils.triggerVibration(context, 3)
        }
        return super.onPreferenceTreeClick(preference)
    }
}