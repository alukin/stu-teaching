// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package ua.cn.stu.blockchain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class Transaction {
    public record Output(PublicKey owner, long amount) {
        public Output {
            Objects.requireNonNull(owner);
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private final PublicKey signer;
    private final List<String> inputs;
    private final List<Output> outputs;
    private final byte[] signature;
    private final boolean genesis;
    private final String id;

    private Transaction(PublicKey signer, List<String> inputs, List<Output> outputs,
                        byte[] signature, boolean genesis) {
        this.signer = signer;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.signature = signature.clone();
        this.genesis = genesis;
        this.id = sha256Hex(serializedWithSignature());
    }

    public static Transaction signed(Wallet wallet, List<String> inputs, List<Output> outputs) {
        byte[] payload = encode(wallet.publicKey(), inputs, outputs, false);
        return new Transaction(wallet.publicKey(), inputs, outputs, wallet.sign(payload), false);
    }

    public static Transaction genesis(PublicKey owner, long initialAmount) {
        return new Transaction(null, List.of(), List.of(new Output(owner, initialAmount)),
                               new byte[0], true);
    }

    public boolean signatureValid() {
        return !genesis && signer != null && signature.length > 0
                && Wallet.verify(signer, encode(signer, inputs, outputs, false), signature);
    }

    public String id() { return id; }
    public String outputId(int index) { return id + ":" + index; }
    public PublicKey signer() { return signer; }
    public List<String> inputs() { return inputs; }
    public List<Output> outputs() { return outputs; }
    public boolean isGenesis() { return genesis; }

    // Deliberately keeps the old signature: used to demonstrate tampering.
    public Transaction changedOutputsForDemo(List<Output> changed) {
        return new Transaction(signer, inputs, changed, signature, genesis);
    }

    private byte[] serializedWithSignature() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            buffer.write(encode(signer, inputs, outputs, genesis));
            DataOutputStream data = new DataOutputStream(buffer);
            data.writeInt(signature.length);
            data.write(signature);
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // Length-prefixed binary encoding avoids ambiguous field concatenation.
    private static byte[] encode(PublicKey signer, List<String> inputs,
                                 List<Output> outputs, boolean genesis) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeInt(1); // Format version for this teaching example.
            out.writeBoolean(genesis);
            writeBytes(out, signer == null ? new byte[0] : signer.getEncoded());
            out.writeInt(inputs.size());
            for (String input : inputs) writeBytes(out, input.getBytes(StandardCharsets.UTF_8));
            out.writeInt(outputs.size());
            for (Output output : outputs) {
                writeBytes(out, output.owner().getEncoded());
                out.writeLong(output.amount());
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
