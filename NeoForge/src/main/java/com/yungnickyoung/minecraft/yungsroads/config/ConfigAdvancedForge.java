package com.yungnickyoung.minecraft.yungsroads.config;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.AdvancedSetting;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigAdvancedForge {
    public final ModConfigSpec.ConfigValue<Integer> nodeStepDistance;
    public final ModConfigSpec.ConfigValue<Double> jitterAmount;
    public final ModConfigSpec.ConfigValue<Double> heuristicWeight;
    public final ModConfigSpec.ConfigValue<Double> slopeWeight;
    public final ModConfigSpec.ConfigValue<Double> freeGrade;
    public final ModConfigSpec.ConfigValue<Boolean> straightenRoutes;
    public final ModConfigSpec.ConfigValue<Double> maxGrade;
    public final ModConfigSpec.ConfigValue<Double> waterWeight;
    public final ModConfigSpec.ConfigValue<Integer> maxBridgeLength;
    public final ModConfigSpec.ConfigValue<Integer> smoothingRadius;
    public final ModConfigSpec.ConfigValue<Integer> maxCutDepth;
    public final ModConfigSpec.ConfigValue<Integer> maxFillDepth;
    public final ModConfigSpec.ConfigValue<Integer> maxLandBridgeLength;
    public final ModConfigSpec.ConfigValue<Double> landBridgeEdgeRoughness;
    public final ModConfigSpec.ConfigValue<Double> landBridgeDecay;
    public final ModConfigSpec.ConfigValue<Double> landBridgeSag;
    public final ModConfigSpec.ConfigValue<Integer> landBridgeRailingDrop;
    public final ModConfigSpec.ConfigValue<Integer> landBridgeRailingChance;

    public ConfigAdvancedForge(final ModConfigSpec.Builder BUILDER) {
        BUILDER
                .comment(
                        """
                                ##########################################################################################################
                                # Advanced settings.
                                ##########################################################################################################""")
                .push("Advanced");

        nodeStepDistance = BUILDER
                .comment(
                        """
                                The distance between adjacent pathfinding nodes, in blocks.
                                Lower values follow the terrain more closely, but are slower to generate.
                                Default: 8""".indent(1))
                .worldRestart()
                .defineInRange("Node Step Distance", 8, (int) AdvancedSetting.NODE_STEP_DISTANCE.min, (int) AdvancedSetting.NODE_STEP_DISTANCE.max);

        jitterAmount = BUILDER
                .comment(
                        """
                                The maximum noise-based sideways offset applied to the road's shape, in blocks.
                                Default: 4.0""".indent(1))
                .worldRestart()
                .defineInRange("Jitter Amount", 4.0, AdvancedSetting.JITTER_AMOUNT.min, AdvancedSetting.JITTER_AMOUNT.max);

        heuristicWeight = BUILDER
                .comment(
                        """
                                How strongly pathfinding is pulled toward the destination.
                                1.0 always finds the cheapest road. Higher values generate faster but give slightly less optimal roads.
                                Default: 1.2""".indent(1))
                .worldRestart()
                .defineInRange("Heuristic Weight", 1.2, AdvancedSetting.HEURISTIC_WEIGHT.min, AdvancedSetting.HEURISTIC_WEIGHT.max);

        slopeWeight = BUILDER
                .comment(
                        """
                                The extra cost of steep terrain. Each step's cost is multiplied by
                                (1 + Slope Weight * (grade^2 - Free Grade^2)), where grade is rise over run, so grades up to
                                Free Grade cost nothing extra. Higher values make roads avoid hills and mountains more.
                                Default: 25.0""".indent(1))
                .worldRestart()
                .defineInRange("Slope Weight", 25.0, AdvancedSetting.SLOPE_WEIGHT.min, AdvancedSetting.SLOPE_WEIGHT.max);

        freeGrade = BUILDER
                .comment(
                        """
                                The steepest grade that costs nothing extra to route over. Higher values let roads run
                                straight over gentle rises instead of weaving around them, but weaken the pull toward
                                switchbacks on hillsides. A 1-block rise over one node step is a grade of 0.125 at the
                                default Node Step Distance.
                                Default: 0.15""".indent(1))
                .worldRestart()
                .defineInRange("Free Grade", 0.15, AdvancedSetting.FREE_GRADE.min, AdvancedSetting.FREE_GRADE.max);

        straightenRoutes = BUILDER
                .comment(
                        """
                                Whether each route is straightened after routing, by cutting out nodes the road can bypass
                                in a straight line that costs no more, stays within Max Grade, and doesn't cross water.
                                Default: true""".indent(1))
                .worldRestart()
                .define("Straighten Routes", true);

        maxGrade = BUILDER
                .comment(
                        """
                                The steepest rise over run a road may have. Routing never crosses steeper terrain between
                                two nodes, and where the ground between nodes is steeper, the road cuts or tunnels through it.
                                1.0 is a 45 degree slope.
                                Default: 1.0""".indent(1))
                .worldRestart()
                .defineInRange("Max Grade", 1.0, AdvancedSetting.MAX_GRADE.min, AdvancedSetting.MAX_GRADE.max);

        waterWeight = BUILDER
                .comment(
                        """
                                The extra cost of bridging water. Each bridge's cost multiplier is increased by this amount.
                                Higher values make roads detour further to avoid rivers and lakes, or to find a shorter crossing.
                                Default: 8.0""".indent(1))
                .worldRestart()
                .defineInRange("Water Weight", 8.0, AdvancedSetting.WATER_WEIGHT.min, AdvancedSetting.WATER_WEIGHT.max);

        maxBridgeLength = BUILDER
                .comment(
                        """
                                The longest bridge a road may build, in blocks. Roads cross rivers and lakes only on straight
                                bridges, so wider water must be routed around. 0 disables bridges. Oceans are never crossed.
                                Default: 32""".indent(1))
                .worldRestart()
                .defineInRange("Max Bridge Length", 32, (int) AdvancedSetting.MAX_BRIDGE_LENGTH.min, (int) AdvancedSetting.MAX_BRIDGE_LENGTH.max);

        smoothingRadius = BUILDER
                .comment(
                        """
                                How far along the road its height is averaged, in blocks. Higher values give gentler slopes,
                                with more cutting and filling to level the ground. 0 follows the terrain.
                                Default: 6""".indent(1))
                .worldRestart()
                .defineInRange("Smoothing Radius", 6, (int) AdvancedSetting.SMOOTHING_RADIUS.min, (int) AdvancedSetting.SMOOTHING_RADIUS.max);

        maxCutDepth = BUILDER
                .comment(
                        """
                                The deepest a road may cut into the ground above it to stay level, in blocks.
                                Where the ground rises higher, the road rises too, unless that would be steeper than
                                Max Grade, where it tunnels through instead.
                                Default: 4""".indent(1))
                .worldRestart()
                .defineInRange("Max Cut Depth", 4, (int) AdvancedSetting.MAX_CUT_DEPTH.min, (int) AdvancedSetting.MAX_CUT_DEPTH.max);

        maxFillDepth = BUILDER
                .comment(
                        """
                                The deepest gap under a road that's filled with ground, in blocks.
                                Deeper gaps, such as ravines and cave openings, are crossed on a land bridge instead.
                                Default: 2""".indent(1))
                .worldRestart()
                .defineInRange("Max Fill Depth", 2, (int) AdvancedSetting.MAX_FILL_DEPTH.min, (int) AdvancedSetting.MAX_FILL_DEPTH.max);

        maxLandBridgeLength = BUILDER
                .comment(
                        """
                                The longest dip in the terrain that a road crosses on a land bridge, in blocks, if it's deeper
                                than Max Fill Depth. Longer dips are followed instead. Ravines and caves made by carvers always
                                get a land bridge, since routing can't see them.
                                Default: 24""".indent(1))
                .worldRestart()
                .defineInRange("Max Land Bridge Length", 24, (int) AdvancedSetting.MAX_LAND_BRIDGE_LENGTH.min, (int) AdvancedSetting.MAX_LAND_BRIDGE_LENGTH.max);

        landBridgeEdgeRoughness = BUILDER
                .comment(
                        """
                                How ragged the edges of land bridges are, from 0 to 1. 0 gives straight edges.
                                Higher values move each edge block up to a block in or out.
                                Default: 0.0""".indent(1))
                .worldRestart()
                .defineInRange("Land Bridge Edge Roughness", 0.0, AdvancedSetting.LAND_BRIDGE_EDGE_ROUGHNESS.min, AdvancedSetting.LAND_BRIDGE_EDGE_ROUGHNESS.max);

        landBridgeDecay = BUILDER
                .comment(
                        """
                                How much of each land bridge's deck is missing, for a ruined look, from 0 to 1.
                                Blocks are likelier to be missing toward the edges, and the center line is always kept.
                                0 keeps the whole deck.
                                Default: 0.0""".indent(1))
                .worldRestart()
                .defineInRange("Land Bridge Decay", 0.0, AdvancedSetting.LAND_BRIDGE_DECAY.min, AdvancedSetting.LAND_BRIDGE_DECAY.max);

        landBridgeSag = BUILDER
                .comment(
                        """
                                How far each land bridge's deck sags between its ends, as a fraction of its length, from 0 to 0.25.
                                The sag is limited so the deck is never steeper than Max Grade. 0 gives straight decks.
                                Default: 0.1""".indent(1))
                .worldRestart()
                .defineInRange("Land Bridge Sag", 0.1, AdvancedSetting.LAND_BRIDGE_SAG.min, AdvancedSetting.LAND_BRIDGE_SAG.max);

        landBridgeRailingDrop = BUILDER
                .comment(
                        """
                                The shortest drop beside a land bridge that gets a railing along its edge, in blocks,
                                from the deck down to the ground. Lower values put railings along more of each bridge.
                                Default: 4""".indent(1))
                .worldRestart()
                .defineInRange("Land Bridge Railing Drop", 4, (int) AdvancedSetting.LAND_BRIDGE_RAILING_DROP.min, (int) AdvancedSetting.LAND_BRIDGE_RAILING_DROP.max);

        landBridgeRailingChance = BUILDER
                .comment(
                        """
                                The percent chance that each spot where a land bridge railing could go gets one, from 0 to 100.
                                100 lines every edge with a drop beside it, and 0 places no railings.
                                Default: 100""".indent(1))
                .worldRestart()
                .defineInRange("Land Bridge Railing Chance", 100, (int) AdvancedSetting.LAND_BRIDGE_RAILING_CHANCE.min, (int) AdvancedSetting.LAND_BRIDGE_RAILING_CHANCE.max);

        BUILDER.pop();
    }
}
