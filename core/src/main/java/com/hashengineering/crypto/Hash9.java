/*
 * Copyright 2014 Hash Engineering Solutions.
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
package com.hashengineering.crypto;

import fr.cryptohash.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.hashengineering.utils.ByteArrayUtils.trim256;

/**
 * Created by Hash Engineering Solutions 2/18/14.
 *
 * This class implements the Quark Proof Of Work hashing function,
 * which is also used as the block hash
 *
 */

public class Hash9 extends HashFunction {
    private static final Logger log = LoggerFactory.getLogger(Hash9.class);

    static BLAKE512 blake512;
    static BMW512 bmw512;
    static Groestl512 groestl512;
    static Skein512 skein512;
    static JH512 jh512;
    static Keccak512 keccak512;

    static {
        loadNativeLibrary("hash9");

        //if(native_library_loaded == false)
        {
            blake512 = new BLAKE512();
            bmw512 = new BMW512();
            groestl512 = new Groestl512();
            skein512 = new Skein512();
            jh512 = new JH512();
            keccak512 = new Keccak512();
        }
    }

    public static byte[] digest(byte[] input, int offset, int length)
    {
        try {
            /*byte [] result = null;
            long start = System.currentTimeMillis();
            result = hash9(input, 0, input.length);
            timeJ2 += System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            result = hash9_native(input, 0, input.length);
            timeN2 += System.currentTimeMillis() - start;
            if(count2 == 100)
            {
                log.info("[stats] Quark Java New: "+ timeJ2);

                log.info("[stats] Quark Native: "+ timeN2);
                count2 = 0;
                timeJ2 = timeN2 = 0;
            }
            count2++;
            return result;*/
            return native_library_loaded ? hash9_native(input, offset, length) : hash9(input, offset, length);
        }
        catch(Exception e) {
            return null;
        }
    }

    static long timeJ = 0;
    static long timeN = 0;
    static long timeNO = 0;
    static int count = 0;
    static long timeJ2 = 0;
    static long timeN2 = 0;
    static int count2 = 0;

    public static byte[] digest(byte[] input) {

        try {
            /*
            byte [] result = null;
            long start = System.currentTimeMillis();
            result = hash9(input, 0, input.length);
            timeJ += System.currentTimeMillis() - start;
            start = System.currentTimeMillis();
            result = hash9_native_old(input);
            timeNO += System.currentTimeMillis() - start;
            start = System.currentTimeMillis();
            result = hash9_native(input, 0, input.length);
            timeN += System.currentTimeMillis() - start;
            if(count == 100)
            {
                log.info("[stats] Quark Java New: "+ timeJ);
                log.info("[stats] Quark Native: "+ timeN);
                log.info("[stats] Quark Native Old: "+ timeNO);
                count = 0;
                timeJ = timeN = timeNO = 0;
            }
            count++;
            return result;*/
            return native_library_loaded ? hash9_native(input, 0, input.length) : hash9(input, 0, input.length);
        } catch (Exception e) {
            return null;
        }
    }

    static native byte [] hash9_native(byte [] input, int offset, int length);
    static native byte [] hash9_native_old(byte [] input);

    static byte [] hash9(byte header[])
    {
        return hash9(header, 0, header.length);
    }
    /*
      Java implimentation of the Quark Hashing Algorithm.
      It consists of 6 of the SHA-3 candidates.  There are 9 rounds.
      Every third round is a "random" hash based on the
      0x8 bit of the first byte of the previous hash.

      The hashes are all calculated to result with 512 bits (64 bytes).
      The result returned is the first 32 bytes.

      Order:
        blake
        bmw
        (groestl or skein)
        groestl
        jh
        (blake or bmw)
        keccak
        skein
        (keccak or jh)
     */
    static byte [] hash9(byte [] header, int offset, int length)
    {
        byte [][] hash = new byte[9][];


        blake512.update(header, offset, length);

        hash[0] = blake512.digest();

        hash[1] = bmw512.digest(hash[0]);

        if((hash[1][0] & 8) != 0)
        {
            hash[2] = groestl512.digest(hash[1]);
        }
        else
        {
            hash[2] = skein512.digest(hash[1]);
        }

        hash[3] = groestl512.digest(hash[2]);

        hash[4] = jh512.digest(hash[3]);

        if((hash[4][0] & 8) != 0)
        {
            hash[5] = blake512.digest(hash[4]);
        }
        else
        {
            hash[5] = bmw512.digest(hash[4]);
        }

        hash[6] = keccak512.digest(hash[5]);

        hash[7] = skein512.digest(hash[6]);

        if((hash[7][0] & 8) != 0)
        {
            hash[8] = keccak512.digest(hash[7]);
        }
        else
        {
            hash[8] = jh512.digest(hash[7]);
        }

        return trim256(hash[8]);
    }



}
