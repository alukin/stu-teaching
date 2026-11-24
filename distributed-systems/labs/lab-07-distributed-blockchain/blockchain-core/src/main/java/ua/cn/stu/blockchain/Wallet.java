// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.blockchain;

import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public final class Wallet {
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private Wallet(PublicKey pub, PrivateKey priv) { publicKey=pub; privateKey=priv; }
    public static Wallet create() {
        try {
            KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair=generator.generateKeyPair();
            return new Wallet(pair.getPublic(),pair.getPrivate());
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public static Wallet restore(String publicKeyBase64,String privateKeyBase64) {
        try {
            KeyFactory factory=KeyFactory.getInstance("EC");
            PublicKey pub=factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
            PrivateKey priv=factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));
            byte[] challenge="training wallet self-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Signature s=Signature.getInstance("SHA256withECDSA");s.initSign(priv);s.update(challenge);
            byte[] signature=s.sign();
            if(!verify(pub,challenge,signature)) throw new IllegalArgumentException("Wallet key pair mismatch");
            return new Wallet(pub,priv);
        } catch(GeneralSecurityException | IllegalArgumentException e){ throw new IllegalArgumentException("Invalid wallet keys",e); }
    }
    public PublicKey publicKey(){return publicKey;}
    public String publicBase64(){return Base64.getEncoder().encodeToString(publicKey.getEncoded());}
    public String privateBase64(){return Base64.getEncoder().encodeToString(privateKey.getEncoded());}
    public byte[] sign(byte[] content){
        try{ Signature s=Signature.getInstance("SHA256withECDSA");s.initSign(privateKey);s.update(content);return s.sign();}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    public static boolean verify(PublicKey pub,byte[] content,byte[] sig){
        try{ Signature s=Signature.getInstance("SHA256withECDSA");s.initVerify(pub);s.update(content);return s.verify(sig); }
        catch(GeneralSecurityException | IllegalArgumentException e){return false;}
    }
    public static PublicKey publicFromBase64(String value){
        try{return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(value)));}
        catch(GeneralSecurityException | IllegalArgumentException e){throw new IllegalArgumentException("Invalid public key",e);}
    }
}
