package org.pivxj.zerocoin;

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

    @Override
    protected void parse() throws ProtocolException {
        list = new ArrayList<>();
        accValue = readBignum();
        accWitnessValue = readBignum();
        long size = readUint32();
        System.out.println("size: " + size);
        for (int i = 0; i < size; i++) {
            list.add(readBignum());
        }
    }

    @Override
    public byte[] bitcoinSerialize() {
        try(ByteArrayOutputStream output = new ByteArrayOutputStream()){
            output.write(new VarInt(list.size()).encode());
            for (BigInteger bigInteger : list) {
                Utils.serializeBigInteger(output, bigInteger);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    @Override
    public String toString() {
        return "PubcoinsMessage{" +
                "list=" + Arrays.toString(list.toArray()) +
                '}';
    }
}
