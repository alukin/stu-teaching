// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.protocol;

import org.junit.jupiter.api.Test;
import ua.cn.stu.blockchain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonCodecTest {
    @Test void genesisLoadsFromClasspathAndHasPinnedHash(){
        Chain chain=JsonCodec.createChain();
        assertEquals(JsonCodec.GENESIS_HASH,chain.genesisHash());
        assertEquals(100,chain.state().utxos().getFirst().amount());
    }
    @Test void signedTransactionSurvivesJsonRoundTrip(){
        Wallet a=Wallet.create(),b=Wallet.create();
        Transaction tx=Transaction.signed(a,List.of("example:0"),List.of(new Transaction.Output(b.publicKey(),1)));
        String raw=JsonCodec.message("NEW_TX","A",JsonCodec.GENESIS_HASH,Wire.from(tx));
        var recovered=Wire.toTx(JsonCodec.payload(JsonCodec.decode(raw),Wire.Tx.class));
        assertEquals(tx.id(),recovered.id());assertTrue(recovered.signatureValid());
    }
    @Test void minedBlockSurvivesJsonRoundTrip(){
        var chain=JsonCodec.createChain();
        var block=chain.mine(List.of());
        String raw=JsonCodec.message("NEW_BLOCK","A",chain.genesisHash(),Wire.from(block));
        var restored=Wire.toBlock(JsonCodec.payload(JsonCodec.decode(raw),Wire.BlockData.class));
        assertEquals(block.hash(),restored.hash());assertTrue(restored.proofValid(2));
    }
    @Test void tamperedTransactionIdIsRejected(){
        Wallet a=Wallet.create(),b=Wallet.create();
        Transaction tx=Transaction.signed(a,List.of("x:0"),List.of(new Transaction.Output(b.publicKey(),1)));
        Wire.Tx wire=Wire.from(tx);
        var broken=new Wire.Tx("bad",wire.signer(),wire.inputs(),wire.outputs(),wire.signature(),false);
        assertThrows(IllegalArgumentException.class,()->Wire.toTx(broken));
    }
}
