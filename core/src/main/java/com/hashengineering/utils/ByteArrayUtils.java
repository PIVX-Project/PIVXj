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
package com.hashengineering.utils;

/**
 * Created by HashEngineering on 7/2/14.
 */
public class ByteArrayUtils {

    public static byte[] trim256(byte [] bytes)
    {
        byte [] result = new byte[32];
        for (int i = 0; i < 32; i++){
            result[i] = bytes[i];
        }
        return result;
    }
}
