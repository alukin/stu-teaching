// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.client;

import ua.cn.stu.protocol.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;

/** One request per short-lived WebSocket /client connection. */
final class WsRequest {
    private static final ObjectMapper JSON=new ObjectMapper();
    static Wire.Envelope send(String endpoint,String type,Object payload){
        CompletableFuture<String> answer=new CompletableFuture<>();
        try(HttpClient http=HttpClient.newHttpClient()){
            WebSocket ws=http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(endpoint),new WebSocket.Listener(){
                    StringBuilder buffer=new StringBuilder();
                    @Override public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){
                        buffer.append(data);
                        if(last){answer.complete(buffer.toString());buffer.setLength(0);}
                        socket.request(1);return null;
                    }
                    @Override public void onOpen(WebSocket socket){socket.request(1);}
                    @Override public void onError(WebSocket socket,Throwable error){answer.completeExceptionally(error);}
                }).get(5,TimeUnit.SECONDS);
            ws.sendText(JsonCodec.message(type,"client",JsonCodec.GENESIS_HASH,payload),true).get(5,TimeUnit.SECONDS);
            String raw=answer.get(20,TimeUnit.SECONDS);
            ws.sendClose(WebSocket.NORMAL_CLOSURE,"bye");
            Wire.Envelope response=JsonCodec.decode(raw);
            if(response.type().equals("ERROR"))throw new IllegalArgumentException(JsonCodec.payload(response,Wire.Error.class).message());
            return response;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted",e);}
        catch(ExecutionException|TimeoutException e){throw new IllegalStateException("WebSocket request failed: "+endpoint,e);}
    }
    static void show(Wire.Envelope response){
        try{System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(response.payload()));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
