/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.config.profiles;

import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.ConfigLoadException;
import games.cubi.raycastedantiesp.core.config.ConfigReader;
import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.config.raycast.PlayerConfig;
import games.cubi.raycastedantiesp.core.config.raycast.TileEntityConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The configured strictness profiles, already resolved against the global config.
 * <p>
 * Held as an immutable snapshot published with the rest of the config, so the resolver can hand a viewer a profile
 * without any further lookup on the hot path.
 */
public final class CheckProfileSet {

    private static final String NAME_PATTERN = "[a-z0-9_-]+";

    private final CheckProfile defaultProfile;
    /** Ordered highest priority first, so the first permission a viewer holds is the one that wins. */
    private final List<CheckProfile> byPriority;
    private final Map<String, CheckProfile> byName;

    private CheckProfileSet(CheckProfile defaultProfile, List<CheckProfile> byPriority, Map<String, CheckProfile> byName) {
        this.defaultProfile = defaultProfile;
        this.byPriority = List.copyOf(byPriority);
        this.byName = Map.copyOf(byName);
    }

    /**
     * Builds the profile set from the {@code checks.profiles} block, resolving each profile against the global config
     * for the same check.
     *
     * @param node the {@code checks.profiles} node, which may be absent.
     */
    public static CheckProfileSet load(@Nullable ConfigurationNode node, String path,
                                       PlayerConfig basePlayer, EntityConfig baseEntity, TileEntityConfig baseTile) {
        CheckProfile defaultProfile = new CheckProfile(CheckProfile.DEFAULT_NAME, Integer.MIN_VALUE,
                CheckProfile.permissionFor(CheckProfile.DEFAULT_NAME), basePlayer, baseEntity, baseTile);

        if (node == null || node.virtual() || node.childrenMap().isEmpty()) {
            return new CheckProfileSet(defaultProfile, List.of(), Map.of());
        }

        List<CheckProfile> profiles = new ArrayList<>();
        Map<String, CheckProfile> named = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            String name = String.valueOf(entry.getKey());
            String profilePath = path + "." + name;
            validateName(name, profilePath);

            ConfigurationNode profileNode = entry.getValue();
            CheckProfile profile = new CheckProfile(
                    name,
                    readPriority(profileNode, profilePath),
                    CheckProfile.permissionFor(name),
                    basePlayer.withOverrides(ProfileOverrides.load(ConfigReader.node(profileNode, "player"), profilePath + ".player")),
                    baseEntity.withOverrides(ProfileOverrides.load(ConfigReader.node(profileNode, "entity"), profilePath + ".entity")),
                    baseTile.withOverrides(ProfileOverrides.load(ConfigReader.node(profileNode, "tile-entity"), profilePath + ".tile-entity"))
            );

            if (profile.sameChecksAs(defaultProfile)) {
                Logger.warning(profilePath + " overrides nothing, so holders of " + profile.permission()
                        + " are checked exactly as everyone else.", 4, CheckProfileSet.class);
            }
            profiles.add(profile);
            named.put(name, profile);
        }

        // Highest priority first, then by name, so two profiles sharing a priority still resolve deterministically
        // rather than following the order the config file happened to be written in.
        profiles.sort(Comparator.comparingInt(CheckProfile::priority).reversed().thenComparing(CheckProfile::name));
        warnOnSharedPriorities(profiles, path);
        return new CheckProfileSet(defaultProfile, profiles, named);
    }

    private static void validateName(String name, String path) {
        if (CheckProfile.DEFAULT_NAME.equals(name)) {
            throw new ConfigLoadException(path + " uses the reserved profile name " + CheckProfile.DEFAULT_NAME
                    + ", which always means the global config. Rename this profile.");
        }
        if (!name.matches(NAME_PATTERN)) {
            throw new ConfigLoadException(path + " is not a usable profile name. Profile names become the permission "
                    + CheckProfile.permissionFor("<name>") + ", so they may only contain lowercase letters, digits,"
                    + " underscores and hyphens.");
        }
    }

    private static int readPriority(ConfigurationNode profileNode, String path) {
        ConfigurationNode priorityNode = ConfigReader.node(profileNode, "priority");
        if (priorityNode.virtual() || priorityNode.raw() == null) {
            throw new ConfigLoadException(path + ".priority is required. It decides which profile wins for a player"
                    + " who holds more than one profile permission, which is common with inherited permission groups.");
        }
        int priority = ConfigReader.integer(priorityNode, path + ".priority");
        if (priority == Integer.MIN_VALUE) {
            throw new ConfigLoadException(path + ".priority may not be " + Integer.MIN_VALUE
                    + ", which is reserved for the implicit default profile.");
        }
        return priority;
    }

    private static void warnOnSharedPriorities(List<CheckProfile> sorted, String path) {
        for (int i = 1; i < sorted.size(); i++) {
            CheckProfile previous = sorted.get(i - 1);
            CheckProfile current = sorted.get(i);
            if (previous.priority() == current.priority()) {
                Logger.warning("Profiles " + previous.name() + " and " + current.name() + " under " + path
                        + " share priority " + current.priority() + ". A player holding both is given "
                        + previous.name() + " because ties break on name. Give them distinct priorities if that is"
                        + " not what you meant.", 4, CheckProfileSet.class);
            }
        }
    }

    /** @return the profile used by viewers who hold no profile permission, built from the global config. */
    public CheckProfile defaultProfile() {
        return defaultProfile;
    }

    /** @return the configured profiles, highest priority first. Does not include {@link #defaultProfile()}. */
    public List<CheckProfile> byPriority() {
        return byPriority;
    }

    public @Nullable CheckProfile byName(String name) {
        return CheckProfile.DEFAULT_NAME.equals(name) ? defaultProfile : byName.get(name);
    }

    /** @return whether no profiles are configured, so every viewer uses the global config. */
    public boolean isEmpty() {
        return byPriority.isEmpty();
    }

    public int size() {
        return byPriority.size();
    }
}
