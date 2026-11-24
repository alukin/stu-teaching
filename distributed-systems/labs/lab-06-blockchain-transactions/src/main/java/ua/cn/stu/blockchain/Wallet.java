// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Oleksiy Lukin
package ua.cn.stu.blockchain;

import java.security.*;
import java.security.spec.ECGenParameterSpec;

public final class Wallet {
    private final KeyPair keys;

    public Wallet() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            keys = generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot create wallet", e);
        }
    }

    public PublicKey publicKey() { return keys.getPublic(); }

    byte[] sign(byte[] message) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(keys.getPrivate());
            signature.update(message);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign transaction", e);
        }
    }

    static boolean verify(PublicKey key, byte[] message, byte[] signed) {
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(signed);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
