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
import games.cubi.raycastedantiesp.core.config.profiles.CheckProfile;
import games.cubi.raycastedantiesp.core.config.profiles.CheckProfileSet;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import games.cubi.raycastedantiesp.paper.utils.PaperScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves which strictness profile a viewer is checked under, from the permissions they hold.
 * <p>
 * Permissions are deliberately only read here, on a thread which is allowed to ask Bukkit about them. The engine and
 * the packet layer never resolve anything: they read the profile already published onto the viewer's
 * {@link PlayerData}. That keeps permission lookups off the Netty threads entirely, and off the per-tick path.
 * <p>
 * Resolution happens when a player's client has loaded its world, and whenever an operator asks for it. It is not
 * polled, so a permission granted mid-session takes effect on the player's next join or on an explicit refresh.
 */
public final class PaperCheckProfileResolver {

    /** The profile permissions this plugin registered, so a reload can retire the ones no longer configured. */
    private static final List<String> registeredPermissions = new ArrayList<>();

    private PaperCheckProfileResolver() {}

    /**
     * Picks the profile a player should be checked under.
     * <p>
     * The profile set is ordered by descending priority, so the first permission the player holds is the winner and
     * a player in several permission groups gets a defined answer rather than whichever was checked first.
     *
     * @return the winning profile, or the default profile when the player holds none.
     */
    public static CheckProfile resolve(Player player) {
        CheckProfileSet profiles = ConfigManager.get().getCheckProfiles();
        for (CheckProfile profile : profiles.byPriority()) {
            if (player.hasPermission(profile.permission())) {
                return profile;
            }
        }
        return profiles.defaultProfile();
    }

    /**
     * Resolves and assigns one player's profile. Must be called from a thread allowed to read that player's
     * permissions, which means the main thread on Paper and the player's own region thread on Folia.
     *
     * @return the profile the player will move onto, or null when the player has no packet state yet.
     */
    public static @Nullable CheckProfile refresh(Player player) {
        PlayerData playerData = PlayerRegistry.getInstance().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return null;
        }
        CheckProfile profile = resolve(player);
        // Only a request. The engine performs the switch, because turning a check off means revealing what it hid
        // before the packet layer stops suppressing packets for those entities.
        playerData.setPendingCheckProfile(profile);
        return profile;
    }

    /**
     * Re-resolves every online player, scheduling each onto the thread which owns them.
     * <p>
     * Used after a reload, which replaces every profile object, so viewers left holding one from the previous load
     * would otherwise keep being checked under settings no longer in the config file.
     *
     * @return the number of players a refresh was scheduled for.
     */
    public static int refreshAll(RaycastedAntiESP plugin) {
        int scheduled = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PaperScheduler.runForAudience(plugin, player, () -> refresh(player));
            scheduled++;
        }
        return scheduled;
    }

    /**
     * Registers a permission for every configured profile, so permission plugins can suggest and manage them.
     * <p>
     * Called at start-up and after every reload, retiring permissions for profiles which have since been removed.
     * Registration is a convenience only, because permission plugins answer for unregistered nodes just as well.
     */
    public static void syncRegisteredPermissions() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        for (String permission : registeredPermissions) {
            Permission registered = pluginManager.getPermission(permission);
            if (registered != null) {
                pluginManager.removePermission(registered);
            }
        }
        registeredPermissions.clear();

        CheckProfileSet profiles = ConfigManager.get().getCheckProfiles();
        for (CheckProfile profile : profiles.byPriority()) {
            if (pluginManager.getPermission(profile.permission()) != null) {
                // Something else already owns this node. Leave it alone rather than fighting over it.
                continue;
            }
            pluginManager.addPermission(new Permission(
                    profile.permission(),
                    "Checks the holder under the RaycastedAntiESP '" + profile.name() + "' strictness profile",
                    PermissionDefault.FALSE));
            registeredPermissions.add(profile.permission());
        }

        if (!profiles.isEmpty()) {
            Logger.info("Registered " + registeredPermissions.size() + " of " + profiles.size()
                    + " check profile permissions.", 5, PaperCheckProfileResolver.class);
        }
    }
}
