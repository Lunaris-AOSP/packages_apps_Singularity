/*
 * Copyright (C) 2025 AxionOS Project
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
package org.lunaris.settings.fragments.miscellaneous

import android.os.Bundle
import android.os.SystemProperties
import android.view.View
import androidx.preference.PreferenceCategory
import com.android.settings.R
import com.android.settings.preferences.BasePreferenceFragment
import org.lunaris.settings.preferences.SecureSettingSeekBarPreference

class Performance : BasePreferenceFragment(R.xml.performance) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val freqProp = SystemProperties.get("persist.sys.ax_max_cpu_freqs", "")
        val maxFreqs = freqProp.split(",").mapNotNull { it.toIntOrNull() }

        val clusters = listOf(
            Cluster("axion_cluster_little", maxFreqs.getOrNull(0) ?: 0,
                "axion_min_freq_boost", "axion_min_freq", "axion_max_freq"),
            Cluster("axion_cluster_big", maxFreqs.getOrNull(1) ?: 0,
                "axion_min_freq_big_boost", "axion_min_freq_big", "axion_max_freq_big"),
            Cluster("axion_cluster_prime", maxFreqs.getOrNull(2) ?: 0,
                "axion_min_freq_prime_boost", "axion_min_freq_prime", "axion_max_freq_prime")
        )

        clusters.forEach { cluster ->
            if (cluster.max > 0) {
                configureCluster(cluster)
            } else {
                findPreference<PreferenceCategory>(cluster.categoryKey)?.let {
                    preferenceScreen.removePreference(it)
                }
            }
        }
        
        configureGpu()
    }

    private fun configureGpu() {
        val freqsPath = SystemProperties.get("persist.sys.axion_gpu_freqs_path", "")
        val minFreqFile = SystemProperties.get("persist.sys.axion_gpu_minfreq_file", "")

        val gpuCategory = findPreference<PreferenceCategory>("axion_gpu")

        if (freqsPath.isNullOrEmpty() || minFreqFile.isNullOrEmpty()) {
            gpuCategory?.let { preferenceScreen.removePreference(it) }
            return
        }

        val maxLevels = SystemProperties.getInt("persist.sys.axion_gpu_levels", 0)
        if (maxLevels <= 0) {
            gpuCategory?.let { preferenceScreen.removePreference(it) }
            return
        }

        findPref("axion_game_gpu_boost_level")?.apply {
            setMax(maxLevels)
            setDefaultValue(1)
        }

        findPref("axion_sys_gpu_boost_level")?.apply {
            setMax(maxLevels)
            setDefaultValue(1)
        }
    }

    private fun configureCluster(cluster: Cluster) {
        findPref(cluster.boostKey)?.apply {
            setMax(cluster.max)
            setDefaultValue(1000000)
        }

        findPref(cluster.userMinKey)?.apply {
            setMax(cluster.max)
            setDefaultValue(0)
        }

        findPref(cluster.userMaxKey)?.apply {
            setMax(cluster.max)
            setDefaultValue(cluster.max)
        }
    }

    private fun findPref(key: String) =
        findPreference<SecureSettingSeekBarPreference>(key)

    private data class Cluster(
        val categoryKey: String,
        val max: Int,
        val boostKey: String,
        val userMinKey: String,
        val userMaxKey: String
    )
}