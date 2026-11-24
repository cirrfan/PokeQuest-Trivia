package com.example.smarttriviahub;

/**
 * Keys used inside Intent Bundles so every Activity reads the same names.
 */
public final class AppConstants {

    private AppConstants() { }

    // Student details (MainActivity -> QuizActivity -> ResultActivity)
    public static final String KEY_NAME = "key_student_name";
    public static final String KEY_STUDENT_ID = "key_student_id";
    public static final String KEY_PROGRAMME = "key_programme";
    public static final String KEY_DIFFICULTY = "key_difficulty";

    // Quiz results (QuizActivity -> ResultActivity)
    public static final String KEY_SCORE = "key_score";
    public static final String KEY_TOTAL = "key_total";
    public static final String KEY_QUESTIONS = "key_questions";

    // Web API: questions are built from live PokeAPI data
    public static final int QUESTION_AMOUNT = 10;
    public static final String POKEAPI_SPECIES_URL = "https://pokeapi.co/api/v2/pokemon-species/";
    public static final int MAX_POKEMON_ID = 1025;
    // Official artwork from the PokeAPI sprites repository; %d = National Pokédex number
    public static final String POKEMON_ARTWORK_URL =
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/%d.png";
}
