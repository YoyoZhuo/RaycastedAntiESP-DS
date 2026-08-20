/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.players;

import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.utils.EntitySpawnTask;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyDataPreviousWorldEntityTest {
    private static final int TRANSITION_TICK = 1000;

    @Test
    void noEntityIsStaleBeforeAnyWorldTransition() {
        NettyData nettyData = nettyData();

        assertFalse(nettyData.isPreviousWorldEntity(26077, TRANSITION_TICK));
    }

    @Test
    void entitiesFromThePreviousWorldAreRecognisedWithinTheStragglerWindow() {
        NettyData nettyData = nettyData();
        nettyData.recordWorldTransition(new int[]{26077, 26080}, TRANSITION_TICK);

        assertTrue(nettyData.isPreviousWorldEntity(26077, TRANSITION_TICK));
        assertTrue(nettyData.isPreviousWorldEntity(26080, TRANSITION_TICK + 15));
    }

    @Test
    void entitiesNotFromThePreviousWorldStillDeferUntilSpawn() {
        NettyData nettyData = nettyData();
        nettyData.recordWorldTransition(new int[]{26077}, TRANSITION_TICK);

        assertFalse(nettyData.isPreviousWorldEntity(30001, TRANSITION_TICK));
    }

    @Test
    void theStragglerWindowClosesOnceTasksWouldHaveExpiredAnyway() {
        // Past this point an entity ID may have been recycled by the new world, and dropping a legitimate deferred
        // update would lose cached metadata or equipment.
        NettyData nettyData = nettyData();
        nettyData.recordWorldTransition(new int[]{26077}, TRANSITION_TICK);

        assertFalse(nettyData.isPreviousWorldEntity(26077, TRANSITION_TICK + EntitySpawnTask.TICKS_BEFORE_EVICTION));
    }

    @Test
    void destroyPacketsForThePreviousWorldDoNotHideStragglers() {
        // This is the regression the snapshot exists for: expectedWorldTransitionDestroyEntityIDs is consumed by the
        // destroy packets, so it cannot be reused to identify a straggler that arrives after them.
        NettyData nettyData = nettyData();
        int[] previousWorldEntities = {26077, 26080};
        nettyData.setExpectedWorldTransitionDestroyEntityIDs(previousWorldEntities);
        nettyData.recordWorldTransition(previousWorldEntities, TRANSITION_TICK);

        assertTrue(nettyData.consumeExpectedWorldTransitionDestroyEntityID(26077));
        assertTrue(nettyData.consumeExpectedWorldTransitionDestroyEntityID(26080));

        assertTrue(nettyData.isPreviousWorldEntity(26077, TRANSITION_TICK + 1));
        assertTrue(nettyData.isPreviousWorldEntity(26080, TRANSITION_TICK + 1));
    }

    @Test
    void aTransitionOutOfAnEmptyWorldRecordsNothing() {
        NettyData nettyData = nettyData();
        nettyData.recordWorldTransition(null, TRANSITION_TICK);

        assertFalse(nettyData.isPreviousWorldEntity(26077, TRANSITION_TICK));

        nettyData.recordWorldTransition(new int[0], TRANSITION_TICK);

        assertFalse(nettyData.isPreviousWorldEntity(26077, TRANSITION_TICK));
    }

    @Test
    void aLaterTransitionReplacesTheEarlierSnapshot() {
        NettyData nettyData = nettyData();
        nettyData.recordWorldTransition(new int[]{26077}, TRANSITION_TICK);
        nettyData.recordWorldTransition(new int[]{31000}, TRANSITION_TICK + 40);

        assertFalse(nettyData.isPreviousWorldEntity(26077, TRANSITION_TICK + 41));
        assertTrue(nettyData.isPreviousWorldEntity(31000, TRANSITION_TICK + 41));
    }

    @Test
    void theSnapshotIsUnaffectedByLaterMutationOfTheSourceArray() {
        NettyData nettyData = nettyData();
        int[] previousWorldEntities = {26077};
        nettyData.recordWorldTransition(previousWorldEntities, TRANSITION_TICK);

        previousWorldEntities[0] = 99999;

        assertTrue(nettyData.isPreviousWorldEntity(26077, TRANSITION_TICK));
        assertFalse(nettyData.isPreviousWorldEntity(99999, TRANSITION_TICK));
    }

    private static NettyData nettyData() {
        return new NettyData(new TestEntity(1, UUID.randomUUID()));
    }

    private static final class TestEntity extends NettyEntity<Clearable> {
        private TestEntity(int entityID, UUID entityUUID) {
            super(null, entityID, entityUUID);
        }
    }
}
