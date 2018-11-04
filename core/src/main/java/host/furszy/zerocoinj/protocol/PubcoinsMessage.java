package host.furszy.zerocoinj.protocol;

import org.pivxj.core.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PubcoinsMessage extends Message {

    private BigInteger accValue;
    private BigInteger accWitnessValue;
    private List<BigInteger> list;
    private long requestNum;
    private boolean hasRequestFailed;
    private long heightStop;

    private long errorCode = 0;

    public enum ERROR_CODES {
        NO_ENOUGH_MINTS(0),
        NON_DETERMINED(1);

        public int code;

        ERROR_CODES(int code) {
            this.code = code;
        }
    }

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
        if (hasMoreBytesThan(64)) {
            accValue = readBignum();
            accWitnessValue = readBignum();
            long size = readUint32();
            for (int i = 0; i < size; i++) {
                list.add(readBignum());
            }
            if (hasMoreBytes()){
                heightStop = readUint32();
            }
        }else {
            hasRequestFailed = true;
            errorCode = readUint32();
        }
        length = cursor;
    }

    @Override
    public byte[] bitcoinSerialize() {
//        try(ByteArrayOutputStream output = new ByteArrayOutputStream()){
//            Utils.uint32ToByteStreamLE(requestNum,output);
//            Utils.serializeBigInteger(output,accValue);
//            Utils.serializeBigInteger(output,accWitnessValue);
//            output.write(new VarInt(list.size()).encode());
//            for (BigInteger bigInteger : list) {
//                Utils.serializeBigInteger(output, bigInteger);
//            }
//            return output.toByteArray();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        return null;
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

    public long getHeightStop() {
        return heightStop;
    }

    public long getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "PubcoinsMessage{" +
                "accValue=" + accValue.toString(16) +
                ", accWitnessValue=" + accWitnessValue.toString(16) +
                ", list=" + listToHexString() +
                ", requestNum=" + requestNum +
                ", hasRequestFailed=" + hasRequestFailed +
                ", heightStop=" + heightStop +
                ", errorCode=" + errorCode +
                '}';
    }

    private String listToHexString(){
        StringBuilder s = new StringBuilder();
        for (BigInteger bigInteger : list) {
            s.append(bigInteger.toString(16)).append(" , ");
        }
        return s.toString();
    }
}
