/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.view.controller;

import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.config.raycast.PlayerConfig;
import games.cubi.raycastedantiesp.core.config.raycast.RaycastConfig;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.tracked.TrackedEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketEntityViewControllerTest {
    private static final TestController CONTROLLER = new TestController();
    private final List<UUID> registeredPlayers = new ArrayList<>();

    @BeforeAll
    static void initialiseViews() {
        ViewRegistry.initialise(
                ignored -> emptyBlockView(),
                ignored -> entityView(false),
                ignored -> entityView(true)
        );
    }

    @AfterEach
    void unregisterPlayers() {
        for (UUID player : registeredPlayers) {
            PlayerRegistry.getInstance().unregisterPlayer(player);
        }
        registeredPlayers.clear();
    }

    @Test
    void directlyShownSelfVehiclePassengersRemainMountedInReplacementPacket() {
        CONTROLLER.directlyShownEntityIDs.clear();
        CONTROLLER.replacementPassengerPackets.clear();
        UUID playerUUID = UUID.randomUUID();
        registeredPlayers.add(playerUUID);
        PlayerData playerData = PlayerRegistry.getInstance().registerAndGetPlayer(
                playerUUID,
                0,
                1,
                TestEntity::createSelf
        );
        UUID world = UUID.randomUUID();
        playerData.beginWorldTransition();
        playerData.completeWorldTransition(world);

        TestEntity firstPassenger = new TestEntity(playerData, 2, UUID.randomUUID(), false);
        TestEntity secondPassenger = new TestEntity(playerData, 3, UUID.randomUUID(), false);
        @SuppressWarnings("unchecked")
        EntityView<NettyEntity<?>> entityView = (EntityView<NettyEntity<?>>) (EntityView<?>) playerData.entityView();
        for (TestEntity passenger : List.of(firstPassenger, secondPassenger)) {
            passenger.setVisible(false);
            passenger.setClientVisible(false);
            entityView.insertEntity(world, passenger);
        }

        TestEntity self = (TestEntity) playerData.nettyData().getSelfEntity();
        boolean cancelled = CONTROLLER.handleEntityPassengersNow(self, new int[]{2, 3}, playerData, 17);

        assertTrue(cancelled, "the original passenger packet must be suppressed");
        assertEquals(List.of(2, 3), CONTROLLER.directlyShownEntityIDs);
        assertEquals(1, CONTROLLER.replacementPassengerPackets.size());
        assertArrayEquals(new int[]{2, 3}, CONTROLLER.replacementPassengerPackets.get(0));
        for (TestEntity passenger : List.of(firstPassenger, secondPassenger)) {
            assertTrue(passenger.visible(), "direct SHOW must make the passenger engine-visible");
            assertTrue(passenger.clientVisible(), "direct SHOW must make the passenger client-visible");
            assertEquals(1, passenger.vehicleID(), "the passenger must remain attached to the self vehicle");
        }
        assertArrayEquals(new int[]{2, 3}, self.passengerIDs());
    }


    /*
     * A viewer holding the bypass permission is skipped by the engine, so anything the packet layer hides from them
     * is never revealed again. The packet layer therefore has to leave them alone in the first place.
     */

    @Test
    void aDistantSpawnIsHiddenFromAnOrdinaryViewer() {
        // The behaviour the bypass case is measured against: the spawn is suppressed and the entity starts hidden,
        // which the engine would then re-evaluate on its next tick.
        PlayerData viewer = spawnViewer(false);

        boolean cancelled = CONTROLLER.spawnDistantPlayerFor(viewer);

        assertTrue(cancelled, "the spawn packet must be suppressed for an ordinary viewer");
        assertFalse(CONTROLLER.lastSpawnedEntity.visible());
    }

    @Test
    void aDistantSpawnStaysVisibleForABypassingViewer() {
        PlayerData viewer = spawnViewer(true);

        boolean cancelled = CONTROLLER.spawnDistantPlayerFor(viewer);

        assertFalse(cancelled, "a bypassing viewer must still receive the spawn packet");
        assertTrue(CONTROLLER.lastSpawnedEntity.visible(), "nothing would ever set this back to visible");
        assertTrue(CONTROLLER.lastSpawnedEntity.clientVisible());
    }

    @Test
    void grantingBypassAfterASpawnLeavesStateOnlyTheEngineCanRepair() {
        // The permission is only read once the client reports its world loaded, so entities spawning during login
        // are processed as an ordinary viewer. This is what the engine reveal pass has to recover from.
        PlayerData viewer = spawnViewer(false);
        CONTROLLER.spawnDistantPlayerFor(viewer);
        assertFalse(CONTROLLER.lastSpawnedEntity.visible());

        viewer.setBypassPermission(true);

        assertFalse(CONTROLLER.lastSpawnedEntity.visible(),
                "granting the permission cannot retroactively unhide, hence the engine reveal pass");
    }

    private PlayerData spawnViewer(boolean bypassing) {
        UUID playerUUID = UUID.randomUUID();
        registeredPlayers.add(playerUUID);
        PlayerData playerData = PlayerRegistry.getInstance().registerAndGetPlayer(playerUUID, 0, 1, TestEntity::createSelf);
        playerData.setBypassPermission(bypassing);
        UUID world = UUID.randomUUID();
        playerData.beginWorldTransition();
        playerData.completeWorldTransition(world);
        playerData.updateOwnLocation(world, 0, 64, 0);
        CONTROLLER.spawnWorld = world;
        return playerData;
    }

    private static EntityView<?> entityView(boolean playerView) {
        Map<Integer, TrackedEntity<?>> entitiesByID = new HashMap<>();
        Map<UUID, TrackedEntity<?>> entitiesByUUID = new HashMap<>();
        return (EntityView<?>) Proxy.newProxyInstance(
                PacketEntityViewControllerTest.class.getClassLoader(),
                new Class[]{EntityView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "insertEntity" -> {
                        TrackedEntity<?> entity = (TrackedEntity<?>) args[1];
                        entitiesByID.put(entity.entityID(), entity);
                        entitiesByUUID.put(entity.entityUUID(), entity);
                        yield null;
                    }
                    case "removeEntity" -> {
                        int entityID = (Integer) args[0];
                        TrackedEntity<?> removed = entitiesByID.remove(entityID);
                        if (removed != null) {
                            entitiesByUUID.remove(removed.entityUUID());
                        }
                        yield null;
                    }
                    case "getEntity" -> args[0] instanceof Integer
                            ? entitiesByID.get(args[0])
                            : entitiesByUUID.get(args[0]);
                    case "exists" -> args[0] instanceof Integer
                            ? entitiesByID.containsKey(args[0])
                            : entitiesByUUID.containsKey(args[0]);
                    case "size" -> entitiesByID.size();
                    case "getKnownEntities" -> List.copyOf(entitiesByUUID.keySet());
                    case "getKnownEntityIDs" -> entitiesByID.keySet().stream().mapToInt(Integer::intValue).toArray();
                    case "getEntityID" -> {
                        TrackedEntity<?> entity = entitiesByUUID.get(args[0]);
                        yield entity == null ? -1 : entity.entityID();
                    }
                    case "getPosition" -> entitiesByUUID.get(args[0]);
                    case "isVisible" -> {
                        TrackedEntity<?> entity = args[0] instanceof Integer
                                ? entitiesByID.get(args[0])
                                : entitiesByUUID.get(args[0]);
                        yield entity == null || entity.visible();
                    }
                    case "recordDirectVisibility", "setVisibility" -> {
                        NettyEntity<?> entity = (NettyEntity<?>) args[0];
                        boolean current = entitiesByUUID.get(entity.entityUUID()) == entity && !entity.isSelfEntity();
                        if (current) {
                            entity.setVisible((Boolean) args[1]);
                            entity.setLastChecked((Integer) args[2]);
                        }
                        yield method.getName().equals("recordDirectVisibility") ? current : null;
                    }
                    case "forEachNeedingRecheck", "forEachNeedingRecheckEntity" -> 0;
                    case "hasPendingTransitions" -> false;
                    case "flushPendingTransitions", "drainTransitions", "clear" -> null;
                    case "isPlayerView" -> playerView;
                    case "getStringDataForDebugging" -> "test";
                    case "toString" -> "TestEntityView";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static BlockView emptyBlockView() {
        return (BlockView) Proxy.newProxyInstance(
                PacketEntityViewControllerTest.class.getClassLoader(),
                new Class[]{BlockView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "TestBlockView";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        return 0;
    }

    private static final class TestController extends PacketEntityViewController<Void> {
        private final List<Integer> directlyShownEntityIDs = new ArrayList<>();
        private final List<int[]> replacementPassengerPackets = new ArrayList<>();
        private UUID spawnWorld;
        private TestEntity lastSpawnedEntity;

        private TestController() {
            entityConfig = loadEntityConfig();
            playerConfig = loadPlayerConfig();
            // Anything spawning beyond this is hidden on arrival; spawnDistantPlayerFor lands well outside it.
            hideOnSpawnPlayerDistanceSquared = 24 * 24;
            hideOnSpawnEntityDistanceSquared = 24 * 24;
        }

        /** @return whether the spawn packet was suppressed. */
        boolean spawnDistantPlayerFor(PlayerData playerData) {
            return handleEntitySpawn0(null, true, playerData, spawnWorld, 0);
        }

        @Override
        protected NettyEntity<?> createSelfEntity(PlayerData ownData, int entityID, UUID playerUUID) {
            return TestEntity.createSelf(ownData, entityID, playerUUID);
        }

        @Override
        protected NettyEntity<?> processEntitySpawn(PlayerData playerData, Void packet, UUID world, int currentTick) {
            lastSpawnedEntity = new TestEntity(playerData, 2, UUID.randomUUID(), 500, 64, 500);
            return lastSpawnedEntity;
        }

        @Override
        protected void processDirectEntityShow(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {
            directlyShownEntityIDs.add(entity.entityID());
            entity.setClientVisible(true);
        }

        @Override
        protected void sendEntityPassengerPacket(int vehicle, IntArrayList passengers, PlayerData playerData) {
            replacementPassengerPackets.add(passengers.toIntArray());
        }

        @Override
        protected int processRelativeMovePacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processRelativeMoveAndRotationPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processTeleportPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processPositionSyncPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected void processTrackedMetadata(Void packet, NettyEntity<?> entity) {}

        @Override
        protected void cachePacket(Void packet, int entityID, PlayerData playerData, int currentTick) {}

        @Override
        protected int processRotationPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processHeadLookPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processEntityVelocityPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected void insertEntityToPlayerView(NettyEntity<?> entity, PlayerData playerData, UUID world) {}

        @Override
        protected void insertEntityToEntityView(NettyEntity<?> entity, PlayerData playerData, UUID world) {}
    }

    private static final class TestEntity extends NettyEntity<Clearable> {
        private TestEntity(PlayerData owningPlayer, int entityID, UUID entityUUID) {
            super(owningPlayer, entityID, entityUUID);
        }

        private TestEntity(PlayerData owningPlayer, int entityID, UUID entityUUID, boolean visible) {
            super(owningPlayer, 0, 0, 0, entityID, entityUUID, false, 0, visible);
        }

        private TestEntity(PlayerData owningPlayer, int entityID, UUID entityUUID, double x, double y, double z) {
            super(owningPlayer, x, y, z, entityID, entityUUID, false, 0, true);
        }

        private static TestEntity createSelf(PlayerData owningPlayer, int entityID, UUID playerUUID) {
            return new TestEntity(owningPlayer, entityID, playerUUID);
        }
    }

    private static PlayerConfig loadPlayerConfig() {
        return PlayerConfig.load(checkNode(), "checks.player");
    }

    private static EntityConfig loadEntityConfig() {
        ConfigurationNode node = checkNode();
        setNode(node, "excluded-types", List.of());
        return EntityConfig.load(node, "checks.entity");
    }

    private static ConfigurationNode checkNode() {
        ConfigurationNode node = BasicConfigurationNode.root();
        setNode(node, "enabled", true);
        setNode(node, "hide-sounds-when-hidden", false);
        setNode(node, "max-occluding-count", 3);
        setNode(node, "always-show-radius", 8);
        setNode(node, "raycast-radius", 128);
        setNode(node, "hide-on-spawn-distance", 24);
        setNode(node, "raycast-step-size", RaycastConfig.DEFAULT_STEP_SIZE);
        setNode(node, "visible-recheck-interval-ticks", 1);
        setNode(node, "keep-client-entity-when-hidden", false);
        setNode(node, "only-check-sneaking", false);
        return node;
    }

    private static void setNode(ConfigurationNode parent, String key, Object value) {
        try {
            parent.node(key).set(value);
        } catch (SerializationException e) {
            throw new AssertionError(e);
        }
    }
}
