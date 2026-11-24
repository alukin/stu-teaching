// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.blockchain;

import java.security.PublicKey;
import java.util.*;

/** Reproducible derived UTXO state, never shared between nodes. */
public final class Ledger {
    public record Utxo(String id,PublicKey owner,long amount) {}
    private final Map<String,Utxo> unspent=new LinkedHashMap<>();
    private final Set<String> knownTx=new HashSet<>();
    public void applyGenesis(Transaction t){
        if(!knownTx.isEmpty()||!t.isGenesis()||!t.inputs().isEmpty()||t.outputs().size()!=1)throw new IllegalArgumentException("Bad genesis");
        addOutputs(t);knownTx.add(t.id());
    }
    public void apply(Transaction t){
        if(t.isGenesis()||!t.signatureValid()||t.inputs().isEmpty()||t.outputs().isEmpty()||knownTx.contains(t.id()))throw new IllegalArgumentException("Bad transaction or signature");
        long inputSum=0;Set<String> unique=new HashSet<>();
        for(String id:t.inputs()){
            if(!unique.add(id))throw new IllegalArgumentException("Repeated input");
            Utxo value=unspent.get(id);
            if(value==null)throw new IllegalArgumentException("Missing or spent UTXO");
            if(!Arrays.equals(value.owner().getEncoded(),t.signer().getEncoded()))throw new IllegalArgumentException("Unauthorized spend");
            inputSum=Math.addExact(inputSum,value.amount());
        }
        long outputSum=0;
        for(Transaction.Output output:t.outputs())outputSum=Math.addExact(outputSum,output.amount());
        if(inputSum!=outputSum)throw new IllegalArgumentException("Input and output sums differ");
        for(int i=0;i<t.outputs().size();i++)if(unspent.containsKey(t.outputId(i)))throw new IllegalArgumentException("Duplicate output ID");
        for(String id:t.inputs())unspent.remove(id);
        addOutputs(t);knownTx.add(t.id());
    }
    private void addOutputs(Transaction tx){
        for(int i=0;i<tx.outputs().size();i++){
            var o=tx.outputs().get(i);String id=tx.outputId(i);unspent.put(id,new Utxo(id,o.owner(),o.amount()));
        }
    }
    public List<Utxo> ownedBy(PublicKey key){return unspent.values().stream().filter(u->Arrays.equals(u.owner().getEncoded(),key.getEncoded())).toList();}
    public long balance(PublicKey key){long sum=0;for(Utxo u:ownedBy(key))sum=Math.addExact(sum,u.amount());return sum;}
    public List<Utxo> utxos(){return List.copyOf(unspent.values());}
    public boolean containsTx(String id){return knownTx.contains(id);}
    /** Stable observable projection used to check convergence across nodes. */
    public String fingerprint(){
        StringBuilder b=new StringBuilder();unspent.values().stream().sorted(Comparator.comparing(Utxo::id)).forEach(u->b.append(u.id()).append(':').append(Base64.getEncoder().encodeToString(u.owner().getEncoded())).append(':').append(u.amount()).append(';'));
        return Transaction.sha256Hex(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
