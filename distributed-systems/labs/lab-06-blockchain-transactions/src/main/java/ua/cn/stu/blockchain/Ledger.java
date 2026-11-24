// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package ua.cn.stu.blockchain;

import java.security.PublicKey;
import java.util.*;

// Derived state, rebuilt by replaying transactions in block order.
public final class Ledger {
    public record Utxo(String id, PublicKey owner, long amount) { }

    private final Map<String, Utxo> unspent = new LinkedHashMap<>();
    private final Set<String> seenTransactions = new HashSet<>();

    void applyGenesis(Transaction transaction) {
        if (!seenTransactions.isEmpty() || !transaction.isGenesis()
                || !transaction.inputs().isEmpty() || transaction.outputs().size() != 1)
            throw new IllegalArgumentException("Invalid genesis transaction");
        addOutputs(transaction);
        seenTransactions.add(transaction.id());
    }

    void apply(Transaction transaction) {
        if (transaction.isGenesis() || !transaction.signatureValid()
                || transaction.inputs().isEmpty() || transaction.outputs().isEmpty()
                || seenTransactions.contains(transaction.id()))
            throw new IllegalArgumentException("Invalid transaction or signature");

        long inputTotal = 0;
        Set<String> uniqueInputs = new HashSet<>();
        for (String id : transaction.inputs()) {
            if (!uniqueInputs.add(id)) throw new IllegalArgumentException("Repeated input");
            Utxo existing = unspent.get(id);
            if (existing == null) throw new IllegalArgumentException("Missing or spent UTXO");
            if (!Arrays.equals(existing.owner().getEncoded(), transaction.signer().getEncoded()))
                throw new IllegalArgumentException("UTXO is owned by another wallet");
            inputTotal = Math.addExact(inputTotal, existing.amount());
        }

        long outputTotal = 0;
        for (Transaction.Output output : transaction.outputs())
            outputTotal = Math.addExact(outputTotal, output.amount());
        if (inputTotal != outputTotal)
            throw new IllegalArgumentException("Inputs and outputs must balance (no fees)");

        // Check everything before modifying the UTXO set.
        for (int i = 0; i < transaction.outputs().size(); i++)
            if (unspent.containsKey(transaction.outputId(i)))
                throw new IllegalArgumentException("Duplicate output ID");
        for (String input : transaction.inputs()) unspent.remove(input);
        addOutputs(transaction);
        seenTransactions.add(transaction.id());
    }

    private void addOutputs(Transaction transaction) {
        for (int i = 0; i < transaction.outputs().size(); i++) {
            Transaction.Output output = transaction.outputs().get(i);
            String id = transaction.outputId(i);
            unspent.put(id, new Utxo(id, output.owner(), output.amount()));
        }
    }

    public List<Utxo> ownedBy(PublicKey owner) {
        return unspent.values().stream()
                .filter(u -> Arrays.equals(u.owner().getEncoded(), owner.getEncoded())).toList();
    }

    public long balance(PublicKey owner) {
        long total = 0;
        for (Utxo utxo : ownedBy(owner)) total = Math.addExact(total, utxo.amount());
        return total;
    }
}
