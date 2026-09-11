/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.chunks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionPolicyTest {
    private static final String SLAB = "minecraft:oak_slab";
    private static final String STAIRS = "minecraft:oak_stairs";
    private static final String LEAVES = "minecraft:pale_oak_leaves";
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
    void anExactNameCoversOnlyThatBlock() {
        OcclusionPolicy policy = new OcclusionPolicy(Set.of(STAIRS), Set.of());

        assertTrue(policy.occludes(STAIRS, false, false));
        assertFalse(policy.occludes("minecraft:birch_stairs", false, false));
    }

    @Test
    void aWildcardCoversEveryBlockWhoseNameFits() {
        // The point of the wildcard: one entry covers all 58 stairs, and keeps covering new wood types added by
        // later Minecraft versions.
        OcclusionPolicy policy = new OcclusionPolicy(List.of("*_stairs", "*_leaves"), Set.of());

        assertTrue(policy.occludes(STAIRS, false, false));
        assertTrue(policy.occludes("minecraft:polished_blackstone_brick_stairs", false, false));
        assertTrue(policy.occludes(LEAVES, false, false));
        assertFalse(policy.occludes(SLAB, false, false), "blocks the pattern does not fit are untouched");
    }

    @Test
    void aWildcardCanBeAnchoredToANamespaceOrPrefix() {
        OcclusionPolicy policy = new OcclusionPolicy(List.of("minecraft:oak_*"), Set.of());

        assertTrue(policy.occludes(STAIRS, false, false));
        assertTrue(policy.occludes(SLAB, false, false));
        assertFalse(policy.occludes("otherplugin:oak_stairs", false, false), "the namespace is part of the match");
    }

    @Test
    void aLiteralNameIsNotTreatedAsARegex() {
        // Block names contain dots in some namespaces, and a dot must not quietly match any character.
        OcclusionPolicy policy = new OcclusionPolicy(Set.of("mod.pack:stone"), Set.of());

        assertFalse(policy.occludes("modxpack:stone", false, false));
        assertTrue(policy.occludes("mod.pack:stone", false, false));
    }

    @Test
    void turningABlockOffBeatsBothThePlatformAndTheFullCubeVariant() {
        OcclusionPolicy policy = new OcclusionPolicy(Set.of(), Set.of(STONE, SLAB));

        assertFalse(policy.occludes(STONE, true, false));
        assertFalse(policy.occludes(SLAB, false, true), "turning a block off must also suppress its double slab state");
    }

    @Test
    void offWinsOverOnWhenAnEntryMatchesBoth() {
        OcclusionPolicy policy = new OcclusionPolicy(List.of("*_stairs"), List.of(STAIRS));

        assertFalse(policy.occludes(STAIRS, false, false));
        assertTrue(policy.occludes("minecraft:birch_stairs", false, false));
    }

    @Test
    void entriesMatchingNoBlockAreReported() {
        OcclusionPolicy policy = new OcclusionPolicy(List.of("*_stairs", "minecraft:oka_leaves"), List.of("*_nonsense"));

        Set<String> unmatched = policy.unmatchedEntries(List.of(STAIRS, LEAVES, STONE));

        assertEquals(Set.of("minecraft:oka_leaves", "*_nonsense"), unmatched);
    }
}
