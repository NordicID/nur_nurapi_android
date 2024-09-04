package com.nordicid.nurapi;

/**
 * NurApiAndroid library properties
 */
public final class NurApiAndroid {
    /** Get NurApiAndroid library version string */
    static public String getVersion() {
        return BuildConfig.NURVERSION;
    }
    /** Get NurApiAndroid library version code */
    static public int getVersionCode() {
        return BuildConfig.NURVERSIONCODE;
    }
}
