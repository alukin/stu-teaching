// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package ua.cn.stu.blockchain;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Block {
    private final String previousHash;
    private final long timestamp = System.currentTimeMillis();
    private final List<Transaction> transactions;
    private long nonce;
    private String hash;

    public Block(String previousHash, List<Transaction> transactions) {
        this.previousHash = previousHash;
        this.transactions = new ArrayList<>(transactions);
        this.hash = calculateHash();
    }

    public String calculateHash() {
        StringBuilder data = new StringBuilder(previousHash).append(':')
                .append(timestamp).append(':').append(nonce);
        for (Transaction transaction : transactions)
            data.append(':').append(transaction.id());
        return Transaction.sha256Hex(data.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void mine(int difficulty) {
        String target = "0".repeat(difficulty);
        hash = calculateHash();
        while (!hash.startsWith(target)) {
            nonce++;
            hash = calculateHash();
        }
    }

    public String hash() { return hash; }
    public String previousHash() { return previousHash; }
    public List<Transaction> transactions() { return List.copyOf(transactions); }

    // This bypasses normal validation, solely for the integrity experiment.
    public void replaceTransactionForDemo(int index, Transaction replacement) {
        transactions.set(index, replacement);
    }
}
