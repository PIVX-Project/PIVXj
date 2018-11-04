package host.furszy.zerocoinj.store;

import org.pivxj.core.Sha256Hash;
import org.pivxj.store.BlockStoreException;

public interface RollbackBlockStore {

    void rollbackTo(int height) throws BlockStoreException;
    void rollbackTo(Sha256Hash blockHash) throws BlockStoreException;
}
