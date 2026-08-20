/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.world;

import games.cubi.raycastedantiesp.core.config.WorldFilterMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldCheckRegistryTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID LOBBY = UUID.randomUUID();

    @AfterEach
    void restoreDefault() {
        WorldCheckRegistry.reset();
    }

    @Test
    void everyWorldIsCheckedBeforeAFilterIsPublished() {
        assertTrue(WorldCheckRegistry.checksEveryWorld());
        assertTrue(WorldCheckRegistry.checksEnabledIn(WORLD));
        assertTrue(WorldCheckRegistry.checksEnabledIn(LOBBY));
    }

    @Test
    void blacklistedWorldsAreNotChecked() {
        WorldCheckRegistry.publish(WorldFilterMode.BLACKLIST, Set.of(LOBBY));

        assertFalse(WorldCheckRegistry.checksEnabledIn(LOBBY));
        assertTrue(WorldCheckRegistry.checksEnabledIn(WORLD));
        assertFalse(WorldCheckRegistry.checksEveryWorld());
    }

    @Test
    void onlyWhitelistedWorldsAreChecked() {
        WorldCheckRegistry.publish(WorldFilterMode.WHITELIST, Set.of(WORLD));

        assertTrue(WorldCheckRegistry.checksEnabledIn(WORLD));
        assertFalse(WorldCheckRegistry.checksEnabledIn(LOBBY));
    }

    @Test
    void anEmptyBlacklistChecksEveryWorld() {
        WorldCheckRegistry.publish(WorldFilterMode.BLACKLIST, Set.of());

        assertTrue(WorldCheckRegistry.checksEveryWorld());
        assertTrue(WorldCheckRegistry.checksEnabledIn(LOBBY));
    }

    @Test
    void anUnknownWorldIsAlwaysChecked() {
        // Failing open here would let an unresolvable world silently disable protection.
        WorldCheckRegistry.publish(WorldFilterMode.WHITELIST, Set.of(WORLD));

        assertTrue(WorldCheckRegistry.checksEnabledIn(null));
    }

    @Test
    void aPublishedFilterIsNotAffectedByLaterMutationOfTheSourceSet() {
        Set<UUID> worlds = new HashSet<>(Set.of(LOBBY));
        WorldCheckRegistry.publish(WorldFilterMode.BLACKLIST, worlds);

        worlds.add(WORLD);

        assertTrue(WorldCheckRegistry.checksEnabledIn(WORLD));
    }

    @Test
    void republishingReplacesThePreviousFilter() {
        WorldCheckRegistry.publish(WorldFilterMode.BLACKLIST, Set.of(LOBBY));
        WorldCheckRegistry.publish(WorldFilterMode.BLACKLIST, Set.of(WORLD));

        assertTrue(WorldCheckRegistry.checksEnabledIn(LOBBY));
        assertFalse(WorldCheckRegistry.checksEnabledIn(WORLD));
    }
}
