package com.example.smarttriviahub.model;

import android.content.res.Resources;

import com.example.smarttriviahub.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Turns live Pokémon data from PokeAPI (https://pokeapi.co/api/v2/pokemon-species/{id})
 * into multiple-choice {@link Question} objects.
 *
 * Every question type reads a different field of the JSONObject:
 *   - Pokédex entry  -> flavor_text_entries (JSONArray)   "Who's that Pokémon?"
 *   - Status         -> is_legendary / is_mythical / is_baby
 *   - Generation     -> generation.name
 *   - Colour         -> color.name
 */
public class PokemonQuestionFactory {

    private static final int TYPE_DEX = 0;
    private static final int TYPE_STATUS = 1;
    private static final int TYPE_GENERATION = 2;
    private static final int TYPE_COLOR = 3;

    private final Resources res;
    private final Random random = new Random();

    public PokemonQuestionFactory(Resources res) {
        this.res = res;
    }

    /** Picks distinct Pokédex numbers: half from the Legendary/Mythical list, the rest at random. */
    public List<Integer> pickPokemonIds(int amount, int maxId) {
        List<Integer> legendary = new ArrayList<>();
        for (int id : res.getIntArray(R.array.legendary_ids)) legendary.add(id);
        Collections.shuffle(legendary, random);

        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < amount / 2 && i < legendary.size(); i++) {
            ids.add(legendary.get(i));
        }
        while (ids.size() < amount) {
            int id = 1 + random.nextInt(maxId);
            if (!ids.contains(id)) ids.add(id);
        }
        Collections.shuffle(ids, random);
        return ids;
    }

    /**
     * Builds one question per species.
     * @param speciesList the JSON objects downloaded from PokeAPI
     * @param difficulty  "any", "easy", "medium" or "hard" from the Game Mode spinner
     */
    public ArrayList<Question> buildQuestions(List<JSONObject> speciesList, String difficulty)
            throws JSONException {
        // English names of all downloaded Pokémon, used as wrong options for "Who's that Pokémon?"
        List<String> allNames = new ArrayList<>();
        for (JSONObject species : speciesList) allNames.add(englishName(species));

        ArrayList<Question> questions = new ArrayList<>();
        for (int i = 0; i < speciesList.size(); i++) {
            JSONObject species = speciesList.get(i);
            int type = pickType(difficulty, species);
            Question question = buildQuestion(type, species, allNames.get(i), allNames);
            question.setPokemonId(species.getInt("id"));   // used to load the official artwork
            questions.add(question);
        }
        return questions;
    }

    private int pickType(String difficulty, JSONObject species) {
        int[] allowed;
        switch (difficulty) {
            case "easy":   allowed = new int[]{TYPE_STATUS, TYPE_COLOR}; break;
            case "medium": allowed = new int[]{TYPE_DEX, TYPE_STATUS}; break;
            case "hard":   allowed = new int[]{TYPE_DEX, TYPE_GENERATION}; break;
            default:       allowed = new int[]{TYPE_DEX, TYPE_STATUS, TYPE_GENERATION, TYPE_COLOR};
        }
        // Legendary and Mythical Pokémon get the status question more often (fun fact focus)
        if (isSpecial(species) && !"hard".equals(difficulty) && random.nextBoolean()) {
            return TYPE_STATUS;
        }
        int type = allowed[random.nextInt(allowed.length)];
        // Some newer Pokémon have no English Pokédex text yet – fall back to another type
        if (type == TYPE_DEX && englishFlavorText(species) == null) {
            type = TYPE_GENERATION;
        }
        return type;
    }

    private Question buildQuestion(int type, JSONObject species, String name, List<String> allNames)
            throws JSONException {
        String category = res.getString(R.string.poke_category);

        switch (type) {
            case TYPE_DEX: {
                String text = hideName(englishFlavorText(species), name);
                List<String> others = new ArrayList<>(allNames);
                others.remove(name);
                return new Question(category, "medium",
                        res.getString(R.string.poke_q_dex, text), name, pickWrong(others, name));
            }
            case TYPE_STATUS: {
                List<String> statuses = Arrays.asList(res.getStringArray(R.array.poke_status_names));
                String correct;
                if (species.getBoolean("is_legendary")) correct = statuses.get(0);
                else if (species.getBoolean("is_mythical")) correct = statuses.get(1);
                else if (species.getBoolean("is_baby")) correct = statuses.get(2);
                else correct = statuses.get(3);
                return new Question(category, "easy",
                        res.getString(R.string.poke_q_status, name), correct, pickWrong(statuses, correct));
            }
            case TYPE_GENERATION: {
                String value = species.getJSONObject("generation").getString("name");
                String correct = lookup(value, R.array.poke_generation_values, R.array.poke_generation_names);
                List<String> gens = Arrays.asList(res.getStringArray(R.array.poke_generation_names));
                return new Question(category, "hard",
                        res.getString(R.string.poke_q_generation, name), correct, pickWrong(gens, correct));
            }
            default: {
                String value = species.getJSONObject("color").getString("name");
                String correct = lookup(value, R.array.poke_color_values, R.array.poke_color_names);
                List<String> colors = Arrays.asList(res.getStringArray(R.array.poke_color_names));
                return new Question(category, "easy",
                        res.getString(R.string.poke_q_color, name), correct, pickWrong(colors, correct));
            }
        }
    }

    // ------------------------------ JSON helpers ------------------------------

    private static boolean isSpecial(JSONObject species) {
        return species.optBoolean("is_legendary") || species.optBoolean("is_mythical");
    }

    /** Reads names[] and returns the English name, e.g. "Mr. Mime". */
    private static String englishName(JSONObject species) throws JSONException {
        JSONArray names = species.getJSONArray("names");
        for (int i = 0; i < names.length(); i++) {
            JSONObject entry = names.getJSONObject(i);
            if ("en".equals(entry.getJSONObject("language").getString("name"))) {
                return entry.getString("name");
            }
        }
        String slug = species.getString("name");
        return slug.substring(0, 1).toUpperCase(Locale.ROOT) + slug.substring(1);
    }

    /** Returns a random English Pokédex entry, or null if there is none. */
    private String englishFlavorText(JSONObject species) {
        JSONArray entries = species.optJSONArray("flavor_text_entries");
        if (entries == null) return null;
        List<String> english = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry != null && "en".equals(entry.optJSONObject("language").optString("name"))) {
                // PokeAPI text contains line breaks and form-feed characters from the games
                english.add(entry.optString("flavor_text").replaceAll("[\\n\\f\\r\\u00AD]", " ")
                        .replaceAll("\\s+", " ").trim());
            }
        }
        return english.isEmpty() ? null : english.get(random.nextInt(english.size()));
    }

    /** Replaces the Pokémon's name in the Pokédex text with "???" so the answer is not given away. */
    private String hideName(String text, String name) {
        String hidden = res.getString(R.string.poke_hidden_name);
        return Pattern.compile(Pattern.quote(name), Pattern.CASE_INSENSITIVE)
                .matcher(text).replaceAll(hidden);
    }

    /** Maps an API value (e.g. "generation-iv") to its display text using two parallel string arrays. */
    private String lookup(String apiValue, int valuesRes, int namesRes) {
        String[] values = res.getStringArray(valuesRes);
        String[] names = res.getStringArray(namesRes);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(apiValue)) return names[i];
        }
        return apiValue;
    }

    /** Picks 3 random wrong options from the pool, never the correct answer. */
    private List<String> pickWrong(List<String> pool, String correct) {
        List<String> candidates = new ArrayList<>();
        for (String option : pool) {
            if (!option.equals(correct) && !candidates.contains(option)) candidates.add(option);
        }
        Collections.shuffle(candidates, random);
        return new ArrayList<>(candidates.subList(0, Math.min(3, candidates.size())));
    }
}
