/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.config.profiles;

import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.config.raycast.PlayerConfig;
import games.cubi.raycastedantiesp.core.config.raycast.TileEntityConfig;

/**
 * One viewer-side strictness setting, already resolved against the global config so the engine and packet layers can
 * read it exactly as they read the global one.
 * <p>
 * Profiles only ever change what a viewer is shown. They cannot change what other viewers see of that player, so a
 * loose profile weakens protection for its holder alone.
 *
 * @param priority decides which profile wins when a viewer holds more than one. Higher wins; ties break on name so
 * the outcome never depends on map iteration order.
 */
public record CheckProfile(String name, int priority, String permission,
                           PlayerConfig playerConfig, EntityConfig entityConfig, TileEntityConfig tileEntityConfig) {

    /** The name reported for viewers who hold no profile permission and therefore use the global config. */
    public static final String DEFAULT_NAME = "default";

    public static final String PERMISSION_PREFIX = "raycastedantiesp.profile.";

    public static String permissionFor(String profileName) {
        return PERMISSION_PREFIX + profileName;
    }

    /** @return whether this is the implicit profile built from the global config rather than a configured one. */
    public boolean isDefault() {
        return DEFAULT_NAME.equals(name);
    }

    /**
     * @return whether every check this profile covers resolves to the same config object as {@code other}. Used to
     * skip the switch work when a re-resolution lands on an equivalent profile.
     */
    public boolean sameChecksAs(CheckProfile other) {
        return playerConfig == other.playerConfig
                && entityConfig == other.entityConfig
                && tileEntityConfig == other.tileEntityConfig;
    }

    @Override
    public String toString() {
        return "CheckProfile{" + name + " priority=" + priority
                + " player.enabled=" + playerConfig.enabled()
                + " entity.enabled=" + entityConfig.enabled()
                + " tile-entity.enabled=" + tileEntityConfig.enabled()
                + '}';
    }
}
