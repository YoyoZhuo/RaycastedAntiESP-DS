/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.raycast;

import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.implementations.ImmutableLocatableImpl;
import games.cubi.locatables.implementations.ImmutableSpatialImpl;
import games.cubi.raycastedantiesp.core.chunks.ChunkOcclusionView;
import games.cubi.raycastedantiesp.core.view.BlockView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The occluding count is expressed in samples, so the step size is what decides how much occluding material that
 * threshold corresponds to: a target is hidden once the ray crosses about {@code count * stepSize} blocks of it.
 */
class RaycastStepSizeTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final int NO_ALWAYS_SHOW = 0;
    private static final int LONG_ENOUGH = 100;

    @Test
    void samplesTakenInsideAWallScaleInverselyWithTheStepSize() {
        // Probed through the threshold: the wall is hidden at a count it can reach, and visible one above it.
        BlockView wall = occludingColumnsAt(10);

        assertFalse(raycast(wall, 1, 1.0f), "one block of wall is exactly one sample at the default step");
        assertTrue(raycast(wall, 2, 1.0f));

        assertFalse(raycast(wall, 2, 0.5f), "halving the step doubles the samples the same wall produces");
        assertTrue(raycast(wall, 3, 0.5f));

        assertFalse(raycast(wall, 4, 0.25f));
        assertTrue(raycast(wall, 5, 0.25f));
    }

    @Test
    void scalingTheCountWithTheStepKeepsTheSameEffectiveThreshold() {
        // 1.0/1, 0.5/2 and 0.25/4 are all "one block of occluding material", so they must agree on this wall.
        BlockView wall = occludingColumnsAt(10);

        assertFalse(raycast(wall, 1, 1.0f));
        assertFalse(raycast(wall, 2, 0.5f));
        assertFalse(raycast(wall, 4, 0.25f));
    }

    @Test
    void aFinerStepPlacesTheThresholdBetweenWholeBlocks() {
        // A count of 3 at a 0.5 step is a threshold of 1.5 blocks, which no count can express at a 1.0 step. The
        // margin is what stops a ray that only clips a block, and so produces far fewer samples than a full
        // crossing, from being treated as a wall.
        BlockView oneBlockThick = occludingColumnsAt(10);
        BlockView twoBlocksThick = occludingColumnsAt(10, 11);

        assertTrue(raycast(oneBlockThick, 3, 0.5f), "one block is two samples, below the 1.5 block threshold");
        assertFalse(raycast(twoBlocksThick, 3, 0.5f), "two blocks is four samples, above it");
    }

    @Test
    void anEmptyLineOfSightStaysVisibleAtEveryStepSize() {
        BlockView empty = occludingColumnsAt();

        for (float stepSize : new float[]{0.25f, 0.5f, 1.0f, 2.0f}) {
            assertTrue(raycast(empty, 1, stepSize), "step size " + stepSize + " must not invent occlusion");
        }
    }

    /** Traces along the X axis from the origin block out to x=20. */
    private static boolean raycast(BlockView blockView, int maxOccluding, float stepSize) {
        Locatable start = new ImmutableLocatableImpl(WORLD, 0.5, 0.5, 0.5);
        return RaycastUtil.raycast(start, new ImmutableSpatialImpl(20.5, 0.5, 0.5), maxOccluding, NO_ALWAYS_SHOW,
                LONG_ENOUGH, false, blockView, stepSize, null);
    }

    /** @param occludingBlockX x coordinates of full-height occluding columns. */
    private static BlockView occludingColumnsAt(int... occludingBlockX) {
        Set<Integer> occluding = new HashSet<>();
        for (int x : occludingBlockX) {
            occluding.add(x);
        }
        return blockView(occluding::contains);
    }

    private static BlockView blockView(IntPredicate occludingByBlockX) {
        ChunkOcclusionView occlusionView = (ChunkOcclusionView) Proxy.newProxyInstance(
                ChunkOcclusionView.class.getClassLoader(),
                new Class<?>[]{ChunkOcclusionView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    // isOccludingGlobal defaults to converting to local coordinates, so answering it directly keeps
                    // the stub independent of that conversion.
                    case "isOccludingGlobal" -> occludingByBlockX.test((Integer) args[0]);
                    case "isOccludingLocal" -> false;
                    case "isSolid" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        return (BlockView) Proxy.newProxyInstance(
                BlockView.class.getClassLoader(),
                new Class<?>[]{BlockView.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getChunkOcclusionView")) {
                        return occlusionView;
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                }
        );
    }
}
