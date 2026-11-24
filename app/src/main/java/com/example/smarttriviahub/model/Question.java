package com.example.smarttriviahub.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MODEL (the "M" in MVC).
 * Holds one Pokémon trivia question built from PokeAPI data,
 * plus the answer the student picked. It is Serializable so a whole
 * list of questions can be put inside a Bundle and sent to ResultActivity,
 * or saved in onSaveInstanceState() when the screen rotates.
 */
public class Question implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String category;
    private final String difficulty;
    private final String questionText;
    private final String correctAnswer;
    private final List<String> options;   // 4 shuffled choices
    private String userAnswer;            // null until the student answers

    // Pokémon arena: National Pokédex number used to load the artwork (0 = not a Pokémon question)
    private int pokemonId = 0;

    // Lifeline state for this question (kept here so it survives screen rotation)
    private final List<String> removedOptions = new ArrayList<>();  // hidden by 50:50
    private boolean hintUsed = false;                                // Pokédex Hint shown
    private String revivedWrong;                                     // first wrong pick saved by Revive

    public Question(String category, String difficulty, String questionText,
                    String correctAnswer, List<String> incorrectAnswers) {
        this.category = category;
        this.difficulty = difficulty;
        this.questionText = questionText;
        this.correctAnswer = correctAnswer;

        // Mix the correct answer in with the wrong ones, then shuffle
        // so the correct answer is not always in the same position.
        this.options = new ArrayList<>(incorrectAnswers);
        this.options.add(correctAnswer);
        Collections.shuffle(this.options);
    }

    // ----- Getters (used by Data Binding expressions in XML: @{question.questionText}) -----

    public String getCategory() { return category; }

    /** Difficulty with a capital first letter, e.g. "Easy". */
    public String getDifficulty() {
        if (difficulty == null || difficulty.isEmpty()) return "";
        return Character.toUpperCase(difficulty.charAt(0)) + difficulty.substring(1);
    }

    public String getQuestionText() { return questionText; }

    public String getCorrectAnswer() { return correctAnswer; }

    public List<String> getOptions() { return options; }

    public String getUserAnswer() { return userAnswer; }

    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }

    public boolean isAnswered() { return userAnswer != null; }

    /** True only when the student's answer matches the correct answer. */
    public boolean isCorrect() {
        return userAnswer != null && userAnswer.equals(correctAnswer);
    }

    // ----- Pokémon artwork -----

    public int getPokemonId() { return pokemonId; }

    public void setPokemonId(int pokemonId) { this.pokemonId = pokemonId; }

    /** Used in XML as @{question.pokemon} to show the artwork only for Pokémon questions. */
    public boolean isPokemon() { return pokemonId > 0; }

    // ----- Lifelines -----

    public List<String> getRemovedOptions() { return removedOptions; }

    public boolean isHintUsed() { return hintUsed; }

    public void setHintUsed(boolean hintUsed) { this.hintUsed = hintUsed; }

    public String getRevivedWrong() { return revivedWrong; }

    public void setRevivedWrong(String revivedWrong) { this.revivedWrong = revivedWrong; }
}
