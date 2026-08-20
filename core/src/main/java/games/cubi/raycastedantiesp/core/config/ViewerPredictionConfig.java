/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.config;

import games.cubi.logs.Logger;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Extrapolates a moving viewer's eye position forward before tracing, so a target becomes visible at the tick the
 * viewer reaches the line of sight rather than the tick after.
 * <p>
 * This trades strictness for responsiveness: a viewer who accelerates towards a corner and then stops is shown
 * targets they never actually got line of sight to.
 */
public record ViewerPredictionConfig(boolean enabled, double minSpeedBlocksPerTick, int ticksAhead) implements Config {
    /**
     * Movement faster than this is treated as a teleport or knockback rather than travel, and is not extrapolated.
     * Sprint-jumping peaks around 0.35 blocks per tick, so this leaves a wide margin.
     */
    public static final double MAX_CREDIBLE_SPEED_BLOCKS_PER_TICK = 2.0;
    /** A sample older than this is too stale to derive a velocity from, for example after a world change. */
    public static final int MAX_SAMPLE_AGE_TICKS = 4;

    public static final double DEFAULT_MIN_SPEED = 0.15;
    public static final int DEFAULT_TICKS_AHEAD = 1;
    public static final int MAX_TICKS_AHEAD = 4;

    public static final ViewerPredictionConfig DISABLED =
            new ViewerPredictionConfig(false, DEFAULT_MIN_SPEED, DEFAULT_TICKS_AHEAD);

    public static ViewerPredictionConfig load(ConfigurationNode node, String path) {
        if (node.virtual() || node.raw() == null && node.childrenMap().isEmpty()) {
            // Introduced after the initial 2.0 config, so an absent block simply means the feature is off.
            return DISABLED;
        }

        boolean enabled = ConfigReader.bool(ConfigReader.node(node, "enabled"), path + ".enabled");

        double minSpeed = ConfigReader.decimal(ConfigReader.node(node, "min-speed-blocks-per-tick"), path + ".min-speed-blocks-per-tick");
        if (minSpeed < 0 || minSpeed > MAX_CREDIBLE_SPEED_BLOCKS_PER_TICK) {
            Logger.warning(path + ".min-speed-blocks-per-tick must be between 0 and " + MAX_CREDIBLE_SPEED_BLOCKS_PER_TICK
                    + " but was " + minSpeed + ". Defaulting to " + DEFAULT_MIN_SPEED + ".", 4, ViewerPredictionConfig.class);
            minSpeed = DEFAULT_MIN_SPEED;
        }

        int ticksAhead = ConfigReader.integer(ConfigReader.node(node, "ticks-ahead"), path + ".ticks-ahead");
        if (ticksAhead < 1 || ticksAhead > MAX_TICKS_AHEAD) {
            Logger.warning(path + ".ticks-ahead must be between 1 and " + MAX_TICKS_AHEAD + " but was " + ticksAhead
                    + ". Defaulting to " + DEFAULT_TICKS_AHEAD + ".", 4, ViewerPredictionConfig.class);
            ticksAhead = DEFAULT_TICKS_AHEAD;
        }

        return new ViewerPredictionConfig(enabled, minSpeed, ticksAhead);
    }
}
