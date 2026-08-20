/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.world;

import games.cubi.raycastedantiesp.core.config.WorldFilterMode;
import games.cubi.raycastedantiesp.core.utils.VarHandler;

import java.lang.invoke.VarHandle;
import java.util.Set;
import java.util.UUID;

/**
 * Global registry of the worlds RaycastedAntiESP runs its checks in, resolved from {@code checks.worlds} by the
 * platform module.
 * <p>
 * Read from the Netty threads on every entity spawn and from the engine threads on every tick, so the filter is
 * published as an immutable snapshot rather than mutated in place.
 */
public final class WorldCheckRegistry {
    /** @param worlds world identities the {@code mode} refers to. NEVER mutate a published instance; replace it. */
    record Filter(boolean whitelist, Set<UUID> worlds) {
        boolean allows(UUID world) {
            // A world we cannot identify is checked, so an unresolved world can never silently disable protection.
            return world == null || whitelist == worlds.contains(world);
        }
    }

    /** Until the platform publishes a filter, every world is checked, which is the behaviour without this feature. */
    private static final Filter ALL_WORLDS = new Filter(false, Set.of());

    private static volatile Filter filter = ALL_WORLDS; private static final VarHandle FILTER = VarHandler.$tatic(WorldCheckRegistry.class, "filter", Filter.class);

    private WorldCheckRegistry() {
    }

    /**
     * Replaces the active filter. Callers own the resolution of config world names to world identities, because a
     * listed world may not exist yet when the config is read.
     *
     * @param worlds the resolved identities of the configured worlds. Copied, so the caller may reuse the collection.
     */
    public static void publish(WorldFilterMode mode, Set<UUID> worlds) {
        FILTER.setRelease(new Filter(mode == WorldFilterMode.WHITELIST, Set.copyOf(worlds)));
    }

    /** Restores the default of checking every world. */
    public static void reset() {
        FILTER.setRelease(ALL_WORLDS);
    }

    /**
     * @return whether checks should run in this world. A {@code null} world is always checked.
     */
    public static boolean checksEnabledIn(UUID world) {
        return filterAcquire().allows(world);
    }

    /** @return whether the active filter would check every world, used for start-up diagnostics. */
    public static boolean checksEveryWorld() {
        Filter current = filterAcquire();
        return !current.whitelist() && current.worlds().isEmpty();
    }

    private static Filter filterAcquire() {
        return (Filter) FILTER.getAcquire();
    }
}
