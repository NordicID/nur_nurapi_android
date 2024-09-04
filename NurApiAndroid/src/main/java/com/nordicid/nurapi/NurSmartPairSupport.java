package com.nordicid.nurapi;

import android.content.Context;

import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Runtime support for the Nordic ID SmartPair library.
 * This class can be used to dynamically check runtime whether app is linked against NurSmartPair.aar.
 */
public class NurSmartPairSupport {

    /**
     * Check for smart pair support.
     * @return true if supported, false otherwise
     */
    public static boolean isSupported()
    {
        try
        {
            Class.forName ("com.nordicid.smartpair.SmartPair");
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static NurApiAutoConnectTransport createSmartPairTransport(Context ctx, NurApi api)
    {
        try
        {
            Constructor<?> c = Class.forName ("com.nordicid.smartpair.NurApiSmartPairAutoConnect").getConstructor(Context.class, NurApi.class);
            return (NurApiAutoConnectTransport)c.newInstance(ctx, api);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    static public String getVersion()
    {
        try
        {
            Method m = Class.forName ("com.nordicid.smartpair.SmartPair").getDeclaredMethod("getVersion");
            return (String)m.invoke(null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    static public String getSettingsString()
    {
        try
        {
            Method m = Class.forName ("com.nordicid.smartpair.SmartPairSettings").getDeclaredMethod("getSettingsString");
            return (String)m.invoke(null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    static public JSONObject getSettings()
    {
        try
        {
            Method m = Class.forName ("com.nordicid.smartpair.SmartPairSettings").getDeclaredMethod("getSettings");
            return (JSONObject)m.invoke(null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    static public boolean setSettingsString(String json) {
        try
        {
            Method m = Class.forName ("com.nordicid.smartpair.SmartPairSettings").getDeclaredMethod("setSettingsString", String.class);
            return (boolean)m.invoke(null, json);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    static public boolean setSettings(JSONObject jsonObject)
    {
        try
        {
            Method m = Class.forName ("com.nordicid.smartpair.SmartPairSettings").getDeclaredMethod("setSettings", JSONObject.class);
            m.invoke(null, jsonObject);
            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }
}
