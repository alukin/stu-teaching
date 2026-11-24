// SPDX-License-Identifier: GPL-3.0-or-later
package ua.cn.stu.node;

import ua.cn.stu.blockchain.Block;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Opt-in scheduled miner; only mines if the node has pending transactions. */
@ApplicationScoped
public class MinerService {
    @Inject NodeService node;

    @Scheduled(every="${node.miner.interval}", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
    void minePending() {
        try {
            Block block=node.minePending();
            if(block!=null){
                node.announceBlock(block,null);
                Log.infof("Miner %s created block %s",node.name(),block.hash());
            }
        }catch(RuntimeException failed){
            Log.warn("Automatic mining failed; will retry on the next tick",failed);
        }
    }
}
