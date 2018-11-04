package host.furszy.zerocoinj.store;


import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.Commitment;
import org.pivxj.core.Sha256Hash;

import java.math.BigInteger;

public class StoredMint {

    private Commitment commitment;
    private BigInteger serial;
    private CoinDenomination denom;
    private Sha256Hash parentTxId;
    private int mintHeight;

    private int computedUpToHeight;
    private BigInteger acc;
    private BigInteger accWit;

    public StoredMint(Commitment commitment, BigInteger serial, CoinDenomination denom, Sha256Hash parentTxId, int mintHeight, int computedUpToHeight, BigInteger acc, BigInteger accWit) {
        this.commitment = commitment;
        this.serial = serial;
        this.denom = denom;
        this.parentTxId = parentTxId;
        this.mintHeight = mintHeight;
        this.computedUpToHeight = computedUpToHeight;
        this.acc = acc;
        this.accWit = accWit;
    }

    public Commitment getCommitment() {
        return commitment;
    }

    public BigInteger getSerial() {
        return serial;
    }

    public CoinDenomination getDenom() {
        return denom;
    }

    public Sha256Hash getParentTxId() {
        return parentTxId;
    }

    public int getMintHeight() {
        return mintHeight;
    }

    public int getComputedUpToHeight() {
        return computedUpToHeight;
    }

    public BigInteger getAcc() {
        return acc;
    }

    public BigInteger getAccWit() {
        return accWit;
    }

    public void setComputedUpToHeight(int computedUpToHeight) {
        this.computedUpToHeight = computedUpToHeight;
    }

    public void setAcc(BigInteger acc) {
        this.acc = acc;
    }

    public void setAccWit(BigInteger accWit) {
        this.accWit = accWit;
    }

    @Override
    public String toString() {
        return "StoredMint{" +
                "commitment=" + commitment +
                ", serial=" + serial +
                ", denom=" + denom +
                ", parentTxId=" + parentTxId +
                ", mintHeight=" + mintHeight +
                ", computedUpToHeight=" + computedUpToHeight +
                ", acc=" + acc +
                ", accWit=" + accWit +
                '}';
    }
}
