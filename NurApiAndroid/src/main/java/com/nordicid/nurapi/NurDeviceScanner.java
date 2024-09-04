package com.nordicid.nurapi;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.nordicid.nurapi.BleScanner.BleScannerListener;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class NurDeviceScanner implements BleScannerListener {

    public static final String TAG = "NurDeviceScanner";
    private static final String NID_FILTER = "exa";
    public static final long MIN_SCAN_PERIOD = 1000;
    public static final long MAX_SCAN_PERIOD = 60000;
    public static final long DEF_SCAN_PERIOD = 10000;
    public static final int REQ_BLE_DEVICES = (1);
    public static final int REQ_USB_DEVICES = (1 << 1);
    public static final int REQ_ETH_DEVICES = (1 << 2);
    public static final int LAST_DEVICE = REQ_ETH_DEVICES;
    public static final int ALL_DEVICES = (LAST_DEVICE << 1) - 1;
    private final int mRequestedDevices;
    private Long mScanPeriod = DEF_SCAN_PERIOD;
    private boolean mCheckNordicID;
    private final NurApi mApi;
    private final List<NurDeviceSpec> mDeviceList;
    private final Handler mHandler;
    private  boolean mEthQueryRunning = false;
    private boolean mScanning = false;
    private final Context mOwner;
    private NurDeviceScannerListener mListener;

    public interface NurDeviceScannerListener{
        void onScanStarted();
        void onDeviceFound(NurDeviceSpec device);
        void onScanFinished();
    }

    public NurDeviceScanner(Context context, int requestedDevices, NurApi mApi) {
        this(context,requestedDevices,null, mApi);
    }

    public NurDeviceScanner(Context context, int requestedDevices, NurDeviceScannerListener listener, NurApi api){
        mDeviceList = new ArrayList<>();
        mOwner = context;
        mRequestedDevices = requestedDevices;
        mHandler = new Handler();
        mListener = listener;
        mApi = api;
    }

    public void registerScanListener(NurDeviceScannerListener listener){
        mListener = listener;
    }

    public void unregisterListener(){
        mListener = null;
    }

    public boolean scanDevices(Long timeout, boolean checkFilter) {

        if(mListener == null)
            return false;

        mCheckNordicID = checkFilter;

        if (timeout < MIN_SCAN_PERIOD)
            timeout = MIN_SCAN_PERIOD;
        else if (timeout > MAX_SCAN_PERIOD)
            timeout = MAX_SCAN_PERIOD;

        mScanning = true;
        mScanPeriod = timeout;
        Log.i(TAG, "scanDevices; timeout " + timeout);

        /* notify scan started */
        mListener.onScanStarted();

        if (requestingIntDevice()) {
            Log.i(TAG,"Add internal reader device");
            addDevice(getIntDeviceSpec());
        }

        if (requestingUSBDevice()) {
            Log.i(TAG,"Scanning USB Devices");
            addDevice(getUsbDeviceSpec());
        }

        if (requestingETHDevice()) {
            Log.i(TAG,"Scanning Local Ethernet Devices");
            queryMdnsDevices();
            queryEthernetDevices();
        }

        if (requestingBLEDevices()) {
            Log.i(TAG,"Scanning BLE Devices");
            queryBLEDevices();
        }

        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(this::stopScan, mScanPeriod);

        return true;
    }

    public void scanDevices() {
        if(!mScanning)
            scanDevices(mScanPeriod, mCheckNordicID);
    }

    public void stopScan() {
        if (mScanning) {
            mScanning = false;
            BleScanner.getInstance().unregisterListener(this);
            if (mNsdManager != null) {
                mNsdManager.stopServiceDiscovery(mDiscoveryListener);
                mNsdManager = null;
            }
            if (mListener != null)
                mListener.onScanFinished();
        }
    }

    public void purge(){
        mDeviceList.clear();
    }

    private boolean requestingBLEDevices() {
        return (mRequestedDevices & REQ_BLE_DEVICES) != 0;
    }

    private boolean requestingUSBDevice() {
        return (mRequestedDevices & REQ_USB_DEVICES) != 0;
    }

    private boolean requestingETHDevice() {
        return (mRequestedDevices & REQ_ETH_DEVICES) != 0;
    }

    private boolean requestingIntDevice() {
        String manufacturer = Build.MANUFACTURER.toLowerCase(Locale.ENGLISH);
        Log.i(TAG,"Manuf Lower = " + manufacturer + " Orig = " + Build.MANUFACTURER);
        return (manufacturer.contains("nordicid") || manufacturer.contains("nordic id"));
    }

    private void addDevice(NurDeviceSpec device) {
        if (!mScanning || device.getName() == null)
            return;
        boolean deviceFound = false;

        for (NurDeviceSpec listDev : mDeviceList) {
            if (listDev.getAddress().equals(device.getAddress())) {
                deviceFound = true;
                break;
            }
        }
        /* device is new */
        if (!deviceFound) {
            Log.i(TAG, "New device found : " + device.getSpec());
            mDeviceList.add(device);

            if(mListener != null)
                mListener.onDeviceFound(device);
        }
    }


    public List<NurDeviceSpec> getDeviceList(){ return mDeviceList; }

    public NurDeviceSpec getIntDeviceSpec() {
        return new NurDeviceSpec("type=INT;addr=integrated_reader;name=Integrated Reader");
    }

    //region Ethernet devices

    public boolean isEthQueryRunning(){
        return mEthQueryRunning;
    }

    public void queryEthernetDevices(){
        Runnable mEthQueryRunnable = this::ethQueryWorker;
        mEthQueryRunning = true;
        (new Thread(mEthQueryRunnable)).start();
    }

    private void ethQueryWorker()
    {
        ArrayList<NurEthConfig> theDevices;
        try {
            while (mScanning) {
                theDevices = mApi.queryEthDevices();
                for (NurEthConfig cfg : theDevices) {
                    if (cfg.hostMode == 0) {// Only show server mode devices
                        Log.i(TAG, "DEV: " + cfg.title + " MAC:" + cfg.mac);
                        postNewDevice(getEthDeviceSpec(cfg));
                    }
                }
            }
        }
        catch (Exception ex)
        {
            // TODO
        }
        mEthQueryRunning = false;
    }

    final String SERVICE_TYPE = "_nur._tcp.";
    NsdManager mNsdManager = null;

    public void queryMdnsDevices()
    {
        try {
            if (mNsdManager == null)
                mNsdManager = (NsdManager) mOwner.getSystemService(Context.NSD_SERVICE);
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Instantiate a new DiscoveryListener
    NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {

        // Called as soon as service discovery begins.
        @Override
        public void onDiscoveryStarted(String regType) {
            Log.d(TAG, "MDNS Service discovery started");
        }

        @Override
        public void onServiceFound(NsdServiceInfo service) {
            // A service was found! Do something with it.
            Log.d(TAG, "MDNS Service discovery success" + service);
            if (!service.getServiceType().equals(SERVICE_TYPE)) {
                // Service type is the string containing the protocol and
                // transport layer for this service.
                Log.d(TAG, "MDNS Unknown Service Type: " + service.getServiceType());
            }
            else
            {
                if (mNsdManager == null) return;
                mNsdManager.resolveService(service, getResolveListener());
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo service) {
            // When the network service is no longer available.
            Log.i(TAG, "MDNS Service lost " + service);
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.i(TAG, "MDNS Discovery stopped: " + serviceType);
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(TAG, "MDNS Discovery failed: Error code:" + errorCode);
            if (mNsdManager == null) return;
            mNsdManager.stopServiceDiscovery(this);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(TAG, "MDNS Discovery failed: Error code:" + errorCode);
            if (mNsdManager == null) return;
            mNsdManager.stopServiceDiscovery(this);
        }
    };

    NsdManager.ResolveListener getResolveListener() {
        return new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                if (mNsdManager == null) return;

                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                    // This happens when multiple devices found in network and other resolving is already in progress.
                    // Just keep trying..
                    mNsdManager.resolveService(serviceInfo, getResolveListener());
                    return;
                }
                // Called when the resolve fails. Use the error code to debug.
                Log.e(TAG, "MDNS Resolve failed " + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                if (mNsdManager == null) return;

                Log.i(TAG, "MDNS Resolve Succeeded. " + serviceInfo);
                Map<String, byte[]> map = serviceInfo.getAttributes();
                String name = serviceInfo.getServiceName();
                InetAddress host = serviceInfo.getHost();
                int port = serviceInfo.getPort();
                String type = "LAN";
                if (map.containsKey("TYPE")) {
                    type = new String(map.get("TYPE")).toUpperCase();
                }

                if (Objects.requireNonNull(host.getHostAddress()).contains(":")) {
                    Log.e(TAG, "IPV6 not supported");
                } else {
                    postNewDevice(new NurDeviceSpec("type=TCP;addr=" + host.getHostAddress() + ":" + port + ";port=" + port + ";name=" + name + ";transport=" + type));
                }
            }
        };
    }

    private void postNewDevice(final NurDeviceSpec device)
    {
        mHandler.post(() -> addDevice(device));
    }

    private NurDeviceSpec getEthDeviceSpec(NurEthConfig ethCfg) {
        String tr = "LAN";
        if (ethCfg.transport==2)
            tr = "WLAN";
        return new NurDeviceSpec("type=TCP;addr="+ethCfg.ip+":"+ethCfg.serverPort+";port="+ethCfg.serverPort+";name="+ethCfg.title+";transport="+tr);
    }

    //endregion

    //region USB devices

    public NurDeviceSpec getUsbDeviceSpec() {
        return new NurDeviceSpec("type=USB;addr=USB;name=USB Device");
    }
    //endregion

    //region BLE Devices

    @Override
    public void onBleDeviceFound(final BluetoothDevice device, final String name, final int rssi)
    {
        if (checkNIDBLEFilter(name))
        {
            if( mApi != null && mApi.getUiThreadRunner() != null) {
                mApi.getUiThreadRunner().runOnUiThread(() -> addDevice(getBtDeviceSpec(device, name, false, rssi)));
            } else {
                addDevice(getBtDeviceSpec(device, name, false, rssi));
            }
        }
    }

    public NurDeviceSpec getSmartPairBleDeviceSpec() {
        return new NurDeviceSpec("type=SmartPair;addr=smartpair;name=Nordic ID Smart Pair");
    }

    public void queryBLEDevices()
    {
        if (NurSmartPairSupport.isSupported()) {
            // Add smart pair
            addDevice(getSmartPairBleDeviceSpec());
        }

        // Add paired
        Set<BluetoothDevice> pairedDevices = BleScanner.getPairedDevices();
        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                if (BleScanner.isBleDevice(device) && checkNIDBLEFilter(device.getName()))
                    addDevice(getBtDeviceSpec(device, device.getName(), true, 0));
            }
        }

        // Start BLE scan
        Log.i(TAG, "Start BLE scan; " + mOwner);
        BleScanner.getInstance().registerScanListener(this);
    }

    private boolean checkNIDBLEFilter(String deviceName)
    {
        if (!mCheckNordicID)
            return true;
        if (deviceName == null)
            return false;
        return deviceName.toLowerCase(Locale.ENGLISH).contains(NID_FILTER);
    }

    private NurDeviceSpec getBtDeviceSpec(BluetoothDevice device, String name, boolean bonded, int rssi) {
        if (name == null || name.equals("null")) {
            name = device.getAddress();
        }

        return new NurDeviceSpec("type=BLE;addr="+device.getAddress()+";name="+name+";bonded="+bonded+";rssi="+rssi);
    }

    //endregion
}
