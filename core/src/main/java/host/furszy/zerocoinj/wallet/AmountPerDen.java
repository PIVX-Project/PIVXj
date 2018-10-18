package host.furszy.zerocoinj.wallet;

import com.zerocoinj.core.CoinDenomination;

import org.pivxj.core.Coin;

import java.util.Objects;

public class AmountPerDen {

    private CoinDenomination den;
    private Coin amount = Coin.ZERO;
    private int coinsCont;

    public AmountPerDen(CoinDenomination den) {
        this.den = den;
    }

    public AmountPerDen(CoinDenomination den, int coinsCont) {
        this.den = den;
        this.coinsCont = coinsCont;
        this.amount = Coin.valueOf(den.getDenomination() * coinsCont, 0 );
    }

    public CoinDenomination getDen() {
        return den;
    }

    public Coin getAmount() {
        if (amount == Coin.ZERO){
            this.amount = Coin.valueOf(den.getDenomination() * coinsCont, 0 );
        }
        return amount;
    }

    public int getCoinsCont() {
        return coinsCont;
    }

    public void increment(Coin value) {
        amount = amount.add(value);
        coinsCont++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AmountPerDen that = (AmountPerDen) o;
        return den == that.den;
    }

    @Override
    public int hashCode() {

        return Objects.hash(den);
    }
}