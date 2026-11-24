// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.protocol;

import com.fasterxml.jackson.databind.*;
import ua.cn.stu.blockchain.Chain;
import java.io.*;
import java.util.*;

public final class JsonCodec {
    private static final ObjectMapper JSON=new ObjectMapper();
    public static final int MAX_MESSAGE_CHARS=262144;
    private JsonCodec() {}
    public static String message(String type,String sender,String genesis,Object payload){
        return message(type,UUID.randomUUID().toString(),sender,genesis,payload);
    }
    public static String message(String type,String requestId,String sender,String genesis,Object payload){
        try{return JSON.writeValueAsString(new Wire.Envelope(type,requestId,sender,genesis,JSON.valueToTree(payload)));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("JSON serialization",e);}
    }
    public static Wire.Envelope decode(String raw){
        if(raw==null||raw.length()>MAX_MESSAGE_CHARS)throw new IllegalArgumentException("Message too large");
        try{
            Wire.Envelope m=JSON.readValue(raw,Wire.Envelope.class);
            if(m==null||m.type()==null||m.type().length()>40||m.requestId()==null||m.sender()==null||m.genesis()==null)throw new IllegalArgumentException("Malformed envelope");
            return m;
        }catch(IOException e){throw new IllegalArgumentException("Invalid JSON",e);}
    }
    public static <T> T payload(Wire.Envelope message,Class<T> type){
        try{return JSON.treeToValue(message.payload(),type);}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Invalid payload for "+message.type(),e);}
    }
    public static String error(String requestId,String sender,String genesis,String message){
        return message("ERROR",requestId,sender,genesis,new Wire.Error(message));
    }
    public static Wire.BlockData genesisResource(){
        try(InputStream in=JsonCodec.class.getClassLoader().getResourceAsStream("genesis.json")){
            if(in==null)throw new IllegalStateException("Missing genesis.json");
            return JSON.readValue(in,Wire.BlockData.class);
        }catch(IOException e){throw new IllegalStateException("Unreadable genesis.json",e);}
    }
    // Pinned at build time: changing a JSON file alone cannot silently redefine this network.
    public static final String GENESIS_HASH="0080a43b6fda7c911231435c98029509bb68dff96b31e805fa56d371202c330f";
    public static Chain createChain(){
        ua.cn.stu.blockchain.Block block=Wire.toBlock(genesisResource());
        if(!block.hash().equals(GENESIS_HASH))throw new IllegalStateException("Genesis differs from pinned network anchor");
        return new Chain(block,2);
    }
}
