package host.furszy.zerocoinj.store;

import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.accumulators.Accumulator;

import java.math.BigInteger;

public interface AccStore {

    void put(int height, Accumulator acc) throws AccStoreException;

    BigInteger get(int height, CoinDenomination denom) throws AccStoreException;

    void truncate();
}
