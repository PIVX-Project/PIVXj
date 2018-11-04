package host.furszy.zerocoinj.protocol;

import org.pivxj.core.Message;
import org.pivxj.core.NetworkParameters;
import org.pivxj.core.ProtocolException;

import java.math.BigInteger;

public class AccValueResponseMessage extends Message {

    private BigInteger accValue;
    private int height;

    public AccValueResponseMessage(NetworkParameters params, byte[] payloadBytes) {
        super(params, payloadBytes, 0, params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT), params.getDefaultSerializer(), payloadBytes.length);
    }

    @Override
    protected void parse() throws ProtocolException {
        this.accValue = readBignum();
        this.height = (int) readUint32();
    }

    public BigInteger getAccValue() {
        return accValue;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public String toString() {
        return "AccMsg{" +
                "accValue=" + accValue +
                ", height=" + height +
                '}';
    }
}
