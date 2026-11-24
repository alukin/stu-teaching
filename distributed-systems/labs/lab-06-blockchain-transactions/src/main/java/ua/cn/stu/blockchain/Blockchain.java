// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package ua.cn.stu.blockchain;

import java.util.*;

public final class Blockchain {
    private final List<Block> blocks = new ArrayList<>();
    private final int difficulty;
    private final String trustedGenesisHash;

    public Blockchain(Wallet initialOwner, long initialAmount, int difficulty) {
        if (difficulty < 1 || difficulty > 4)
            throw new IllegalArgumentException("Difficulty must be 1..4");
        this.difficulty = difficulty;
        Block genesis = new Block("0", List.of(Transaction.genesis(initialOwner.publicKey(), initialAmount)));
        genesis.mine(difficulty);
        blocks.add(genesis);
        trustedGenesisHash = genesis.hash();
    }

    // Validate ALL transactions before mining or publishing the new block.
    public Block addBlock(List<Transaction> transactions) {
        if (transactions.isEmpty()) throw new IllegalArgumentException("Empty block");
        Ledger candidate = replay();
        for (Transaction transaction : transactions) candidate.apply(transaction);
        Block block = new Block(blocks.get(blocks.size() - 1).hash(), transactions);
        block.mine(difficulty);
        blocks.add(block);
        return block;
    }

    // Rebuild the UTXO set from genesis instead of trusting a mutable balance map.
    public Ledger replay() {
        Ledger state = new Ledger();
        String previous = "0";
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (!block.previousHash().equals(previous)
                    || !block.hash().equals(block.calculateHash())
                    || !block.hash().startsWith("0".repeat(difficulty)))
                throw new IllegalStateException("Broken block chain at index " + i);
            if (i == 0) {
                if (!block.hash().equals(trustedGenesisHash) || block.transactions().size() != 1)
                    throw new IllegalStateException("Genesis does not match trusted anchor");
                state.applyGenesis(block.transactions().get(0));
            } else {
                if (block.transactions().isEmpty()) throw new IllegalStateException("Empty block");
                for (Transaction tx : block.transactions()) state.apply(tx);
            }
            previous = block.hash();
        }
        return state;
    }

    public boolean isValid() {
        try {
            replay();
            return true;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            return false;
        }
    }

    public Block get(int index) { return blocks.get(index); }
    public int size() { return blocks.size(); }
}
