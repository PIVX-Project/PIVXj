package host.furszy.zerocoinj.wallet;

import com.google.common.collect.Lists;
import com.zerocoinj.core.CoinDenomination;

import java.util.*;

public class PSolution {
    int number;
    List<CoinDenomination> selection = new ArrayList<>();

    public PSolution(int number) {
        this.number = number;
    }

    public PSolution(int number, List<CoinDenomination> selection) {
        this.number = number;
        this.selection = selection;
    }

    public int getValue() {
        int value = 0;
        for (CoinDenomination coinDenomination : selection) {
            value += coinDenomination.getDenomination();
        }
        return value;
    }

    public void add(CoinDenomination coinDenomination, int amount) {
        if (amount > 7)
            throw new IllegalArgumentException("Invalid amount, it's greater than the max denominations, amount: " + amount);
        for (int i = 0; i < amount; i++) {
            selection.add(coinDenomination);
        }
    }

    public String parseSelectionToString() {
        Map<CoinDenomination, Integer> amountPerDenom = new HashMap<>();
        for (CoinDenomination coinDenomination : selection) {
            if (amountPerDenom.containsKey(coinDenomination)) {
                int currentAmount = amountPerDenom.remove(coinDenomination);
                currentAmount++;
                amountPerDenom.put(coinDenomination, currentAmount);
            } else {
                amountPerDenom.put(coinDenomination, 1);
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        Set<Map.Entry<CoinDenomination, Integer>> entries = amountPerDenom.entrySet();
        Iterator<Map.Entry<CoinDenomination, Integer>> it = entries.iterator();
        int totalAmount = 0;
        while (it.hasNext()) {
            Map.Entry<CoinDenomination, Integer> entry = it.next();
            stringBuilder.append(
                    entry.getValue() + "x" + entry.getKey().getDenomination()
            );
            if (it.hasNext()) {
                stringBuilder.append(" + ");
            }
            totalAmount += entry.getValue() * entry.getKey().getDenomination();
        }
        stringBuilder.append(" = " + totalAmount);
        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        return "Num of denom: " + selection.size() + " , " + parseSelectionToString();
    }

    /**
     * Returns the amount of free space denominations that this solution has
     *
     * @return
     */
    public int denomSpace() {
        return 7 - selection.size();
    }

    public boolean isValid() {
        int space = denomSpace();
        return getValue() >= number && space <= 7 && space >= 0;
    }

    public PSolution copy() {
        List<CoinDenomination> list = Lists.newArrayList(selection);
        list = Lists.reverse(list);
        return new PSolution(
                number,
                list
        );
    }

    /**
     * Calculates the quantity that is needed for each denomination
     *
     * @return
     */
    public Map<CoinDenomination, Integer> getNeededDenominations() {
        Map<CoinDenomination, Integer> map = new HashMap<>();
        for (CoinDenomination coinDenomination : selection) {
            if (map.containsKey(coinDenomination)){
                map.put(
                        coinDenomination,
                        map.remove(coinDenomination) + 1
                );
            }else {
                map.put(coinDenomination, 1);
            }
        }
        return map;
    }

}