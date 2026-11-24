package com.example.smarttriviahub;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.DataBindingUtil;

import com.example.smarttriviahub.databinding.ActivityResultBinding;
import com.example.smarttriviahub.databinding.ItemBreakdownBinding;
import com.example.smarttriviahub.model.Question;

import java.util.ArrayList;

/**
 * ACTIVITY 3 – Performance Analytics & Breakdown.
 * Calculates the percentage and grade, lists every question as correct/incorrect
 * inside a ScrollView, and lets the student retake the quiz or start over.
 */
public class ResultActivity extends AppCompatActivity {

    private static final String TAG = "ResultActivity";

    private ActivityResultBinding binding;
    private Bundle resultBundle;

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity is being created");

        EdgeToEdge.enable(this);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_result);
        applySystemBarPadding();

        // ----- Read the Bundle payload sent by QuizActivity -----
        resultBundle = getIntent().getExtras() != null ? getIntent().getExtras() : new Bundle();
        String name = resultBundle.getString(AppConstants.KEY_NAME, "");
        String studentId = resultBundle.getString(AppConstants.KEY_STUDENT_ID, "");
        String programme = resultBundle.getString(AppConstants.KEY_PROGRAMME, "");
        int passedScore = resultBundle.getInt(AppConstants.KEY_SCORE, 0);
        ArrayList<Question> questions =
                (ArrayList<Question>) resultBundle.getSerializable(AppConstants.KEY_QUESTIONS);
        if (questions == null) questions = new ArrayList<>();

        // ----- Calculation logic -----
        int total = questions.size();
        int correct = 0;
        for (Question q : questions) {
            if (q.isCorrect()) correct++;
        }
        int incorrect = total - correct;
        double percentage = calculatePercentage(correct, total);
        String grade = calculateGrade(percentage);

        Log.d(TAG, "Score received = " + passedScore + ", recalculated = " + correct + "/" + total
                + " = " + percentage + "% grade " + grade);

        // ----- Push values into the layout through Data Binding -----
        binding.setStudentLine(getString(R.string.result_student_format, name, studentId, programme));
        binding.setPercentText(getString(R.string.percent_format, percentage));
        binding.setScoreSummary(getString(R.string.score_summary_format, correct, total));
        binding.setCorrectCount(getString(R.string.correct_count_format, correct));
        binding.setWrongCount(getString(R.string.wrong_count_format, incorrect));
        binding.setGradeLabel(getString(R.string.grade_format, grade));
        binding.setGradeMessage(getString(gradeMessageRes(grade)));

        int gradeColor = ContextCompat.getColor(this, gradeColorRes(grade));
        binding.setGradeColor(gradeColor);
        binding.summary.ringScore.setIndicatorColor(gradeColor);
        binding.summary.ringScore.setProgressCompat((int) Math.round(percentage), true);
        binding.summary.imgResult.setImageResource(percentage >= 50 ? R.drawable.ic_trophy : R.drawable.ic_wrong);

        buildBreakdown(questions);

        binding.btnRetake.setOnClickListener(v -> retakeQuiz());
        binding.btnRestart.setOnClickListener(v -> backToRegistration());
    }

    /** percentage = (correct / total) x 100 */
    static double calculatePercentage(int correct, int total) {
        if (total <= 0) return 0;
        return (correct * 100.0) / total;
    }

    /** A >= 80, B >= 60, C >= 40, otherwise F */
    static String calculateGrade(double percentage) {
        if (percentage >= 80) return "A";
        if (percentage >= 60) return "B";
        if (percentage >= 40) return "C";
        return "F";
    }

    private static int gradeMessageRes(String grade) {
        switch (grade) {
            case "A": return R.string.grade_a_msg;
            case "B": return R.string.grade_b_msg;
            case "C": return R.string.grade_c_msg;
            default:  return R.string.grade_f_msg;
        }
    }

    private static int gradeColorRes(String grade) {
        switch (grade) {
            case "A": return R.color.grade_a;
            case "B": return R.color.grade_b;
            case "C": return R.color.grade_c;
            default:  return R.color.grade_f;
        }
    }

    /** Inflates one item_breakdown card per question into the scrollable list. */
    private void buildBreakdown(ArrayList<Question> questions) {
        binding.breakdownContainer.removeAllViews();
        for (int i = 0; i < questions.size(); i++) {
            ItemBreakdownBinding item = ItemBreakdownBinding.inflate(
                    getLayoutInflater(), binding.breakdownContainer, false);
            item.setNumber(i + 1);
            item.setItem(questions.get(i));
            item.executePendingBindings();
            binding.breakdownContainer.addView(item.getRoot());
        }
    }

    /** Start a new quiz with the same student details. */
    private void retakeQuiz() {
        Log.d(TAG, "Retake quiz");
        Bundle bundle = new Bundle();
        bundle.putString(AppConstants.KEY_NAME, resultBundle.getString(AppConstants.KEY_NAME));
        bundle.putString(AppConstants.KEY_STUDENT_ID, resultBundle.getString(AppConstants.KEY_STUDENT_ID));
        bundle.putString(AppConstants.KEY_PROGRAMME, resultBundle.getString(AppConstants.KEY_PROGRAMME));
        bundle.putString(AppConstants.KEY_DIFFICULTY, resultBundle.getString(AppConstants.KEY_DIFFICULTY, "any"));

        Intent intent = new Intent(ResultActivity.this, QuizActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
        finish();
    }

    /** Go back to the existing MainActivity and clear everything above it. */
    private void backToRegistration() {
        Log.d(TAG, "Restart -> MainActivity");
        Intent intent = new Intent(ResultActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void applySystemBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
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
