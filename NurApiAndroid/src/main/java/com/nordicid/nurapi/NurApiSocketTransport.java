package com.nordicid.nurapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import android.util.Log;

/** 
 * Socket transport for NUR Java API.
 * @author Nordic ID.
 * @version 1.6.1.7
 */
public class NurApiSocketTransport implements NurApiTransport
{
	static final String TAG = "NurApiSocketTransport";

	private boolean isClient;
	private Socket mSocket = null;
	private InputStream mInput = null;
	private OutputStream mOutput = null;
	private String mHost = "";
	private int mPort = 0;
	private boolean mConnected = false;

	/**
	 * Server uses this internally.
	 * @param client Client socket that are accepted by the server.
	 */
	public NurApiSocketTransport(Socket client)
	{
		mSocket = client;
	}
	
	/**
	 * Constructor to create transport for client connection.
	 * @param host	IP address to the host NUR device.
	 * @param port	Port to the host NUR device.
	 */
	public NurApiSocketTransport(String host, int port)
	{
		isClient = true;
		mHost = host;
		mPort = port;
	}
	
	@Override
	public void connect() throws Exception
	{
		if(mConnected)
			return;
		
		if(isClient) //client connection
		{
			try {
				String host = mHost;
				int port = mPort;
				if (mHost.toLowerCase(Locale.ENGLISH).equals("integrated_reader")) {
					host = "localhost";
					port = 6734;
				}
				mSocket = new Socket();
				mSocket.connect(new InetSocketAddress(host, port));

				//mSocket.setSoTimeout(1000);
				//mSocket.setKeepAlive(true);// NEW
				//mSocket.setTcpNoDelay(false);// NEW (Nagle)
				mOutput = mSocket.getOutputStream();
				mInput = mSocket.getInputStream();
			}
			catch (UnknownHostException e) 
			{				 
				e.printStackTrace();
				throw new NurApiException("Error connecting to NUR device. Unknown host: " + mHost);
			}
			catch (Exception e) 
			{
				e.printStackTrace();				
				throw new NurApiException("Exception when connecting to NUR device.");
			}
		}
		else //host connection
		{
			try {
				mOutput = mSocket.getOutputStream();
				mInput = mSocket.getInputStream();
			} catch (Exception e) {
				throw new NurApiException("Error while opening I/O-streams between the client and the server.");
			}
		}
		mConnected = true;
	}

	@Override
	public void disconnect()
	{
		Log.d(TAG, "disconnect() mConnected " + mConnected);

        try{
        	if(mInput != null) 
        	{
        		mInput.close();
        		//mInput = null;
        	}
        }
    	catch(Exception ignored) {}
        
        try{            
            if(mOutput != null) 
            {
            	mOutput.close();
            	//mOutput = null;
            }
        }
    	catch(Exception ignored) {}

		try{
			if(mSocket != null)
			{
				mSocket.close();
				//mSocket = null;
			}
		}
		catch(Exception ignored) {}

		mConnected = false;
	}

	@Override
	public boolean isConnected()
	{
		return (mConnected);
	}
	
	@Override
	public int readData(byte[] buffer) throws IOException
	{
		int read = 0;
		if(mConnected)
		{
			if (mInput == null)
				return -1;
			
			try {
				int r = mInput.read(buffer, 0, buffer.length);
				if (r < 0)
				{
					//System.out.println("Socket gracefull exit");
					disconnect();
					return -1;
				}
				read += r;
			} 
			catch (SocketTimeoutException sto) {
				return -1;
			}		
			catch (SocketException se) {
				se.printStackTrace();
				disconnect();
				return -1;
			}
		}
		return read;
	}

	@Override
	public int writeData(byte[] buffer, int len) {
		if (mOutput == null)
			return -1;

		try {			
			mOutput.write(buffer, 0, len);			
		} catch (Exception e) {
			e.printStackTrace();
			len = -1;
		}
		return len;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean disableAck()
	{
		return false;
	}
}
