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

import java.util.List;
import java.util.Set;

/**
 * Which worlds all checks apply to. World names are the platform's own world names, so resolving them to the world
 * identity used on the hot path is left to the platform module. See
 * {@link games.cubi.raycastedantiesp.core.world.WorldCheckRegistry}.
 */
public record WorldFilterConfig(WorldFilterMode mode, Set<String> worldNames) implements Config {
    public WorldFilterConfig {
        worldNames = Set.copyOf(worldNames);
    }

    public static WorldFilterConfig load(ConfigurationNode node, String path) {
        String modeName = ConfigReader.string(ConfigReader.node(node, "mode"), path + ".mode");
        WorldFilterMode mode = WorldFilterMode.fromString(modeName);
        if (mode == null) {
            throw new ConfigLoadException(path + ".mode has unsupported value '" + modeName
                    + "'. Expected 'blacklist' or 'whitelist'.");
        }

        List<String> worldNames = ConfigReader.stringList(ConfigReader.node(node, "list"), path + ".list");
        if (mode == WorldFilterMode.WHITELIST && worldNames.isEmpty()) {
            Logger.warning(path + ".mode is 'whitelist' but " + path + ".list is empty, so no checks will run in any"
                    + " world. Add world names to the list, or switch the mode to 'blacklist'.", 3, WorldFilterConfig.class);
        }
        return new WorldFilterConfig(mode, Set.copyOf(worldNames));
    }

    /** @return whether checks should run in a world with this name, ignoring whether that world exists. */
    public boolean allows(String worldName) {
        return (mode == WorldFilterMode.WHITELIST) == worldNames.contains(worldName);
    }
}
