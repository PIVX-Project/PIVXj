package host.furszy.zerocoinj.store;

import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.ZCoin;

import java.io.IOException;

public class AccStoreException extends Exception {

    public int height;
    public ZCoin zCoin;
    public CoinDenomination denom;

    public AccStoreException(Exception e1) {
        super(e1);
    }

    public AccStoreException(String message, ZCoin zCoin, int height) {
        super(message);
        this.zCoin = zCoin;
        this.height = height;
        this.denom = zCoin.getCoinDenomination();
    }

    public AccStoreException(String message, int height, CoinDenomination denomination) {
        super(message);
        this.height = height;
        this.denom = denom;
    }
}
