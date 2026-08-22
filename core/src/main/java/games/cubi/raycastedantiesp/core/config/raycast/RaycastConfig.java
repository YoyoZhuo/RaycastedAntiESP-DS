/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.config.raycast;

import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.Config;
import games.cubi.raycastedantiesp.core.config.ConfigReader;
import org.spongepowered.configurate.ConfigurationNode;

public class RaycastConfig implements Config {
    /** Matches the fixed one-block sampling the raycast used before the step size became configurable. */
    public static final float DEFAULT_STEP_SIZE = 1.0f;
    public static final float MIN_STEP_SIZE = 0.1f;
    public static final float MAX_STEP_SIZE = 4.0f;

    private final boolean enabled;
    private final boolean hideSoundsWhenHidden;
    private final byte maxOccludingCount;
    private final short alwaysShowRadius;
    private final short raycastRadius;
    private final short hideOnSpawnDistance;
    private final short visibleRecheckIntervalTicks;
    private final boolean keepClientEntityWhenHidden;
    private final float raycastStepSize;
    private final boolean alwaysShowGlowing;

    public RaycastConfig(boolean enabled, boolean hideSoundsWhenHidden, int maxOccludingCount, int alwaysShowRadius,
                         int raycastRadius, int hideOnSpawnDistance, int visibleRecheckIntervalTicks) {
        this(enabled, hideSoundsWhenHidden, maxOccludingCount, alwaysShowRadius, raycastRadius, hideOnSpawnDistance,
                visibleRecheckIntervalTicks, false);
    }

    public RaycastConfig(boolean enabled, boolean hideSoundsWhenHidden, int maxOccludingCount, int alwaysShowRadius,
                         int raycastRadius, int hideOnSpawnDistance, int visibleRecheckIntervalTicks,
                         boolean keepClientEntityWhenHidden) {
        this(enabled, hideSoundsWhenHidden, maxOccludingCount, alwaysShowRadius, raycastRadius, hideOnSpawnDistance,
                visibleRecheckIntervalTicks, keepClientEntityWhenHidden, DEFAULT_STEP_SIZE, true);
    }

    public RaycastConfig(boolean enabled, boolean hideSoundsWhenHidden, int maxOccludingCount, int alwaysShowRadius,
                         int raycastRadius, int hideOnSpawnDistance, int visibleRecheckIntervalTicks,
                         boolean keepClientEntityWhenHidden, double raycastStepSize, boolean alwaysShowGlowing) {
        this.enabled = enabled;
        this.hideSoundsWhenHidden = hideSoundsWhenHidden;
        this.maxOccludingCount = (byte) maxOccludingCount;
        this.alwaysShowRadius = (short) alwaysShowRadius;
        this.raycastRadius = (short) raycastRadius;
        this.hideOnSpawnDistance = (short) hideOnSpawnDistance;
        this.visibleRecheckIntervalTicks = (short) visibleRecheckIntervalTicks;
        this.keepClientEntityWhenHidden = keepClientEntityWhenHidden;
        this.raycastStepSize = (float) raycastStepSize;
        this.alwaysShowGlowing = alwaysShowGlowing;
    }

    protected static RaycastConfig load(ConfigurationNode node, String path, boolean hasHideSoundsWhenHidden) {
        // Blocks cannot glow or be kept client-side, so the tile entity check does not read either option.
        return load(node, path, hasHideSoundsWhenHidden, false, false);
    }

    protected static RaycastConfig load(ConfigurationNode node, String path, boolean hasHideSoundsWhenHidden,
                                        boolean hasKeepClientEntityWhenHidden) {
        return load(node, path, hasHideSoundsWhenHidden, hasKeepClientEntityWhenHidden, true);
    }

    private static RaycastConfig load(ConfigurationNode node, String path, boolean hasHideSoundsWhenHidden,
                                      boolean hasKeepClientEntityWhenHidden, boolean hasAlwaysShowGlowing) {
        int maxOccludingCount = ConfigReader.integer(ConfigReader.node(node, "max-occluding-count"), path + ".max-occluding-count");
        if (maxOccludingCount < 0 || maxOccludingCount > Byte.MAX_VALUE) {
            Logger.warning(path + ".max-occluding-count must be between 0 and " + Byte.MAX_VALUE + " but was " + maxOccludingCount +". Defaulting to 3.", 4, RaycastConfig.class);
            maxOccludingCount = 3;
        }
        int alwaysShowRadius = ConfigReader.integer(ConfigReader.node(node, "always-show-radius"), path + ".always-show-radius");
        if (alwaysShowRadius < 0 || alwaysShowRadius > Short.MAX_VALUE) {
            Logger.warning(path + ".always-show-radius must be between 0 and " + Short.MAX_VALUE + " but was " + alwaysShowRadius +". Defaulting to 8.", 4, RaycastConfig.class);
            alwaysShowRadius = 8;
        }
        int raycastRadius = ConfigReader.integer(ConfigReader.node(node, "raycast-radius"), path + ".raycast-radius");
        if (raycastRadius < 0 || raycastRadius > Short.MAX_VALUE) {
            Logger.warning(path + ".raycast-radius must be between 0 and " + Short.MAX_VALUE + " but was " + raycastRadius +". Defaulting to 48.", 4, RaycastConfig.class);
            raycastRadius = 48;
        }
        int hideOnSpawnDistance = ConfigReader.integer(ConfigReader.node(node, "hide-on-spawn-distance"), path + ".hide-on-spawn-distance");
        if (hideOnSpawnDistance < 0 || hideOnSpawnDistance > Short.MAX_VALUE) {
            Logger.warning(path + ".hide-on-spawn-distance must be between 0 and " + Short.MAX_VALUE + " but was " + hideOnSpawnDistance +". Defaulting to 32.", 4, RaycastConfig.class);
            hideOnSpawnDistance = 32;
        }
        int visibleRecheckIntervalTicks = ConfigReader.integer(ConfigReader.node(node, "visible-recheck-interval-ticks"), path + ".visible-recheck-interval-ticks");
        if (visibleRecheckIntervalTicks < -1 || visibleRecheckIntervalTicks > Short.MAX_VALUE) {
            Logger.warning(path + ".visible-recheck-interval-ticks must be between -1 and " + Short.MAX_VALUE + " but was " + visibleRecheckIntervalTicks +". Defaulting to 5.", 4, RaycastConfig.class);
            visibleRecheckIntervalTicks = 5;
        }
        // The occluding count is measured in samples, so the step size is what gives that threshold its resolution:
        // the ray must cross roughly max-occluding-count * raycast-step-size blocks of occluding material to hide.
        // A finer step lets the threshold sit above one sample, so a ray that only clips a block's corner, and so
        // takes few samples inside it, stops counting as a wall.
        // Introduced after the initial 2.0 config, so an absent value keeps the previous fixed one-block sampling
        // rather than failing the whole load for anyone whose file predates the merge of the new default.
        ConfigurationNode stepSizeNode = ConfigReader.node(node, "raycast-step-size");
        double raycastStepSize = stepSizeNode.virtual() || stepSizeNode.raw() == null
                ? DEFAULT_STEP_SIZE
                : ConfigReader.decimal(stepSizeNode, path + ".raycast-step-size");
        if (raycastStepSize < MIN_STEP_SIZE || raycastStepSize > MAX_STEP_SIZE) {
            Logger.warning(path + ".raycast-step-size must be between " + MIN_STEP_SIZE + " and " + MAX_STEP_SIZE
                    + " but was " + raycastStepSize + ". Defaulting to " + DEFAULT_STEP_SIZE + ".", 4, RaycastConfig.class);
            raycastStepSize = DEFAULT_STEP_SIZE;
        }
        // A glowing entity is one the server has told the client to outline through walls, so hiding it would
        // defeat whatever asked for the outline. Absent means the previous always-show behaviour.
        ConfigurationNode glowingNode = ConfigReader.node(node, "always-show-glowing");
        boolean alwaysShowGlowing = !hasAlwaysShowGlowing
                || glowingNode.virtual() || glowingNode.raw() == null
                || ConfigReader.bool(glowingNode, path + ".always-show-glowing");
        return new RaycastConfig(
                ConfigReader.bool(ConfigReader.node(node, "enabled"), path + ".enabled"),
                hasHideSoundsWhenHidden && ConfigReader.bool(ConfigReader.node(node, "hide-sounds-when-hidden"), path + ".hide-sounds-when-hidden"),
                maxOccludingCount,
                alwaysShowRadius,
                raycastRadius,
                hideOnSpawnDistance,
                visibleRecheckIntervalTicks,
                hasKeepClientEntityWhenHidden && ConfigReader.bool(ConfigReader.node(node, "keep-client-entity-when-hidden"), path + ".keep-client-entity-when-hidden"),
                raycastStepSize,
                alwaysShowGlowing
        );
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean hideSoundsWhenHidden() {
        return hideSoundsWhenHidden;
    }

    public byte getMaxOccludingCount() {
        return maxOccludingCount;
    }

    public short getAlwaysShowRadius() {
        return alwaysShowRadius;
    }

    public short getRaycastRadius() {
        return raycastRadius;
    }

    public short hideOnSpawnDistance() {
        return hideOnSpawnDistance;
    }

    public short getVisibleRecheckIntervalTicks() {
        return visibleRecheckIntervalTicks;
    }

    public boolean keepClientEntityWhenHidden() {
        return keepClientEntityWhenHidden;
    }

    /**
     * @return the distance in blocks between occlusion samples along a ray. Smaller values cost proportionally more
     * to trace but make {@link #getMaxOccludingCount()} a finer-grained threshold.
     */
    public float getRaycastStepSize() {
        return raycastStepSize;
    }

    /**
     * @return whether an entity the server has marked as glowing skips the occlusion check entirely. Turning this off
     * hides glowing entities behind cover like any other, which also removes the outline the glow would have drawn,
     * because a hidden entity is never sent to the client at all.
     */
    public boolean alwaysShowGlowing() {
        return alwaysShowGlowing;
    }
}
