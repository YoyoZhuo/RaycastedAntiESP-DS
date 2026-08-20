/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.config;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFilterConfigTest {
    @Test
    void blacklistChecksEveryWorldExceptTheListedOnes() throws SerializationException {
        WorldFilterConfig config = WorldFilterConfig.load(node("blacklist", List.of("lobby", "creative")), "checks.worlds");

        assertEquals(WorldFilterMode.BLACKLIST, config.mode());
        assertFalse(config.allows("lobby"));
        assertFalse(config.allows("creative"));
        assertTrue(config.allows("world"));
        assertTrue(config.allows("world_nether"));
    }

    @Test
    void whitelistChecksOnlyTheListedWorlds() throws SerializationException {
        WorldFilterConfig config = WorldFilterConfig.load(node("whitelist", List.of("world", "world_nether")), "checks.worlds");

        assertEquals(WorldFilterMode.WHITELIST, config.mode());
        assertTrue(config.allows("world"));
        assertTrue(config.allows("world_nether"));
        assertFalse(config.allows("lobby"));
    }

    @Test
    void anEmptyBlacklistChecksEveryWorld() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("mode").set("blacklist");

        WorldFilterConfig config = WorldFilterConfig.load(node, "checks.worlds");

        assertTrue(config.worldNames().isEmpty());
        assertTrue(config.allows("world"));
        assertTrue(config.allows("anything-else"));
    }

    @Test
    void anEmptyWhitelistChecksNoWorld() {
        // Constructed directly rather than loaded, because loading this combination logs a warning.
        WorldFilterConfig config = new WorldFilterConfig(WorldFilterMode.WHITELIST, Set.of());

        assertFalse(config.allows("world"));
    }

    @Test
    void theModeIsCaseInsensitive() throws SerializationException {
        assertEquals(WorldFilterMode.BLACKLIST, WorldFilterConfig.load(node("BlackList", List.of()), "checks.worlds").mode());
    }

    @Test
    void anUnknownModeIsRejected() throws SerializationException {
        ConfigurationNode node = node("allow-list", List.of("world"));

        ConfigLoadException thrown = assertThrows(ConfigLoadException.class,
                () -> WorldFilterConfig.load(node, "checks.worlds"));

        assertTrue(thrown.getMessage().contains("checks.worlds.mode"));
    }

    @Test
    void configsWithTheSameWorldsInADifferentOrderAreEqual() throws SerializationException {
        // ConfigManager compares against the start-up config to decide whether a reload needs a restart, so ordering
        // must not be mistaken for a change.
        WorldFilterConfig first = WorldFilterConfig.load(node("blacklist", List.of("lobby", "creative")), "checks.worlds");
        WorldFilterConfig second = WorldFilterConfig.load(node("blacklist", List.of("creative", "lobby")), "checks.worlds");

        assertEquals(first, second);
    }

    @Test
    void addingAWorldMakesConfigsUnequal() throws SerializationException {
        WorldFilterConfig before = WorldFilterConfig.load(node("blacklist", List.of("lobby")), "checks.worlds");
        WorldFilterConfig after = WorldFilterConfig.load(node("blacklist", List.of("lobby", "creative")), "checks.worlds");

        assertNotEquals(before, after);
    }

    private static ConfigurationNode node(String mode, List<String> worldNames) throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("mode").set(mode);
        node.node("list").set(worldNames);
        return node;
    }
}
