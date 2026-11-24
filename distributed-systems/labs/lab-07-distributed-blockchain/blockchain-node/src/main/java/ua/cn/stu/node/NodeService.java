// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.node;

import ua.cn.stu.blockchain.*;
import ua.cn.stu.protocol.*;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;

/** All modifications are synchronized per node; no shared Java state between node processes. */
@ApplicationScoped
public class NodeService {
    @ConfigProperty(name="node.name",defaultValue="A") String name;
    @Inject PeerLinks links;
    @ConfigProperty(name="node.miner.enabled",defaultValue="false") boolean configuredMiner;
    private Chain chain;
    private volatile boolean minerEnabled;
    private final Map<String,Transaction> mempool=new LinkedHashMap<>();
    @PostConstruct void init(){chain=JsonCodec.createChain();minerEnabled=configuredMiner;}
    public boolean minerEnabled(){return minerEnabled;}
    public void setMinerEnabled(boolean enabled){minerEnabled=enabled;}
    public String name(){return name;}
    public synchronized String genesis(){return chain.genesisHash();}
    public synchronized Wire.Status status(){
        return new Wire.Status(name,chain.genesisHash(),chain.height(),chain.tip(),chain.accumulatedWork(),chain.knownBlocks(),chain.state().fingerprint(),mempool.size(),minerEnabled);
    }
    public synchronized List<Wire.BlockData> selectedBlocks(){return chain.selectedPath().stream().map(Wire::from).toList();}
    public synchronized List<Wire.Utxo> utxos(String owner){
        return chain.state().ownedBy(Wallet.publicFromBase64(owner)).stream().map(Wire::from).toList();
    }
    public synchronized Wire.TxStatus txStatus(String id){
        List<Block> blocks=chain.selectedPath();
        for(int i=blocks.size()-1;i>=0;i--)for(Transaction tx:blocks.get(i).transactions())
            if(tx.id().equals(id))return new Wire.TxStatus("CONFIRMED",blocks.size()-i);
        return new Wire.TxStatus(mempool.containsKey(id)?"PENDING":"UNKNOWN",0);
    }
    /** Mempool validates all pending spends in deterministic arrival order. */
    public synchronized boolean submit(Transaction tx){
        if(chain.state().containsTx(tx.id())||mempool.containsKey(tx.id()))return false;
        Ledger state=chain.state();
        for(Transaction pending:mempool.values())state.apply(pending);
        state.apply(tx);
        mempool.put(tx.id(),tx);
        return true;
    }
    /** Automatic mining only packs pending transactions. Manual MINE also permits empty blocks. */
    public synchronized Block minePending(){
        if(!minerEnabled||mempool.isEmpty())return null;
        return mine();
    }
    public synchronized Block mine(){
        List<Block> old=chain.selectedPath();
        Block b=chain.mine(List.copyOf(mempool.values()));
        reconcile(old);return b;
    }
    private void reconcile(List<Block> oldSelected){
        List<Transaction> candidates=new ArrayList<>(mempool.values());
        for(int i=1;i<oldSelected.size();i++)candidates.addAll(oldSelected.get(i).transactions());
        mempool.clear();
        Ledger state=chain.state();Set<String> visited=new HashSet<>();
        for(Transaction tx:candidates){
            if(!visited.add(tx.id())||state.containsTx(tx.id()))continue;
            try{state.apply(tx);mempool.put(tx.id(),tx);}catch(IllegalArgumentException | ArithmeticException ignored){/* invalid after reorg */}
        }
    }
    public synchronized Chain.Result accept(Block block){
        List<Block> old=chain.selectedPath();
        Chain.Result result=chain.accept(block);
        if(result==Chain.Result.BEST_CHANGED)reconcile(old);
        return result;
    }
    public synchronized int importChain(List<Block> path){
        List<Block> old=chain.selectedPath();
        int count=chain.importPath(path);
        if(!chain.tip().equals(old.getLast().hash()))reconcile(old);
        return count;
    }
    private String out(String type,String requestId,Object payload){return JsonCodec.message(type,requestId,name,genesis(),payload);}
    public String message(String type,Object payload){return JsonCodec.message(type,name,genesis(),payload);}
    public String reply(Wire.Envelope request,String type,Object payload){return out(type,request.requestId(),payload);}
    public String error(Wire.Envelope request,Exception e){return JsonCodec.error(request.requestId(),name,genesis(),e.getMessage());}
    public void announceTx(Transaction tx,String from){links.publishExcept(from,message("NEW_TX",Wire.from(tx)));}
    public void announceBlock(Block b,String from){links.publishExcept(from,message("NEW_BLOCK",Wire.from(b)));}
    public String handlePeer(Wire.Envelope request){
        // ERROR is terminal: never reply to an error with another error (no ping-pong).
        if(request.type().equals("ERROR"))return null;
        if(!request.genesis().equals(genesis())){
            if(links.isConfigured(request.sender()))links.setBlocked(request.sender(),true);
            return reply(request,"ERROR",new Wire.Error("Different genesis"));
        }
        if(links.isBlocked(request.sender()))return null;
        if(!links.isConfigured(request.sender()))return reply(request,"ERROR",new Wire.Error("Unknown peer"));
        try{
            return switch(request.type()){
                case "HELLO" -> reply(request,"HELLO_ACK",status());
                case "HELLO_ACK" -> null;
                case "GET_CHAIN" -> reply(request,"CHAIN",selectedBlocks());
                case "CHAIN" -> {
                    Wire.BlockData[] data=JsonCodec.payload(request,Wire.BlockData[].class);
                    List<Block> blocks=Arrays.stream(data).map(Wire::toBlock).toList();
                    importChain(blocks);
                    yield null;
                }
                case "NEW_TX" -> {
                    Transaction tx=Wire.toTx(JsonCodec.payload(request,Wire.Tx.class));
                    if(submit(tx))announceTx(tx,request.sender());
                    yield null;
                }
                case "NEW_BLOCK" -> {
                    Block b=Wire.toBlock(JsonCodec.payload(request,Wire.BlockData.class));
                    Chain.Result result=accept(b);
                    if(result==Chain.Result.ORPHAN)yield reply(request,"GET_CHAIN",Map.of());
                    if(result!=Chain.Result.DUPLICATE)announceBlock(b,request.sender());
                    yield null;
                }
                default -> reply(request,"ERROR",new Wire.Error("Unknown peer message: "+request.type()));
            };
        }catch(RuntimeException ex){
            return error(request,ex);
        }
    }
    public String handleClient(Wire.Envelope request){
        if(!request.genesis().equals(genesis()))return reply(request,"ERROR",new Wire.Error("Wrong genesis"));
        try{
            return switch(request.type()){
                case "STATUS" -> reply(request,"STATUS_RESULT",status());
                case "UTXOS" -> reply(request,"UTXOS_RESULT",utxos(JsonCodec.payload(request,Wire.UtxosRequest.class).owner()));
                case "TX_STATUS" -> reply(request,"TX_STATUS_RESULT",txStatus(JsonCodec.payload(request,Wire.TxQuery.class).txId()));
                case "SUBMIT_TX" -> {
                    Transaction tx=Wire.toTx(JsonCodec.payload(request,Wire.Tx.class));
                    boolean accepted=submit(tx);
                    if(accepted)announceTx(tx,null);
                    yield reply(request,"SUBMIT_RESULT",Map.of("accepted",accepted,"txId",tx.id()));
                }
                case "MINER_STATUS" -> reply(request,"MINER_RESULT",Map.of("enabled",minerEnabled()));
                case "MINER_ON" -> {
                    setMinerEnabled(true);
                    yield reply(request,"MINER_RESULT",Map.of("enabled",true));
                }
                case "MINER_OFF" -> {
                    setMinerEnabled(false);
                    yield reply(request,"MINER_RESULT",Map.of("enabled",false));
                }
                case "MINE" -> {
                    Block block=mine();announceBlock(block,null);
                    yield reply(request,"MINE_RESULT",Map.of("hash",block.hash(),"height",status().height()));
                }
                case "DISCONNECT" -> {
                    String peer=JsonCodec.payload(request,Wire.PeerCommand.class).peer();
                    links.setBlocked(peer,true);
                    yield reply(request,"PEER_RESULT",Map.of("peer",peer,"blocked",true));
                }
                case "RECONNECT" -> {
                    String peer=JsonCodec.payload(request,Wire.PeerCommand.class).peer();
                    links.setBlocked(peer,false);
                    links.requestSync(peer);
                    yield reply(request,"PEER_RESULT",Map.of("peer",peer,"blocked",false));
                }
                case "PEERS" -> reply(request,"PEERS_RESULT",links.summary());
                default -> reply(request,"ERROR",new Wire.Error("Unknown client command: "+request.type()));
            };
        }catch(RuntimeException ex){
            return error(request,ex);
        }
    }
}
