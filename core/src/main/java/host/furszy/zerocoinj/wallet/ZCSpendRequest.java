package host.furszy.zerocoinj.wallet;

import com.google.common.collect.Lists;
import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.CoinSpend;
import com.zerocoinj.core.SpendType;
import com.zerocoinj.core.ZCoin;
import com.zerocoinj.core.accumulators.Accumulator;
import com.zerocoinj.core.accumulators.AccumulatorWitness;
import com.zerocoinj.core.accumulators.Accumulators;
import com.zerocoinj.core.context.ZerocoinContext;
import host.furszy.zerocoinj.protocol.GenWitMessage;
import host.furszy.zerocoinj.protocol.PubcoinsMessage;
import org.pivxj.core.*;
import org.pivxj.core.listeners.OnGetDataResponseEventListener;
import org.pivxj.script.Script;
import org.pivxj.script.ScriptBuilder;
import org.pivxj.script.ScriptOpCodes;
import org.pivxj.utils.Pair;
import org.pivxj.wallet.SendRequest;
import org.pivxj.wallet.exceptions.RequestFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.google.common.primitives.Ints.min;

public class ZCSpendRequest implements Callable<Transaction>,OnGetDataResponseEventListener {

    private static final Logger log = LoggerFactory.getLogger(ZCSpendRequest.class);

    private SendRequest sendRequest;
    private PeerGroup peerGroup;
    private ZerocoinContext zerocoinContext;
    private List<Pair<ZCoin,GenWitMessage>> waitingRequests = new ArrayList<>();
    private ConcurrentHashMap<Integer, Pair<ArrayList<ZCoin>,GenWitMessage>> requests;
    private LinkedBlockingQueue<PubcoinsMessage> messagesQueue = new LinkedBlockingQueue<>();
    private Transaction transaction;
    private Sha256Hash txHashOutput;

    public ZCSpendRequest(SendRequest sendRequest, PeerGroup peerGroup) {
        this.sendRequest = sendRequest;
        this.transaction = sendRequest.tx;
        Transaction temp = new Transaction(this.transaction.getParams());
        for (TransactionOutput output : this.transaction.getOutputs()) {
            temp.addOutput(output);
        }
        this.txHashOutput = temp.getHash();
        this.peerGroup = peerGroup;
        this.zerocoinContext = Context.get().zerocoinContext;
    }

    public SendRequest getSendRequest() {
        return sendRequest;
    }

    public PeerGroup getPeerGroup() {
        return peerGroup;
    }

    public void addWaitingRequest(GenWitMessage genWitMessage, ZCoin zCoin){
        if (zCoin.getCoinDenomination() == CoinDenomination.ZQ_ERROR) throw new IllegalArgumentException("Invalid denomination");
        waitingRequests.add(new Pair<>(zCoin, genWitMessage));
    }

    @Override
    public Transaction call() throws Exception {
        if (this.peerGroup.getConnectedPeers().isEmpty()) throw new IllegalStateException("No peers online");

        Map<CoinDenomination,Pair<ArrayList<ZCoin>,GenWitMessage>> genWitByDenomination = new HashMap<>();

        for (Pair<ZCoin, GenWitMessage> entry : waitingRequests) {
            GenWitMessage genWitMessage = entry.getSecond();
            if (genWitByDenomination.containsKey(genWitMessage.getDen())){
                Pair<ArrayList<ZCoin>,GenWitMessage> pair = genWitByDenomination.get(genWitMessage.getDen());
                GenWitMessage currentGenWit = pair.getSecond();
                // merge the current one
                currentGenWit.getFilter().merge(genWitMessage.getFilter());
                // start height must be the minimum
                currentGenWit.setStartHeight(min(currentGenWit.getStartHeight(), genWitMessage.getStartHeight()));
                pair.getFirst().add(entry.getFirst());
            }else {
                // create a new one
                Pair<ArrayList<ZCoin>, GenWitMessage> pair = new Pair<>(Lists.newArrayList(entry.getFirst()),genWitMessage);
                genWitByDenomination.put(genWitMessage.getDen(), pair);
                genWitMessage.complete();
            }
        }

        requests = new ConcurrentHashMap<>();
        for (Map.Entry<CoinDenomination, Pair<ArrayList<ZCoin>, GenWitMessage>> entry : genWitByDenomination.entrySet()) {
            requests.put(entry.getValue().getSecond().getRequestNum(), entry.getValue());
        }
        // TODO: send this to several peers and not just one
        Peer peer0 = this.peerGroup.getConnectedPeers().get(0);
        peer0.addOnGetDataResponseEventListener(this);
        for (Map.Entry<CoinDenomination, Pair<ArrayList<ZCoin>, GenWitMessage>> entry : genWitByDenomination.entrySet()) {
            peer0.sendMessage(entry.getValue().getSecond());
        }

        log.info("Waiting for node response..");
        // Now wait for the completeness..
        while (true){
            // Now spend it if all of the inputs are completed
            if (requests.size() == 0) {
                break;
            }

            PubcoinsMessage message = messagesQueue.take();
            onPubcoinReceived(message);
            requests.remove((int) message.getRequestNum());
        }


        log.info("Transaction ready!");
        for (Peer peer : peerGroup.getConnectedPeers()) {
            // Remove this listener now
            peer.removeOnGetDataResponse(this);
        }
        return transaction;
    }

    public void onPubcoinReceived(PubcoinsMessage pubcoinsMessage) throws InvalidSpendException {

        Pair<ArrayList<ZCoin>,GenWitMessage> pair = requests.get((int) pubcoinsMessage.getRequestNum());
        GenWitMessage request = pair.getSecond();
        ArrayList<ZCoin> coins = pair.getFirst();

        if (request != null) {
            for (ZCoin coinToSpend : coins) {

                try {
                    if (pubcoinsMessage.isHasRequestFailed()) {
                        log.info("Request have failed for {}", pair);
                        throw new RequestFailedException();
                    }
                    List<BigInteger> list = pubcoinsMessage.getList();
                    System.out.println("amount of data received: " + list.size());
                    // Create accumulator:

                    // First check that my commitment is in the filtered list
                    if (!list.contains(coinToSpend.getCommitment().getCommitmentValue())) {
                        // TODO: Notify fail here..
                        log.error("Pubcoins response list doesn't contains our commitment value..");
                        throw new InvalidSpendException("Pubcoins response list doesn't contains our commitment value.., check core sources");
                    }

                    // Accumulator
                    Accumulator acc = new Accumulator(
                            zerocoinContext.accumulatorParams,
                            coinToSpend.getCoinDenomination(),
                            pubcoinsMessage.getAccValue()
                    );

                    // Now accumulate the pubcoins to the result to obtain the same witness that is created by the rpc method.
                    Accumulator accWit = new Accumulator(
                            zerocoinContext.accumulatorParams,
                            coinToSpend.getCoinDenomination(),
                            pubcoinsMessage.getAccWitnessValue()

                    );
                    AccumulatorWitness witness = new AccumulatorWitness(accWit, coinToSpend);

                    int i = 0;
                    for (BigInteger bigInteger : list) {
                        witness.addElementUnchecked(bigInteger);
                        if (i % 100 == 0)
                            log.info("coin incremented: " + i);
                        i++;
                    }

                    log.info("Witness: " + witness.getValue());
                    if (!witness.verifyWitness(acc, coinToSpend)) {
                        log.error("Verify witness failed");
                        throw new InvalidSpendException("Verify witness failed");
                    }
                    log.info("Valid accumulator");

                    log.info("accumulator: " + acc.getValue());
                    log.info("witness: " + witness.getValue());


                    // 3) Complete the tx

                    // Accumulator checksum
                    BigInteger accChecksum = BigInteger.valueOf(
                            Accumulators.getChecksum(acc.getValue())
                    );

                    CoinSpend coinSpend = new CoinSpend(
                            zerocoinContext,
                            coinToSpend,
                            acc,
                            accChecksum,
                            witness,
                            txHashOutput,
                            SpendType.SPEND,
                            null
                    );


                    if (!coinSpend.verify(acc)) {
                        log.error("CoinSpend not valid");
                        throw new InvalidSpendException("CoinSpend verify failed");
                    }

                    if (!coinSpend.hasValidSignature()) {
                        log.error(String.format("CoinSpend signature invalid, coinSpend: %s", coinSpend));
                        //throw new InvalidSpendException("CoinSpend signature invalid");
                    }

                    System.out.println("coin randomness: " + coinToSpend);
                    System.out.println("coin spend: " + coinSpend);
                    System.out.println("private key: " + coinSpend.getPubKey().getPrivateKeyAsHex());

                    add(coinSpend, coinToSpend.getCommitment().getCommitmentValue());

                } catch (InvalidSpendException e) {
                    log.info("InvalidSpendException", e);
                    throw e;
                } catch (Exception e) {
                    log.info("Exception creating a spend", e);
                    throw new InvalidSpendException(e);
                }
            }
        }else {
            log.warn("Request returned null for {}" + pubcoinsMessage);
        }

    }

    @Override
    public void onResponseReceived(final PubcoinsMessage pubcoinsMessage) {
        log.info("onResponseReceived: " + pubcoinsMessage.toString());
        if (! requests.containsKey((int) pubcoinsMessage.getRequestNum())) return;
        messagesQueue.offer(pubcoinsMessage);
    }

    private synchronized void add(CoinSpend coinSpend, BigInteger commitmentValue){
        // zc_spend input
        byte[] coinSpendBytes = coinSpend.bitcoinSerialize();
        log.info("Coin spend bytes length: " + coinSpendBytes.length);
        log.info("Coin spend bytes: " + Hex.toHexString(coinSpendBytes));

        Script script = new ScriptBuilder()
                .op(ScriptOpCodes.OP_ZEROCOINSPEND) // OP_ZEROCOINMINT = 0xc2 == 194
                .number(coinSpendBytes.length)
                .build();

        byte[] halfScriptProgram = script.getProgram();
        log.info("Half program: " + Hex.toHexString(halfScriptProgram));
        byte[] program = new byte[halfScriptProgram.length + coinSpendBytes.length];
        System.arraycopy(halfScriptProgram,0,program,0,halfScriptProgram.length);
        System.arraycopy(coinSpendBytes,0,program,halfScriptProgram.length,coinSpendBytes.length);

        log.info("program: " + Hex.toHexString(program));
        TransactionInput transactionInput = new TransactionInput(transaction.getParams(), transaction, program);

        //use nSequence as a shorthand lookup of denomination
        //NOTE that this should never be used in place of checking the value in the final blockchain acceptance/verification
        //of the transaction
        transactionInput.setSequenceNumber(coinSpend.getDenomination().getDenomination());

        // Remove previos fake input first
        List<TransactionInput> inputs = transaction.getInputs();
        List<TransactionInput> realInputs = new ArrayList<>();

        for (TransactionInput input : inputs) {
            if (input.getConnectedOutput() == null){
                log.info("Not connected input.. should be one of the new ones..");
                realInputs.add(input);
                continue;
            }
            boolean isZcMint = input.getConnectedOutput().isZcMint();
            if (isZcMint &&
                    !( Utils.areBigIntegersEqual(input.getConnectedOutput().getScriptPubKey().getCommitmentValue(), commitmentValue)) ){
                realInputs.add(input);
            }
        }

        realInputs.add(transactionInput);

        transaction.clearInputs();
        for (TransactionInput realInput : realInputs) {
            transaction.addInput(realInput);
        }

        System.out.println(transaction);
        System.out.println("Coin spend inputs program: " + Hex.toHexString(transaction.getInput(0).getScriptBytes()));
    }
}
