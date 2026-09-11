/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.chunks;

import java.util.Set;

/**
 * Decides whether one block state counts as blocking line of sight, on top of whatever the platform reports.
 * <p>
 * Kept apart from the platform lookup so the rules can be tested without a server, and so the platform only has to
 * answer the two questions this needs about a block state.
 */
public final class OcclusionPolicy {
    private final Set<String> alwaysOccluding;
    private final Set<String> neverOccluding;

    public OcclusionPolicy(Set<String> alwaysOccluding, Set<String> neverOccluding) {
        this.alwaysOccluding = Set.copyOf(alwaysOccluding);
        this.neverOccluding = Set.copyOf(neverOccluding);
    }

    /**
     * @param blockKey the namespaced block name, used to match the configured overrides.
     * @param platformSaysOccluding what the platform reports for the block, which is answered for the block's default
     * state rather than this particular one.
     * @param fullBlockVariant whether this state fills its whole cube even though the default state does not, which
     * is what a double slab is. Such a state occludes regardless of the default's answer.
     * @return whether a ray passing through this block state should count it as occluding.
     */
    public boolean occludes(String blockKey, boolean platformSaysOccluding, boolean fullBlockVariant) {
        if (neverOccluding.contains(blockKey)) {
            return false;
        }
        if (alwaysOccluding.contains(blockKey)) {
            return true;
        }
        return platformSaysOccluding || fullBlockVariant;
    }

    /** @return the configured names, so the caller can report any which matched no block. */
    public Set<String> configuredNames() {
        java.util.Set<String> names = new java.util.HashSet<>(alwaysOccluding);
        names.addAll(neverOccluding);
        return names;
    }

    public boolean hasOverrides() {
        return !alwaysOccluding.isEmpty() || !neverOccluding.isEmpty();
    }
}
