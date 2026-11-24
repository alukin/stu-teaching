// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.node;

import ua.cn.stu.protocol.*;
import io.quarkus.websockets.next.*;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/** Each node actively connects to its peers. Partition commands filter messages in both directions. */
@ApplicationScoped
public class PeerLinks {
    @Inject Instance<WebSocketConnector<OutboundPeer>> connectors;
    @Inject NodeService node;
    @ConfigProperty(name="node.peers",defaultValue="") String configuration;
    private final Map<String,URI> addresses=new LinkedHashMap<>();
    private final ConcurrentMap<String,WebSocketClientConnection> outgoing=new ConcurrentHashMap<>();
    private final Set<String> blocked=ConcurrentHashMap.newKeySet();
    @PostConstruct void parse(){
        for(String entry:configuration.split(",")){
            if(entry.isBlank())continue;
            String[] parts=entry.trim().split("@",2);
            if(parts.length!=2||!parts[1].startsWith("ws://"))throw new IllegalArgumentException("node.peers uses B@ws://localhost:8082");
            if(!parts[0].equals(node.name()))addresses.put(parts[0],URI.create(parts[1]));
        }
    }
    public boolean isBlocked(String name){return blocked.contains(name);}
    public boolean isConfigured(String name){return addresses.containsKey(name);}
    public void setBlocked(String peer,boolean value){
        if(!addresses.containsKey(peer))throw new IllegalArgumentException("Unknown peer: "+peer);
        if(value)blocked.add(peer);else blocked.remove(peer);
    }
    public Map<String,String> summary(){
        Map<String,String> result=new LinkedHashMap<>();
        addresses.forEach((name,uri)->result.put(name,blocked.contains(name)?"PARTITIONED":outgoing.containsKey(name)?"CONNECTED":"RECONNECTING"));
        return result;
    }
    @Scheduled(every="3s",concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
    void reconnect(){
        for(var entry:addresses.entrySet()){
            String peer=entry.getKey();
            if(blocked.contains(peer)||outgoing.containsKey(peer))continue;
            try{
                WebSocketClientConnection c=connectors.get().baseUri(entry.getValue()).connectAndAwait();
                outgoing.put(peer,c);
                c.sendTextAndAwait(node.message("HELLO",node.status()));
                c.sendTextAndAwait(node.message("GET_CHAIN",Map.of()));
            }catch(Exception ignored){/* other student nodes may not be running yet */}
        }
    }
    public void disconnected(WebSocketClientConnection c){outgoing.entrySet().removeIf(entry->entry.getValue().equals(c));}
    public void publishExcept(String excluded,String json){
        outgoing.forEach((peer,connection)->{
            if(peer.equals(excluded)||blocked.contains(peer))return;
            try{connection.sendTextAndAwait(json);}catch(RuntimeException e){outgoing.remove(peer,connection);}
        });
    }
    public void requestSync(String peer){
        WebSocketClientConnection connection=outgoing.get(peer);
        if(connection!=null&&!blocked.contains(peer)){
            try{connection.sendTextAndAwait(node.message("GET_CHAIN",Map.of()));}
            catch(RuntimeException e){outgoing.remove(peer,connection);}
        }
    }
}
