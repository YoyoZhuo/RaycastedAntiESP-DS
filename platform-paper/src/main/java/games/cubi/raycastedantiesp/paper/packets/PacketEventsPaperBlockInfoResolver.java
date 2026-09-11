/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.packets;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.OcclusionPolicy;
import games.cubi.raycastedantiesp.packetevents.config.PacketEventsBlockProcessorConfig;
import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PacketEventsPaperBlockInfoResolver implements BlockInfoResolver {
    private final boolean[] occlusionArray;
    /** Raw Bukkit TileState capability, before plugin config overrides. */
    private final boolean[] rawTileEntityArray;
    /** Anti-ESP managed tile entity state, after exemptions and force-includes. */
    private final boolean[] tileEntityArray;

    public static PacketEventsPaperBlockInfoResolver get;
    /** Applies the configured occlusion overrides on top of what Bukkit reports for each block. */
    private final OcclusionPolicy occlusionPolicy;
    /** Configured block names which matched no block, reported once the scan knows every name it saw. */
    private final Set<String> unmatchedOverrideNames = new HashSet<>();

    public PacketEventsPaperBlockInfoResolver() {
        get = this;
        PacketEventsBlockProcessorConfig startupConfig = RaycastedAntiESP.getConfigManager() == null ? null
                : RaycastedAntiESP.getConfigManager().getExtensionConfig(PacketEventsBlockProcessorConfig.class);
        occlusionPolicy = startupConfig == null
                ? new OcclusionPolicy(Set.of(), Set.of())
                : new OcclusionPolicy(Set.copyOf(startupConfig.alwaysOccludingBlocks()), Set.copyOf(startupConfig.neverOccludingBlocks()));
        boolean[][] result = iterateBlockIDs(false);
        occlusionArray = result[0];
        rawTileEntityArray = result[1];
        tileEntityArray = Arrays.copyOf(rawTileEntityArray, rawTileEntityArray.length);
        PacketEventsBlockProcessorConfig config = RaycastedAntiESP.getConfigManager().getExtensionConfig(PacketEventsBlockProcessorConfig.class);
        if (config != null) {
            for (int blockStateId : config.tileEntityExemptedIds()) {
                if (blockStateId >= 0 && blockStateId < tileEntityArray.length) {
                    tileEntityArray[blockStateId] = false;
                }
            }
            for (int blockStateId : config.tileEntityForceIncludedIds()) {
                if (blockStateId >= 0 && blockStateId < tileEntityArray.length) {
                    tileEntityArray[blockStateId] = true;
                }
            }
        }
    }

    /**
     * @return Boolean array with two nested boolean arrays. <code>boolean[0]</code> returns the occlusion status array, <code>boolean[1]</code> returns the tile entity status array. Both arrays are indexed by block state ID. Air blocks and invalid IDs are treated as non-occluding and non-tile-entity, and trailing air IDs are ignored to save memory.
     */
    public boolean[][] iterateBlockIDs(boolean materialToIDMode) {
        boolean run = true;
        int airs = 0;
        int lastNonAirID = 0;
        Map<Integer, Boolean> occlusion = new HashMap<>(111000); //Tests show 30,000 block IDs in 1.21.11, and we scan forwards for 80k air ids just in case, so 111k is enough. This is a pointless micro optimization but why not
        Set<String> seenBlockKeys = new HashSet<>();
        Map<Integer, Boolean> tileEntity = new HashMap<>(111000);
        int iterator = 0;
        while (run) {
            BlockData blockData = SpigotConversionUtil.toBukkitBlockData(WrappedBlockState.getByGlobalId(iterator));
            if (blockData == null) {
                Logger.warning("Material for block state ID " + iterator + " is null, stopping iteration. This is not expected to happen.", 5, PacketEventsPaperBlockInfoResolver.class);
                run = false;
                continue;
            }
            if (blockData.getMaterial() == Material.AIR) {
                airs++;
                if (airs > 80000) { // There is a sequence of ~40 air blocks around ID 100, and another of several hundred at ~3000. We scan forwards 80k to future-proof any mojank. Since it runs once at startup, perf is irrelevant here
                    run = false;
                    continue;
                }
            }
            else {
                airs = 0;
                lastNonAirID = iterator;
                if (materialToIDMode) {
                    Logger.info(blockData.getAsString() + iterator,1);
                }
            }

            // Bukkit answers isOccluding for a block's DEFAULT state, but this loop walks every state. A scan of
            // all 1095 blocks on 1.21.4 found slabs to be the only case where that matters: a double slab fills its
            // cube and Minecraft treats it as solid, yet the default state is the half slab, so the material-level
            // answer wrongly reports the whole block as see-through.
            boolean fullBlockVariant = blockData instanceof Slab slab && slab.getType() == Slab.Type.DOUBLE;
            String blockKey = blockData.getMaterial().getKey().toString();
            seenBlockKeys.add(blockKey);
            occlusion.put(iterator, occlusionPolicy.occludes(blockKey, blockData.getMaterial().isOccluding(), fullBlockVariant));
            try {
                if (blockData.createBlockState() instanceof TileState) {
                    //Logger.debug("tile at" + iterator + " is tile entity" + material.name());
                    tileEntity.put(iterator, true);
                } else {
                    tileEntity.put(iterator, false);
                }
            } catch (Exception a) {
                tileEntity.put(iterator, false);
                // will sometimes inconsistently happen, just ignore it ig?
            }
            iterator++;
        }
        recordUnmatchedOverrideNames(seenBlockKeys);
        boolean[][] result = new boolean[2][lastNonAirID + 1];
        for (int i = 0; i < (lastNonAirID + 1) /*Ignore the trailing airs*/; i++) {
            result[0][i] = occlusion.get(i);
            result[1][i] = tileEntity.get(i);
        }
        return result;
    }

    /**
     * Remembers configured block names which matched nothing, so a typo is reported rather than silently ignored.
     * Collected here because only the scan knows which block names actually exist on this server version.
     */
    private void recordUnmatchedOverrideNames(Set<String> seenBlockKeys) {
        unmatchedOverrideNames.clear();
        for (String configured : occlusionPolicy.configuredNames()) {
            if (!seenBlockKeys.contains(configured)) {
                unmatchedOverrideNames.add(configured);
            }
        }
        if (!unmatchedOverrideNames.isEmpty()) {
            Logger.warning("These occlusion override block names matched no block on this server version and were"
                    + " ignored: " + String.join(", ", unmatchedOverrideNames)
                    + ". Names must be namespaced, for example minecraft:oak_stairs.", 3, PacketEventsPaperBlockInfoResolver.class);
        }
    }

    @Override
    public boolean isOccluding(int blockStateID) {
        if (blockStateID < 0 || blockStateID >= occlusionArray.length) {
            return false; // Default to non-occluding for invalid IDs, should be safe since invalid IDs shouldn't exist in the world
        }
        return occlusionArray[blockStateID];
    }

    @Override
    public boolean isTileEntity(int blockStateID) {
        if (blockStateID < 0 || blockStateID >= tileEntityArray.length) {
            return false; // Default to non-tile-entity for invalid IDs, should be safe since invalid IDs shouldn't exist in the world
        }
        return tileEntityArray[blockStateID];
    }

    @Override
    public boolean hasBlockEntityData(int blockStateID) {
        if (blockStateID < 0 || blockStateID >= rawTileEntityArray.length) {
            return false; // Default to non-block-entity for invalid IDs, should be safe since invalid IDs shouldn't exist in the world
        }
        return rawTileEntityArray[blockStateID];
    }

    public boolean[] dumpOcclusionArray() {
        return occlusionArray;
    }

    private boolean[] dumpTileEntityArray() {
        return tileEntityArray;
    }
}
