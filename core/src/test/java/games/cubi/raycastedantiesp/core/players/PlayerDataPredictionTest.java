/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.players;

import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.implementations.ImmutableLocatableImpl;
import games.cubi.raycastedantiesp.core.config.ViewerPredictionConfig;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerDataPredictionTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID OTHER_WORLD = UUID.randomUUID();
    /** Roughly sprinting speed, comfortably above the default minimum. */
    private static final double SPRINT_PER_TICK = 0.28;
    private static final double MIN_SPEED = ViewerPredictionConfig.DEFAULT_MIN_SPEED;

    private final List<UUID> registered = new ArrayList<>();

    @BeforeAll
    static void initialiseViews() {
        ViewRegistry.initialise(ignored -> null, ignored -> emptyEntityView(), ignored -> emptyEntityView());
    }

    @AfterEach
    void unregisterPlayers() {
        registered.forEach(PlayerRegistry.getInstance()::unregisterPlayer);
        registered.clear();
    }

    @Test
    void withoutAPreviousSampleThereIsNothingToExtrapolate() {
        PlayerData player = playerData();

        assertNull(player.predictLocation(at(0, 0, 0), 100, MIN_SPEED, 1));
    }

    @Test
    void aSprintingViewerIsExtrapolatedAlongTheirTravel() {
        PlayerData player = playerData();
        player.recordMovementSample(at(0, 64, 0), 100);

        Locatable predicted = player.predictLocation(at(SPRINT_PER_TICK, 64, 0), 101, MIN_SPEED, 1);

        assertNotNull(predicted);
        assertEquals(SPRINT_PER_TICK * 2, predicted.x(), 1.0e-9, "one tick past the current position");
        assertEquals(64, predicted.y(), 1.0e-9);
        assertEquals(WORLD, predicted.world());
    }

    @Test
    void ticksAheadScalesTheLead() {
        PlayerData player = playerData();
        player.recordMovementSample(at(0, 64, 0), 100);

        Locatable predicted = player.predictLocation(at(SPRINT_PER_TICK, 64, 0), 101, MIN_SPEED, 3);

        assertNotNull(predicted);
        assertEquals(SPRINT_PER_TICK * 4, predicted.x(), 1.0e-9);
    }

    @Test
    void aViewerBelowTheSpeedThresholdIsNotExtrapolated() {
        PlayerData player = playerData();
        player.recordMovementSample(at(0, 64, 0), 100);

        assertNull(player.predictLocation(at(MIN_SPEED / 2, 64, 0), 101, MIN_SPEED, 1),
                "standing still or creeping must keep the strict current-position check");
    }

    @Test
    void aJumpTooLargeToBeTravelIsNotExtrapolated() {
        // A teleport would otherwise project the viewer hundreds of blocks away and reveal most of the map.
        PlayerData player = playerData();
        player.recordMovementSample(at(0, 64, 0), 100);

        assertNull(player.predictLocation(at(5000, 64, 0), 101, MIN_SPEED, 1));
    }

    @Test
    void aSampleFromAnotherWorldIsNotExtrapolated() {
        PlayerData player = playerData();
        player.recordMovementSample(new ImmutableLocatableImpl(OTHER_WORLD, 0, 64, 0), 100);

        assertNull(player.predictLocation(at(SPRINT_PER_TICK, 64, 0), 101, MIN_SPEED, 1));
    }

    @Test
    void aStaleSampleIsNotExtrapolated() {
        PlayerData player = playerData();
        player.recordMovementSample(at(0, 64, 0), 100);

        int staleTick = 100 + ViewerPredictionConfig.MAX_SAMPLE_AGE_TICKS + 1;
        assertNull(player.predictLocation(at(SPRINT_PER_TICK, 64, 0), staleTick, MIN_SPEED, 1),
                "a gap this long means the viewer was not being ticked, so the delta is not a velocity");
    }

    @Test
    void speedIsNormalisedOverTheGapBetweenSamples() {
        // The engine can skip ticks, so a two tick gap of double the distance is still the same speed.
        PlayerData player = playerData();
        player.recordMovementSample(at(0, 64, 0), 100);

        Locatable predicted = player.predictLocation(at(SPRINT_PER_TICK * 2, 64, 0), 102, MIN_SPEED, 1);

        assertNotNull(predicted);
        assertEquals(SPRINT_PER_TICK * 3, predicted.x(), 1.0e-9);
    }

    @Test
    void aRepeatedSampleAtTheSameTickIsRejected() {
        PlayerData player = playerData();
        player.recordMovementSample(at(0, 64, 0), 100);

        assertNull(player.predictLocation(at(SPRINT_PER_TICK, 64, 0), 100, MIN_SPEED, 1));
    }

    private static Locatable at(double x, double y, double z) {
        return new ImmutableLocatableImpl(WORLD, x, y, z);
    }

    private PlayerData playerData() {
        UUID uuid = UUID.randomUUID();
        registered.add(uuid);
        return PlayerRegistry.getInstance().registerAndGetPlayer(uuid, 0, 1, TestEntity::createSelf);
    }

    private static EntityView<?> emptyEntityView() {
        return (EntityView<?>) Proxy.newProxyInstance(
                EntityView.class.getClassLoader(),
                new Class<?>[]{EntityView.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null
        );
    }

    private static final class TestEntity extends NettyEntity<Clearable> {
        private TestEntity(PlayerData owningPlayer, int entityID, UUID entityUUID) {
            super(owningPlayer, entityID, entityUUID);
        }

        private static TestEntity createSelf(PlayerData owningPlayer, int entityID, UUID playerUUID) {
            return new TestEntity(owningPlayer, entityID, playerUUID);
        }
    }
}
