/* 
  Copyright 2016- Nordic ID
  NORDIC ID SOFTWARE DISCLAIMER

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
package com.nordicid.nurapi;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
//import android.util.Log;
import android.os.Handler;
import android.util.Log;

public class NurApiUsbAutoConnect implements NurApiAutoConnectTransport {
    static final String TAG = "NurApiUsbAutoConnect";

    /**
     * USB vendor ID for Nordic devices
     */
    private static final int NUR_VENDOR_ID_1 = 3589;

    /**
     * USB vendor ID for Nordic devices
     */
    private static final int NUR_VENDOR_ID_2 = 1254;

    /**
     * USB product ID for Nordic RFID devices
     */
    private static final int NUR_PRODUCT_ID_1 = 274;

    /**
     * USB product ID for Nordic RFID devices
     */
    private static final int NUR_PRODUCT_ID_2 = 2321;

    private NurApi mApi = null;
    private Context mContext = null;
    private UsbManager mUsbManager = null;
    private UsbDevice mUsbDevice = null;
    private static final String ACTION_USB_PERMISSION = "com.nordicid.nurapi.USB_PERMISSION";
    private boolean mReceiverRegistered = false;
    private PendingIntent mPermissionIntent;
    private boolean mRequestingPermission = false;

    private String mReceiverRegisteredAction = "";

    private boolean mEnabled = false;

    void registerReceiver(String action) {
        if (action.length() == 0) {
            if (mReceiverRegistered) {
                mReceiverRegistered = false;
                mReceiverRegisteredAction = "";
                mContext.unregisterReceiver(mUsbReceiver);
                Log.d(TAG, "registerReceiver unregistered");
            } else {
                Log.d(TAG, "registerReceiver ALREADY unregistered");
            }
        } else {

            if (mReceiverRegisteredAction.equals(action)) {
                Log.d(TAG, "registerReceiver " + action + " ALREADY registered");
                return;
            }

            if (mReceiverRegistered)
                mContext.unregisterReceiver(mUsbReceiver);

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_USB_PERMISSION);
            intentFilter.addAction(action);
            Log.d(TAG, "registerReceiver " + action + " registered");
            mContext.registerReceiver(mUsbReceiver, intentFilter);
            mReceiverRegistered = true;
            mReceiverRegisteredAction = action;
        }
    }

    public NurApiUsbAutoConnect(Context c, NurApi na) {
        this.mContext = c;
        this.mApi = na;
        this.mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                synchronized (this) {
                    Log.d(TAG, "ACTION_USB_DEVICE_ATTACHED " + intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
                    UsbDevice intentUsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    // If the usb device is not a Nordic one do nothing
                    if (!checkIsNurDevice(intentUsbDevice)){
                        return;
                    }
                    mUsbDevice = intentUsbDevice;

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (mUsbDevice != null) {
                            NurApiUsbAutoConnect.this.setAddress(getAddress());
                        }
                    } else {
                        Log.d(TAG, "ACTION_USB_DEVICE_ATTACHED permission denied for device " + mUsbDevice);
                        Log.d(TAG, "FORCE CONNECTION to" + getAddress());
                        NurApiUsbAutoConnect.this.setAddress(getAddress());
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice intentUsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                // Check the disconnected device is the one that we have connected and no other USB device
                // We check that the vendor ID matches with one of the Nur IDs and the product ID also matches
                if (checkIsNurDevice(intentUsbDevice)) {
                    Log.d(TAG, "ACTION_USB_DEVICE_DETACHED");
                    disconnect();
                }

            } else if (ACTION_USB_PERMISSION.equals(action)) {
                mRequestingPermission = false;
                Log.d(TAG, "ACTION_USB_PERMISSION " + intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    NurApiUsbAutoConnect.this.setAddress(getAddress());
            }
        }
    };

    /**
     * Checks if the given usbDevice is a valid NUR device
     * @param usbDevice Usb device to check if is NUR or not
     * @return True if the usb device is a Nur device, false otherwise
     */
    private static boolean checkIsNurDevice(UsbDevice usbDevice) {
        return usbDevice != null && (usbDevice.getVendorId() == NUR_VENDOR_ID_1 || usbDevice.getVendorId() == NUR_VENDOR_ID_2)
                && (usbDevice.getProductId() == NUR_PRODUCT_ID_1 || usbDevice.getProductId() == NUR_PRODUCT_ID_2);
    }

    private void connect() {
        if (mUsbDevice != null && mUsbManager.hasPermission(mUsbDevice)) {
            try {
                mApi.setTransport(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            NurApiUsbTransport tr = new NurApiUsbTransport(mUsbManager, mUsbDevice);
            try {
                mApi.setTransport(tr);
                mApi.connect();

                registerReceiver(UsbManager.ACTION_USB_DEVICE_DETACHED);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else if (mUsbDevice != null && !mUsbManager.hasPermission(mUsbDevice)) {
            mRequestingPermission = true;
            mUsbManager.requestPermission(mUsbDevice, mPermissionIntent);
        }
    }

    private void disconnect() {
        if (mApi.isConnected()) {
            try {
                registerReceiver(UsbManager.ACTION_USB_DEVICE_ATTACHED);
                mApi.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onResume() {
        if (mApi.isConnected())
            return;

        if (!mRequestingPermission)
            this.setAddress(getAddress());
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void setAddress(String addr) {
        if (addr == null)
            addr = "";

        Log.d(TAG, "setAddress " + addr);

        mEnabled = addr.length() > 0;

        this.mUsbDevice = null;
        for (UsbDevice device : mUsbManager.getDeviceList().values()) {
            if (device.getVendorId() == 1254 || device.getVendorId() == 3589) {
                this.mUsbDevice = device;
                break;
            }
        }

        if (mUsbDevice == null) {
            registerReceiver(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        } else {
            registerReceiver(UsbManager.ACTION_USB_DEVICE_DETACHED);
        }

        if (mUsbDevice != null) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    connect();
                }
            }, 200);
        }
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onStop() {
        if (mApi.isConnected()) {
            try {
                mApi.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        registerReceiver("");
    }

    @Override
    public void dispose() {
        onStop();
    }

    @Override
    public String getType() {
        return "USB";
    }

    @Override
    public String getAddress() {
        return mEnabled ? "USB" : "";
    }

    @Override
    public String getDetails() {
        if (mApi.isConnected())
            return "Connected to USB";
        else if (!mEnabled)
            return "Disabled";

        return "Disconnected from USB";
    }
}
