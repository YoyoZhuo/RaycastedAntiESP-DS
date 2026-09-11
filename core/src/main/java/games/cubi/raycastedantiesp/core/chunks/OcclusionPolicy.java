/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.chunks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether one block state counts as blocking line of sight, on top of whatever the platform reports.
 * <p>
 * Kept apart from the platform lookup so the rules can be tested without a server, and so the platform only has to
 * answer the two questions this needs about a block state.
 */
public final class OcclusionPolicy {
    /** An entry may name one block, or use {@code *} to stand for any run of characters, as in {@code *_stairs}. */
    private record Rule(String configured, Pattern pattern) {
        static Rule of(String configured) {
            StringBuilder regex = new StringBuilder();
            for (String literal : configured.split(java.util.regex.Pattern.quote("*"), -1)) {
                if (regex.length() > 0) {
                    regex.append(".*");
                }
                regex.append(Pattern.quote(literal));
            }
            return new Rule(configured, Pattern.compile(regex.toString()));
        }

        boolean matches(String blockKey) {
            return pattern.matcher(blockKey).matches();
        }
    }

    private final List<Rule> alwaysOccluding;
    private final List<Rule> neverOccluding;

    public OcclusionPolicy(Collection<String> alwaysOccluding, Collection<String> neverOccluding) {
        this.alwaysOccluding = compile(alwaysOccluding);
        this.neverOccluding = compile(neverOccluding);
    }

    private static List<Rule> compile(Collection<String> configured) {
        List<Rule> rules = new ArrayList<>(configured.size());
        for (String entry : configured) {
            rules.add(Rule.of(entry));
        }
        return List.copyOf(rules);
    }

    /**
     * @param blockKey the namespaced block name, matched against the configured overrides.
     * @param platformSaysOccluding what the platform reports for the block, which it answers for the block's default
     * state rather than this particular one.
     * @param fullBlockVariant whether this state fills its whole cube even though the default state does not, which
     * is what a double slab is. Such a state occludes regardless of the default's answer.
     * @return whether a ray passing through this block state should count it as occluding.
     */
    public boolean occludes(String blockKey, boolean platformSaysOccluding, boolean fullBlockVariant) {
        // Off wins over on, so listing a block in both resolves towards the weaker claim rather than hiding players
        // behind something the config also says is see-through.
        if (matches(neverOccluding, blockKey)) {
            return false;
        }
        if (matches(alwaysOccluding, blockKey)) {
            return true;
        }
        return platformSaysOccluding || fullBlockVariant;
    }

    private static boolean matches(List<Rule> rules, String blockKey) {
        for (Rule rule : rules) {
            if (rule.matches(blockKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param allBlockKeys every block name that exists on this server version.
     * @return the configured entries which named or matched no block, so a typo is reported rather than ignored.
     */
    public Set<String> unmatchedEntries(Collection<String> allBlockKeys) {
        Set<String> unmatched = new LinkedHashSet<>();
        for (List<Rule> rules : List.of(alwaysOccluding, neverOccluding)) {
            for (Rule rule : rules) {
                if (allBlockKeys.stream().noneMatch(rule::matches)) {
                    unmatched.add(rule.configured());
                }
            }
        }
        return unmatched;
    }

    public boolean hasOverrides() {
        return !alwaysOccluding.isEmpty() || !neverOccluding.isEmpty();
    }
}
