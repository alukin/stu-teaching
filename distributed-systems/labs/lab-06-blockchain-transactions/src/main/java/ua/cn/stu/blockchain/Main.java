// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package ua.cn.stu.blockchain;

import java.util.List;

public final class Main {
    public static void main(String[] args) {
        int difficulty = args.length == 0 ? 2 : Integer.parseInt(args[0]);
        Wallet alice = new Wallet(), bob = new Wallet(), charlie = new Wallet();
        Blockchain chain = new Blockchain(alice, 100, difficulty);
        String genesisUtxo = chain.replay().ownedBy(alice.publicKey()).get(0).id();

        Transaction aToB = Transaction.signed(alice, List.of(genesisUtxo), List.of(
                new Transaction.Output(bob.publicKey(), 40),
                new Transaction.Output(alice.publicKey(), 60)));
        Transaction bToC = Transaction.signed(bob, List.of(aToB.outputId(0)), List.of(
                new Transaction.Output(charlie.publicKey(), 15),
                new Transaction.Output(bob.publicKey(), 25)));

        demonstrateRejection("Wrong order", () -> chain.addBlock(List.of(bToC, aToB)), chain);
        chain.addBlock(List.of(aToB, bToC));
        System.out.printf("Balances after block 1: Alice=%d, Bob=%d, Charlie=%d%n",
                chain.replay().balance(alice.publicKey()),
                chain.replay().balance(bob.publicKey()),
                chain.replay().balance(charlie.publicKey()));
        System.out.println("Chain valid: " + chain.isValid());

        String aliceChange = aToB.outputId(1);
        Transaction spendOnce = Transaction.signed(alice, List.of(aliceChange), List.of(
                new Transaction.Output(bob.publicKey(), 10),
                new Transaction.Output(alice.publicKey(), 50)));
        Transaction spendTwice = Transaction.signed(alice, List.of(aliceChange), List.of(
                new Transaction.Output(charlie.publicKey(), 20),
                new Transaction.Output(alice.publicKey(), 40)));
        demonstrateRejection("Double spending", () -> chain.addBlock(List.of(spendOnce, spendTwice)), chain);

        Transaction overspend = Transaction.signed(bob, List.of(bToC.outputId(1)),
                List.of(new Transaction.Output(charlie.publicKey(), 26)));
        demonstrateRejection("Overspending", () -> chain.addBlock(List.of(overspend)), chain);

        Transaction tampered = spendOnce.changedOutputsForDemo(List.of(
                new Transaction.Output(bob.publicKey(), 11),
                new Transaction.Output(alice.publicKey(), 49)));
        System.out.println("Signature valid after changing outputs: " + tampered.signatureValid());
        demonstrateRejection("Altered signature payload", () -> chain.addBlock(List.of(tampered)), chain);

        chain.get(1).replaceTransactionForDemo(1, bToC.changedOutputsForDemo(List.of(
                new Transaction.Output(charlie.publicKey(), 16),
                new Transaction.Output(bob.publicKey(), 24))));
        System.out.println("Chain valid after block tampering: " + chain.isValid());
        chain.get(1).replaceTransactionForDemo(1, bToC);
        System.out.println("Chain valid after restoring block: " + chain.isValid());
    }

    private static void demonstrateRejection(String label, Runnable action, Blockchain chain) {
        int before = chain.size();
        try {
            action.run();
            throw new AssertionError("This operation should be rejected: " + label);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            if (chain.size() != before || !chain.isValid())
                throw new AssertionError("A rejected block changed the chain state");
            System.out.println(label + " rejected: " + expected.getMessage());
        }
    }
}
