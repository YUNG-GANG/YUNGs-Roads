package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.world.config.ITunableSetting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Definitions of road generation terms that designers may not know, highlighted wherever they appear in the debug
 * screen's text.
 * <p>
 * Each term's label, definition, and the words it's highlighted on are lang entries, so the words match the language
 * the text is in. Must only be used on the render thread.
 */
final class RoutingGlossary {
    static final int TERM_COLOR = 0xFFE080;
    static final int VALUE_COLOR = 0x70D8FF;
    private static final int DEFINITION_COLOR = 0xB0B0B0;

    enum Term {
        NODE("node"),
        STEP("step"),
        STEP_COST("step_cost"),
        BRIDGE("bridge"),
        RUN("run"),
        GRADE("grade"),
        WATER("water"),
        SEARCH_PRIORITY("search_priority"),
        COST_SO_FAR("cost_so_far"),
        DISTANCE_LEFT("distance_left"),
        JITTER("jitter"),
        LAND_BRIDGE("land_bridge"),
        ROAD_TYPE("road_type"),
        ROAD_NETWORK("road_network"),
        VARIANT("variant");

        private final String key;

        Term(String id) {
            this.key = "yungsroads.glossary." + id;
        }

        String label() {
            return Language.getInstance().getOrDefault(this.key);
        }

        String definition() {
            return Language.getInstance().getOrDefault(this.key + ".definition");
        }

        /** The words the term is highlighted on, in lower case. */
        List<String> words() {
            return Arrays.stream(Language.getInstance().getOrDefault(this.key + ".words").split(","))
                    .map(word -> word.trim().toLowerCase(Locale.ROOT))
                    .filter(word -> !word.isEmpty())
                    .toList();
        }
    }

    /** The language the words below were read from. Rebuilt when the language changes. */
    private static Language loadedLanguage;
    private static Map<String, Term> termsByWord;
    private static Pattern termPattern;

    private RoutingGlossary() {
    }

    private static void loadWords() {
        Language language = Language.getInstance();
        if (language == loadedLanguage) {
            return;
        }
        termsByWord = new HashMap<>();
        for (Term term : Term.values()) {
            for (String word : term.words()) {
                termsByWord.put(word, term);
            }
        }
        // Longest first, so "step cost" wins over "step"
        String alternatives = termsByWord.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        termPattern = Pattern.compile("\\b(" + alternatives + ")\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        loadedLanguage = language;
    }

    /** The text with each glossary word highlighted, showing its definition when hovered. */
    static MutableComponent highlight(String text) {
        loadWords();
        MutableComponent result = Component.empty();
        Matcher matcher = termPattern.matcher(text);
        int end = 0;
        while (matcher.find()) {
            result.append(text.substring(end, matcher.start()));
            result.append(term(termsByWord.get(matcher.group(1).toLowerCase(Locale.ROOT)), matcher.group(1)));
            end = matcher.end();
        }
        return result.append(text.substring(end));
    }

    /**
     * The text with glossary words highlighted, followed by their definitions. For tooltips, which can't be hovered
     * to show definitions themselves.
     */
    static MutableComponent withDefinitions(String text) {
        MutableComponent result = highlight(text);
        Set<Term> terms = new LinkedHashSet<>();
        Matcher matcher = termPattern.matcher(text);
        while (matcher.find()) {
            terms.add(termsByWord.get(matcher.group(1).toLowerCase(Locale.ROOT)));
        }
        for (Term term : terms) {
            result.append("\n\n").append(definition(term));
        }
        return result;
    }

    /** The term's label and definition, for a list of definitions. */
    static MutableComponent definition(Term term) {
        return Component.literal(term.label() + ": ").withColor(TERM_COLOR)
                .append(Component.literal(term.definition()).withColor(DEFINITION_COLOR));
    }

    /** The given text, highlighted as the term and showing its definition when hovered. */
    static MutableComponent term(Term term, String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TERM_COLOR)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(term.label() + ": ").withColor(TERM_COLOR)
                        .append(Component.literal(term.definition()).withColor(0xFFFFFF)))));
    }

    /** A setting's value, highlighted and showing the setting's description when hovered. */
    static MutableComponent value(ITunableSetting setting, double value) {
        return Component.literal(setting.format(value)).withStyle(Style.EMPTY.withColor(VALUE_COLOR)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(setting.nameKey()).append(": ").withColor(VALUE_COLOR)
                        .append(Component.translatable(setting.descriptionKey()).withColor(0xFFFFFF)))));
    }
}
