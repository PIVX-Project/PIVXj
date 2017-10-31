package com.hashengineering.crypto;

/**
 * Created by Eric on 7/2/14.
 */
public class HashFunction {
    protected static boolean native_library_loaded = false;

    static void loadNativeLibrary(String name)
    {
        try {
            System.loadLibrary(name);
            native_library_loaded = true;
        }
        catch(UnsatisfiedLinkError e)
        {
            native_library_loaded = false;
        }
        catch(Exception e)
        {
            native_library_loaded = false;
        }
    }
}
