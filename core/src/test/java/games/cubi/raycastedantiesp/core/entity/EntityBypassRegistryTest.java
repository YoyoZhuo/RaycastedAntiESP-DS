/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.entity;

import games.cubi.raycastedantiesp.core.utils.EntitySpawnTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityBypassRegistryTest {
    private static final int ARROW = 4854;
    private static final int TICK = 1000;
    /** Anything at or beyond this is guaranteed to be past the grace period. */
    private static final int WELL_PAST_GRACE = 1000;

    @AfterEach
    void clearRegistry() {
        EntityBypassRegistry.markEntityDespawned(ARROW, TICK);
        EntityBypassRegistry.purgeDespawnedEntities(TICK + WELL_PAST_GRACE);
    }

    @Test
    void anAddedEntityIsBypassed() {
        EntityBypassRegistry.addEntity(ARROW);

        assertTrue(EntityBypassRegistry.isBypassed(ARROW));
    }

    @Test
    void aDespawnedEntityStaysBypassedForTheStragglerWindow() {
        // Packets written before the removal was observed are intercepted afterwards, and must stay exempt rather
        // than being queued as deferred spawn tasks that can never complete.
        EntityBypassRegistry.addEntity(ARROW);
        EntityBypassRegistry.markEntityDespawned(ARROW, TICK);

        assertTrue(EntityBypassRegistry.isBypassed(ARROW));

        EntityBypassRegistry.purgeDespawnedEntities(TICK + EntitySpawnTask.TICKS_BEFORE_EVICTION);

        assertTrue(EntityBypassRegistry.isBypassed(ARROW),
                "the grace period must outlive the deferred task eviction window");
    }

    @Test
    void aDespawnedEntityIsReleasedOnceTheGracePeriodElapses() {
        EntityBypassRegistry.addEntity(ARROW);
        EntityBypassRegistry.markEntityDespawned(ARROW, TICK);

        EntityBypassRegistry.purgeDespawnedEntities(TICK + WELL_PAST_GRACE);

        assertFalse(EntityBypassRegistry.isBypassed(ARROW));
    }

    @Test
    void purgingWithoutADespawnKeepsLiveEntitiesBypassed() {
        EntityBypassRegistry.addEntity(ARROW);

        EntityBypassRegistry.purgeDespawnedEntities(TICK + WELL_PAST_GRACE);

        assertTrue(EntityBypassRegistry.isBypassed(ARROW));
    }

    @Test
    void anEntityReAddedInsideItsGracePeriodIsNotPurged() {
        // Entity IDs are effectively never recycled this quickly, but a re-add must still cancel the pending removal
        // rather than leaving it armed.
        EntityBypassRegistry.addEntity(ARROW);
        EntityBypassRegistry.markEntityDespawned(ARROW, TICK);
        EntityBypassRegistry.addEntity(ARROW);

        EntityBypassRegistry.purgeDespawnedEntities(TICK + WELL_PAST_GRACE);

        assertTrue(EntityBypassRegistry.isBypassed(ARROW));
    }

    @Test
    void despawningAnEntityThatWasNeverBypassedDoesNothing() {
        EntityBypassRegistry.markEntityDespawned(ARROW, TICK);
        EntityBypassRegistry.purgeDespawnedEntities(TICK + WELL_PAST_GRACE);

        assertFalse(EntityBypassRegistry.isBypassed(ARROW));
    }

    @Test
    void theGracePeriodRunsFromTheFirstDespawnObservation() {
        EntityBypassRegistry.addEntity(ARROW);
        EntityBypassRegistry.markEntityDespawned(ARROW, TICK);
        // A repeat removal event must not keep pushing the deadline back.
        EntityBypassRegistry.markEntityDespawned(ARROW, TICK + WELL_PAST_GRACE);

        EntityBypassRegistry.purgeDespawnedEntities(TICK + WELL_PAST_GRACE);

        assertFalse(EntityBypassRegistry.isBypassed(ARROW));
    }
}
