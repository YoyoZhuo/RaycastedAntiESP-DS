/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.config.profiles;

import games.cubi.raycastedantiesp.core.config.ConfigLoadException;
import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.config.raycast.PlayerConfig;
import games.cubi.raycastedantiesp.core.config.raycast.TileEntityConfig;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckProfileSetTest {

    @Test
    void anAbsentProfilesBlockLeavesEveryoneOnTheGlobalConfig() throws SerializationException {
        Fixture fixture = new Fixture();

        CheckProfileSet profiles = fixture.load(null);

        assertTrue(profiles.isEmpty());
        assertEquals(0, profiles.size());
        assertSame(fixture.player, profiles.defaultProfile().playerConfig());
        assertSame(fixture.entity, profiles.defaultProfile().entityConfig());
        assertSame(fixture.tile, profiles.defaultProfile().tileEntityConfig());
    }

    @Test
    void aProfileOverridesOnlyWhatItStatesAndInheritsTheRest() throws SerializationException {
        Fixture fixture = new Fixture();
        ConfigurationNode profilesNode = BasicConfigurationNode.root();
        ConfigurationNode strict = profilesNode.node("strict");
        strict.node("priority").set(100);
        strict.node("player").node("max-occluding-count").set(1);
        strict.node("player").node("raycast-step-size").set(0.5);

        CheckProfile profile = fixture.load(profilesNode).byName("strict");

        assertNotNull(profile);
        assertEquals(1, profile.playerConfig().getMaxOccludingCount());
        assertEquals(0.5f, profile.playerConfig().getRaycastStepSize());
        // Inherited from the global player check rather than reset to a built-in default.
        assertEquals(fixture.player.getRaycastRadius(), profile.playerConfig().getRaycastRadius());
        assertEquals(fixture.player.getAlwaysShowRadius(), profile.playerConfig().getAlwaysShowRadius());
        assertEquals(fixture.player.onlyCheckSneaking(), profile.playerConfig().onlyCheckSneaking());
        // A profile which says nothing about a check shares that check's config object outright.
        assertSame(fixture.entity, profile.entityConfig());
        assertSame(fixture.tile, profile.tileEntityConfig());
    }

    @Test
    void settingsWhichStayServerWideSurviveAnOverrideOfTheSameCheck() throws SerializationException {
        Fixture fixture = new Fixture();
        ConfigurationNode profilesNode = BasicConfigurationNode.root();
        ConfigurationNode profile = profilesNode.node("loose");
        profile.node("priority").set(1);
        profile.node("player").node("raycast-radius").set(32);

        PlayerConfig loaded = fixture.load(profilesNode).byName("loose").playerConfig();

        assertEquals(32, loaded.getRaycastRadius());
        // Rebuilding the check for this profile must not disturb the settings a profile cannot reach, or one viewer
        // could change the shape of the packet stream for an entity everyone else can also see.
        assertEquals(fixture.player.hideOnSpawnDistance(), loaded.hideOnSpawnDistance());
        assertEquals(fixture.player.keepClientEntityWhenHidden(), loaded.keepClientEntityWhenHidden());
        assertEquals(fixture.player.getVisibleRecheckIntervalTicks(), loaded.getVisibleRecheckIntervalTicks());
        assertEquals(fixture.player.hideSoundsWhenHidden(), loaded.hideSoundsWhenHidden());
        assertEquals(fixture.player.alwaysShowGlowing(), loaded.alwaysShowGlowing());
    }

    @Test
    void aProfileCanTurnOneCheckOffWithoutTouchingTheOthers() throws SerializationException {
        Fixture fixture = new Fixture();
        ConfigurationNode profilesNode = BasicConfigurationNode.root();
        ConfigurationNode profile = profilesNode.node("no-tiles");
        profile.node("priority").set(5);
        profile.node("tile-entity").node("enabled").set(false);

        CheckProfile loaded = fixture.load(profilesNode).byName("no-tiles");

        assertFalse(loaded.tileEntityConfig().enabled());
        assertTrue(loaded.playerConfig().enabled());
        assertTrue(loaded.entityConfig().enabled());
    }

    @Test
    void profilesAreOrderedByDescendingPriority() throws SerializationException {
        Fixture fixture = new Fixture();
        ConfigurationNode profilesNode = BasicConfigurationNode.root();
        addProfile(profilesNode, "low", 1);
        addProfile(profilesNode, "high", 500);
        addProfile(profilesNode, "middle", 50);

        List<CheckProfile> ordered = fixture.load(profilesNode).byPriority();

        assertEquals(List.of("high", "middle", "low"), ordered.stream().map(CheckProfile::name).toList());
    }

    @Test
    void theDefaultProfileIsNotListedAmongTheConfiguredOnes() throws SerializationException {
        Fixture fixture = new Fixture();
        ConfigurationNode profilesNode = BasicConfigurationNode.root();
        addProfile(profilesNode, "strict", 10);

        CheckProfileSet profiles = fixture.load(profilesNode);

        assertEquals(1, profiles.size());
        assertSame(profiles.defaultProfile(), profiles.byName(CheckProfile.DEFAULT_NAME));
        assertNull(profiles.byName("missing"));
    }

    @Test
    void theReservedDefaultNameIsRejected() throws SerializationException {
        Fixture fixture = new Fixture();
        ConfigurationNode profilesNode = BasicConfigurationNode.root();
        addProfile(profilesNode, CheckProfile.DEFAULT_NAME, 10);

        assertThrows(ConfigLoadException.class, () -> fixture.load(profilesNode));
    }

    @Test
    void aNameWhichWouldNotMakeAUsablePermissionIsRejected() throws SerializationException {
        Fixture fixture = new Fixture();
        ConfigurationNode profilesNode = BasicConfigurationNode.root();
        addProfile(profilesNode, "Not Valid", 10);

        assertThrows(ConfigLoadException.class, () -> fixture.load(profilesNode));
    }

    @Test
    void aMissingPriorityIsRejectedBecauseItWouldMakeResolutionAmbiguous() throws SerializationException {
        Fixture fixture = new Fixture();
        ConfigurationNode profilesNode = BasicConfigurationNode.root();
        profilesNode.node("strict").node("player").node("raycast-radius").set(32);

        assertThrows(ConfigLoadException.class, () -> fixture.load(profilesNode));
    }

    @Test
    void theProfilePermissionIsDerivedFromTheName() {
        assertEquals("raycastedantiesp.profile.strict", CheckProfile.permissionFor("strict"));
    }

    private static void addProfile(ConfigurationNode profilesNode, String name, int priority) throws SerializationException {
        ConfigurationNode profile = profilesNode.node(name);
        profile.node("priority").set(priority);
        // Overriding something keeps the load from warning that the profile changes nothing.
        profile.node("player").node("raycast-radius").set(32);
    }

    /** The three global checks a profile resolves against, loaded from a minimal but complete config. */
    private static final class Fixture {
        private final PlayerConfig player;
        private final EntityConfig entity;
        private final TileEntityConfig tile;

        private Fixture() throws SerializationException {
            player = PlayerConfig.load(playerNode(), "checks.player");
            entity = EntityConfig.load(checkNode(92), "checks.entity");
            tile = TileEntityConfig.load(checkNode(64), "checks.tile-entity");
        }

        private CheckProfileSet load(ConfigurationNode profilesNode) {
            return CheckProfileSet.load(profilesNode, "checks.profiles", player, entity, tile);
        }

        private static ConfigurationNode playerNode() throws SerializationException {
            ConfigurationNode node = checkNode(128);
            node.node("only-check-sneaking").set(true);
            return node;
        }

        private static ConfigurationNode checkNode(int raycastRadius) throws SerializationException {
            ConfigurationNode node = BasicConfigurationNode.root();
            node.node("enabled").set(true);
            node.node("hide-sounds-when-hidden").set(true);
            node.node("max-occluding-count").set(3);
            node.node("always-show-radius").set(8);
            node.node("raycast-radius").set(raycastRadius);
            node.node("hide-on-spawn-distance").set(24);
            node.node("visible-recheck-interval-ticks").set(5);
            node.node("keep-client-entity-when-hidden").set(false);
            node.node("raycast-step-size").set(1.0);
            node.node("always-show-glowing").set(true);
            return node;
        }
    }
}
