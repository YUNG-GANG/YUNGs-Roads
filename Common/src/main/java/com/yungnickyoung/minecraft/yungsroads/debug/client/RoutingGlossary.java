package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.AdvancedSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Definitions of road generation terms that designers may not know, highlighted wherever they appear in the debug
 * screen's text.
 */
final class RoutingGlossary {
    static final int TERM_COLOR = 0xFFE080;
    static final int VALUE_COLOR = 0x70D8FF;
    private static final int DEFINITION_COLOR = 0xB0B0B0;

    enum Term {
        NODE("Node", "A point on the grid that roads are routed through. Neighboring nodes are Node Step Distance blocks apart.",
                "node", "nodes"),
        STEP("Step", "A move from a node to one of its 16 neighbors: the 8 around it, including diagonals, and the 8 "
                + "a chess knight's move away, which allow smoother headings.",
                "step", "steps"),
        STEP_COST("Step cost", "How much routing charges for one step. Routing picks the route with the lowest total cost, "
                + "so costly terrain is avoided when a cheaper detour exists. A flat step on land costs its run.",
                "step cost", "step's cost"),
        BRIDGE("Bridge", "A straight crossing over water, from a node on one bank to the first dry node on the other. "
                + "Bridges can head the same ways as steps, and their length is measured between the two bank nodes, "
                + "so the shortest possible bridge is twice Node Step Distance. Like steps, bridges can't be steeper than "
                + "Max Grade. Their extra cost per block makes roads take the shortest crossing that's worth it over a detour.",
                "bridge", "bridges", "bridge's"),
        RUN("Run", "The horizontal length of a step or bridge in blocks: Node Step Distance for each lattice move, or "
                + "1.41 or 2.24 times that for diagonal or knight's moves.",
                "run"),
        GRADE("Grade", "How steep a step is: its change in height divided by its run (rise over run). "
                + "0 is flat, 0.5 climbs 1 block every 2 blocks, and 1 is a 45 degree slope. Roads over water are measured at sea level.",
                "grade", "grades"),
        WATER("Water", "Ground below sea level, such as rivers and lakes. Steps never enter it, so roads only cross it on straight bridges at sea level. Oceans are never crossed.",
                "water"),
        PRIORITY("Priority", "Nodes are explored in order of priority, lowest first, until the destination is reached.",
                "priority"),
        COST_SO_FAR("Cost so far", "The total step cost of the cheapest known route from the start to a node.",
                "cost so far"),
        DISTANCE_LEFT("Distance left", "The straight-line distance from a node to the destination, in blocks. "
                + "Weighted by Heuristic Weight: at 1, routing always finds the cheapest road. Higher weights rush toward "
                + "the destination, which generates faster but can give slightly costlier roads.",
                "distance left"),
        JITTER("Jitter", "A noise-based sideways shift applied to each node after routing, so roads look less straight. It doesn't affect cost.",
                "jitter"),
        LAND_BRIDGE("Land bridge", "A crossing over a hole or dip in the ground, such as a ravine or cave opening, built "
                + "from bridge blocks at the height of the road on either side. Routing plans one over each dip in the terrain "
                + "deeper than Max Fill Depth and up to Max Land Bridge Length long. Holes made by carvers, which routing "
                + "can't see, get one wherever the road would be more than Max Fill Depth above the ground.",
                "land bridge", "land bridges");

        final String label;
        final String definition;
        final String[] words;

        Term(String label, String definition, String... words) {
            this.label = label;
            this.definition = definition;
            this.words = words;
        }
    }

    private static final Map<String, Term> TERMS_BY_WORD = new HashMap<>();
    private static final Pattern TERM_PATTERN;

    static {
        for (Term term : Term.values()) {
            for (String word : term.words) {
                TERMS_BY_WORD.put(word, term);
            }
        }
        // Longest first, so "step cost" wins over "step"
        String alternatives = TERMS_BY_WORD.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        TERM_PATTERN = Pattern.compile("\\b(" + alternatives + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    private RoutingGlossary() {
    }

    /** The text with each glossary word highlighted, showing its definition when hovered. */
    static MutableComponent highlight(String text) {
        MutableComponent result = Component.empty();
        Matcher matcher = TERM_PATTERN.matcher(text);
        int end = 0;
        while (matcher.find()) {
            result.append(text.substring(end, matcher.start()));
            result.append(term(TERMS_BY_WORD.get(matcher.group(1).toLowerCase(Locale.ROOT)), matcher.group(1)));
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
        Matcher matcher = TERM_PATTERN.matcher(text);
        while (matcher.find()) {
            terms.add(TERMS_BY_WORD.get(matcher.group(1).toLowerCase(Locale.ROOT)));
        }
        for (Term term : terms) {
            result.append("\n\n")
                    .append(Component.literal(term.label + ": ").withColor(TERM_COLOR))
                    .append(Component.literal(term.definition).withColor(DEFINITION_COLOR));
        }
        return result;
    }

    /** The given text, highlighted as the term and showing its definition when hovered. */
    static MutableComponent term(Term term, String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TERM_COLOR)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(term.label + ": ").withColor(TERM_COLOR)
                        .append(Component.literal(term.definition).withColor(0xFFFFFF)))));
    }

    /** A setting's value, highlighted and showing the setting's description when hovered. */
    static MutableComponent value(AdvancedSetting setting, double value) {
        return Component.literal(setting.format(value)).withStyle(Style.EMPTY.withColor(VALUE_COLOR)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(setting.displayName + ": ").withColor(VALUE_COLOR)
                        .append(Component.literal(setting.description).withColor(0xFFFFFF)))));
    }
}
