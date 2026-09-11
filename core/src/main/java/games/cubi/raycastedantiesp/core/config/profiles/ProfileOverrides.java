/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.config.profiles;

import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.ConfigReader;
import games.cubi.raycastedantiesp.core.config.raycast.RaycastConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Set;

/**
 * The subset of one check's settings a profile may override, with {@code null} meaning "inherit the global value".
 * <p>
 * Deliberately narrow. Only settings which are read on the viewer's own side of a check can differ per viewer without
 * changing what other viewers are allowed to see, so settings such as {@code keep-client-entity-when-hidden} and
 * {@code hide-on-spawn-distance} stay global even though they are per-check elsewhere.
 */
public record ProfileOverrides(@Nullable Boolean enabled,
                               @Nullable Integer maxOccludingCount,
                               @Nullable Integer alwaysShowRadius,
                               @Nullable Integer raycastRadius,
                               @Nullable Float raycastStepSize) {

    public static final ProfileOverrides NONE = new ProfileOverrides(null, null, null, null, null);

    /** Everything a profile is allowed to say. Anything else in a profile block is reported and ignored. */
    private static final Set<String> OVERRIDABLE_KEYS = Set.of(
            "enabled", "max-occluding-count", "always-show-radius", "raycast-radius", "raycast-step-size");

    /** @return whether this override set would leave the inherited config untouched, so it can be shared as-is. */
    public boolean isEmpty() {
        return enabled == null && maxOccludingCount == null && alwaysShowRadius == null
                && raycastRadius == null && raycastStepSize == null;
    }

    public boolean enabledOr(boolean inherited) {
        return enabled == null ? inherited : enabled;
    }

    public int maxOccludingCountOr(int inherited) {
        return maxOccludingCount == null ? inherited : maxOccludingCount;
    }

    public int alwaysShowRadiusOr(int inherited) {
        return alwaysShowRadius == null ? inherited : alwaysShowRadius;
    }

    public int raycastRadiusOr(int inherited) {
        return raycastRadius == null ? inherited : raycastRadius;
    }

    public float raycastStepSizeOr(float inherited) {
        return raycastStepSize == null ? inherited : raycastStepSize;
    }

    /**
     * Reads one check's override block. Every key is optional, and an absent or empty block yields {@link #NONE}.
     * <p>
     * Out-of-range values are dropped back to the inherited value rather than to the global default, because a
     * profile which silently became stricter than the server-wide setting would be the surprising outcome.
     */
    public static ProfileOverrides load(ConfigurationNode node, String path) {
        if (node.virtual() || node.raw() == null && node.childrenMap().isEmpty()) {
            return NONE;
        }
        warnAboutIgnoredKeys(node, path);
        return new ProfileOverrides(
                optionalBool(node, "enabled", path + ".enabled"),
                optionalRangedInt(node, "max-occluding-count", path + ".max-occluding-count", 0, Byte.MAX_VALUE),
                optionalRangedInt(node, "always-show-radius", path + ".always-show-radius", 0, Short.MAX_VALUE),
                optionalRangedInt(node, "raycast-radius", path + ".raycast-radius", 0, Short.MAX_VALUE),
                optionalStepSize(node, path + ".raycast-step-size")
        );
    }

    private static @Nullable Boolean optionalBool(ConfigurationNode parent, String key, String path) {
        ConfigurationNode node = ConfigReader.node(parent, key);
        return present(node) ? ConfigReader.bool(node, path) : null;
    }

    private static @Nullable Integer optionalRangedInt(ConfigurationNode parent, String key, String path, int min, int max) {
        ConfigurationNode node = ConfigReader.node(parent, key);
        if (!present(node)) {
            return null;
        }
        int value = ConfigReader.integer(node, path);
        if (value < min || value > max) {
            Logger.warning(path + " must be between " + min + " and " + max + " but was " + value
                    + ". Ignoring this override, so the profile inherits the global value.", 4, ProfileOverrides.class);
            return null;
        }
        return value;
    }

    private static @Nullable Float optionalStepSize(ConfigurationNode parent, String path) {
        ConfigurationNode node = ConfigReader.node(parent, "raycast-step-size");
        if (!present(node)) {
            return null;
        }
        double value = ConfigReader.decimal(node, path);
        if (value < RaycastConfig.MIN_STEP_SIZE || value > RaycastConfig.MAX_STEP_SIZE) {
            Logger.warning(path + " must be between " + RaycastConfig.MIN_STEP_SIZE + " and " + RaycastConfig.MAX_STEP_SIZE
                    + " but was " + value + ". Ignoring this override, so the profile inherits the global value.",
                    4, ProfileOverrides.class);
            return null;
        }
        return (float) value;
    }

    /**
     * Reports settings a profile states but cannot change, rather than ignoring them in silence. Writing something
     * like hide-on-spawn-distance into a profile and seeing no effect is otherwise very hard to diagnose.
     */
    private static void warnAboutIgnoredKeys(ConfigurationNode node, String path) {
        for (Object key : node.childrenMap().keySet()) {
            String name = String.valueOf(key);
            if (!OVERRIDABLE_KEYS.contains(name)) {
                Logger.warning(path + "." + name + " is not a setting a profile can override, so it is ignored and"
                        + " the server-wide value is used. A profile may only set " + OVERRIDABLE_KEYS + ".",
                        4, ProfileOverrides.class);
            }
        }
    }

    private static boolean present(ConfigurationNode node) {
        return !node.virtual() && node.raw() != null;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "inherited";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, "enabled", enabled);
        append(sb, "max-occluding-count", maxOccludingCount);
        append(sb, "always-show-radius", alwaysShowRadius);
        append(sb, "raycast-radius", raycastRadius);
        append(sb, "raycast-step-size", raycastStepSize);
        return sb.toString();
    }

    private static void append(StringBuilder sb, String key, @Nullable Object value) {
        if (value == null) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(key).append('=').append(value);
    }
}
