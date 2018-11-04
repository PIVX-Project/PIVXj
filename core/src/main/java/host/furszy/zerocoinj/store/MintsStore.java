package host.furszy.zerocoinj.store;

import java.math.BigInteger;

public interface MintsStore {

    void put(StoredMint storedMint);

    StoredMint get(BigInteger commitmentValue);

}
