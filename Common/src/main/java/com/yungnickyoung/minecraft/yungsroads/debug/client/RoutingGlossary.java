package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.GlobalSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.ITunableSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSetting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Definitions of road generation terms that designers may not know, highlighted wherever they appear in the debug
 * screen's text.
 * <p>
 * Setting names, such as Max Grade, are highlighted as a whole and show the setting's description, so the terms within
 * them, such as grade, aren't highlighted as if they stood alone.
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
        VARIANT("variant"),
        REGION("region"),
        SURFACE("surface");

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
    /** Every setting, by its name as written in the text. */
    private static Map<String, ITunableSetting> settingsByName;
    /** Matches a setting name in group 1, or a term's word in group 2. */
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
        settingsByName = new HashMap<>();
        Stream.<ITunableSetting>concat(Arrays.stream(RoadSetting.values()), Arrays.stream(GlobalSetting.values()))
                .forEach(setting -> settingsByName.put(language.getOrDefault(setting.nameKey()), setting));
        // Setting names come first, so a match starting at the same word takes the whole name. They match case
        // sensitively, so the same words in ordinary text, such as "water weight", are left to the terms.
        termPattern = Pattern.compile("\\b(?:((?-i:" + alternatives(settingsByName.keySet()) + "))|(" + alternatives(termsByWord.keySet()) + "))\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        loadedLanguage = language;
    }

    /** The words as regex alternatives, longest first, so "step cost" wins over "step". */
    private static String alternatives(Collection<String> words) {
        return words.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
    }

    /**
     * The text with each glossary word highlighted, showing its definition when hovered, and each setting name
     * highlighted, showing the setting's description.
     */
    static MutableComponent highlight(String text) {
        loadWords();
        MutableComponent result = Component.empty();
        Matcher matcher = termPattern.matcher(text);
        int end = 0;
        while (matcher.find()) {
            result.append(text.substring(end, matcher.start()));
            if (matcher.group(1) != null) {
                result.append(settingName(settingsByName.get(matcher.group(1)), matcher.group(1)));
            } else {
                result.append(term(termsByWord.get(matcher.group(2).toLowerCase(Locale.ROOT)), matcher.group(2)));
            }
            end = matcher.end();
        }
        return result.append(text.substring(end));
    }

    /**
     * The text with glossary words highlighted, followed by their definitions in the order the words appear. For
     * tooltips, which can't be hovered to show definitions themselves.
     *
     * @param fits Whether the text with the definitions added so far still fits. Definitions after the first that
     *             doesn't fit are left out, since the text and its first definitions matter most.
     */
    static MutableComponent withDefinitions(String text, Predicate<Component> fits) {
        MutableComponent result = highlight(text);
        // Only terms are defined. Setting names are left out, since each has a row of its own to hover.
        Set<Term> terms = new LinkedHashSet<>();
        Matcher matcher = termPattern.matcher(text);
        while (matcher.find()) {
            if (matcher.group(2) != null) {
                terms.add(termsByWord.get(matcher.group(2).toLowerCase(Locale.ROOT)));
            }
        }
        for (Term term : terms) {
            MutableComponent extended = result.copy().append("\n\n").append(definition(term));
            if (!fits.test(extended)) {
                break;
            }
            result = extended;
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
        return settingName(setting, setting.format(value));
    }

    /** The given text, highlighted as a setting and showing its description when hovered. */
    private static MutableComponent settingName(ITunableSetting setting, String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(VALUE_COLOR)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(setting.nameKey()).append(": ").withColor(VALUE_COLOR)
                        .append(Component.translatable(setting.descriptionKey()).withColor(0xFFFFFF)))));
    }
}
