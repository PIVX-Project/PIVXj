package host.furszy.zerocoinj.protocol;

import org.pivxj.core.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PubcoinsMessage extends Message {

    private BigInteger accValue;
    private BigInteger accWitnessValue;
    private List<BigInteger> list;
    private long requestNum;
    private boolean hasRequestFailed;

    public PubcoinsMessage(NetworkParameters params, byte[] payload, int offset, int lenght) throws ProtocolException {
        super(
                params,
                payload,
                offset,
                params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT),
                params.getDefaultSerializer(),
                lenght
        );
    }

    public PubcoinsMessage(NetworkParameters params, BigInteger accValue, BigInteger accWitnessValue, List<BigInteger> list, long requestNum) {
        super(params);
        this.accValue = accValue;
        this.accWitnessValue = accWitnessValue;
        this.list = list;
        this.requestNum = requestNum;
        //
        this.length = bitcoinSerialize().length;
    }

    @Override
    protected void parse() throws ProtocolException {
        list = new ArrayList<>();
        requestNum = readUint32();
        if (hasMoreBytes()) {
            accValue = readBignum();
            accWitnessValue = readBignum();
            long size = readUint32();
            for (int i = 0; i < size; i++) {
                list.add(readBignum());
            }
        }else
            hasRequestFailed = true;
        length = cursor;
    }

    @Override
    public byte[] bitcoinSerialize() {
        try(ByteArrayOutputStream output = new ByteArrayOutputStream()){
            Utils.uint32ToByteStreamLE(requestNum,output);
            Utils.serializeBigInteger(output,accValue);
            Utils.serializeBigInteger(output,accWitnessValue);
            output.write(new VarInt(list.size()).encode());
            for (BigInteger bigInteger : list) {
                Utils.serializeBigInteger(output, bigInteger);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isHasRequestFailed() {
        return hasRequestFailed;
    }

    public List<BigInteger> getList() {
        return list;
    }

    public BigInteger getAccValue() {
        return accValue;
    }

    public BigInteger getAccWitnessValue() {
        return accWitnessValue;
    }

    public long getRequestNum() {
        return requestNum;
    }


    @Override
    public String toString() {
        return "PubcoinsMessage{" +
                "accValue=" + accValue +
                ", accWitnessValue=" + accWitnessValue +
                ", list=" + list +
                ", requestNum=" + requestNum +
                '}';
    }
}
