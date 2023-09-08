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

import com.nordicid.nurapi.NurApiErrors;
import com.nordicid.nurapi.NurApiException;
import com.nordicid.nurapi.NurPacket;

/** the extension configuration class. */
public class NurAccessoryConfig
{
	/** Get configuration command. */
	public static final int ACC_EXT_GET_CFG = 1;
	/** Set configuration command. */
	public static final int ACC_EXT_SET_CFG = 2;	

	/** Constant signature value used by the module and API extension. */
	public static final int APP_PERM_SIG = 0x21039807;
	public static final int APP_PERM_SIG_OLD1 = 0x21039803;
	
	/** Byte size of the application context. */
	public static final int APP_CTX_SIZE = 50;
	public static final int APP_CTX_SIZE_OLD1 = (6 * 4);

	/** Size, in bytes, of the name field in the configuration protocol packet. */
	public static final int SZ_NAME_FIELD = 32;
	/** Maximum name length in characters. */
	public static final int MAX_NAME_LENGTH = (SZ_NAME_FIELD - 1);

	/** HID-bit for the barcode scan. */
	public static final int APP_FL_HID_BARCODE = (1<<0);
	/** HID-bit for the RFID scan / inventory. */
	public static final int APP_FL_HID_RFID = (1<<1);
	/** Allow pairing bit */
	public static final int APP_FL_USE_PEERMGR = (1<<5);
	/** Device is configured as EXA51 reader. */
	public static final int CONFIG_FLAG_EXA51 = (1<<0);
	/** Device is configured as EXA31 reader. */
	public static final int CONFIG_FLAG_EXA31 = (1<<1);

	/** Device has 1D/2D imager. */
	public static final int DEV_FEATURE_IMAGER = (1<<2);

	/** Device has built-in wireless charging device. */
	public static final int DEV_FEATURE_WIRELESS_CHG = (1<<3);

	/** Device has built-in vibrator. */
	public static final int DEV_FEATURE_VIBRATOR = (1<<4);

	/** For recognition. See #APP_PERM_SIG. */
	public int signature = NurAccessoryExtension.INTVALUE_NOT_VALID;
	/** */
	public int configValue = NurAccessoryExtension.INTVALUE_NOT_VALID;
	/** Control/status flag set. */
	public int flags = 0;
	/** Barcode scan timeout. */
	public int hidBarcodeTimeout = NurAccessoryExtension.INTVALUE_NOT_VALID;
	/** RFID scan / inventory timeout. */
	public int hidRFIDTimeout = NurAccessoryExtension.INTVALUE_NOT_VALID;
	/** Maximum number of tags. */
	public int hidRFIDMaxTags = NurAccessoryExtension.INTVALUE_NOT_VALID;

	/** Name read from the accessory / reader. */
	public String name = "No name";

	// Check whether the given signature is correct.
	private static void checkSignatureThrow(int signature, String message) throws NurApiException
	{
		if (signature != APP_PERM_SIG && signature != APP_PERM_SIG_OLD1)
			throw new NurApiException(message + "; signature = " + signature, NurApiErrors.INVALID_PACKET);
	}

	// Check whether the given signature is correct as extracted from the byte data.
	private static void checkSignatureThrow(byte []source, String message) throws NurApiException
	{
		if(source == null || source.length < 4)
			throw new NurApiException(message + "; source invalid", NurApiErrors.INVALID_PACKET);
		checkSignatureThrow(NurPacket.BytesToDword(source, 0), message);
	}

	/**
	 * Set the HID flag in the barcode scan.
	 *
	 * @param setHID set to true to set the behavior to "HID".
	 */
	public void setHidBarcode(boolean setHID) {
		if (setHID)
			flags |= APP_FL_HID_BARCODE;
		else
			flags &= ~APP_FL_HID_BARCODE;
	}

	/**
	 * Get whether the HID-bit is set in the flag set.
	 * @return Returns true if the barcode HID-bit is set in the current flag set.
	 */
	public boolean getHidBarCode() {
		return (flags & APP_FL_HID_BARCODE) != 0;
	}

	/**
	 * Set the RFID HID-flag in the barcode scan.
	 * @param setHID set to true to set the behavior to "HID".
	 */
	public void setHidRFID(boolean setHID) {
		if (setHID)
			flags |= APP_FL_HID_RFID;
		else
			flags &= ~APP_FL_HID_RFID;
	}

	/**
	 * Get whether the RFID HID-bit is set in the flag set.
	 * @return Returns true if the RFID HID-bit is set in the current flag set.
	 */
	public boolean getHidRFID() {
		return (flags & APP_FL_HID_RFID) != 0;
	}

	/**
	 * Get state of 'Allow pairing' flag.
	 * @return Returns true if pairing is allowed.
	 */
	public boolean getAllowPairingState()
    {
			return (flags & APP_FL_USE_PEERMGR) != 0;
	}

	/**
	 * Set the 'Allow pairing' state.
	 * @param setAllowPairing set to true if allowing device to pair with the target device".
	 */
	public void setAllowPairingState(boolean setAllowPairing)
    {
		if (setAllowPairing)
			flags |= APP_FL_USE_PEERMGR;
		else
			flags &= ~APP_FL_USE_PEERMGR;
	}

	/**
	 * Deserializes the accessory extension's configuration reply.
	 * 
	 * @param reply Bytes received from the transport layer / reader module.
	 * @return Return the configuration class if the reply is OK.
	 * 
	 * @throws NurApiException Exception is thrown if he reply is invalid.
	 */
	public static NurAccessoryConfig deserializeConfigurationReply(byte []reply) throws Exception
	{
		int sourceOffset = 0;	// 0 = no sub-command echo in reply.

		// The reply starts with signature.
		checkSignatureThrow(reply, "Accessory extension, getConfig: unknown configuration signature");

		if (reply.length < APP_CTX_SIZE)
			throw new NurApiException("Accessory config, invalid reply length", NurApiErrors.INVALID_PACKET);
		
		NurAccessoryConfig cfg = new NurAccessoryConfig();
		// -1 = no "sub-command" byte present in reply. 
		cfg.signature = NurPacket.BytesToDword(reply, sourceOffset);
		sourceOffset += 4;		
		cfg.configValue = NurPacket.BytesToDword(reply, sourceOffset);
		sourceOffset += 4;
		cfg.flags = NurPacket.BytesToDword(reply, sourceOffset);
		sourceOffset += 4;

		if (cfg.signature != APP_PERM_SIG_OLD1) {
			// cfg.name = new String(reply, sourceOffset, MAX_NAME_LENGTH, StandardCharsets.UTF_8);
			cfg.name = "";
			for (int n=0; n<32; n++)
			{
				if (reply[sourceOffset+n] == 0)
				{
					cfg.name = new String(reply, sourceOffset, n);
					break;
				}
			}
			sourceOffset += SZ_NAME_FIELD;		
			cfg.hidBarcodeTimeout = NurPacket.BytesToWord(reply, sourceOffset);
			sourceOffset += 2;
			cfg.hidRFIDTimeout = NurPacket.BytesToWord(reply, sourceOffset);
			sourceOffset += 2;
			cfg.hidRFIDMaxTags = NurPacket.BytesToWord(reply, sourceOffset);

		} else {
			cfg.name = "NOT SUPPORTED";
			cfg.hidBarcodeTimeout = NurPacket.BytesToDword(reply, sourceOffset);
			sourceOffset += 4;
			cfg.hidRFIDTimeout = NurPacket.BytesToDword(reply, sourceOffset);
			sourceOffset += 4;
			cfg.hidRFIDMaxTags = NurPacket.BytesToDword(reply, sourceOffset);
		}

		return cfg;
	}	
	
	/**
	 * Serializes accessory extension configuration into a byte array that the extension module consumes / accepts.
	 * 
	 * @param cfg A valid accessory extension configuration.
	 * 
	 * @return Returns a byte array containing the protocol defined bytes required for the configuration.
	 * 
	 * @throws Exception API exception is thrown with invalid signature.
	 */
	public static byte[] serializeConfiguration(NurAccessoryConfig cfg) throws Exception
	{
		checkSignatureThrow(cfg.signature, "Accessory extension, setConfig: unknown configuration signature");
		
		byte []serializedCfg;
		byte []nameBytes;
		int offset = 0;

		if (cfg.signature == APP_PERM_SIG_OLD1) {
			serializedCfg = new byte[APP_CTX_SIZE_OLD1+1];
		} else {
			serializedCfg = new byte[APP_CTX_SIZE+1];
		}

		// Start with "sub-command".
		serializedCfg[offset++] = (byte)ACC_EXT_SET_CFG;

		// Add all according to the packet content rules.
		offset += NurPacket.PacketDword(serializedCfg, offset, cfg.signature);
		offset += NurPacket.PacketDword(serializedCfg, offset, cfg.configValue);
		offset += NurPacket.PacketDword(serializedCfg, offset, cfg.flags);

		if (cfg.signature != APP_PERM_SIG_OLD1) {
			nameBytes = new byte[MAX_NAME_LENGTH+1];
			for (int n=0; n<MAX_NAME_LENGTH+1; n++) nameBytes[n] = 0;
			int copyLength = (cfg.name.length() > MAX_NAME_LENGTH) ? MAX_NAME_LENGTH : cfg.name.length();
			System.arraycopy(cfg.name.getBytes(), 0, nameBytes, 0, copyLength);
			offset += NurPacket.PacketBytes(serializedCfg, offset, nameBytes);
			offset += NurPacket.PacketWord(serializedCfg, offset, cfg.hidBarcodeTimeout);
			offset += NurPacket.PacketWord(serializedCfg, offset, cfg.hidRFIDTimeout);
			NurPacket.PacketWord(serializedCfg, offset, cfg.hidRFIDMaxTags);
		} else {
			offset += NurPacket.PacketDword(serializedCfg, offset, cfg.hidBarcodeTimeout);
			offset += NurPacket.PacketDword(serializedCfg, offset, cfg.hidRFIDTimeout);
			NurPacket.PacketDword(serializedCfg, offset, cfg.hidRFIDMaxTags);			
		}

		return serializedCfg;
	}	
	
	/**
	 * Configuration get -command.
	 * @return Returns the configuration request command parameter(s). 
	 */	
	public static byte []getQueryCommand()
	{
		return new byte [] {  ACC_EXT_GET_CFG };
	}
	
	/**
	 * Creates an empty configuration.
	 * 
	 * @return Returns the new allocated configuration object with correct signature set.
	 */
	public static NurAccessoryConfig allocEmpty()
	{
		NurAccessoryConfig newConfig = new NurAccessoryConfig();
		newConfig.signature = APP_PERM_SIG;
		return newConfig;		
	}

		/**
 	* Get whether the device is configured as EXA81 reader.
 	* @return Returns true if the reader is EXA81.
 	*/
	public boolean isDeviceEXA81()
	{			
		return (hasImagerScanner() && ((configValue & CONFIG_FLAG_EXA51) == 0) && ((configValue & CONFIG_FLAG_EXA31) == 0));
	}

	
	/**
	 * Get whether the device is configured as EXA51 reader.
	 * @return Returns true if the reader is EXA51.
     */
	public boolean isDeviceEXA51()
	{
		return ((configValue & CONFIG_FLAG_EXA51) != 0);
	}

	/**
	 * Get whether the device is configured as EXA31 reader.
	 * @return Returns true if the reader is EXA31.
	 */
	public boolean isDeviceEXA31()
	{
		return ((configValue & CONFIG_FLAG_EXA31) != 0);
	}

	/**
	 * Get whether the device is configured as EXA21 reader.
	 * @return Returns true if the reader is EXA21.
	 */
	public boolean isDeviceEXA21()
	{			
		return hasImagerScanner() ? false:true; //not good way but only one..				
	}
	
	/**
	 * Get device name EXA51 or EXA31
	 * @return String with device Name
	 */
	public String getDeviceType()
	{
		if (isDeviceEXA21())
			return "EXA21";
		else if (isDeviceEXA31())
			return "EXA31";
		else if (isDeviceEXA51())
			return "EXA51";
		else if (isDeviceEXA81())
			return "EXA81";
		return "N/A";
	}

	// public static final int DEV_FEATURE_IMAGER = (1<<2);
	/**
	 * Get whether the accessory has 1D/2D imager present.
	 *
	 * @return Returns true if there is a 1D/2D imager in the accessory device.
	 */
	public boolean hasImagerScanner()
	{
		return ((configValue & DEV_FEATURE_IMAGER) != 0);
	}

	/**
	 * Get whether the accessory has wireless charging device.
	 *
	 * @return Returns true if there is wireless charging option available.
     */
	public boolean hasWirelessCharging()
	{
		return ((configValue & DEV_FEATURE_WIRELESS_CHG) != 0);
	}

	/**
	 * Get whether the accessory has built-in vibrator.
	 *
	 * @return Returns true if there is built-in vibrator in the device.
	 */
	public boolean hasVibrator()
	{
		return ((configValue & DEV_FEATURE_VIBRATOR) != 0);
	}
};

