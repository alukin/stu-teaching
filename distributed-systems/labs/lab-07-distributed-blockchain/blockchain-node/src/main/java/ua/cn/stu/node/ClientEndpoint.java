// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.node;

import ua.cn.stu.protocol.*;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;

@WebSocket(path="/client")
public class ClientEndpoint {
    @Inject NodeService node;
    @OnTextMessage
    public String receive(String raw){
        try{return node.handleClient(JsonCodec.decode(raw));}
        catch(RuntimeException error){return JsonCodec.error("bad-request",node.name(),node.genesis(),error.getMessage());}
    }
}
