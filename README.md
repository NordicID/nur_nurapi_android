## NurApiAndroid v2
This repository contains NurApi for Android. 

### Samples
- [Sample repository](https://github.com/NordicID/nur_sample_android)
- [Nordic ID RFID Demo repository](https://github.com/NordicID/nur_tools_rfiddemo_android)

## Creating a project
- Create a project that fits your needs. 
- Add NurApi.jar and NurApiAndroid.aar to your project as modules.

To add the module to the project in Android Studio:

1. Select the project view
2. Right-click the project and select New -> Module
3. In the new module dialog, select the "Import .JAR/.AAR Package"
4. Navigate to the aar or jar file.
5. Set the sub-project name to e.g. "nurapiandroid" or "nurapi"
6. Right-click the created module and select "Open Module Settings"
7. Add the module dependency to "app"
8. Change the project view to "Android" and open the app-module's Gradle script
9. Change the minimum SDK version (minSdkVersion) to 21 if it isn't already
10. Target SDK version (targetSdkVersion) is good to be 21 or higher, e.g. 24 will cause trouble when using Bluetooth low energy.
11. Where you want to use the Android API, use `import com.nordicid.nurapi.*;`

## Using the API in your application 

The API has actually two parts - The NurApi that is same for all Java applications and on top of that is the NurApiAndroid that has some extensions such as battery information and barcode scanning.

In your app declare the API instance and the accessory extension. The latter takes the API as its parameter:

    private NurApi mApi = new NurApi();
    private NurAccessoryExtension mExtension = new NurAccessoryExtension(mApi);

## Setting up the transport layer

A NUR reader based application has these layers: application - NUR API - transport. The transport layer merely handles the low level communication with the NUR module.

There are various transport types available. The ones of interest in Android are BLE (Bluetooth Low Energy), USB (with e.g STIX reader) and TCP/IP (with Sampo and ARxx readers).

In the NurApiAndroid there is an interface that represents an automatic connection (NurApiAutoConnectTransport). An example of an automatic connection implemntation is e.g "NurApiBLEAutoConnect". The automatic connection is started by giving an address to the connection class. This method's name is "setAddress" and the address parameter is a string which' contents depend on the type of connection used. In the "RFIDDemo" there is an example how the NurApiAndroid's built-in device search is used and how the address received from the activity is given to the transport layer.

### License
All source files in this repository is provided under terms specified in [LICENSE](LICENSE) file.

