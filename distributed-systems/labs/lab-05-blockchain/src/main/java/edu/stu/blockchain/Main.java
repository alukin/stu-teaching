// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package edu.stu.blockchain;

public final class Main {
    public static void main(String[] args) {
        int difficulty = args.length == 0 ? 3 : Integer.parseInt(args[0]);
        long started = System.nanoTime();
        Blockchain chain = new Blockchain(difficulty); // Includes the genesis block.
        printBlock(0, chain.get(0), started);

        for (String data : new String[] {"First message", "Second message"}) {
            started = System.nanoTime();
            Block block = chain.add(data);
            printBlock(chain.size() - 1, block, started);
        }

        System.out.println("Valid chain: " + chain.isValid());
        chain.get(1).changeDataForDemo("Changed message");
        System.out.println("After data change: " + chain.isValid());
        chain.get(1).mine(difficulty);
        System.out.println("After re-mining block 1: " + chain.isValid());
    }

    private static void printBlock(int index, Block block, long started) {
        double milliseconds = (System.nanoTime() - started) / 1_000_000.0;
        System.out.printf("Block %d: nonce=%d, time=%.2f ms, hash=%s%n",
                index, block.getNonce(), milliseconds, block.getHash());
    }
}
