package host.furszy.zerocoinj.protocol;

import com.zerocoinj.core.CoinDenomination;
import org.pivxj.core.Message;
import org.pivxj.core.NetworkParameters;
import org.pivxj.core.ProtocolException;
import org.pivxj.core.Utils;

import java.io.IOException;
import java.io.OutputStream;

public class AccValueMessage extends Message {

    private int height;
    private CoinDenomination denom;

    public AccValueMessage(NetworkParameters params, int height, CoinDenomination denom) {
        super(params);
        this.height = height;
        this.denom = denom;
    }

    @Override
    protected void parse() throws ProtocolException {

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(height, stream);
        Utils.uint32ToByteStreamLE(denom.getDenomination(), stream);
    }

    public int getHeight() {
        return height;
    }

    public CoinDenomination getDenom() {
        return denom;
    }

    @Override
    public String toString() {
        return "height=" + height + ", denom=" + denom;
    }
}
