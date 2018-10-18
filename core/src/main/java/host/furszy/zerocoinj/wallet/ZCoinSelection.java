package host.furszy.zerocoinj.wallet;

import com.zerocoinj.core.CoinDenomination;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ZCoinSelection {

    public static CoinDenomination nextDenomInDecreasingOrder(CoinDenomination coinDenomination) {
        return CoinDenomination.values()[coinDenomination.ordinal() - 1];
    }

    public static List<CoinDenomination> decoupleNumberInExactDenominations(int number, CoinDenomination denom){
        List<CoinDenomination> usedDenom = new ArrayList<>();

        // This is for the coinDenomination that are greater than the actual number
        if (denom.getDenomination() > number) {
            usedDenom.add(denom);
            return usedDenom;
        }

        // This is for the coinDenomination that multiplied by a factor of X are greater than the actual number
        int num = number / denom.getDenomination();
        if (denom.getDenomination() * (num + 1) > number) {
            int numToAdd = num + 1;
            if (numToAdd <= 7) {
                for (int i = 0; i < numToAdd; i++) {
                    usedDenom.add(denom);
                }
                return usedDenom;
            }
        }
        return usedDenom;
    }

    /**
     *
     * @param number
     * @param denom
     * @param maxAmountOfDenom
     * @return
     */
    public static Set<List<CoinDenomination>> decoupleNumberInDenominations(int number, CoinDenomination denom, int maxAmountOfDenom) {

        Set<List<CoinDenomination>> set = new HashSet<>();

        PSolution pSolution = new PSolution(number);

        if (number > denom.getDenomination()){
            int num = number / denom.getDenomination();
            int mod = num % denom.getDenomination();
            if (mod != 0){
                int numToAdd = num + 1;
                PSolution temp1 = pSolution.copy();
                if (numToAdd <= maxAmountOfDenom) {
                    pSolution.add(denom, numToAdd);
                    set.add(pSolution.selection);
                }
                //  this maybe need more rounds..
                try {

                    temp1.add(denom, num);
                    CoinDenomination nextDenom = nextDenomInDecreasingOrder(denom);

                    while (nextDenom != CoinDenomination.ZQ_ERROR){
                        int modDen = mod % nextDenom.getDenomination();

                        if (temp1.getValue() + nextDenom.getDenomination() > temp1.number){
                            //
                            PSolution temp = temp1.copy();
                            temp.selection.add(nextDenom);
                            if (temp.isValid()) {
                                set.add(temp.selection);
                            }
                        }

                        if (temp1.denomSpace() >= modDen) {
                            Set<List<CoinDenomination>> set2 = decoupleNumberInDenominations(mod, nextDenom, temp1.denomSpace());
                            for (List<CoinDenomination> coinDenominations : set2) {
                                PSolution temp = temp1.copy();
                                temp.selection.addAll(coinDenominations);
                                if (temp.isValid()) {
                                    set.add(temp.selection);
                                }
                            }
                        }

                        nextDenom = nextDenomInDecreasingOrder(nextDenom);
                    }
                }catch (IllegalArgumentException e){
                    // nothing..
                }
                return set;
            }else {
                if (num > maxAmountOfDenom) return set; // invalid num

                pSolution.add(denom, num);
                if (pSolution.getValue() >= number && pSolution.denomSpace() <= maxAmountOfDenom){
                    set.add(pSolution.selection);
                    return set;
                }
            }


        }else {
            int num = number % denom.getDenomination();
            PSolution temp1 = pSolution.copy();
            if (num < denom.getDenomination()){
                pSolution.selection.add(denom);
                set.add(pSolution.selection);
            }

            try {

                CoinDenomination nextDenom = nextDenomInDecreasingOrder(denom);

                while (nextDenom != CoinDenomination.ZQ_ERROR){
                    if (temp1.denomSpace() >= (num % nextDenom.getDenomination())) {
                        Set<List<CoinDenomination>> set2 = decoupleNumberInDenominations(num, nextDenom, temp1.denomSpace());
                        for (List<CoinDenomination> coinDenominations : set2) {
                            PSolution temp = temp1.copy();
                            temp.selection.addAll(coinDenominations);
                            if (temp.isValid()) {
                                set.add(temp.selection);
                            }
                        }
                    }

                    nextDenom = nextDenomInDecreasingOrder(nextDenom);
                }
                return set;
            }catch (IllegalArgumentException e){
                // nothing..
            }
        }
        set.add(pSolution.selection);
        return set;
    }

    public static List<PSolution> calculateAllPossibleSolutionsFor(int numberToDecouple){
        List<PSolution> listOfPossibleSolutions = new ArrayList<>();
        // Start the loop for each denom
        for (CoinDenomination coinDenomination : CoinDenomination.invertedValues()) {
            if (CoinDenomination.ZQ_ERROR == coinDenomination) continue;

            PSolution pSolution = new PSolution(numberToDecouple);
            pSolution.selection = decoupleNumberInExactDenominations(pSolution.number, coinDenomination);

            if (!pSolution.selection.isEmpty()){
                listOfPossibleSolutions.add(pSolution);
            }

            // Now add the remaining if the denomination value is lower than the number and add all of the possibilities that start with that denomination and are not included
            // on the list of possibilities
            if (coinDenomination.getDenomination() <= pSolution.number){
                // Here i have to decouple it in lower denom.

                // First need to get the max amount of this denom that enters on the number and start decreasing the amount on every loop + increasing the
                // lower denom numbers
                int numberOfDenom = pSolution.number / coinDenomination.getDenomination();

                if (numberOfDenom < 7){
                    // loop until this denom value gets to den 0
                    // to have the:
                    // 3x100 + 1x50
                    // 3x100 + 4x10
                    // 3x100 + 3x10 + 1x5
                    // 2x100 + 5x50
                    // 1x100 + 5x50
                    for (int j = numberOfDenom; j != 0; j--) {

                        PSolution internalSol = new PSolution(pSolution.number);
                        // First add the amount of coinDenom that work as the base (3x100)
                        internalSol.add(coinDenomination, j);

                        // Now we have the minimum here, let's start getting deeper until we get all of the results
                        // Now the number to decouple
                        int number = internalSol.number - internalSol.getValue();
                        int internalDenomSpace = internalSol.denomSpace();

                        CoinDenomination secondLevelDeno = nextDenomInDecreasingOrder(coinDenomination);
                        while (secondLevelDeno != CoinDenomination.ZQ_ERROR) {

                            // Temp
                            PSolution secondLevelSolution = internalSol.copy();

                            // Now the loop on the internal numbers
                            CoinDenomination nextDenom = secondLevelDeno;
                            while (nextDenom != CoinDenomination.ZQ_ERROR) {
                              // First need to get the max amount of this denom that enters on the number and start decreasing the amount on every loop + increasing the
                              // lower denom numbers
                              int numberOfDenomInternal = numberOfDenom / nextDenom.getDenomination();
                              if (numberOfDenomInternal <= internalDenomSpace && !secondLevelSolution.isValid()) {
                                  // Now decouple

                                  Set<List<CoinDenomination>> set = decoupleNumberInDenominations(number, nextDenom, internalDenomSpace);

                                  for (List<CoinDenomination> denom : set) {
                                      PSolution temp = secondLevelSolution.copy();
                                      temp.selection.addAll(denom);

                                      if (temp.isValid()) {
                                          secondLevelSolution.selection.addAll(denom);

                                          if (secondLevelSolution.isValid()) {
                                              listOfPossibleSolutions.add(secondLevelSolution);
                                              // Reset values
                                              secondLevelSolution = internalSol.copy();
                                              number = internalSol.number - internalSol.getValue();
                                              internalDenomSpace = internalSol.denomSpace();
                                          } else {
                                              number = number - secondLevelSolution.getValue();
                                              internalDenomSpace = secondLevelSolution.denomSpace();
                                          }
                                      }
                                  }
                              }else {
                                  if (secondLevelSolution.isValid()){
                                      listOfPossibleSolutions.add(secondLevelSolution);
                                  }
                              }
                              nextDenom = nextDenomInDecreasingOrder(nextDenom);
                            }
                            secondLevelDeno = nextDenomInDecreasingOrder(secondLevelDeno);

                        }
                    }
                }
            }
        }
        return listOfPossibleSolutions;
    }


}
