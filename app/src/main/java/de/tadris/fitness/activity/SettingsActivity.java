/*
 * Copyright (c) 2020 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.tadris.fitness.activity;

import android.Manifest;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

import de.tadris.fitness.R;
import de.tadris.fitness.export.BackupController;
import de.tadris.fitness.export.RestoreController;
import de.tadris.fitness.recording.announcement.VoiceAnnouncements;
import de.tadris.fitness.util.FileUtils;
import de.tadris.fitness.util.unit.UnitUtils;
import de.tadris.fitness.view.ProgressDialogController;

public class SettingsActivity extends FitoTrackSettingsActivity {

    private final Handler mHandler = new Handler();

        private void handleBackupError(Exception e, ProgressDialogController dialogController, int errorMessage) {
        Log.e("BackupError", "An error occurred during backup", e);
        mHandler.post(() -> {
            dialogController.cancel();
            showErrorDialog(e, R.string.error, errorMessage);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        setTitle(R.string.settings);

        addPreferencesFromResource(R.xml.preferences_main);

        bindPreferenceSummaryToValue(findPreference("unitSystem"));
        bindPreferenceSummaryToValue(findPreference("mapStyle"));
        bindPreferenceSummaryToValue(findPreference("themeSetting"));
        findPreference("themeSetting").setOnPreferenceChangeListener((preference, newValue) -> {
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, newValue);
            Toast.makeText(SettingsActivity.this, R.string.hintRestart, Toast.LENGTH_LONG).show();
            return true;
        });

        findPreference("weight").setOnPreferenceClickListener(preference -> {
            showWeightPicker();
            return true;
        });
        findPreference("speech").setOnPreferenceClickListener(preference -> {
            checkTTSandShowConfig();
            return true;
        });
        findPreference("import").setOnPreferenceClickListener(preference -> {
            showImportDialog();
            return true;
        });
        findPreference("export").setOnPreferenceClickListener(preference -> {
            showExportDialog();
            return true;
        });

    }

    private VoiceAnnouncements voiceAnnouncements;

    private boolean checkAndRequestPermissions() {
        if (!hasPermission()) {
            requestPermissions();
            return false;
        }
        return true;
    }

    private void checkTTSandShowConfig() {
        voiceAnnouncements = new VoiceAnnouncements(this, available -> {
            if (available) {
                showSpeechConfig();
            } else {
                // TextToSpeech is not available
                Toast.makeText(SettingsActivity.this, R.string.ttsNotAvailable, Toast.LENGTH_LONG).show();
            }
            if (voiceAnnouncements != null) {
                voiceAnnouncements.destroy();
            }
        });
    }

    private void showSpeechConfig() {
        startActivity(new Intent(this, VoiceAnnouncementsSettingsActivity.class));
    }

    private void showExportDialog() {

        if (!checkAndRequestPermissions()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(positiveButtonRes, (dialog, which) -> positiveAction.run())
                .create()
                .show();
    }

    private void exportBackup(){
        ProgressDialogController dialogController= new ProgressDialogController(this, getString(R.string.backup));
        dialogController.show();
        new Thread(() -> {
            try{
                String file= getFilesDir().getAbsolutePath() + "/shared/backup.ftb";
                File parent = new File(file).getParentFile();
                if (!parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Cannot write");
                }
                Uri uri= FileProvider.getUriForFile(getBaseContext(), "de.tadris.fitness.fileprovider", new File(file));

                BackupController backupController= new BackupController(getBaseContext(), new File(file), (progress, action) -> mHandler.post(() -> dialogController.setProgress(progress, action)));
                backupController.exportData();

                mHandler.post(() -> {
                    dialogController.cancel();
                    FileUtils.saveOrShareFile(this, uri, "ftb");
                });
            }catch (Exception e){

                handleError(e, dialogController);
            }
        }).start();
    }

    private void showImportDialog() {
        if (!checkAndRequestPermissions()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.importBackup)
                .setMessage(R.string.importBackupMessage)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore, (dialog, which) -> importBackup()).create().show();
    }

    private void requestPermissions() {
        if (!hasPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 10);
        }
    }

    private boolean hasPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private static final int FILE_SELECT_CODE= 21;
    private void importBackup(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.chooseBackupFile)), FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ignored) {
            Toast.makeText(this, "File manager not found", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == RESULT_OK) {
                importBackup(data.getData());
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void importBackup(Uri uri){
        ProgressDialogController dialogController= new ProgressDialogController(this, getString(R.string.backup));
        dialogController.show();
        new Thread(() -> {
            try{
                RestoreController restoreController= new RestoreController(getBaseContext(), uri,
                        (progress, action) -> mHandler.post(() -> dialogController.setProgress(progress, action)));
                restoreController.restoreData();

                mHandler.post(dialogController::cancel);
            }catch (Exception e){
                handleError(e, dialogController);
            }
        }).start();
    }

    private void showWeightPicker() {
        UnitUtils.setUnit(this); // Ensure the correct unit system

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        View v = getLayoutInflater().inflate(R.layout.dialog_weight_picker, null);
        NumberPicker np = v.findViewById(R.id.weightPicker);

        np.setMaxValue((int) UnitUtils.CHOSEN_SYSTEM.getWeightFromKilogram(150));
        np.setMinValue((int) UnitUtils.CHOSEN_SYSTEM.getWeightFromKilogram(20));
        np.setFormatter(value -> value + " " + UnitUtils.CHOSEN_SYSTEM.getWeightUnit());

        final String preferenceVariable = "weight";
        np.setValue((int) Math.round(UnitUtils.CHOSEN_SYSTEM.getWeightFromKilogram(preferences.getInt(preferenceVariable, 80))));
        np.setWrapSelectorWheel(false);

        showNumberPickerDialog(getString(R.string.pref_weight), v, (dialog, which) -> {
            int unitValue = np.getValue();
            int kilograms = (int) Math.round(UnitUtils.CHOSEN_SYSTEM.getKilogramFromUnit(unitValue));
            preferences.edit().putInt(preferenceVariable, kilograms).apply();
        });
    }

    /**
     * Handles errors by logging the exception, canceling the progress dialog,
     * and displaying an error message to the user.
     *
     * @param e The exception that was caught.
     * @param dialogController The progress dialog controller to be canceled.
     */
    private void handleError(Exception e, ProgressDialogController dialogController) {
        e.printStackTrace();
        mHandler.post(() -> {
            dialogController.cancel();
            showErrorDialog(e, R.string.error, R.string.errorExportFailed);
        });
    }

}
