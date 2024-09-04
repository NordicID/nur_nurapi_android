package com.nordicid.nurapi;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Mikko on 13.6.2017.
 */

public class BleScanner {

    public static final String TAG = "BleScanner";

    public interface BleScannerListener {
        void onBleDeviceFound(final BluetoothDevice device, final String name, final int rssi);
    }
    public interface BleScannerListenerEx {
        void onBleDeviceFound(final ScanResult scanResult);
    }

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScannerCompat mScanner;
    private final Handler mHandler;

    private boolean mScanning = false;

    private Context mOwner;

    private final List<BleScannerListener> mListeners = new ArrayList<>();
    private final List<BleScannerListenerEx> mListenersEx = new ArrayList<>();

    private int listenerCount() {
        return (mListeners.size() + mListenersEx.size());
    }

    private BleScanner(Context context) {
        mOwner = context;
        mHandler = new Handler();
        Log.i(TAG, "BleScanner() mOwner " + mOwner);
    }

    static BleScanner gInstance = null;
    static public void init(Context context) {
        if (context == null) {
            Log.e(TAG, "init() Context is NULL");
        }

        if (gInstance == null)
            gInstance = new BleScanner(context);
        else
            gInstance.mOwner = context;
    }

    static public BleScanner getInstance() {
        return gInstance;
    }

    public void registerScanListener(BleScannerListener listener){
        if (!mListeners.contains(listener))
            mListeners.add(listener);

        scanDevices();
    }

    public void unregisterListener(BleScannerListener listener) {
        mListeners.remove(listener);
    }

    public void registerScanListenerEx(BleScannerListenerEx listener){
        if (!mListenersEx.contains(listener))
            mListenersEx.add(listener);

        scanDevices();
    }

    public void unregisterListenerEx(BleScannerListenerEx listener) {
        mListenersEx.remove(listener);
    }

    boolean isLocationServicesEnabled() {
        int locationMode = Settings.Secure.LOCATION_MODE_OFF;

        try {
            locationMode = Settings.Secure.getInt(mOwner.getContentResolver(), Settings.Secure.LOCATION_MODE);
            Log.d(TAG, "locationMode = " + locationMode);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        return (locationMode != Settings.Secure.LOCATION_MODE_OFF);
    }

    private void onScanStarted() {
        Log.i(TAG,"onScanStarted() mScanning " + mScanning);

        if (mScanning)
            return;

        // If requesting only BT devices, check for BT on
        if (!mBluetoothAdapter.isEnabled())
        {
            Log.w(TAG, "BT not ON");
            return;
        }

        mScanning = true;

        // Stops scanning after a pre-defined scan period.
        int mScanPeriod = 20000;
        mHandler.postDelayed(this::onScanFinished, mScanPeriod);

        ScanSettings settings = new ScanSettings.Builder()
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0).build();
        mScanner.startScan(null, settings, mScanCallback);
    }

    static public Set<BluetoothDevice> getPairedDevices() {
        BluetoothManager bluetoothManager = (BluetoothManager)getInstance().mOwner.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return new HashSet<>();
        }
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        if (adapter == null) {
            return new HashSet<>();
        }
        return adapter.getBondedDevices();
    }

    static public boolean isBleDevice(BluetoothDevice device) {
        // Log.w(TAG, "NOT BLE device; " + device.getAddress() + "; " + device.getType());
        return device.getType() == BluetoothDevice.DEVICE_TYPE_LE || device.getType() == BluetoothDevice.DEVICE_TYPE_UNKNOWN;
    }

    private void onDeviceFound(final ScanResult result) {

        BluetoothDevice device = result.getDevice();
        String name = result.getScanRecord().getDeviceName();
        int rssi = result.getRssi();

        // Log.i(TAG, "onDeviceFound() " + device.getAddress() + "; name " + name + "; rssi " + rssi);

        if (listenerCount() == 0) {
            //Log.i(TAG, "onDeviceFound() No listeners");
            return;
        }

        if (name == null || !isBleDevice(device)) {
            return;
        }

        // Log.i(TAG, "onDeviceFound() " + device.getAddress() + "; name " + name + "; rssi " + rssi);

        List<BleScannerListener> listeners = new ArrayList<>(mListeners);
        for (BleScannerListener l : listeners) {
            l.onBleDeviceFound(device, name, rssi);
        }

        List<BleScannerListenerEx> listenersEx = new ArrayList<>(mListenersEx);
        for (BleScannerListenerEx lex : listenersEx) {
            lex.onBleDeviceFound(result);
        }
    }

    private void onScanFinished() {
        Log.i(TAG, "onScanFinished() mScanning " + mScanning + "; mListeners " + mListeners.size());

        if (mScanning) {
            mScanning = false;
            try {
                mScanner.stopScan(mScanCallback);
            }
            catch (Exception e) {
                Log.e(TAG,"onScanFinished:" + e.getMessage());
            }
        }

        if (listenerCount() > 0) {
            Log.i(TAG, "onScanFinished() restart scan");
            mHandler.postDelayed(this::onScanStarted, 500);
        }
    }

    private void showToast(final int strRes)
    {
        new Handler(mOwner.getMainLooper()).post(() -> Toast.makeText(mOwner, strRes, Toast.LENGTH_SHORT).show());
    }

    private void scanDevices() {

        Log.i(TAG, "scanDevices() mOwner " + mOwner);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (mOwner.getPackageManager() != null && !mOwner.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "BT not supported; missing feature: " + PackageManager.FEATURE_BLUETOOTH_LE);
            // showToast(R.string.ble_not_supported);
            return;
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        BluetoothManager bluetoothManager =
                (BluetoothManager) mOwner.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.w(TAG, "BT not supported; BT service not available");
            showToast(R.string.ble_not_supported);
            return;
        }

        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BT not supported; BT adapter not available");
            // showToast(R.string.ble_not_supported);
            return;
        }

        // If requesting only BT devices, check for BT on
        if (!mBluetoothAdapter.isEnabled())
        {
            Log.w(TAG, "BT not ON");
            showToast(R.string.text_bt_not_on);
            return;
        }

        Configuration config = mOwner.getResources().getConfiguration();
        boolean isWatch = (config.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_WATCH;
        if (!isWatch) {
            // Location ON is required for android M or newer (location not required for watch..
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationServicesEnabled()) {
                Log.w(TAG, "Location not ON; BT search not available");
                showToast(R.string.text_location_not_on);
                // return; // Do not return, it might still work.. some xiaomi miui8 phones for example
            }
        }

        if (mScanner == null)
            mScanner = BluetoothLeScannerCompat.getScanner();

        onScanStarted();
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            if (!mScanning) {
                Log.d(TAG, "onScanResult() Got event while NOT scanning");
                return;
            }
            onDeviceFound(result);
        }

        @Override
        public void onBatchScanResults(final List<ScanResult> results) {
            Log.i(TAG, "onBatchScanResults() Found " + results.size() + " BLE devices");
            if (!mScanning) {
                Log.d(TAG, "onBatchScanResults() Got event while NOT scanning");
                return;
            }

            for (final ScanResult result : results)
            {
                onDeviceFound(result);
                if (!mScanning) {
                    break;
                }
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            Log.e(TAG, "onScanFailed " + errorCode);
        }
    };

}
