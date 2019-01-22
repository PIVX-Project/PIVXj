package host.furszy.zerocoinj.store.coins;


import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.Commitment;
import org.pivxj.core.Sha256Hash;

import java.math.BigInteger;

public class StoredMint {

    // Only stored commitment value and randomness
    private BigInteger commitmentValue;
    // Content of the commitment
    private BigInteger serial;
    //
    private CoinDenomination denom;
    private Sha256Hash parentTxId;
    // Height in which the coin was added to the accumulator
    private int mintHeight;
    // Height for the witness accumulator calculation
    private int computedUpToHeight;
    private BigInteger acc;
    private BigInteger accWit;

    public StoredMint(
            BigInteger commitmentValue,
            BigInteger serial,
            CoinDenomination denom,
            Sha256Hash parentTxId,
            int mintHeight,
            int computedUpToHeight,
            BigInteger acc,
            BigInteger accWit) {

        this.commitmentValue = commitmentValue;
        this.serial = serial;
        this.denom = denom;
        this.parentTxId = parentTxId;
        this.mintHeight = mintHeight;
        this.computedUpToHeight = computedUpToHeight;
        this.acc = acc;
        this.accWit = accWit;
    }

    public BigInteger getCommitmentValue() {
        return commitmentValue;
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
                "commitmentValue=" + commitmentValue +
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
