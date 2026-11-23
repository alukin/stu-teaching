// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package edu.stu.blockchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Block {
    private String data;
    private final String previousHash;
    private final long timestamp = System.currentTimeMillis();
    private long nonce;
    private String hash;

    public Block(String data, String previousHash) {
        this.data = data;
        this.previousHash = previousHash;
        this.hash = calculateHash();
    }

    public String calculateHash() {
        String input = previousHash + ":" + timestamp + ":" + nonce + ":" + data;
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public void mine(int difficulty) {
        String target = "0".repeat(difficulty);
        hash = calculateHash();
        while (!hash.startsWith(target)) {
            nonce++;
            hash = calculateHash();
        }
    }

    public String getHash() { return hash; }
    public String getPreviousHash() { return previousHash; }
    public long getNonce() { return nonce; }

    // Only for demonstrating what happens when stored data is changed.
    public void changeDataForDemo(String newData) { data = newData; }
}
