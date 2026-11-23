package com.example.smarttriviahub;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.DataBindingUtil;

import com.example.smarttriviahub.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

/**
 * ACTIVITY 1 – User Registration & Entrance.
 * Collects the student's details, validates them, logs all 7 lifecycle
 * callbacks and starts QuizActivity with an explicit Intent + Bundle.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Letters, spaces and common name characters (e.g. "Ahmad bin Ali", "Siti @ Nur"), 3–50 chars
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z .@'/-]{2,49}$");
    // Letters/digits only, 6–12 chars, no spaces
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{6,12}$");

    // Data Binding object – replaces every findViewById() call
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity is being created");

        EdgeToEdge.enable(this);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        applySystemBarPadding();

        setupSpinners();
        setupErrorClearing();

        binding.formFields.btnStart.setOnClickListener(v -> onStartClicked());
    }

    /** Fill the Category and Difficulty spinners from strings.xml arrays. */
    private void setupSpinners() {
        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(
                this, R.array.category_names, R.layout.item_spinner);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.formFields.spinnerCategory.setAdapter(categoryAdapter);

        ArrayAdapter<CharSequence> difficultyAdapter = ArrayAdapter.createFromResource(
                this, R.array.difficulty_names, R.layout.item_spinner);
        difficultyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.formFields.spinnerDifficulty.setAdapter(difficultyAdapter);
    }

    /** Remove a field's red error message as soon as the user starts typing again. */
    private void setupErrorClearing() {
        clearErrorOnType(binding.formFields.tilName);
        clearErrorOnType(binding.formFields.tilStudentId);
        clearErrorOnType(binding.formFields.tilProgramme);
    }

    private void clearErrorOnType(TextInputLayout layout) {
        if (layout.getEditText() == null) return;
        layout.getEditText().addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                layout.setError(null);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void onStartClicked() {
        String name = textOf(binding.formFields.etName.getText());
        String studentId = textOf(binding.formFields.etStudentId.getText()).toUpperCase();
        String programme = textOf(binding.formFields.etProgramme.getText());

        if (!validateInputs(name, studentId, programme)) {
            Log.d(TAG, "Validation failed");
            Snackbar.make(binding.getRoot(), R.string.err_form_summary, Snackbar.LENGTH_SHORT).show();
            return;
        }

        int categoryId = getResources().getIntArray(R.array.category_ids)
                [binding.formFields.spinnerCategory.getSelectedItemPosition()];
        String difficulty = getResources().getStringArray(R.array.difficulty_values)
                [binding.formFields.spinnerDifficulty.getSelectedItemPosition()];

        // Pack the student's details into a Bundle payload
        Bundle bundle = new Bundle();
        bundle.putString(AppConstants.KEY_NAME, name);
        bundle.putString(AppConstants.KEY_STUDENT_ID, studentId);
        bundle.putString(AppConstants.KEY_PROGRAMME, programme);
        bundle.putInt(AppConstants.KEY_CATEGORY_ID, categoryId);
        bundle.putString(AppConstants.KEY_DIFFICULTY, difficulty);

        Log.d(TAG, "Starting QuizActivity for " + name + " (" + studentId + ")"
                + " category=" + categoryId + " difficulty=" + difficulty);

        Toast.makeText(this, getString(R.string.welcome_toast, name), Toast.LENGTH_SHORT).show();

        // Explicit Intent: we name the exact Activity class to open
        Intent intent = new Intent(MainActivity.this, QuizActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    /**
     * Checks every field. Shows the error under each invalid field
     * and returns true only if ALL fields are valid.
     */
    private boolean validateInputs(String name, String studentId, String programme) {
        boolean valid = true;

        if (name.isEmpty()) {
            binding.formFields.tilName.setError(getString(R.string.err_name_empty));
            valid = false;
        } else if (!NAME_PATTERN.matcher(name).matches()) {
            binding.formFields.tilName.setError(getString(R.string.err_name_invalid));
            valid = false;
        }

        if (studentId.isEmpty()) {
            binding.formFields.tilStudentId.setError(getString(R.string.err_id_empty));
            valid = false;
        } else if (!ID_PATTERN.matcher(studentId).matches()) {
            binding.formFields.tilStudentId.setError(getString(R.string.err_id_invalid));
            valid = false;
        }

        if (programme.isEmpty()) {
            binding.formFields.tilProgramme.setError(getString(R.string.err_programme_empty));
            valid = false;
        } else if (programme.length() < 2 || programme.length() > 30) {
            binding.formFields.tilProgramme.setError(getString(R.string.err_programme_invalid));
            valid = false;
        }

        return valid;
    }

    private static String textOf(Editable editable) {
        return editable == null ? "" : editable.toString().trim();
    }

    /** Pads the root view so content is not hidden behind the status/navigation bars. */
    private void applySystemBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    // ===================== The 7 Activity lifecycle callbacks =====================

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: Activity is becoming visible");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Activity is in the foreground and interactive");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Activity is partially hidden / losing focus");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: Activity is no longer visible");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart: Activity is coming back after being stopped");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Activity is being destroyed");
    }
}
