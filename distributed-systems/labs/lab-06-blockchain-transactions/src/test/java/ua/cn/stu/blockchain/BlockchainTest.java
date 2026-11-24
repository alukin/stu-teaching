// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package ua.cn.stu.blockchain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BlockchainTest {
    @Test
    void twoDependentTransactionsInOneBlock() {
        Wallet a = new Wallet(), b = new Wallet(), c = new Wallet();
        Blockchain chain = new Blockchain(a, 100, 1);
        String input = chain.replay().ownedBy(a.publicKey()).get(0).id();
        Transaction first = Transaction.signed(a, List.of(input), List.of(
                new Transaction.Output(b.publicKey(), 40), new Transaction.Output(a.publicKey(), 60)));
        Transaction second = Transaction.signed(b, List.of(first.outputId(0)), List.of(
                new Transaction.Output(c.publicKey(), 15), new Transaction.Output(b.publicKey(), 25)));
        assertThrows(IllegalArgumentException.class, () -> chain.addBlock(List.of(second, first)));
        assertEquals(1, chain.size());
        assertEquals(100, chain.replay().balance(a.publicKey()));
        chain.addBlock(List.of(first, second));
        assertTrue(chain.isValid());
        assertEquals(60, chain.replay().balance(a.publicKey()));
        assertEquals(25, chain.replay().balance(b.publicKey()));
        assertEquals(15, chain.replay().balance(c.publicKey()));
    }

    @Test
    void duplicateInputInSameBlockFailsAtomically() {
        Wallet a = new Wallet(), b = new Wallet();
        Blockchain chain = new Blockchain(a, 100, 1);
        String input = chain.replay().ownedBy(a.publicKey()).get(0).id();
        Transaction one = Transaction.signed(a, List.of(input), List.of(
                new Transaction.Output(b.publicKey(), 10), new Transaction.Output(a.publicKey(), 90)));
        Transaction two = Transaction.signed(a, List.of(input), List.of(
                new Transaction.Output(b.publicKey(), 20), new Transaction.Output(a.publicKey(), 80)));
        assertThrows(IllegalArgumentException.class, () -> chain.addBlock(List.of(one, two)));
        assertEquals(1, chain.size());
        assertEquals(100, chain.replay().balance(a.publicKey()));
        assertEquals(0, chain.replay().balance(b.publicKey()));
        assertTrue(chain.isValid());
    }

    @Test
    void invalidAmountsAndForeignInputsAreRejected() {
        Wallet a = new Wallet(), b = new Wallet();
        Blockchain chain = new Blockchain(a, 100, 1);
        String input = chain.replay().ownedBy(a.publicKey()).get(0).id();
        Transaction overspend = Transaction.signed(a, List.of(input),
                List.of(new Transaction.Output(b.publicKey(), 101)));
        assertThrows(IllegalArgumentException.class, () -> chain.addBlock(List.of(overspend)));
        Transaction stolen = Transaction.signed(b, List.of(input),
                List.of(new Transaction.Output(b.publicKey(), 100)));
        assertTrue(stolen.signatureValid()); // Valid signature, but wrong owner.
        assertThrows(IllegalArgumentException.class, () -> chain.addBlock(List.of(stolen)));
        assertThrows(IllegalArgumentException.class, () ->
                new Transaction.Output(b.publicKey(), -1));
        assertEquals(1, chain.size());
    }

    @Test
    void signatureCoversTransactionOutputs() {
        Wallet a = new Wallet(), b = new Wallet();
        Blockchain chain = new Blockchain(a, 100, 1);
        String input = chain.replay().ownedBy(a.publicKey()).get(0).id();
        Transaction original = Transaction.signed(a, List.of(input), List.of(
                new Transaction.Output(b.publicKey(), 40), new Transaction.Output(a.publicKey(), 60)));
        Transaction altered = original.changedOutputsForDemo(List.of(
                new Transaction.Output(b.publicKey(), 41), new Transaction.Output(a.publicKey(), 59)));
        assertTrue(original.signatureValid());
        assertFalse(altered.signatureValid());
        assertThrows(IllegalArgumentException.class, () -> chain.addBlock(List.of(altered)));
        assertEquals(1, chain.size());
    }

    @Test
    void tamperingWithMinedBlockInvalidatesReplay() {
        Wallet a = new Wallet(), b = new Wallet();
        Blockchain chain = new Blockchain(a, 100, 1);
        String input = chain.replay().ownedBy(a.publicKey()).get(0).id();
        Transaction original = Transaction.signed(a, List.of(input), List.of(
                new Transaction.Output(b.publicKey(), 40), new Transaction.Output(a.publicKey(), 60)));
        chain.addBlock(List.of(original));
        chain.get(1).replaceTransactionForDemo(0, original.changedOutputsForDemo(List.of(
                new Transaction.Output(b.publicKey(), 50), new Transaction.Output(a.publicKey(), 50))));
        assertFalse(chain.isValid());
        assertThrows(IllegalStateException.class, chain::replay);
        chain.get(1).replaceTransactionForDemo(0, original);
        assertTrue(chain.isValid());
        assertEquals(60, chain.replay().balance(a.publicKey()));
    }
}
