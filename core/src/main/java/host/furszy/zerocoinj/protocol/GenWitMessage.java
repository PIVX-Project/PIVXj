package host.furszy.zerocoinj.protocol;

import com.zerocoinj.core.CoinDenomination;
import org.pivxj.core.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

public class GenWitMessage extends Message {

    private BloomFilter bloomFilter;
    private int startHeight;
    private CoinDenomination den;

    public GenWitMessage(
            NetworkParameters params,
            int startHeight,
            CoinDenomination den,
            int elements,
            double falsePositiveRate,
            long randomNonce
    ) {
        super(params);
        this.startHeight = startHeight;
        this.den = den;
        this.bloomFilter = new BloomFilter(elements, falsePositiveRate, randomNonce);
    }

    public void insert(BigInteger data){
        this.bloomFilter.insert(Utils.serializeBigInteger(data));
    }

    public boolean contains(BigInteger data){
        return this.bloomFilter.contains(Utils.serializeBigInteger(data));
    }

    @Override
    protected void parse() throws ProtocolException {
        // Nothing
    }

    @Override
    public byte[] bitcoinSerialize() {
        try(ByteArrayOutputStream buf = new ByteArrayOutputStream()){
            bloomFilter.bitcoinSerialize(buf);
            Utils.uint32ToByteStreamLE(startHeight, buf);
           Utils.uint32ToByteStreamLE(den.getDenomination(), buf);
            return buf.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
