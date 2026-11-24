// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.client;

import ua.cn.stu.blockchain.Wallet;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Teaching-only, plaintext PKCS#8 keys. Never use these files with real currency. */
final class WalletStore {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Path ROOT=Path.of("wallets");
    public record KeyFile(String publicKey,String privateKey) {}
    static Path path(String name){
        if(name==null||!name.matches("[a-zA-Z0-9_-]{1,40}"))throw new IllegalArgumentException("Wallet name must be alphanumeric");
        return ROOT.resolve(name+".json");
    }
    static Wallet load(String name){
        try{ KeyFile keys=JSON.readValue(path(name).toFile(),KeyFile.class);return Wallet.restore(keys.publicKey(),keys.privateKey());}
        catch(IOException e){throw new IllegalArgumentException("Missing wallet: "+name+". Run wallet-demo or wallet-create",e);}
    }
    static void create(String name,Wallet wallet){
        try{Files.createDirectories(ROOT);Path f=path(name);
            if(Files.exists(f))throw new IllegalArgumentException("Wallet already exists: "+name);
            try(OutputStream out=Files.newOutputStream(f,StandardOpenOption.CREATE_NEW)){
                JSON.writeValue(out,new KeyFile(wallet.publicBase64(),wallet.privateBase64()));
            }
            System.out.println("Created "+f+" (plaintext training private key; never commit)");
        }catch(IOException e){throw new IllegalStateException("Could not save wallet",e);}
    }
    static void demo(){
        try(InputStream in=WalletStore.class.getClassLoader().getResourceAsStream("demo-alice-wallet.json")){
            if(in==null)throw new IllegalStateException("Missing demo-alice-wallet.json");
            KeyFile k=JSON.readValue(in,KeyFile.class);
            create("alice",Wallet.restore(k.publicKey(),k.privateKey()));
        }catch(IOException e){throw new IllegalStateException(e);}
    }
}
