/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.config.raycast;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaycastConfigTest {
    @Test
    void playerAndEntityRetentionSettingsLoadIndependently() throws SerializationException {
        PlayerConfig playerConfig = PlayerConfig.load(entityNode(true), "checks.player");
        EntityConfig entityConfig = EntityConfig.load(entityNode(false), "checks.entity");

        assertTrue(playerConfig.keepClientEntityWhenHidden());
        assertFalse(playerConfig.onlyCheckSneaking());
        assertFalse(entityConfig.keepClientEntityWhenHidden());
    }

    @Test
    void playerSneakingOnlySettingLoadsIndependently() throws SerializationException {
        ConfigurationNode node = entityNode(true);
        node.node("only-check-sneaking").set(true);

        PlayerConfig playerConfig = PlayerConfig.load(node, "checks.player");

        assertTrue(playerConfig.onlyCheckSneaking());
    }

    @Test
    void tileEntityConfigDoesNotRequireOrEnableClientRetention() throws SerializationException {
        TileEntityConfig config = TileEntityConfig.load(baseNode(), "checks.tile-entity");

        assertFalse(config.keepClientEntityWhenHidden());
    }

    @Test
    void legacyConstructorKeepsDestroyBehaviorWhileNewOverloadCanRetain() {
        RaycastConfig legacy = new RaycastConfig(true, true, 3, 8, 48, 24, 5);
        RaycastConfig retaining = new RaycastConfig(true, true, 3, 8, 48, 24, 5, true);

        assertFalse(legacy.keepClientEntityWhenHidden());
        assertTrue(retaining.keepClientEntityWhenHidden());
    }

    private static ConfigurationNode entityNode(boolean keepClientEntityWhenHidden) throws SerializationException {
        ConfigurationNode node = baseNode();
        node.node("hide-sounds-when-hidden").set(true);
        node.node("keep-client-entity-when-hidden").set(keepClientEntityWhenHidden);
        node.node("only-check-sneaking").set(false);
        return node;
    }

    @Test
    void stepSizeLoadsAndDefaultsToOneBlockWhenOutOfRange() throws SerializationException {
        ConfigurationNode node = baseNode();
        node.node("raycast-step-size").set(0.5);
        assertEquals(0.5f, TileEntityConfig.load(node, "checks.tile-entity").getRaycastStepSize());

        node.node("raycast-step-size").set(0);
        assertEquals(RaycastConfig.DEFAULT_STEP_SIZE, TileEntityConfig.load(node, "checks.tile-entity").getRaycastStepSize(),
                "a zero step would never advance the ray, so it must fall back to the default");

        node.node("raycast-step-size").set(-1);
        assertEquals(RaycastConfig.DEFAULT_STEP_SIZE, TileEntityConfig.load(node, "checks.tile-entity").getRaycastStepSize());

        node.node("raycast-step-size").set(1000);
        assertEquals(RaycastConfig.DEFAULT_STEP_SIZE, TileEntityConfig.load(node, "checks.tile-entity").getRaycastStepSize());
    }

    @Test
    void stepSizeSurvivesEveryCheckSubclass() throws SerializationException {
        ConfigurationNode node = entityNode(false);
        node.node("raycast-step-size").set(0.25);
        node.node("excluded-types").set(java.util.List.of());

        assertEquals(0.25f, PlayerConfig.load(node, "checks.player").getRaycastStepSize());
        assertEquals(0.25f, EntityConfig.load(node, "checks.entity").getRaycastStepSize());
        assertEquals(0.25f, TileEntityConfig.load(node, "checks.tile-entity").getRaycastStepSize());
    }

    @Test
    void anAbsentStepSizeKeepsTheOldFixedSampling() throws SerializationException {
        // Config files written before this setting existed must still load.
        ConfigurationNode node = baseNode();
        node.removeChild("raycast-step-size");

        assertEquals(RaycastConfig.DEFAULT_STEP_SIZE, TileEntityConfig.load(node, "checks.tile-entity").getRaycastStepSize());
    }

    @Test
    void theLegacyConstructorsKeepOneBlockSampling() {
        assertEquals(RaycastConfig.DEFAULT_STEP_SIZE, new RaycastConfig(true, true, 3, 8, 48, 24, 5).getRaycastStepSize());
        assertEquals(RaycastConfig.DEFAULT_STEP_SIZE, new RaycastConfig(true, true, 3, 8, 48, 24, 5, true).getRaycastStepSize());
    }

    private static ConfigurationNode baseNode() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("enabled").set(true);
        node.node("max-occluding-count").set(3);
        node.node("always-show-radius").set(8);
        node.node("raycast-radius").set(48);
        node.node("hide-on-spawn-distance").set(24);
        node.node("raycast-step-size").set(1.0);
        node.node("visible-recheck-interval-ticks").set(5);
        return node;
    }
}
