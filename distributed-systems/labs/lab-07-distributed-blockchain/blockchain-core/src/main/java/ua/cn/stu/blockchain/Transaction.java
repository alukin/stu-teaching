// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.blockchain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Signatures cover every input and output. Transaction IDs cover the signature too. */
public final class Transaction {
    public record Output(PublicKey owner, long amount) {
        public Output {
            Objects.requireNonNull(owner);
            if(amount<=0) throw new IllegalArgumentException("Nonpositive output");
        }
    }
    private final PublicKey signer;
    private final List<String> inputs;
    private final List<Output> outputs;
    private final byte[] signature;
    private final boolean genesis;
    private final String id;
    private Transaction(PublicKey signer,List<String> inputs,List<Output> outputs,byte[] signature,boolean genesis){
        this.signer=signer;this.inputs=List.copyOf(inputs);this.outputs=List.copyOf(outputs);
        this.signature=signature.clone();this.genesis=genesis;this.id=sha256Hex(serialize(true));
    }
    public static Transaction signed(Wallet wallet,List<String> inputs,List<Output> outputs){
        Objects.requireNonNull(wallet);byte[] bytes=encode(wallet.publicKey(),inputs,outputs,false);
        return new Transaction(wallet.publicKey(),inputs,outputs,wallet.sign(bytes),false);
    }
    public static Transaction genesis(PublicKey owner,long amount){
        return new Transaction(null,List.of(),List.of(new Output(owner,amount)),new byte[0],true);
    }
    public static Transaction restore(String expectedId,PublicKey signer,List<String> inputs,List<Output> outputs,byte[] sig,boolean genesis){
        if(inputs==null||outputs==null||sig==null||genesis!=(signer==null))throw new IllegalArgumentException("Invalid transaction shape");
        Transaction tx=new Transaction(signer,inputs,outputs,sig,genesis);
        if(!tx.id.equals(expectedId))throw new IllegalArgumentException("Transaction ID mismatch");
        if(genesis && (sig.length!=0 || !inputs.isEmpty() || outputs.size()!=1))throw new IllegalArgumentException("Invalid genesis payload");
        return tx;
    }
    public boolean signatureValid(){return !genesis && signer!=null && signature.length>0 && Wallet.verify(signer,encode(signer,inputs,outputs,false),signature);}
    public String id(){return id;}
    public String outputId(int index){return id+":"+index;}
    public PublicKey signer(){return signer;}
    public List<String> inputs(){return inputs;}
    public List<Output> outputs(){return outputs;}
    public byte[] signature(){return signature.clone();}
    public boolean isGenesis(){return genesis;}
    private byte[] serialize(boolean includeSignature){
        try{
            ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(encode(signer,inputs,outputs,genesis));
            if(includeSignature){DataOutputStream d=new DataOutputStream(b);d.writeInt(signature.length);d.write(signature);}
            return b.toByteArray();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private static byte[] encode(PublicKey signer,List<String> inputs,List<Output> outputs,boolean genesis){
        try{
            ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
            d.writeInt(1);d.writeBoolean(genesis);
            put(d,signer==null?new byte[0]:signer.getEncoded());
            d.writeInt(inputs.size());for(String input:inputs)put(d,input.getBytes(StandardCharsets.UTF_8));
            d.writeInt(outputs.size());for(Output out:outputs){put(d,out.owner().getEncoded());d.writeLong(out.amount());}
            return b.toByteArray();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private static void put(DataOutputStream d,byte[] bytes)throws IOException{d.writeInt(bytes.length);d.write(bytes);}
    public static String sha256Hex(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
