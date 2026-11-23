// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package ua.cn.stu.blockchain;

import java.util.ArrayList;
import java.util.List;

public final class Blockchain {
    private final List<Block> blocks = new ArrayList<>();
    private final int difficulty;

    public Blockchain(int difficulty) {
        if (difficulty < 1 || difficulty > 5) {
            throw new IllegalArgumentException("Difficulty must be between 1 and 5");
        }
        this.difficulty = difficulty;
        add("Genesis");
    }

    public Block add(String data) {
        String previous = blocks.isEmpty() ? "0" : blocks.get(blocks.size() - 1).getHash();
        Block block = new Block(data, previous);
        block.mine(difficulty);
        blocks.add(block);
        return block;
    }

    public Block get(int index) { return blocks.get(index); }
    public int size() { return blocks.size(); }

    public boolean isValid() {
        String target = "0".repeat(difficulty);
        for (int i = 0; i < blocks.size(); i++) {
            Block current = blocks.get(i);
            String expectedPrevious = i == 0 ? "0" : blocks.get(i - 1).getHash();
            if (!current.getPreviousHash().equals(expectedPrevious)
                    || !current.getHash().equals(current.calculateHash())
                    || !current.getHash().startsWith(target)) {
                return false;
            }
        }
        return true;
    }
}
