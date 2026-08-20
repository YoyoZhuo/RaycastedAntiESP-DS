/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.config;

import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.WorldFilterConfig;
import games.cubi.raycastedantiesp.core.config.WorldFilterMode;
import games.cubi.raycastedantiesp.core.world.WorldCheckRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the world names in {@code checks.worlds} to Bukkit world UUIDs and publishes them to
 * {@link WorldCheckRegistry}.
 * <p>
 * A configured world may not be loaded when the config is read, so the resolution is repeated whenever a world is
 * loaded or unloaded.
 */
public final class PaperWorldCheckResolver implements Listener {
    private static PaperWorldCheckResolver instance;

    private PaperWorldCheckResolver() {
    }

    public static void initialise(Plugin plugin) {
        if (instance != null) {
            return;
        }
        instance = new PaperWorldCheckResolver();
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        resolveAndPublish();
    }

    /** Re-resolves the configured world names and republishes the filter. */
    public static void resolveAndPublish() {
        WorldFilterConfig config = ConfigManager.get().getWorldFilterConfig();
        Set<UUID> worldIDs = new HashSet<>();
        List<String> unresolved = new ArrayList<>();

        for (String worldName : config.worldNames()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                unresolved.add(worldName);
                continue;
            }
            worldIDs.add(world.getUID());
        }

        WorldCheckRegistry.publish(config.mode(), worldIDs);
        warnAboutUnresolvedWorlds(config, unresolved);
        logActiveFilter(config, worldIDs.size());
    }

    private static void warnAboutUnresolvedWorlds(WorldFilterConfig config, List<String> unresolved) {
        if (unresolved.isEmpty()) {
            return;
        }
        // A whitelist entry that never resolves is a protection gap, whereas an unresolved blacklist entry only means
        // the exemption is not in effect yet, so the two deserve different wording.
        String consequence = config.mode() == WorldFilterMode.WHITELIST
                ? "no checks will run in them once they load until the world names are corrected"
                : "they are not exempt from checks";
        Logger.warning("checks.worlds.list names worlds which are not loaded, so " + consequence + ": "
                + String.join(", ", unresolved) + ". This is expected if these worlds are loaded later by another"
                + " plugin; the list is re-resolved whenever a world loads.", 3, PaperWorldCheckResolver.class);
    }

    private static void logActiveFilter(WorldFilterConfig config, int resolvedCount) {
        if (WorldCheckRegistry.checksEveryWorld()) {
            Logger.info("Checks are active in every world.", 5, PaperWorldCheckResolver.class);
            return;
        }
        Logger.info("Checks are restricted by checks.worlds: mode=" + config.mode().getName()
                + " resolvedWorlds=" + resolvedCount + "/" + config.worldNames().size(), 5, PaperWorldCheckResolver.class);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        resolveAndPublish();
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        resolveAndPublish();
    }
}
