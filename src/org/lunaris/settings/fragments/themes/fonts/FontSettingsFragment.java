/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lunaris.settings.fragments.themes.fonts;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.android.internal.util.lunaris.VibrationUtils;

import org.lunaris.settings.preferences.SystemSettingListPreference;
import org.lunaris.settings.utils.ExternalFontInstaller;

@SearchIndexable
public class FontSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "FontSettingsFragment";

    private static final String KEY_PREBUILT_FONTS = "system_font";
    private static final String KEY_CUSTOM_FONT_PICKER = "custom_font_picker";
    private static final String KEY_GITHUB_FONT_PICKER = "github_font_picker";
    private static final String KEY_CUSTOM_FONT_INFO = "custom_font_info";
    private static final String KEY_RESET_CUSTOM_FONT = "reset_custom_font";
    private static final String KEY_REBOOT_FOR_FONT = "reboot_for_font";
    private static final String KEY_EMOJI_STYLE = "emoji_style";

    private static final String SETTING_CUSTOM_FONT_NAME = "custom_font_name";

    private static final int REQUEST_PICK_FONT = 1001;

    private Preference mPrebuiltFontsPref;
    private Preference mCustomFontPickerPref;
    private Preference mGithubFontPickerPref;
    private Preference mCustomFontInfoPref;
    private Preference mResetCustomFontPref;
    private Preference mRebootForFontPref;
    private SystemSettingListPreference mEmojiStylePref;

    private FontOverlayManager mOverlayManager;
    private ExternalFontInstaller mFontInstaller;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lunaris_settings_fonts);

        mOverlayManager = new FontOverlayManager(requireActivity());
        mFontInstaller  = new ExternalFontInstaller(requireActivity());

        bindPreferences();
        updateUiState();
    }

    private void bindPreferences() {
        mPrebuiltFontsPref = findPreference(KEY_PREBUILT_FONTS);

        mCustomFontPickerPref = findPreference(KEY_CUSTOM_FONT_PICKER);
        mCustomFontPickerPref.setOnPreferenceClickListener(p -> {
            launchFontFilePicker();
            return true;
        });

        mGithubFontPickerPref = findPreference(KEY_GITHUB_FONT_PICKER);
        mGithubFontPickerPref.setOnPreferenceClickListener(p -> {
            openGithubPicker();
            return true;
        });

        mCustomFontInfoPref  = findPreference(KEY_CUSTOM_FONT_INFO);

        mResetCustomFontPref = findPreference(KEY_RESET_CUSTOM_FONT);
        mResetCustomFontPref.setOnPreferenceClickListener(p -> {
            resetCustomFont();
            return true;
        });

        mRebootForFontPref = findPreference(KEY_REBOOT_FOR_FONT);
        mRebootForFontPref.setOnPreferenceClickListener(p -> {
            showRebootDialog();
            return true;
        });

        mEmojiStylePref = findPreference(KEY_EMOJI_STYLE);
        if (mEmojiStylePref != null) {
            mEmojiStylePref.setOnPreferenceChangeListener(this);
        }
    }

    private void updateUiState() {
        String customFontName = getCustomFontName();
        boolean hasCustomFont = customFontName != null && !customFontName.isEmpty();

        mPrebuiltFontsPref.setVisible(true);
        mCustomFontPickerPref.setVisible(true);
        mGithubFontPickerPref.setVisible(true);
        mCustomFontInfoPref.setVisible(hasCustomFont);
        mResetCustomFontPref.setVisible(hasCustomFont);
        mRebootForFontPref.setVisible(hasCustomFont);

        if (hasCustomFont) {
            mCustomFontInfoPref.setSummary(
                    getString(R.string.custom_font_installed_summary, customFontName));
        }
    }

    private String getCustomFontName() {
        return Settings.Secure.getStringForUser(
                requireContext().getContentResolver(),
                SETTING_CUSTOM_FONT_NAME,
                UserHandle.USER_CURRENT);
    }

    private void setCustomFontName(String name) {
        Settings.Secure.putStringForUser(
                requireContext().getContentResolver(),
                SETTING_CUSTOM_FONT_NAME,
                name != null ? name : "",
                UserHandle.USER_CURRENT);
    }

    public void applyPrebuiltOverlay(String overlayPackage) {
        mFontInstaller.resetFontUpdates();
        setCustomFontName("");
        boolean ok = mOverlayManager.applyOverlay(overlayPackage);
        if (!ok) {
            Toast.makeText(requireContext(),
                    R.string.custom_font_install_failed, Toast.LENGTH_SHORT).show();
        }
        updateUiState();
    }

    private void launchFontFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"font/ttf", "font/otf",
                        "application/x-font-ttf", "application/x-font-otf"});
        startActivityForResult(intent, REQUEST_PICK_FONT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FONT
                && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            showPreviewAndInstall(data.getData());
        }
    }

    private void showPreviewAndInstall(Uri fontUri) {
        FontPreviewDialog dialog = new FontPreviewDialog(requireContext(), fontUri);
        dialog.setOnFontInstallListener(new FontPreviewDialog.OnFontInstallListener() {
            @Override public void onInstall(Uri uri) { installCustomFont(uri); }
            @Override public void onCancel() {}
        });
        dialog.show();
    }

    private void installCustomFont(Uri fontUri) {
        new Thread(() -> {
            mFontInstaller.resetFontUpdates();
            String postScriptName = mFontInstaller.installFontFromUri(fontUri);
            requireActivity().runOnUiThread(() -> {
                if (postScriptName != null) {
                    setCustomFontName(postScriptName);
                    updateUiState();
                    Toast.makeText(requireContext(),
                            R.string.custom_font_installed_success,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(),
                            R.string.custom_font_install_failed,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void openGithubPicker() {
        GithubFontPickerDialog dialog = new GithubFontPickerDialog(requireActivity());
        dialog.setOnFontSelectedListener(fontName ->
                requireActivity().runOnUiThread(this::updateUiState));
        dialog.show();
    }

    private void resetCustomFont() {
        new Thread(() -> {
            mFontInstaller.resetFontUpdates();
            setCustomFontName("");
            mOverlayManager.resetToDefault();
            requireActivity().runOnUiThread(() -> {
                updateUiState();
                Toast.makeText(requireContext(),
                        R.string.custom_font_reset_success, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void showRebootDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.reboot_required_title)
                .setMessage(R.string.reboot_required_message)
                .setPositiveButton(R.string.reboot_device,
                        (d, w) -> ExternalFontInstaller.rebootDevice())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(requireContext(), 3);
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mEmojiStylePref != null && preference == mEmojiStylePref) {
            SystemProperties.set("persist.sys.ax_emoji_style", (String) newValue);
            showRebootDialog();
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lunaris_settings_fonts);
}
