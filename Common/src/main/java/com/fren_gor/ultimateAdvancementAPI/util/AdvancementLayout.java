package com.fren_gor.ultimateAdvancementAPI.util;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;

import java.util.*;

/**
 * Vanilla-style tidy-tree layout, calculated once before native definitions are created.
 * Adapted from IOVEYOUMC0/UltimateAdvancementAPI (14b8895), under LGPL-3.0-or-later.
 * Traversals are iterative and ancestor membership uses parent identity.
 */
public final class AdvancementLayout {
    private AdvancementLayout() {}

    public static void applyVanilla(RootAdvancement root, Collection<? extends BaseAdvancement> advancements) {
        Objects.requireNonNull(root, "Root advancement is null");
        Objects.requireNonNull(advancements, "Advancements are null");
        Map<Advancement, Position> positions = new IdentityHashMap<>();
        Set<AdvancementKey> keys = new HashSet<>();
        Position rootPosition = new Position(root);
        positions.put(root, rootPosition);
        keys.add(root.getKey());
        for (BaseAdvancement advancement : advancements) {
            Objects.requireNonNull(advancement, "An advancement is null");
            if (positions.put(advancement, new Position(advancement)) != null || !keys.add(advancement.getKey())) {
                throw new IllegalArgumentException("Duplicate advancement: " + advancement.getKey());
            }
        }
        for (BaseAdvancement advancement : advancements) {
            Position parent = positions.get(advancement.getParent());
            if (parent == null) throw new IllegalArgumentException("Advancement parent is outside the registered tree");
            Position child = positions.get(advancement);
            child.parent = parent;
            parent.children.add(child);
        }
        for (Position position : positions.values()) {
            position.children.sort(Comparator.comparing(child -> child.key));
            Position previous = null;
            for (int i = 0; i < position.children.size(); i++) {
                Position child = position.children.get(i);
                child.previousSibling = previous;
                child.childIndex = i + 1;
                previous = child;
            }
        }
        List<Position> order = new ArrayList<>(positions.size());
        Deque<Position> stack = new ArrayDeque<>();
        stack.push(rootPosition);
        while (!stack.isEmpty()) {
            Position position = stack.pop();
            order.add(position);
            for (int i = position.children.size() - 1; i >= 0; i--) stack.push(position.children.get(i));
        }
        // Each non-root node has one parent, so a cycle must be disconnected from the root.
        if (order.size() != positions.size()) throw new IllegalArgumentException("Advancement tree is disconnected or cyclic");
        Position cursor = rootPosition;
        while (true) {
            if (cursor.nextChild < cursor.children.size()) {
                cursor = cursor.children.get(cursor.nextChild++);
                continue;
            }
            cursor.firstWalk();
            Position parent = cursor.parent;
            if (parent == null) break;
            parent.defaultAncestor = cursor.apportion(parent.defaultAncestor == null ? cursor : parent.defaultAncestor);
            cursor = parent;
        }
        float min = 0;
        for (Position position : order) {
            if (position.parent != null) {
                position.depth = position.parent.depth + 1;
                position.modSum = position.parent.modSum + position.parent.mod;
            }
            position.y += position.modSum;
            min = Math.min(min, position.y);
        }
        for (Position position : order) position.advancement.getDisplay().setCoordinates(position.depth, position.y - min);
    }

    private static final class Position {
        private final Advancement advancement;
        private final String key;
        private final List<Position> children = new ArrayList<>();
        private Position parent, previousSibling, ancestor, thread, defaultAncestor;
        private int childIndex, nextChild, depth;
        private float y, mod, change, shift, modSum;

        private Position(Advancement advancement) {
            this.advancement = advancement;
            this.key = advancement.getKey().toString();
            this.ancestor = this;
        }

        private void firstWalk() {
            if (children.isEmpty()) {
                y = previousSibling == null ? 0 : previousSibling.y + 1;
                return;
            }
            float shiftValue = 0, changeValue = 0;
            for (int i = children.size() - 1; i >= 0; i--) {
                Position child = children.get(i);
                child.y += shiftValue;
                child.mod += shiftValue;
                changeValue += child.change;
                shiftValue += child.shift + changeValue;
            }
            float midpoint = (children.get(0).y + children.get(children.size() - 1).y) / 2;
            y = previousSibling == null ? midpoint : previousSibling.y + 1;
            mod = y - midpoint;
        }

        private Position firstOrThread() {
            return thread != null ? thread : (children.isEmpty() ? null : children.get(0));
        }

        private Position lastOrThread() {
            return thread != null ? thread : (children.isEmpty() ? null : children.get(children.size() - 1));
        }

        private Position apportion(Position defaultAncestor) {
            if (previousSibling == null) return defaultAncestor;
            Position vir = this, vor = this, vil = previousSibling, vol = parent.children.get(0);
            float sir = mod, sor = mod, sil = vil.mod, sol = vol.mod;
            while (vil.lastOrThread() != null && vir.firstOrThread() != null) {
                vil = vil.lastOrThread();
                vir = vir.firstOrThread();
                vol = vol.firstOrThread();
                vor = vor.lastOrThread();
                vor.ancestor = this;
                float requiredShift = vil.y + sil - (vir.y + sir) + 1;
                if (requiredShift > 0) {
                    Position left = vil.ancestor.parent == parent ? vil.ancestor : defaultAncestor;
                    left.moveSubtree(this, requiredShift);
                    sir += requiredShift;
                    sor += requiredShift;
                }
                sil += vil.mod;
                sir += vir.mod;
                sol += vol.mod;
                sor += vor.mod;
            }
            if (vil.lastOrThread() != null && vor.lastOrThread() == null) {
                vor.thread = vil.lastOrThread();
                vor.mod += sil - sor;
            } else {
                if (vir.firstOrThread() != null && vol.firstOrThread() == null) {
                    vol.thread = vir.firstOrThread();
                    vol.mod += sir - sol;
                }
                defaultAncestor = this;
            }
            return defaultAncestor;
        }

        private void moveSubtree(Position right, float requiredShift) {
            int subtrees = right.childIndex - childIndex;
            if (subtrees != 0) {
                right.change -= requiredShift / subtrees;
                change += requiredShift / subtrees;
            }
            right.shift += requiredShift;
            right.y += requiredShift;
            right.mod += requiredShift;
        }
    }
}
