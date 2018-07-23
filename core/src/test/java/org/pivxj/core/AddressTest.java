/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pivxj.core;

import org.pivxj.params.MainNetParams;
import org.pivxj.params.Networks;
import org.pivxj.params.TestNet3Params;
import org.pivxj.script.Script;
import org.pivxj.script.ScriptBuilder;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.pivxj.core.Utils.HEX;
import static org.junit.Assert.*;

public class AddressTest {
    static final NetworkParameters testParams = TestNet3Params.get();
    static final NetworkParameters mainParams = MainNetParams.get();

    @Test
    public void testJavaSerialization() throws Exception {
        Address testAddress = Address.fromBase58(testParams, "y4EmHcSgo7Tent6cWkehtG1QZdEmxJwMBG");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(testAddress);
        VersionedChecksummedBytes testAddressCopy = (VersionedChecksummedBytes) new ObjectInputStream(
                new ByteArrayInputStream(os.toByteArray())).readObject();
        assertEquals(testAddress, testAddressCopy);

        Address mainAddress = Address.fromBase58(mainParams, "DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm");
        os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(mainAddress);
        VersionedChecksummedBytes mainAddressCopy = (VersionedChecksummedBytes) new ObjectInputStream(
                new ByteArrayInputStream(os.toByteArray())).readObject();
        assertEquals(mainAddress, mainAddressCopy);
    }

    @Test
    public void stringification() throws Exception {

        if(CoinDefinition.supportsTestNet) {
        // Test a testnet address.
            Address a = new Address(testParams, HEX.decode("4f9b4c8a7412b41eb7f2b471edcf83b8fd22d41c"));
            assertEquals("y4EmHcSgo7Tent6cWkehtG1QZdEmxJwMBG", a.toString());
            assertFalse(a.isP2SHAddress());
        }
        Address b = new Address(mainParams, HEX.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));
        assertEquals(CoinDefinition.UNITTEST_ADDRESS, b.toString());

        assertFalse(b.isP2SHAddress());
    }
    
    @Test
    public void decoding() throws Exception {
        Address a = Address.fromBase58(testParams, "y4EmHcSgo7Tent6cWkehtG1QZdEmxJwMBG");
        assertEquals("4f9b4c8a7412b41eb7f2b471edcf83b8fd22d41c", Utils.HEX.encode(a.getHash160()));

        Address b = Address.fromBase58(mainParams, "DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm");
        assertEquals("d88fb72e7b7478af88c095061ce6153ca22c203c", Utils.HEX.encode(b.getHash160()));
    }
    @Test
    public void errorPaths() {
        // Check what happens if we try and decode garbage.
        try {
            Address.fromBase58(testParams, "this is not a valid address!");
            fail();
        } catch (WrongNetworkException e) {
            fail();
        } catch (AddressFormatException e) {
            // Success.
        }

        // Check the empty case.
        try {
            Address.fromBase58(testParams, "");
            fail();
        } catch (WrongNetworkException e) {
            fail();
        } catch (AddressFormatException e) {
            // Success.
        }

        // Check the case of a mismatched network.
        try {
            Address.fromBase58(testParams, "DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm");
            fail();
        } catch (WrongNetworkException e) {
            // Success.
            assertEquals(e.verCode, MainNetParams.get().getAddressHeader());
            assertTrue(Arrays.equals(e.acceptableVersions, TestNet3Params.get().getAcceptableAddressCodes()));
        } catch (AddressFormatException e) {
            fail();
        }
    }

    @Test
    public void getNetwork() throws Exception {
        NetworkParameters params = Address.getParametersFromAddress(CoinDefinition.UNITTEST_ADDRESS);
        assertEquals(MainNetParams.get().getId(), params.getId());
        if(CoinDefinition.supportsTestNet)
        {
            params = Address.getParametersFromAddress("y4EmHcSgo7Tent6cWkehtG1QZdEmxJwMBG");
            assertEquals(TestNet3Params.get().getId(), params.getId());
        }
    }

    @Test
    public void getAltNetwork() throws Exception {
        // An alternative network
        class AltNetwork extends MainNetParams {
            AltNetwork() {
                super();
                id = "alt.network";
                addressHeader = 48;
                p2shHeader = 5;
                acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
            }
        }
        AltNetwork altNetwork = new AltNetwork();
        // Add new network context
        Networks.register(altNetwork);
        // Check if can parse address
        NetworkParameters params = Address.getParametersFromAddress("LLxSnHLN2CYyzB5eWTR9K9rS9uWtbTQFb6");
        assertEquals(altNetwork.getId(), params.getId());
        // Check if main network works as before
        params = Address.getParametersFromAddress("DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm");
        assertEquals(MainNetParams.get().getId(), params.getId());
        // Unregister network
        Networks.unregister(altNetwork);
        try {
            Address.getParametersFromAddress("LLxSnHLN2CYyzB5eWTR9K9rS9uWtbTQFb6");
            fail();
        } catch (AddressFormatException e) { }
    }
    
    @Test
    public void p2shAddress() throws Exception {
        // Test that we can construct P2SH addresses
        Address mainNetP2SHAddress = Address.fromBase58(MainNetParams.get(), "6XhmA6QewRiLVaakw19RHS5Y5TMSGzFte1");
        assertEquals(mainNetP2SHAddress.version, MainNetParams.get().p2shHeader);
        assertTrue(mainNetP2SHAddress.isP2SHAddress());
        Address testNetP2SHAddress = Address.fromBase58(TestNet3Params.get(), "8pPApY8B9mrA1EsaQxfjxGV7doPAKPKgWj");
        assertEquals(testNetP2SHAddress.version, TestNet3Params.get().p2shHeader);
        assertTrue(testNetP2SHAddress.isP2SHAddress());

        // Test that we can determine what network a P2SH address belongs to
        NetworkParameters mainNetParams = Address.getParametersFromAddress("6XhmA6QewRiLVaakw19RHS5Y5TMSGzFte1");
        assertEquals(MainNetParams.get().getId(), mainNetParams.getId());
        NetworkParameters testNetParams = Address.getParametersFromAddress("8pPApY8B9mrA1EsaQxfjxGV7doPAKPKgWj");
        assertEquals(TestNet3Params.get().getId(), testNetParams.getId());

        // Test that we can convert them from hashes
        byte[] hex = HEX.decode("bdddbfdf7f8cc7ab7d42361324d036004204cf83");
        Address a = Address.fromP2SHHash(mainParams, hex);
        assertEquals("6XhmA6QewRiLVaakw19RHS5Y5TMSGzFte1", a.toString());
        Address b = Address.fromP2SHHash(testParams, HEX.decode("6d425404bc3a9e8ed54fad0a0b4e42a84df49bc3"));
        assertEquals("8pPApY8B9mrA1EsaQxfjxGV7doPAKPKgWj", b.toString());
        Address c = Address.fromP2SHScript(mainParams, ScriptBuilder.createP2SHOutputScript(hex));
        assertEquals("6XhmA6QewRiLVaakw19RHS5Y5TMSGzFte1", c.toString());
    }

    @Test
    public void p2shAddressCreationFromKeys() throws Exception {
        // import some keys from this example: https://gist.github.com/gavinandresen/3966071
        ECKey key1 = DumpedPrivateKey.fromBase58(mainParams, "YQsMtn5eGmNsn8GpUH7r8JRsPW2unPjNZ8AGAjX7uXGUzuN2SzCQ").getKey();
        key1 = ECKey.fromPrivate(key1.getPrivKeyBytes());
        ECKey key2 = DumpedPrivateKey.fromBase58(mainParams, "YQsMtn5eGmNsn8GpUH7r8JRsPW2unPjNZ8AGAjX7uXGUzuN2SzCQ").getKey();
        key2 = ECKey.fromPrivate(key2.getPrivKeyBytes());
        ECKey key3 = DumpedPrivateKey.fromBase58(mainParams, "YQsMtn5eGmNsn8GpUH7r8JRsPW2unPjNZ8AGAjX7uXGUzuN2SzCQ").getKey();
        key3 = ECKey.fromPrivate(key3.getPrivKeyBytes());

        List<ECKey> keys = Arrays.asList(key1, key2, key3);
        Script p2shScript = ScriptBuilder.createP2SHOutputScript(2, keys);
        Address address = Address.fromP2SHScript(mainParams, p2shScript);
        assertEquals("6EbNF32C3ZRTkuRAA71rPaujLRouXq59HF", address.toString());
    }

    @Test
    public void cloning() throws Exception {
        Address a = new Address(testParams, HEX.decode("4f9b4c8a7412b41eb7f2b471edcf83b8fd22d41c"));
        Address b = a.clone();

        assertEquals(a, b);
        assertNotSame(a, b);
    }

    @Test
    public void roundtripBase58() throws Exception {
        String base58 = "DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm";
        assertEquals(base58, Address.fromBase58(null, base58).toBase58());
    }

    @Test
    public void comparisonCloneEqualTo() throws Exception {
        Address a = Address.fromBase58(mainParams, "DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm");
        Address b = a.clone();

        int result = a.compareTo(b);
        assertEquals(0, result);
    }

    @Test
    public void comparisonEqualTo() throws Exception {
        Address a = Address.fromBase58(mainParams, "DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm");
        Address b = a.clone();

        int result = a.compareTo(b);
        assertEquals(0, result);
    }

    @Test
    public void comparisonLessThan() throws Exception {
        Address a = Address.fromBase58(mainParams, "DMdKx4UdSdsK85nUqVmmrH6JgTLPk3xDtz");
        Address b = Address.fromBase58(mainParams, "DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm");

        int result = a.compareTo(b);
        assertTrue(result < 0);
    }

    @Test
    public void comparisonGreaterThan() throws Exception {
        Address a = Address.fromBase58(mainParams, "DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm");
        Address b = Address.fromBase58(mainParams, "DMdKx4UdSdsK85nUqVmmrH6JgTLPk3xDtz");

        int result = a.compareTo(b);
        assertTrue(result > 0);
    }

    @Test
    public void comparisonBytesVsString() throws Exception {
        // TODO: To properly test this we need a much larger data set
        Address a = Address.fromBase58(mainParams, "DMdKx4UdSdsK85nUqVmmrH6JgTLPk3xDtz");
        Address b = Address.fromBase58(mainParams, "DQtAfE2omdMov3s8kviAASj3PBqz2f5Wtm");

        int resultBytes = a.compareTo(b);
        int resultsString = a.toString().compareTo(b.toString());
        assertTrue( resultBytes < 0 );
        assertTrue( resultsString < 0 );
    }
}
