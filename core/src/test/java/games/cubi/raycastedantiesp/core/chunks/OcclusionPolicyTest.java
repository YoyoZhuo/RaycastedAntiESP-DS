/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.chunks;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionPolicyTest {
    private static final String SLAB = "minecraft:oak_slab";
    private static final String STAIRS = "minecraft:oak_stairs";
    private static final String STONE = "minecraft:stone";

    private static final OcclusionPolicy NO_OVERRIDES = new OcclusionPolicy(Set.of(), Set.of());

    @Test
    void withoutOverridesThePlatformAnswerStands() {
        assertTrue(NO_OVERRIDES.occludes(STONE, true, false));
        assertFalse(NO_OVERRIDES.occludes(STAIRS, false, false));
        assertFalse(NO_OVERRIDES.hasOverrides());
    }

    @Test
    void aFullCubeVariantOccludesEvenWhenItsDefaultStateDoesNot() {
        // The double slab case: Bukkit answers for the half slab, so the platform says false for a state which
        // actually fills its cube.
        assertFalse(NO_OVERRIDES.occludes(SLAB, false, false), "a half slab still does not block sight");
        assertTrue(NO_OVERRIDES.occludes(SLAB, false, true), "a double slab does");
    }

    @Test
    void forcingABlockOnCoversEveryOneOfItsStates() {
        OcclusionPolicy policy = new OcclusionPolicy(Set.of(STAIRS), Set.of());

        assertTrue(policy.occludes(STAIRS, false, false));
        assertFalse(policy.occludes(SLAB, false, false), "other blocks are untouched");
    }

    @Test
    void forcingABlockOffBeatsBothThePlatformAndTheFullCubeVariant() {
        OcclusionPolicy policy = new OcclusionPolicy(Set.of(), Set.of(STONE, SLAB));

        assertFalse(policy.occludes(STONE, true, false));
        assertFalse(policy.occludes(SLAB, false, true), "turning a block off must also suppress its double slab state");
    }

    @Test
    void offWinsOverOnWhenABlockIsInBothLists() {
        // Listing a block twice is a mistake, so resolve it towards the weaker claim rather than silently occluding.
        OcclusionPolicy policy = new OcclusionPolicy(Set.of(STONE), Set.of(STONE));

        assertFalse(policy.occludes(STONE, true, false));
    }

    @Test
    void configuredNamesReportsEverythingTheUserListed() {
        OcclusionPolicy policy = new OcclusionPolicy(Set.of(STAIRS), Set.of(STONE));

        assertTrue(policy.configuredNames().containsAll(Set.of(STAIRS, STONE)));
        assertTrue(policy.hasOverrides());
    }
}
