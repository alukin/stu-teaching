// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.node;

import ua.cn.stu.protocol.*;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;

@WebSocketClient(path="/peer")
public class OutboundPeer {
    @Inject NodeService node;
    @Inject PeerLinks links;
    @OnTextMessage
    public String received(String raw){
        try{return node.handlePeer(JsonCodec.decode(raw));}
        catch(RuntimeException error){return null;}
    }
    @OnClose
    public void closed(WebSocketClientConnection connection){links.disconnected(connection);}
}
