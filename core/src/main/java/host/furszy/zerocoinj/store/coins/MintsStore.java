package host.furszy.zerocoinj.store.coins;

import host.furszy.zerocoinj.store.coins.StoredMint;

import java.math.BigInteger;
import java.util.List;

public interface MintsStore {

    boolean put(StoredMint storedMint);

    StoredMint get(BigInteger commitmentValue);

    List<StoredMint> list();

    void deleteStore();

    boolean update(StoredMint storedMint);
}
