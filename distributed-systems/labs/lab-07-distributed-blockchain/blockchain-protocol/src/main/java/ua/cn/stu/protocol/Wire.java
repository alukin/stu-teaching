// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.protocol;

import ua.cn.stu.blockchain.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.stream.*;

/** Human-readable JSON DTOs. Cryptographic identity is computed from binary encoding, not JSON. */
public final class Wire {
    private Wire() {}
    public record Output(String owner,long amount) {}
    public record Tx(String id,String signer,List<String> inputs,List<Output> outputs,String signature,boolean genesis) {}
    public record BlockData(String previousHash,long timestamp,long nonce,String hash,List<Tx> transactions) {}
    public record Utxo(String id,String owner,long amount) {}
    public record Status(String node,String genesis,int height,String tip,long work,int knownBlocks,String utxoFingerprint,int mempool,boolean minerEnabled) {}
    public record Envelope(String type,String requestId,String sender,String genesis,JsonNode payload) {}
    public record Error(String message) {}
    public record Mine(List<String> txIds) {}
    public record PeerCommand(String peer) {}
    public record TxQuery(String txId) {}
    public record UtxosRequest(String owner) {}
    public record TxStatus(String state,int confirmations) {}
    public static Tx from(Transaction t){
        return new Tx(t.id(),t.signer()==null?null:Base64.getEncoder().encodeToString(t.signer().getEncoded()),
                t.inputs(),t.outputs().stream().map(o->new Output(Base64.getEncoder().encodeToString(o.owner().getEncoded()),o.amount())).toList(),
                Base64.getEncoder().encodeToString(t.signature()),t.isGenesis());
    }
    public static Transaction toTx(Tx w){
        if(w==null||w.inputs()==null||w.outputs()==null||w.id()==null||w.signature()==null)throw new IllegalArgumentException("Malformed transaction JSON");
        return Transaction.restore(w.id(),w.signer()==null?null:Wallet.publicFromBase64(w.signer()),w.inputs(),
                w.outputs().stream().map(o->new Transaction.Output(Wallet.publicFromBase64(o.owner()),o.amount())).toList(),
                Base64.getDecoder().decode(w.signature()),w.genesis());
    }
    public static BlockData from(ua.cn.stu.blockchain.Block b){
        return new BlockData(b.previousHash(),b.timestamp(),b.nonce(),b.hash(),b.transactions().stream().map(Wire::from).toList());
    }
    public static ua.cn.stu.blockchain.Block toBlock(BlockData w){
        if(w==null||w.transactions()==null||w.transactions().size()>1000)throw new IllegalArgumentException("Malformed block JSON");
        return ua.cn.stu.blockchain.Block.restore(w.previousHash(),w.timestamp(),w.nonce(),w.transactions().stream().map(Wire::toTx).toList(),w.hash());
    }
    public static Utxo from(Ledger.Utxo value){return new Utxo(value.id(),Base64.getEncoder().encodeToString(value.owner().getEncoded()),value.amount());}
}
