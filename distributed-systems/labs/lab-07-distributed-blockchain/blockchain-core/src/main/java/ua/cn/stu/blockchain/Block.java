// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.blockchain;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable block with preserved wire timestamp/nonce/hash: never remine on receipt. */
public final class Block {
    private final String previousHash;
    private final long timestamp,nonce;
    private final List<Transaction> transactions;
    private final String hash;
    private Block(String previousHash,long timestamp,long nonce,List<Transaction> txs,String hash){
        this.previousHash=Objects.requireNonNull(previousHash);this.timestamp=timestamp;this.nonce=nonce;
        this.transactions=List.copyOf(txs);this.hash=Objects.requireNonNull(hash);
    }
    public static Block mine(String previousHash,List<Transaction> txs,int difficulty,long timestamp){
        if(difficulty<1||difficulty>4)throw new IllegalArgumentException("Difficulty must be 1..4");
        String target="0".repeat(difficulty);
        for(long nonce=0;nonce<Long.MAX_VALUE;nonce++){
            String hash=calculateHash(previousHash,timestamp,nonce,txs);
            if(hash.startsWith(target))return new Block(previousHash,timestamp,nonce,txs,hash);
        }
        throw new IllegalStateException("Nonce exhausted");
    }
    public static Block restore(String previousHash,long timestamp,long nonce,List<Transaction> txs,String hash){
        if(timestamp<0||nonce<0||txs==null||txs.size()>1000)throw new IllegalArgumentException("Invalid block shape");
        Block b=new Block(previousHash,timestamp,nonce,txs,hash);
        if(!b.hash.equals(b.calculateHash()))throw new IllegalArgumentException("Block hash mismatch");
        return b;
    }
    public static String calculateHash(String previousHash,long timestamp,long nonce,List<Transaction> txs){
        StringBuilder b=new StringBuilder(previousHash).append(':').append(timestamp).append(':').append(nonce);
        for(Transaction tx:txs)b.append(':').append(tx.id());
        return Transaction.sha256Hex(b.toString().getBytes(StandardCharsets.UTF_8));
    }
    public String calculateHash(){return calculateHash(previousHash,timestamp,nonce,transactions);}
    public boolean proofValid(int difficulty){return hash.equals(calculateHash())&&hash.startsWith("0".repeat(difficulty));}
    public String hash(){return hash;}
    public String previousHash(){return previousHash;}
    public long timestamp(){return timestamp;}
    public long nonce(){return nonce;}
    public List<Transaction> transactions(){return transactions;}
}
