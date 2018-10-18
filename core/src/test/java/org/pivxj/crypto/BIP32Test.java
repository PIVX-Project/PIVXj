/*
 * Copyright 2013 Matija Mazi.
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

package org.pivxj.crypto;

import org.pivxj.core.Base58;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.pivxj.core.NetworkParameters;
import org.pivxj.params.MainNetParams;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.pivxj.core.Utils.HEX;
import static org.junit.Assert.assertEquals;

/**
 * A test with test vectors as per BIP 32 spec: https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki#Test_Vectors
 */
public class BIP32Test {
    private static final Logger log = LoggerFactory.getLogger(BIP32Test.class);

    HDWTestVector[] tvs = {
            new HDWTestVector(
                    "000102030405060708090a0b0c0d0e0f",
                    "TDt9EWvD5T5T44hAac1bLFvmH5fhMx2T6zgUKCmsDWZskySV2A1VDhHjPie5CBJ8nZs7TKXmo5sy2eHhFCSwa4gxqpKBK9NEZaua11yXwhAQ2wp",
                    "ToEA6epvY6iUs9r4R5mvZQyK8EtsCsK1xZ6hEXrGFDKjQpAT8V28vb7LKuKhFM7zoxQ7CzWX7dE7mJNiqBw8t8PU91Bu8oAqabvg9xGoBzHZLxp",
                    Arrays.asList(
                            new HDWTestVector.DerivedTestCase(
                                    "Test1 m/0H",
                                    new ChildNumber[]{new ChildNumber(0, true)},
                                    "TDt9EZBd5ANv9Kp9jD9JY1oQA7mogmaoV5AUzpCmBbwzjAdmPSJfdvppWrzUySeUCHF5ANRUAPCqR18ZxxUfDey9dtPhP23NoYcTi2cPNoMUB6w",
                                    "ToEA6h6LXp1wxQy3ZgudmAqx1GzyXgsNLdahv9HADJhrP1MjVmKKLpeRT3g72cghZGJeDCo5yrmYVwSooauGqA7rCtedLRy6VuptftQMVd9h3VM"
                            ),
                            new HDWTestVector.DerivedTestCase(
                                    "Test1 m/0H/1",
                                    new ChildNumber[]{new ChildNumber(0, true), new ChildNumber(1, false)},
                                    "TDt9EbMkGwvoY2hDZfBkGGhGkrfTk9ZuV6tmw8sbmsiofoRycyooMjF8URtiPVqXoD27cTB9yCoGVi5z6tztLzm8R3t8to16sJPKNpvkun8rYcG",
                                    "ToEA6jGTjbZqM7r7Q8x5VRjpc1tdb4rULfJzrTwzoaUfKe9wjJpT4d4jQcaLSh9GyD1zAvR8mHDecXwHPte6JVHjtYUZddowyYqAJcw8ncLBfDe"
                            ),
                            new HDWTestVector.DerivedTestCase(
                                    "Test1 m/0H/1/2H",
                                    new ChildNumber[]{new ChildNumber(0, true), new ChildNumber(1, false), new ChildNumber(2, true)},
                                    "TDt9Edy2KUkfEuYdRpyqWs6e7rhbyJSRkeUmGg7H2D1xqfoR2pzvAV5ZNsSdwY49mSXc7vCYZfkBv7utL6jdw1gKY9KoFQZf2EdFz6fyYM6oD1B",
                                    "ToEA6msjn8Ph3zhXGJkAk29By1vmpDizcCtzC1Bg3umpVWXP9A1ZsNuAK48FziL7R5TJXSWsNfCFzUWFrDnP1aqGwzdQDXn64Vtr2JrGstysVAH"
                            ),
                            new HDWTestVector.DerivedTestCase(
                                    "Test1 m/0H/1/2H/2",
                                    new ChildNumber[]{new ChildNumber(0, true), new ChildNumber(1, false), new ChildNumber(2, true), new ChildNumber(2, false)},
                                    "TDt9EgCR9uscRPDcWJTZD8Jj1Mrv47jAPEnxi9zaHAYSA7N2PvdHEFmvaSuZcrTcE6w3BiLFBaykpwN3oHe68SDfjXuqeeekaKSy95993rNMzUu",
                                    "ToEA6p78cZWeEUNWLnDtSHMGrX65u31jEoDBdV4yJsJHox5zWFdvw9bXWdbBg3KVpsc2zke3WfgD3DQNQe8P6tGUfb6f8KLGtgng2zm82T6KJan"
                            ),
                            new HDWTestVector.DerivedTestCase(
                                    "Test1 m/0H/1/2H/2/1000000000",
                                    new ChildNumber[]{new ChildNumber(0, true), new ChildNumber(1, false), new ChildNumber(2, true), new ChildNumber(2, false), new ChildNumber(1000000000, false)},
                                    "TDt9EhvBdbUrYWbp2eF1KfsTK4rurMJUCjbzWQP8XSvaNBkX742fkg2aqZ4dFiEp9247o7jVxwxhfyuAoTeTfWEPrVZNz9QyhECgRQhVo1R47Ej",
                                    "ToEA6qpu6F7tMbkhs81LYpv1AE65hGb34J2DRjTXZ9gS22UVDP3KTZrBmjkFJsESg8gkUSvb21c1aR6y14XQCjmKWrFmzrkMhANJqxP3GenZSJd"
                            )
                    )
            ),
            new HDWTestVector(
                    "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542",
                    "TDt9EWvD5T5T44hAaDWvSR13jHPTc9AsncwHznZbWTJhjMZMK92gPr4fpcofYF5By5RChNHCYNLRJkKtLjB3cV5fTBdwmZSUhvpJaGbmJc39xAS",
                    "ToEA6epvY6iUs9r4QhHFfa3baScdT4TSeBMWv7dzYA4ZPCHKRU3L6jtGkoVHbTCyXCm57YkK2HtHANsyYyfN1KJdGvBqzjNJR6RLUKwuTyjmv5e",
                    Arrays.asList(
                            new HDWTestVector.DerivedTestCase(
                                    "Test2 m/0",
                                    new ChildNumber[]{new ChildNumber(0, false)},
                                    "TDt9EaBxMAmQY5XF6V7DaCNvo5nyy6v8VbRaYxJxgGL9omRK17Uey7QjyTNv7kLzc9G372X2SNk9juk1hEosAmaiPQoLmjrY6Qz3hgWKfy7BcRy",
                                    "ToEA6i6fopQSMAg8vxsYoMRUeF29p2ChM9qoUHPMhy61Tc9H7SVJg1ELue4YAvAsKHmWKsEUtWt2ZTiwi963tU13cWr8Gn6yKSZibp4ZuCFDqQD"
                            ),
                            new HDWTestVector.DerivedTestCase(
                                    "Test2 m/0/2147483647H",
                                    new ChildNumber[]{new ChildNumber(0, false), new ChildNumber(2147483647, true)},
                                    "TDt9EbM1bmnvuyhMGMKPzeNaGRUYMr9335bGe7PVbfEvTWBhTQi7azq4BXAWj6pyLPzX1FqvDPwnnvF88UYdu9BqxiEuCU5srviJVTqrSM8WfhM",
                                    "ToEA6jFj4RRxj4rF6q5jDoR87ahiCmRbte1VZSTtdMzn7LufZjimHtef7hr8nJQz52Nc7PA2YMjdrg9YNoxPfNVALfJkorj2gApjUCLAjzRSrz1"
                            ),
                            new HDWTestVector.DerivedTestCase(
                                    "Test2 m/0/2147483647H/1",
                                    new ChildNumber[]{new ChildNumber(0, false), new ChildNumber(2147483647, true), new ChildNumber(1, false)},
                                    "TDt9Ee9z1nms5czYMxzY4JX9ENQmhP1Y9GWXxYtMSiuFmPY98EaGtWMm5RYtZu2bN7CDKDCcxh2BCTvLTBKnx2iDyC4CLEcgi2ggyGGauzni6xJ",
                                    "ToEA6n4hUSQtti9SCSksHTZh5XdwYJJ6zpvkssxkURf7REG7EZavbQBN1cEWd6c7ru4thZcaNzpsVoTNTcDQaGv7bzmfQ8vstiFwPy17zPEAhLs"
                            ),
                            new HDWTestVector.DerivedTestCase(
                                    "Test2 m/0/2147483647H/1/2147483646H",
                                    new ChildNumber[]{new ChildNumber(0, false), new ChildNumber(2147483647, true), new ChildNumber(1, false), new ChildNumber(2147483646, true)},
                                    "TDt9EfL1vkiDh1xFevE8nnLgbt6xc7UuCF92zFcEySbfc9oPaauCzBHmbwuzCbkyzXaVrnVKGWoAaqVvGJRATycif2jNAB9zrEiKJrH6yj3CXGC",
                                    "ToEA6oEjPQMFW779VPzU1wPET3L8T2mU3oZFuage19MXFzXMguurh57NY8bcFkjd4GbLf1GDBVtaYQnE7FNoGA2dKMU7g3FNT1MMGC5hXLLMMwA"
                            ),
                            new HDWTestVector.DerivedTestCase(
                                    "Test2 m/0/2147483647H/1/2147483646H/2",
                                    new ChildNumber[]{new ChildNumber(0, false), new ChildNumber(2147483647, true), new ChildNumber(1, false), new ChildNumber(2147483646, true), new ChildNumber(2, false)},
                                    "TDt9Egh3tBvjDCGVQBR2WrUNadUw1B21Sa47eRE8PDxAEweyenEn9pDXnWrzH1Rz9YfZu6M3ZGRz2wLcJrJgCHywi1U5y2fVuer31Dcf4Mqgck3",
                                    "ToEA6pbmLqZm2HRPEfBMk1WvRni6r6JaJ8ULZkJXQvi1tnNwm7FRri38ihYcL9ouBqx3B4USTRPzS7oDbgSJ6FiXdcKhKD5TPNe5wVGtC3N27Jk"
                            )
                    )
            )
    };

    @Test
    public void testVector1() throws Exception {
        testVector(0);
    }

    @Test
    public void testVector2() throws Exception {
        testVector(1);
    }

    private void testVector(int testCase) {
        log.info("=======  Test vector {}", testCase);
        HDWTestVector tv = tvs[testCase];
        NetworkParameters params = MainNetParams.get();
        DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey(HEX.decode(tv.seed));
        assertEquals(testEncode(tv.priv), testEncode(masterPrivateKey.serializePrivB58(params)));
        assertEquals(testEncode(tv.pub), testEncode(masterPrivateKey.serializePubB58(params)));
        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey);
        for (int i = 0; i < tv.derived.size(); i++) {
            HDWTestVector.DerivedTestCase tc = tv.derived.get(i);
            log.info("{}", tc.name);
            assertEquals(tc.name, String.format(Locale.US, "Test%d %s", testCase + 1, tc.getPathDescription()));
            int depth = tc.path.length - 1;
            DeterministicKey ehkey = dh.deriveChild(Arrays.asList(tc.path).subList(0, depth), false, true, tc.path[depth]);
            assertEquals(testEncode(tc.priv), testEncode(ehkey.serializePrivB58(params)));
            assertEquals(testEncode(tc.pub), testEncode(ehkey.serializePubB58(params)));
        }
    }

    private String testEncode(String what) {
        return HEX.encode(Base58.decodeChecked(what));
    }

    static class HDWTestVector {
        final String seed;
        final String priv;
        final String pub;
        final List<DerivedTestCase> derived;

        HDWTestVector(String seed, String priv, String pub, List<DerivedTestCase> derived) {
            this.seed = seed;
            this.priv = priv;
            this.pub = pub;
            this.derived = derived;
        }

        static class DerivedTestCase {
            final String name;
            final ChildNumber[] path;
            final String pub;
            final String priv;

            DerivedTestCase(String name, ChildNumber[] path, String priv, String pub) {
                this.name = name;
                this.path = path;
                this.pub = pub;
                this.priv = priv;
            }

            String getPathDescription() {
                return "m/" + Joiner.on("/").join(Iterables.transform(Arrays.asList(path), Functions.toStringFunction()));
            }
        }
    }
}
