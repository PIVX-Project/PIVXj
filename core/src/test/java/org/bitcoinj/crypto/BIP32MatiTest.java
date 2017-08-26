package org.bitcoinj.crypto;

import org.bitcoinj.core.Address;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by furszy on 6/3/17.
 */
public class BIP32MatiTest {


    @Test
    public void createMasterKeyAndDeriveChildsTest(){
        // network params
        MainNetParams mainNetParams = MainNetParams.get();
        // secure random generator
        LinuxSecureRandom linuxSecureRandom = new LinuxSecureRandom();
        // create seed
        byte[] seed = linuxSecureRandom.engineGenerateSeed(256);

        // create the master private key
        DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey(seed);
        // now i have to create the extended public key and extended private key
        String extendedPubKey = masterPrivateKey.serializePubB58(mainNetParams);
        String extendedPrivKey = masterPrivateKey.serializePrivB58(mainNetParams);

        System.out.println("exteded priv key: "+extendedPrivKey);
        System.out.println("exteded pub key: "+extendedPubKey);

        // Create key tree class
        DeterministicHierarchy deterministicHierarchy = new DeterministicHierarchy(masterPrivateKey);


        // another test..

        DeterministicKeyChain deterministicKeyChain = DeterministicKeyChain.builder().random(new SecureRandom(),256).build();

        System.out.println("Mnemonic: "+ Arrays.toString(deterministicKeyChain.getMnemonicCode().toArray(new String[12])));

        List<ChildNumber> accountList = new ArrayList();
        accountList.add(new ChildNumber(0,false));

        // this is the master key, should have depth 0
        // path -> (0)
        DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(deterministicKeyChain.getSeed().getSeedBytes());  //deterministicKeyChain.getKeyByPath(accountList,true);

        System.out.println("Issue external keys: "+deterministicKeyChain.getIssuedExternalKeys());
        System.out.println("Issue internal keys: "+deterministicKeyChain.getIssuedInternalKeys());

        System.out.println("master Key: "+Arrays.toString(masterKey.getPath().toArray()));

        assert masterKey.getDepth()==0:"depth: "+masterKey.getDepth();


        // Now the accounts
        // First account, path -> (m,0)
        List<ChildNumber> account2List = new ArrayList();
        account2List.add(new ChildNumber(0,false));

        DeterministicKey account0 = deterministicKeyChain.getKeyByPath(account2List,true);

        // Second account, path -> (m,1)
        List<ChildNumber> account3List = new ArrayList();
        account3List.add(new ChildNumber(1,false));
        DeterministicKey account1 = deterministicKeyChain.getKeyByPath(account3List,true);

        // now i can get the extended public key and private key for each account
        String extendedPublicKeyAccount0Str = account0.serializePubB58(mainNetParams);
        String extendedPrivKeyAccount0Str = account0.serializePrivB58(mainNetParams);

        // now i can create address from the extended public key without have access to the private key
        DeterministicKey deterministicKey = DeterministicKey.deserializeB58(extendedPublicKeyAccount0Str,mainNetParams);
        DeterministicKey firstAddressDerived = HDKeyDerivation.deriveChildKey(deterministicKey,0);
        Address firstAddress = firstAddressDerived.toAddress(mainNetParams);

        DeterministicKey deterministicKey2 = DeterministicKey.deserializeB58(extendedPublicKeyAccount0Str,mainNetParams);
        Address copyFirstAddress = HDKeyDerivation.deriveChildKey(deterministicKey2,0).toAddress(mainNetParams);
        //Address copyFirstAddress = deterministicKey2.derive(0).toAddress(mainNetParams);

        System.out.println("address 1: "+firstAddress.toBase58());
        System.out.println("address 2: "+copyFirstAddress.toBase58());

        // con esto compruebo que puedo exportar la public key y generar las mismas address sin necesidad de tener un archivo de copia.
        // Se generan las mismas addresses en base de una extended public key :) .
        assert firstAddress.equals(copyFirstAddress):"address is not the same, i cannot recover this from other apps";

        // one more check..
        // me fijo si la key derivada puede firmar algo o no..
        assert firstAddressDerived.isPubKeyOnly():"Address is not pub key only, something wrong here..";
    }

}
