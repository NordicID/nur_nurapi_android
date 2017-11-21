/*
  Copyright 2016- Nordic ID
  NORDIC ID DEMO SOFTWARE DISCLAIMER

  You are about to use Nordic ID Demo Software ("Software").
  It is explicitly stated that Nordic ID does not give any kind of warranties,
  expressed or implied, for this Software. Software is provided "as is" and with
  all faults. Under no circumstances is Nordic ID liable for any direct, special,
  incidental or indirect damages or for any economic consequential damages to you
  or to any third party.

  The use of this software indicates your complete and unconditional understanding
  of the terms of this disclaimer.

  IF YOU DO NOT AGREE OF THE TERMS OF THIS DISCLAIMER, DO NOT USE THE SOFTWARE.
*/

package com.nordicid.nuraccessory;

import android.util.Log;

/** Decode accessory device version info */
public class NurAccessoryVersionInfo {
    static String TAG = "NurAccessoryVersionInfo";

    private String mBootloaderVersion;
    private String mFullApplicationVersion;
    private String mApplicationVersion;

    /** Decode accessory device version info.
     * @param version Version string from device.
     */
    public NurAccessoryVersionInfo(String version)
    {
        /* Format : <Applicationversion><space><details>;<bootloaderversion> */
        Log.i(TAG,"version string: " + version);
        String [] versions = version.split(";");
        mBootloaderVersion = (versions.length > 1) ? versions[1] : "1";
        Log.i(TAG, "bootloader: " + mBootloaderVersion);
        mFullApplicationVersion = versions[0];
        Log.i(TAG, "fullapp: " + mFullApplicationVersion);
        mApplicationVersion = versions[0].split(" ")[0].replaceAll("[^\\d.]", "");
        Log.i(TAG, "app: " + mApplicationVersion);
    }

    /** Get accessory device application version number
     * @return  Version number as string
     */
    public String getApplicationVersion() { return mApplicationVersion; }

    /** Get accessory device application full version info
     * @return  Version info as string
     */
    public String getFullApplicationVersion() {return mFullApplicationVersion; }

    /** Get accessory device bootloader version number
     * @return  Version number as string
     */
    public String getBootloaderVersion() {return mBootloaderVersion; }
}
