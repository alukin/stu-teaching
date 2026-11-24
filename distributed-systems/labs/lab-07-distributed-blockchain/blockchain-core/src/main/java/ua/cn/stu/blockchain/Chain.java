// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.blockchain;

import java.util.*;

/** Stores competing branches; fixed PoW difficulty => accumulated work proportional to height.
 * Equal-work forks use lexicographically smaller tip hash, for deterministic teaching runs.
 */
public final class Chain {
    public enum Result { ACCEPTED, BEST_CHANGED, DUPLICATE, ORPHAN }
    private record Entry(Block block,String parent,int height) {}
    private final Map<String,Entry> entries=new HashMap<>();
    private final int difficulty;
    private final String genesisHash;
    private String tip;
    public Chain(Block genesis,int difficulty){
        if(difficulty<1||difficulty>4||!genesis.previousHash().equals("0")||genesis.transactions().size()!=1||!genesis.proofValid(difficulty))throw new IllegalArgumentException("Invalid genesis block");
        Ledger ledger=new Ledger();ledger.applyGenesis(genesis.transactions().getFirst());
        this.difficulty=difficulty;this.genesisHash=genesis.hash();this.tip=genesisHash;
        entries.put(tip,new Entry(genesis,null,0));
    }
    public Result accept(Block candidate){
        if(entries.containsKey(candidate.hash()))return Result.DUPLICATE;
        Entry parent=entries.get(candidate.previousHash());
        if(parent==null)return Result.ORPHAN;
        if(!candidate.proofValid(difficulty))throw new IllegalArgumentException("Invalid PoW");
        Ledger ledger=replayTo(candidate.previousHash());
        for(Transaction tx:candidate.transactions())ledger.apply(tx);
        int height=parent.height+1;
        entries.put(candidate.hash(),new Entry(candidate,candidate.previousHash(),height));
        int bestHeight=entries.get(tip).height;
        if(height>bestHeight||(height==bestHeight&&candidate.hash().compareTo(tip)<0)){
            tip=candidate.hash();return Result.BEST_CHANGED;
        }
        return Result.ACCEPTED;
    }
    public Block mine(List<Transaction> txs){
        Ledger preview=state();for(Transaction tx:txs)preview.apply(tx);
        Block b=Block.mine(tip,txs,difficulty,System.currentTimeMillis());
        Result result=accept(b);if(result!=Result.BEST_CHANGED)throw new IllegalStateException("Locally mined block not selected");
        return b;
    }
    public int importPath(List<Block> blocks){
        if(blocks.isEmpty()||!blocks.getFirst().hash().equals(genesisHash))throw new IllegalArgumentException("Different genesis");
        Map<String,Entry> before=new HashMap<>(entries);
        String oldTip=tip;
        try {
            int added=0;
            for(int i=1;i<blocks.size();i++){
                Block b=blocks.get(i);
                if(!b.previousHash().equals(blocks.get(i-1).hash()))throw new IllegalArgumentException("Broken supplied path");
                Result r=accept(b);
                if(r==Result.ORPHAN)throw new IllegalArgumentException("Missing ancestor");
                if(r!=Result.DUPLICATE)added++;
            }
            return added;
        } catch(RuntimeException badPath) {
            entries.clear();entries.putAll(before);tip=oldTip;
            throw badPath;
        }
    }
    public Ledger replayTo(String hash){
        List<Block> blocks=pathTo(hash);
        Ledger ledger=new Ledger();ledger.applyGenesis(blocks.getFirst().transactions().getFirst());
        for(int i=1;i<blocks.size();i++)for(Transaction tx:blocks.get(i).transactions())ledger.apply(tx);
        return ledger;
    }
    private List<Block> pathTo(String hash){
        if(!entries.containsKey(hash))throw new IllegalArgumentException("Unknown block");
        List<Block> path=new ArrayList<>();
        for(String cursor=hash;cursor!=null;cursor=entries.get(cursor).parent)path.add(entries.get(cursor).block);
        Collections.reverse(path);return path;
    }
    public Ledger state(){return replayTo(tip);}
    public List<Block> selectedPath(){return List.copyOf(pathTo(tip));}
    public boolean knows(String hash){return entries.containsKey(hash);}
    public String tip(){return tip;}
    public String genesisHash(){return genesisHash;}
    public int height(){return entries.get(tip).height;}
    public int knownBlocks(){return entries.size();}
    public int difficulty(){return difficulty;}
    public long accumulatedWork(){return Math.multiplyExact((long)(height()+1), (long)Math.pow(16,difficulty));}
}
