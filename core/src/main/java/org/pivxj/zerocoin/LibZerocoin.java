package org.pivxj.zerocoin;

public class LibZerocoin {

    public enum CoinDenomination {
        ZQ_ERROR(0),
        ZQ_ONE(1),
        ZQ_FIVE(5),
        ZQ_TEN(10),
        ZQ_FIFTY(50),
        ZQ_ONE_HUNDRED(100),
        ZQ_FIVE_HUNDRED(500),
        ZQ_ONE_THOUSAND(1000),
        ZQ_FIVE_THOUSAND(5000);

        private int denomination;

        CoinDenomination(int denomination) {
            this.denomination = denomination;
        }

        public int getDenomination() {
            return denomination;
        }

        public static CoinDenomination fromValue(int value){
            for (CoinDenomination coinDenomination : values()) {
                if (coinDenomination.denomination == value){
                    return coinDenomination;
                }
            }
            throw new IllegalArgumentException("Coin denomination doesn't exists");
        }
    };

}
