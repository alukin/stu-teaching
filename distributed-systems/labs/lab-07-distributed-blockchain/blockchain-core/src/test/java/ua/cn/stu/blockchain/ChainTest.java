// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.blockchain;

import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChainTest {
    private static Chain chain(Wallet alice){
        Block genesis=Block.mine("0",List.of(Transaction.genesis(alice.publicKey(),100)),2,1000);
        return new Chain(genesis,2);
    }
    @Test void twoDependentTransactionsShareOneBlock(){
        Wallet a=Wallet.create(),b=Wallet.create(),c=Wallet.create();Chain chain=chain(a);
        String initial=chain.state().ownedBy(a.publicKey()).getFirst().id();
        var t1=Transaction.signed(a,List.of(initial),List.of(new Transaction.Output(b.publicKey(),40),new Transaction.Output(a.publicKey(),60)));
        var t2=Transaction.signed(b,List.of(t1.outputId(0)),List.of(new Transaction.Output(c.publicKey(),15),new Transaction.Output(b.publicKey(),25)));
        assertThrows(IllegalArgumentException.class,()->chain.mine(List.of(t2,t1)));
        chain.mine(List.of(t1,t2));
        assertEquals(60,chain.state().balance(a.publicKey()));
        assertEquals(25,chain.state().balance(b.publicKey()));
        assertEquals(15,chain.state().balance(c.publicKey()));
    }
    @Test void doubleSpendRejectsEntireCandidate(){
        Wallet a=Wallet.create(),b=Wallet.create();Chain chain=chain(a);
        String initial=chain.state().ownedBy(a.publicKey()).getFirst().id();
        var t1=Transaction.signed(a,List.of(initial),List.of(new Transaction.Output(b.publicKey(),40),new Transaction.Output(a.publicKey(),60)));
        var t2=Transaction.signed(a,List.of(initial),List.of(new Transaction.Output(b.publicKey(),30),new Transaction.Output(a.publicKey(),70)));
        assertThrows(IllegalArgumentException.class,()->chain.mine(List.of(t1,t2)));
        assertEquals(0,chain.height());assertEquals(100,chain.state().balance(a.publicKey()));
    }
    @Test void forksConvergeByWorkAndDeterministicTieBreaker(){
        Wallet a=Wallet.create();Chain left=chain(a);
        Chain right=new Chain(left.selectedPath().getFirst(),2);
        Block a1=left.mine(List.of()),b1=right.mine(List.of());
        // Timestamps can coincide, so generate a distinct competing block if necessary.
        if(a1.hash().equals(b1.hash())){
            right=new Chain(left.selectedPath().getFirst(),2);
            b1=Block.mine(right.tip(),List.of(),2,System.currentTimeMillis()+1234);
            right.accept(b1);
        }
        left.accept(b1);right.accept(a1);
        assertEquals(left.tip(),right.tip());
        Chain winner=left.tip().equals(a1.hash())?left:right;
        winner.mine(List.of());
        left.importPath(winner.selectedPath());right.importPath(winner.selectedPath());
        assertEquals(left.tip(),right.tip());
        assertEquals(left.state().fingerprint(),right.state().fingerprint());
    }
    @Test void invalidBlockCannotChangeState(){
        Wallet a=Wallet.create();Chain chain=chain(a);String oldTip=chain.tip();
        assertThrows(IllegalArgumentException.class,()->Block.restore(oldTip,2,0,List.of(),"0000bad"));
        assertEquals(oldTip,chain.tip());
    }
    @Test void unauthorizedSpendRejected(){
        Wallet a=Wallet.create(),b=Wallet.create();Chain chain=chain(a);
        String initial=chain.state().ownedBy(a.publicKey()).getFirst().id();
        var stolen=Transaction.signed(b,List.of(initial),List.of(new Transaction.Output(b.publicKey(),100)));
        assertThrows(IllegalArgumentException.class,()->chain.mine(List.of(stolen)));
    }
    @Test void malformedImportedPathIsAtomic(){
        Wallet a=Wallet.create(),b=Wallet.create();Chain current=chain(a);
        Block first=Block.mine(current.tip(),List.of(),2,5000);
        String original=current.tip();
        String utxo=current.state().fingerprint();
        Block second=Block.mine(first.hash(),List.of(),2,5001);
        Block unrelated=Block.mine("f".repeat(64),List.of(),2,5002);
        assertThrows(IllegalArgumentException.class,()->current.importPath(List.of(current.selectedPath().getFirst(),first,second,unrelated)));
        assertEquals(original,current.tip());assertEquals(1,current.knownBlocks());
        assertEquals(utxo,current.state().fingerprint());
    }
    @Test void unknownParentReturnsOrphan(){
        Wallet a=Wallet.create();Chain chain=chain(a);
        Block orphan=Block.mine("f".repeat(64),List.of(),2,2222);
        assertEquals(Chain.Result.ORPHAN,chain.accept(orphan));
        assertEquals(1,chain.knownBlocks());
    }
}
