// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.client;

import ua.cn.stu.blockchain.*;
import ua.cn.stu.protocol.*;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;
import picocli.CommandLine.*;
import jakarta.inject.Inject;
import java.util.*;

/** Quarkus command-mode WebSocket client; wallets never leave this process. */
@QuarkusMain
@Command(name="blockchain-client",mixinStandardHelpOptions=true,description="Lab 07 console client",
    subcommands={ClientMain.Demo.class,ClientMain.Create.class,ClientMain.Balance.class,
       ClientMain.Send.class,ClientMain.Status.class,ClientMain.Mine.class,
       ClientMain.Tx.class,ClientMain.Peers.class,ClientMain.Disconnect.class,ClientMain.Reconnect.class})
public class ClientMain implements Runnable,QuarkusApplication {
    @Inject CommandLine.IFactory factory;
    @Option(names="--node",defaultValue="ws://localhost:8081/client",scope=ScopeType.INHERIT,
            description="Node WebSocket URL") String node;
    @Override public void run(){System.out.println("Use --help to list commands");}
    @Override public int run(String...args){return new CommandLine(this,factory).execute(args);}
    Wire.Envelope call(String type,Object payload){return WsRequest.send(node,type,payload);}
    void execute(String type,Object payload){WsRequest.show(call(type,payload));}

    @Command(name="wallet-demo",description="Install the public demonstration key of genesis owner Alice")
    static class Demo implements Runnable { @Override public void run(){WalletStore.demo();} }
    @Command(name="wallet-create",description="Generate a new local wallet")
    static class Create implements Runnable { @Option(names="--name",required=true)String name;@Override public void run(){WalletStore.create(name,Wallet.create());} }
    @Command(name="balance",description="Read confirmed UTXOs belonging to a local wallet")
    static class Balance implements Runnable {
        @ParentCommand ClientMain root;
        @Option(names="--wallet",required=true)String name;
        @Override public void run(){
            Wallet w=WalletStore.load(name);
            var response=root.call("UTXOS",new Wire.UtxosRequest(w.publicBase64()));
            Wire.Utxo[] all=JsonCodec.payload(response,Wire.Utxo[].class);
            long total=0;for(var u:all)total=Math.addExact(total,u.amount());
            System.out.printf("%s: %d confirmed units (%d UTXOs)%n",name,total,all.length);
        }
    }
    @Command(name="send",description="Sign and submit a transaction")
    static class Send implements Runnable {
        @ParentCommand ClientMain root;
        @Option(names="--from",required=true)String from;
        @Option(names="--to",required=true)String to;
        @Option(names="--amount",required=true)long amount;
        @Override public void run(){
            if(amount<=0)throw new IllegalArgumentException("Positive amount required");
            Wallet sender=WalletStore.load(from),recipient=WalletStore.load(to);
            Wire.Envelope response=root.call("UTXOS",new Wire.UtxosRequest(sender.publicBase64()));
            Wire.Utxo[] available=JsonCodec.payload(response,Wire.Utxo[].class);
            List<String> ids=new ArrayList<>();long total=0;
            for(var u:available){ids.add(u.id());total=Math.addExact(total,u.amount());if(total>=amount)break;}
            if(total<amount)throw new IllegalArgumentException("Insufficient confirmed funds");
            List<Transaction.Output> outs=new ArrayList<>();outs.add(new Transaction.Output(recipient.publicKey(),amount));
            if(total>amount)outs.add(new Transaction.Output(sender.publicKey(),total-amount));
            Transaction tx=Transaction.signed(sender,ids,outs);
            root.execute("SUBMIT_TX",Wire.from(tx));
        }
    }
    @Command(name="status",description="Read node height, tip, work and UTXO digest")
    static class Status implements Runnable { @ParentCommand ClientMain root;@Override public void run(){root.execute("STATUS",Map.of());} }
    @Command(name="mine",description="Mine one block (includes pending transactions or an empty block)")
    static class Mine implements Runnable { @ParentCommand ClientMain root;@Override public void run(){root.execute("MINE",Map.of());} }
    @Command(name="tx",description="Read transaction status")
    static class Tx implements Runnable {
        @ParentCommand ClientMain root;@Option(names="--id",required=true)String id;
        @Override public void run(){root.execute("TX_STATUS",new Wire.TxQuery(id));}
    }
    @Command(name="peers",description="List configured and partitioned peers")
    static class Peers implements Runnable { @ParentCommand ClientMain root;@Override public void run(){root.execute("PEERS",Map.of());} }
    @Command(name="disconnect",description="Simulate a logical network partition to a peer")
    static class Disconnect implements Runnable {
        @ParentCommand ClientMain root;@Option(names="--peer",required=true)String peer;
        @Override public void run(){root.execute("DISCONNECT",new Wire.PeerCommand(peer));}
    }
    @Command(name="reconnect",description="Remove partition and request synchronization")
    static class Reconnect implements Runnable {
        @ParentCommand ClientMain root;@Option(names="--peer",required=true)String peer;
        @Override public void run(){root.execute("RECONNECT",new Wire.PeerCommand(peer));}
    }
}
