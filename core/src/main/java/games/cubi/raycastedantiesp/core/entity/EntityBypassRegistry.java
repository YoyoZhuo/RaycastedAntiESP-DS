/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.entity;

import games.cubi.raycastedantiesp.core.utils.EntitySpawnTask;
import games.cubi.utils.sets.CopyOnWriteMTIntSet;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * Global registry of entity IDs which RaycastedAntiESP must ignore.
 */
public final class EntityBypassRegistry {
    /**
     * How long a despawned entity keeps its bypass after the removal is observed.
     * <p>
     * Removals are observed on the server thread while packets are intercepted on the Netty threads, so a packet
     * written before the removal can still arrive afterwards. Dropping the bypass immediately would make that packet
     * look like it belongs to an untracked entity, which queues a deferred spawn task that can never complete and is
     * eventually reported as evicted. Outliving {@link EntitySpawnTask#TICKS_BEFORE_EVICTION} keeps the whole
     * straggler window covered.
     */
    private static final int DESPAWN_GRACE_TICKS = EntitySpawnTask.TICKS_BEFORE_EVICTION + 4;

    private static final CopyOnWriteMTIntSet BYPASSED_ENTITY_IDS = CopyOnWriteMTIntSet.get();
    /** Despawn tick per entity still inside its grace period. Guarded by its own monitor. */
    private static final Int2IntOpenHashMap PENDING_REMOVAL_TICKS = new Int2IntOpenHashMap();
    // Technically, (26.2+) Minecraft only guarantees that an entity ID corresponds to one-and-only-one entity within a single world, and entities in different worlds can have the same ID.
    //              (26.1.2-) Minecraft does not guarantee that an entity ID is unique.
    // However, this requires the int id counter to overflow from Integer.MAX_VALUE all the way back to 0. This is unlikely to occur, and even if it does occur, it is unlikely that the original entities are still alive.
    private EntityBypassRegistry() {
    }

    public static void addEntity(int entityID) {
        synchronized (PENDING_REMOVAL_TICKS) {
            // An ID that comes back before its grace period elapsed must not be removed by the pending purge.
            PENDING_REMOVAL_TICKS.remove(entityID);
        }
        BYPASSED_ENTITY_IDS.add(entityID);
    }

    /**
     * For use when an entity is despawned/killed, or in other words completely gone from the server.
     * <p>
     * The bypass is dropped by {@link #purgeDespawnedEntities} once {@link #DESPAWN_GRACE_TICKS} have passed, rather
     * than immediately, so that packets already written for this entity stay exempt.
     */
    public static void markEntityDespawned(int entityID, int currentTick) {
        if (!BYPASSED_ENTITY_IDS.contains(entityID)) {
            return;
        }
        synchronized (PENDING_REMOVAL_TICKS) {
            PENDING_REMOVAL_TICKS.putIfAbsent(entityID, currentTick);
        }
    }

    /**
     * Completes the deferred removals whose grace period has elapsed. Expected to be called once per server tick, so
     * that entities stop being bypassed even when nothing else despawns.
     */
    public static void purgeDespawnedEntities(int currentTick) {
        synchronized (PENDING_REMOVAL_TICKS) {
            if (PENDING_REMOVAL_TICKS.isEmpty()) {
                return;
            }
            ObjectIterator<Int2IntMap.Entry> iterator = PENDING_REMOVAL_TICKS.int2IntEntrySet().fastIterator();
            while (iterator.hasNext()) {
                Int2IntMap.Entry entry = iterator.next();
                if (currentTick - entry.getIntValue() < DESPAWN_GRACE_TICKS) {
                    continue;
                }
                BYPASSED_ENTITY_IDS.remove(entry.getIntKey());
                iterator.remove();
            }
        }
    }

    public static boolean isBypassed(int entityID) {
        return BYPASSED_ENTITY_IDS.contains(entityID);
    }
}
