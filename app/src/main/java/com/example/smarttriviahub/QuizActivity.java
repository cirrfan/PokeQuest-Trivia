package com.example.smarttriviahub;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.DataBindingUtil;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.smarttriviahub.databinding.ActivityQuizBinding;
import com.example.smarttriviahub.model.PokemonQuestionFactory;
import com.example.smarttriviahub.model.Question;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ACTIVITY 2 – Web-Connected Trivia Quiz Hub.
 * 1. Downloads 10 random Pokémon from the PokeAPI REST API using Volley.
 * 2. Turns each JSONObject response into a Question (PokemonQuestionFactory).
 * 3. Shows each question through Data Binding (ActivityQuizBinding).
 * 4. Handles answer clicks: updates the score, ImageView feedback and Toast/Snackbar popups.
 * 5. Sends the results to ResultActivity in a Bundle.
 */
public class QuizActivity extends AppCompatActivity {

    private static final String TAG = "QuizActivity";
    private static final String REQUEST_TAG = "pokeapi_request";

    // Keys for saving state when the screen rotates
    private static final String STATE_QUESTIONS = "state_questions";
    private static final String STATE_INDEX = "state_index";
    private static final String STATE_LIFELINES = "state_lifelines";

    private ActivityQuizBinding binding;
    private RequestQueue requestQueue;
    private MaterialButton[] optionButtons;

    private ArrayList<Question> questions = new ArrayList<>();
    private int currentIndex = 0;
    private int score = 0;
    private int streak = 0;   // combo: correct answers in a row

    // Lifelines: each one can be used once per game
    private boolean fiftyUsed = false;
    private boolean hintUsed = false;
    private boolean reviveUsed = false;
    private boolean reviveArmed = false;   // Revive switched on, waiting for a wrong answer

    // Pokémon artwork already downloaded, by Pokédex number
    private final Map<Integer, Bitmap> artworkCache = new HashMap<>();
    private final Set<Integer> artworkLoading = new HashSet<>();   // downloads in progress
    private static final int ARTWORK_MAX_SIZE = 360;               // px, smaller bitmap = faster decode

    // Student details received from MainActivity
    private Bundle studentBundle;

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity is being created");

        EdgeToEdge.enable(this);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_quiz);
        applySystemBarPadding();

        // Read the Bundle payload sent by MainActivity
        studentBundle = getIntent().getExtras() != null ? getIntent().getExtras() : new Bundle();
        String name = studentBundle.getString(AppConstants.KEY_NAME, "");
        binding.setGreeting(getString(R.string.quiz_greeting, name));
        Log.d(TAG, "Received student: " + name + " / "
                + studentBundle.getString(AppConstants.KEY_STUDENT_ID));

        optionButtons = new MaterialButton[]{
                binding.btnOption1, binding.btnOption2, binding.btnOption3, binding.btnOption4
        };
        for (int i = 0; i < optionButtons.length; i++) {
            final int index = i;
            optionButtons[i].setOnClickListener(v -> onOptionSelected(index));
        }
        binding.btnNext.setOnClickListener(v -> onNextClicked());
        binding.btnRetry.setOnClickListener(v -> fetchQuestions());
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnFifty.setOnClickListener(v -> useFiftyFifty());
        binding.btnHint.setOnClickListener(v -> useHint());
        binding.btnRevive.setOnClickListener(v -> armRevive());

        setupBackPressConfirmation();

        requestQueue = Volley.newRequestQueue(this);

        // After a rotation we restore the questions instead of downloading new ones
        if (savedInstanceState != null && savedInstanceState.getSerializable(STATE_QUESTIONS) != null) {
            questions = (ArrayList<Question>) savedInstanceState.getSerializable(STATE_QUESTIONS);
            currentIndex = savedInstanceState.getInt(STATE_INDEX, 0);
            score = countCorrect();
            streak = countStreak();
            boolean[] lifelines = savedInstanceState.getBooleanArray(STATE_LIFELINES);
            if (lifelines != null && lifelines.length == 4) {
                fiftyUsed = lifelines[0];
                hintUsed = lifelines[1];
                reviveUsed = lifelines[2];
                reviveArmed = lifelines[3];
            }
            Log.d(TAG, "Restored " + questions.size() + " questions at index " + currentIndex);
            preloadAllArtwork();
            showQuizState();
            renderQuestion();
        } else {
            fetchQuestions();
        }
    }

    // ============================== VOLLEY WEB API ==============================

    /**
     * Sends one Volley GET per Pokémon to PokeAPI
     * (e.g. https://pokeapi.co/api/v2/pokemon-species/150) and builds the
     * 10 questions once every JSONObject has arrived.
     */
    private void fetchQuestions() {
        showLoadingState();
        PokemonQuestionFactory factory = new PokemonQuestionFactory(getResources());
        List<Integer> ids = factory.pickPokemonIds(AppConstants.QUESTION_AMOUNT, AppConstants.MAX_POKEMON_ID);
        JSONObject[] downloaded = new JSONObject[ids.size()];
        int[] received = {0};
        boolean[] failed = {false};
        String difficulty = studentBundle.getString(AppConstants.KEY_DIFFICULTY, "any");

        for (int i = 0; i < ids.size(); i++) {
            final int slot = i;
            String url = AppConstants.POKEAPI_SPECIES_URL + ids.get(i);
            Log.d(TAG, "Volley GET -> " + url);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.GET, url, null,
                    response -> {
                        if (isFinishing() || isDestroyed() || failed[0]) return;
                        downloaded[slot] = response;
                        received[0]++;
                        if (received[0] == downloaded.length) {
                            Log.d(TAG, "All " + downloaded.length + " Pokémon received");
                            handlePokemonResponses(factory, Arrays.asList(downloaded), difficulty);
                        }
                    },
                    error -> {
                        if (isFinishing() || isDestroyed() || failed[0]) return;
                        failed[0] = true;                       // report only the first error
                        requestQueue.cancelAll(REQUEST_TAG);
                        Log.e(TAG, "Volley error (PokeAPI): " + error);
                        showErrorState(getString(mapVolleyError(error)));
                    });
            request.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1f));
            request.setTag(REQUEST_TAG);
            requestQueue.add(request);
        }
    }

    private void handlePokemonResponses(PokemonQuestionFactory factory, List<JSONObject> speciesList,
                                        String difficulty) {
        try {
            questions = factory.buildQuestions(speciesList, difficulty);
            currentIndex = 0;
            score = 0;
            streak = 0;
            resetLifelines();
            Log.d(TAG, "Built " + questions.size() + " Pokémon questions");
            preloadAllArtwork();
            showQuizState();
            renderQuestion();
        } catch (JSONException e) {
            Log.e(TAG, "PokeAPI JSON parsing failed", e);
            showErrorState(getString(R.string.err_parse));
        }
    }

    private static int mapVolleyError(VolleyError error) {
        if (error instanceof NoConnectionError) return R.string.err_no_internet;
        if (error instanceof TimeoutError) return R.string.err_timeout;
        if (error instanceof ServerError) {
            return R.string.err_server;
        }
        if (error instanceof ParseError) return R.string.err_parse;
        return R.string.err_unknown;
    }

    // ============================== SCREEN STATES ==============================
    // These booleans are Data Binding variables; the XML shows/hides views from them.

    private void showLoadingState() {
        binding.setIsLoading(true);
        binding.setHasError(false);
        // Spinning Poké Ball while Volley downloads the questions
        binding.imgLoadingBall.startAnimation(AnimationUtils.loadAnimation(this, R.anim.spin_pokeball));
    }

    private void showErrorState(String message) {
        binding.imgLoadingBall.clearAnimation();
        binding.setIsLoading(false);
        binding.setHasError(true);
        binding.setErrorMessage(message);
    }

    private void showQuizState() {
        binding.imgLoadingBall.clearAnimation();
        binding.setIsLoading(false);
        binding.setHasError(false);
    }

    // ============================== QUIZ LOGIC ==============================

    /** Pushes the current question into the layout through Data Binding. */
    private void renderQuestion() {
        Question q = questions.get(currentIndex);
        int total = questions.size();

        binding.setQuestion(q);
        binding.setProgressLabel(getString(R.string.progress_format, currentIndex + 1, total));
        binding.setScoreLabel(getString(R.string.score_format, score));
        binding.setIsLastQuestion(currentIndex == total - 1);
        binding.setAnswered(q.isAnswered());

        binding.progressQuiz.setMax(total);
        binding.progressQuiz.setProgressCompat(currentIndex + 1, true);

        resetOptionStyles();
        if (q.isAnswered()) {
            // Question was already answered before a rotation – redraw feedback without popups
            applyFeedback(q, false);
        } else {
            // Re-apply lifelines already used on this question (e.g. after a rotation)
            for (int i = 0; i < optionButtons.length; i++) {
                String option = q.getOptions().get(i);
                if (q.getRemovedOptions().contains(option)) {
                    hideOption(optionButtons[i]);
                } else if (option.equals(q.getRevivedWrong())) {
                    styleOption(optionButtons[i], R.color.wrong_light, R.color.wrong);
                    optionButtons[i].setEnabled(false);
                }
            }
        }
        binding.setHintMessage(q.isHintUsed() ? buildHintMessage(q) : null);
        updateLifelineBindings();
        loadPokemonArtwork(q);
        binding.scrollQuiz.scrollTo(0, 0);
        Log.d(TAG, "Showing question " + (currentIndex + 1) + "/" + total);
    }

    /** Called when the student taps one of the 4 answer buttons. */
    private void onOptionSelected(int optionIndex) {
        Question q = questions.get(currentIndex);
        if (q.isAnswered()) return;               // Only one answer per question

        String chosen = q.getOptions().get(optionIndex);

        // Revive: the first wrong pick is forgiven and the player tries again
        if (reviveArmed && !chosen.equals(q.getCorrectAnswer())) {
            reviveArmed = false;
            reviveUsed = true;
            q.setRevivedWrong(chosen);
            styleOption(optionButtons[optionIndex], R.color.wrong_light, R.color.wrong);
            optionButtons[optionIndex].setEnabled(false);
            updateLifelineBindings();
            Snackbar.make(binding.getRoot(), getString(R.string.snack_revive_used, chosen), Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(ContextCompat.getColor(this, R.color.primary_dark))
                    .setTextColor(ContextCompat.getColor(this, R.color.white))
                    .setAnchorView(binding.btnNext)
                    .show();
            Log.d(TAG, "Q" + (currentIndex + 1) + " Revive used on '" + chosen + "'");
            return;
        }

        q.setUserAnswer(chosen);

        if (q.isCorrect()) {
            score++;
            streak++;
        } else {
            streak = 0;                           // a miss breaks the combo
        }
        Log.d(TAG, "Q" + (currentIndex + 1) + " chose '" + chosen + "' -> "
                + (q.isCorrect() ? "CORRECT" : "WRONG") + ", score = " + score + ", combo = " + streak);

        binding.setScoreLabel(getString(R.string.score_format, score));
        binding.setAnswered(true);
        applyFeedback(q, true);
        revealPokemon(true);
    }

    // ============================== LIFELINES ==============================

    /** ½ 50:50 – removes two wrong answers (keeps at least one wrong answer on screen). */
    private void useFiftyFifty() {
        Question q = questions.get(currentIndex);
        if (fiftyUsed || q.isAnswered()) return;

        List<Integer> wrongIndexes = new ArrayList<>();
        for (int i = 0; i < optionButtons.length; i++) {
            String option = q.getOptions().get(i);
            if (!option.equals(q.getCorrectAnswer()) && !option.equals(q.getRevivedWrong())) {
                wrongIndexes.add(i);
            }
        }
        Collections.shuffle(wrongIndexes);
        int toRemove = Math.min(2, wrongIndexes.size() - 1);   // always leave one wrong option
        for (int i = 0; i < toRemove; i++) {
            int index = wrongIndexes.get(i);
            q.getRemovedOptions().add(q.getOptions().get(index));
            hideOption(optionButtons[index]);
        }
        fiftyUsed = true;
        updateLifelineBindings();
        Toast.makeText(this, R.string.toast_fifty, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Q" + (currentIndex + 1) + " 50:50 removed " + q.getRemovedOptions());
    }

    /** 💡 Pokédex Hint – shows the first letter and length of the correct answer. */
    private void useHint() {
        Question q = questions.get(currentIndex);
        if (hintUsed || q.isAnswered()) return;
        hintUsed = true;
        q.setHintUsed(true);
        binding.setHintMessage(buildHintMessage(q));
        updateLifelineBindings();
        Log.d(TAG, "Q" + (currentIndex + 1) + " hint shown");
    }

    /** ❤ Revive – switches on a second chance for the next wrong answer. */
    private void armRevive() {
        if (reviveUsed || reviveArmed || questions.get(currentIndex).isAnswered()) return;
        reviveArmed = true;
        updateLifelineBindings();
        Toast.makeText(this, R.string.toast_revive_armed, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Revive armed");
    }

    private String buildHintMessage(Question q) {
        String answer = q.getCorrectAnswer();
        String first = answer.substring(0, 1).toUpperCase(Locale.ROOT);
        return getString(R.string.hint_format, first, answer.length());
    }

    /** Pushes the lifeline state into the Data Binding variables (enables/disables the buttons). */
    private void updateLifelineBindings() {
        binding.setFiftyAvailable(!fiftyUsed);
        binding.setHintAvailable(!hintUsed);
        binding.setReviveAvailable(!reviveUsed);
        binding.setReviveArmed(reviveArmed);
    }

    private void resetLifelines() {
        fiftyUsed = false;
        hintUsed = false;
        reviveUsed = false;
        reviveArmed = false;
    }

    private void hideOption(MaterialButton button) {
        button.setEnabled(false);
        button.setAlpha(0.3f);
    }

    // ============================== POKÉMON ARTWORK ==============================

    /** Shows the current question's artwork as a silhouette (downloads it first if needed). */
    private void loadPokemonArtwork(Question q) {
        binding.questionBody.imgPokemon.setImageDrawable(null);
        if (!q.isPokemon()) return;

        Bitmap cached = artworkCache.get(q.getPokemonId());
        if (cached != null) {
            showArtwork(cached);
        } else {
            requestArtwork(q.getPokemonId());   // appears as soon as the download finishes
        }
    }

    /** Starts downloading the artwork of all 10 Pokémon at once, so the next silhouette is ready instantly. */
    private void preloadAllArtwork() {
        for (Question q : questions) {
            if (q.isPokemon()) requestArtwork(q.getPokemonId());
        }
    }

    /** Volley ImageRequest for one Pokémon's official artwork (skipped if cached or already downloading). */
    private void requestArtwork(int id) {
        if (artworkCache.containsKey(id) || artworkLoading.contains(id)) return;
        artworkLoading.add(id);

        String url = String.format(Locale.ROOT, AppConstants.POKEMON_ARTWORK_URL, id);
        ImageRequest request = new ImageRequest(url,
                bitmap -> {
                    artworkLoading.remove(id);
                    if (isFinishing() || isDestroyed()) return;
                    artworkCache.put(id, bitmap);
                    // Only draw it if the player is on this Pokémon's question right now
                    if (questions.get(currentIndex).getPokemonId() == id) showArtwork(bitmap);
                },
                ARTWORK_MAX_SIZE, ARTWORK_MAX_SIZE, ImageView.ScaleType.FIT_CENTER, Bitmap.Config.ARGB_8888,
                error -> {
                    artworkLoading.remove(id);
                    Log.w(TAG, "Artwork download failed for #" + id + ": " + error);
                });
        request.setTag(REQUEST_TAG);
        requestQueue.add(request);
    }

    private void showArtwork(Bitmap bitmap) {
        binding.questionBody.imgPokemon.setImageBitmap(bitmap);
        revealPokemon(false);
    }

    /** "Who's that Pokémon?" – black silhouette until answered, then shown in full colour. */
    private void revealPokemon(boolean animate) {
        ImageView image = binding.questionBody.imgPokemon;
        Question q = questions.get(currentIndex);
        if (!q.isPokemon() || !artworkCache.containsKey(q.getPokemonId())) return;

        if (!q.isAnswered()) {
            image.setColorFilter(ContextCompat.getColor(this, R.color.silhouette), PorterDuff.Mode.SRC_IN);
            return;
        }
        image.clearColorFilter();
        if (animate) {
            image.setScaleX(0.6f);
            image.setScaleY(0.6f);
            image.animate().scaleX(1f).scaleY(1f).setDuration(350).start();
        }
    }

    /** Colours the buttons, swaps the ImageView graphic and (optionally) shows a popup. */
    private void applyFeedback(Question q, boolean showPopup) {
        List<String> options = q.getOptions();
        for (int i = 0; i < optionButtons.length; i++) {
            String option = options.get(i);
            if (option.equals(q.getCorrectAnswer())) {
                styleOption(optionButtons[i], R.color.correct_light, R.color.correct);
            } else if (option.equals(q.getUserAnswer())) {
                styleOption(optionButtons[i], R.color.wrong_light, R.color.wrong);
            }
        }

        // ImageView feedback: tick for correct, cross for wrong
        binding.questionBody.imgFeedback.setImageResource(q.isCorrect() ? R.drawable.ic_correct : R.drawable.ic_wrong);
        binding.questionBody.imgFeedback.setScaleX(0.5f);
        binding.questionBody.imgFeedback.setScaleY(0.5f);
        binding.questionBody.imgFeedback.animate().scaleX(1f).scaleY(1f).setDuration(250).start();

        if (!showPopup) return;

        if (q.isCorrect()) {
            // 2 or more correct in a row shows a combo popup instead of the normal one
            String message = streak >= 2
                    ? getString(R.string.combo_toast, streak)
                    : getString(R.string.toast_correct);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else {
            Snackbar.make(binding.getRoot(),
                            getString(R.string.snack_wrong, q.getCorrectAnswer()),
                            Snackbar.LENGTH_LONG)
                    .setBackgroundTint(ContextCompat.getColor(this, R.color.wrong))
                    .setTextColor(ContextCompat.getColor(this, R.color.white))
                    .setAnchorView(binding.btnNext)
                    .show();
        }
    }

    private void resetOptionStyles() {
        for (MaterialButton button : optionButtons) {
            styleOption(button, R.color.option_default, R.color.option_stroke);
            button.setEnabled(true);
            button.setAlpha(1f);
        }
        binding.questionBody.imgFeedback.setImageResource(R.drawable.ic_question);
    }

    private void styleOption(MaterialButton button, @ColorRes int fill, @ColorRes int stroke) {
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, fill)));
        button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, stroke)));
    }

    private void onNextClicked() {
        if (currentIndex < questions.size() - 1) {
            currentIndex++;
            renderQuestion();
        } else {
            goToResults();
        }
    }

    private int countCorrect() {
        int count = 0;
        for (Question q : questions) {
            if (q.isCorrect()) count++;
        }
        return count;
    }

    /** Rebuilds the combo after a rotation: correct answers in a row, counting back from the last answered question. */
    private int countStreak() {
        int count = 0;
        for (int i = questions.size() - 1; i >= 0; i--) {
            Question q = questions.get(i);
            if (!q.isAnswered()) continue;
            if (!q.isCorrect()) break;
            count++;
        }
        return count;
    }

    /** Sends the student details + all answered questions to ResultActivity. */
    private void goToResults() {
        Bundle bundle = new Bundle(studentBundle);      // name, ID, programme, category, difficulty
        bundle.putInt(AppConstants.KEY_SCORE, score);
        bundle.putInt(AppConstants.KEY_TOTAL, questions.size());
        bundle.putSerializable(AppConstants.KEY_QUESTIONS, questions);

        Log.d(TAG, "Quiz finished. Score " + score + "/" + questions.size() + " -> ResultActivity");

        Intent intent = new Intent(QuizActivity.this, ResultActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
        finish();   // Remove the quiz from the back stack so Back does not return to it
    }

    /** Ask before leaving a quiz that is in progress. */
    private void setupBackPressConfirmation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (questions.isEmpty()) {
                    finish();
                    return;
                }
                new MaterialAlertDialogBuilder(QuizActivity.this)
                        .setTitle(R.string.quit_title)
                        .setMessage(R.string.quit_message)
                        .setPositiveButton(R.string.quit_yes, (d, w) -> finish())
                        .setNegativeButton(R.string.quit_no, null)
                        .show();
            }
        });
    }

    private void applySystemBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /** Save the questions and position so rotating the phone does not restart the quiz. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!questions.isEmpty()) {
            outState.putSerializable(STATE_QUESTIONS, questions);
            outState.putInt(STATE_INDEX, currentIndex);
            outState.putBooleanArray(STATE_LIFELINES,
                    new boolean[]{fiftyUsed, hintUsed, reviveUsed, reviveArmed});
            Log.d(TAG, "onSaveInstanceState: saved quiz at index " + currentIndex);
        }
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
        // Cancel any download still running so it cannot call back into a dead Activity
        if (requestQueue != null) {
            requestQueue.cancelAll(REQUEST_TAG);
        }
        Log.d(TAG, "onDestroy: Activity is being destroyed");
    }
}
