/***********************************************************************************************************************
*
*  A Hubitat Driver using Telnet to connect to an Envisalink 3 or 4.
*  http://www.eyezon.com/
*
*  Copyright (C) 2018 Doug Beard
*  Copyright (C) 2021 Dale Munn
*
*  Vista related portions and general enhancements by CybrMage
*
*  License:
*  This program is free software: you can redistribute it and/or modify it under the terms of the GNU
*  General Public License as published by the Free Software Foundation, either version 3 of the License, or
*  (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
*  implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
*  for more details.
*
*  You should have received a copy of the GNU General Public License along with this program.
*  If not, see <http://www.gnu.org/licenses/>.
*
*  Name: Envisalink Connection
*  https://github.com/omayhemo/hubitat_envisalink
*
*	Special Thanks to Chuck Swchwer, Mike Maxwell and cuboy29
*	and to the entire Hubitat staff, you guys are killing it!
*	See Release Notes at the bottom
***********************************************************************************************************************/

import groovy.transform.Field

def version() { return "Envisalink 0.9.0" }
metadata {
		definition (name: "Envisalink Connection", 
			namespace: "dwb", 
			author: "Doug Beard", 
			importUrl: "https://raw.githubusercontent.com/damunn/hubitat_envisalink/master/hubitat_envisalink_connection_driver.groovy") {
			capability "Initialize"
			capability "Telnet"
			capability "Alarm"
			capability "Switch"
			capability "Actuator"
			capability "Polling"
			capability "TamperAlert"
			capability "ContactSensor"
			
			attribute   "Status", "string"
			attribute   "Codes", "json"
			attribute   "LastUsedCodePosition", "string"
			attribute   "LastUsedCodeName", "string"
			attribute		"Trouble LED", "string"
			attribute 	"Bypassed Zones", "string"
			attribute		"CID_Code", "string"
			attribute		"CID_Type", "string"
			attribute		"CID_Partition", "string"
			attribute		"CID_UserZone", "string"
			attribute		"CID_DATA", "string"
		
			command "sendTelnetCommand", ["String"]
			command "StatusReport"
			command "ArmAway"
			command "ArmHome"
			command "ArmNight"
			//command "ArmAwayZeroEntry"
			//command "SoundAlarm"
			command "Disarm"
			command "ChimeToggle"
			command "ToggleTimeStamp"
			command "poll"
			command "setUserCode", ["String", "Number", "Number"]
			command "deleteUserCode", ["Number"]
			command	"BypassZone", ["number"]
//			command "testParse", ["String"]


		
		}

		preferences {
			def PanelTypes = ["0" : "DSC", "1" : "Vista"]
			input ("PanelType", "enum", title: "Panel Type", options: PanelTypes, defaultValue: 0)
			input("ip", "text", title: "IP Address",  required: true)
			input("passwd", "text", title: "Password", required: true)
			input("masterCode", "text", title: "Master Code", required: true)
			input("installerCode", "text", title: "Installer Code", description: "Installer Code is required if you wish to program the panel from this driver", required: false)
            input("disablePartReady", "bool", title: "Disable Force Arming Enabled", description: "Removes Partition Ready Message", defaultVale: true)
            def pollRate = ["0" : "Disabled", "1" : "Poll every minute", "5" : "Poll every 5 minutes", "10" : "Poll every 10 minutes", "15" : "Poll every 15 minutes", "30" : "Poll every 30 minutes (not recommended)"]
			input ("poll_Rate", "enum", title: "Device Poll Rate", options: pollRate, defaultValue: 0)
			if (installerCode) {
				def delayOptions = ["030" : "30 seconds", "045" : "45 seconds", "060" : "60 seconds", "090" : "90 seconds", "120" : "120 seconds"]
				input ("entry_delay1", "enum", title: "Entry Delay 1", options: delayOptions, defaultValue: 060)
				input ("entry_delay2", "enum", title: "Entry Delay 2", options: delayOptions, defaultValue: 060)
				input ("exit_delay", "enum", title: "Exit Delay 2", options: delayOptions, defaultValue: 060)
			}
			input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
		}
	
}

/***********************************************************************************************************************
*   Platform Events
*/

def installed() {
	if(isDebug) log.debug("installed...")
	initialize()
}

def updated() {
	if(isDebug) log.debug("updated...")
	if(isDebug) log.debug("Configuring IP: ${ip}, Code: ${masterCode}, Password: ${passwd}")
	initialize()
	unschedule()
	switch(poll_Rate) {
		case "0" :
			if(isDebug) log.debug("Envisalink Polling is Disabled")
			break
		case "1" :
			runEvery1Minute(poll)
			if(isDebug) log.debug("Poll Rate set at 1 minute")
			break
		case "5" :
			runEvery5Minutes(poll)
			if(isDebug) log.debug("Poll Rate set at 5 minutes")
			break
		case "10" :
			runEvery10Minutes(poll)
			if(isDebug) log.debug("Poll Rate set at 10 minutes")
			break
		case "15" :
			runEvery15Minutes(poll)
			if(isDebug) log.debug("Poll Rate set at 15 minutes")
			break
		case "30" :
			runEvery30Minutes(poll)
			if(isDebug) log.debug("Poll Rate set at 30 minutes")
			break
	}

	if (installerCode) {
		setDelays(entry_delay1, entry_delay2, exit_delay)
	}
}

def initialize() {
	runIn(5, "telnetConnection")
//	state.programmingMode = ""
	state.remove("programmingMode")
}

def uninstalled() {
	telnetClose()
	removeChildDevices(getChildDevices())
}

/***********************************************************************************************************************
*   Driver Commands
*/

def ArmAway(){
	if(isDebug) log.debug("armAway()")
	state.armState = "arming_away"
	composeArmAway()
}

def ArmHome(){
	if(isDebug) log.debug("armHome()")
	state.armState = "arming_home"
	composeArmHome()
}

def ArmNight(){
	if(isDebug) log.debug("armNight()")
	state.armState = "arming_night"
	composeArmNight()
}

def ArmAwayZeroEntry(){
	if(isDebug) log.debug("ArmAwayZeroEntry()")
	composeZeroEntryDelayArm()
}

def both(){
	if(isDebug) log.debug("both()")
	siren()
	strobe()
}

def BypassZone(zone){
	if(isDebug) log.debug("BypassZone ${zone}")
	composeBypassZone(zone as int)
}

def ChimeToggle(){
	if(isDebug) log.debug("ChimeToggle()")
	composeChimeToggle()
}

def deleteUserCode(position){
	if(isDebug) log.debug("deleteUserCode ${position}")
	composeDeleteUserCode(position)
}

def Disarm(){
 	if(isDebug) log.debug("Disarm()")
	composeDisarm()
}

def on(){
	if(isDebug) log.debug("On")
	ArmAway()
}

def off(){
 	if(isDebug) log.debug("Off")
	Disarm()
}

def poll() {
	if(isDebug) log.debug("Polling...")
	composePoll()
}

def SoundAlarm(){
 	if(isDebug) log.debug("Sound Alarm : NOT IMPLEMENTED")
}

def siren(){
	if(isDebug) log.debug("Siren : NOT IMPLEMENTED")
}

def StatusReport(){
	if(isDebug) log.debug("StatusReport")
	composeStatusReport()
}

def strobe(){
 	if(isDebug) log.debug("Stobe : NOT IMPLEMENTED")
}

def setUserCode(name, position, code){
	if(isDebug) log.debug("setUserCode ${name} ${position} ${code}")
	composeSetUserCode(name, position, code)   
}

def setDelays(entry, entry2, exit){
	if(isDebug) log.debug("setDelays ${entry} ${entry2} ${exit}")
	composeSetDelays(entry, entry2, exit)
}

def ToggleTimeStamp(){
	if(isDebug) log.debug("Toggle Time Stamp")
	composeTimeStampToggle()
}

// def testParse(position){
//     parseUser("11111102")
// }

/***********************************************************************************************************************
*   End Points
*/

def createZone(zoneInfo){
	if(isDebug) log.debug( "Creating ${zoneInfo.zoneName} with label '${zoneInfo.zoneLabel}' with deviceNetworkId = ${zoneInfo.deviceNetworkId} of type: ${zoneInfo.zoneType} for panel type: " + PanelType)
	def newDevice
	if (zoneInfo.zoneType == "0")
	{
		newDevice = addChildDevice("hubitat", "Virtual Contact Sensor", zoneInfo.deviceNetworkId, [name: zoneInfo.zoneName, isComponent: true, label: zoneInfo.zoneLabel])
		//newDevice = getChildDevice(zoneInfo.deviceNetworkId)
		newDevice.updateSetting("zoneType", zoneInfo.zoneType)
		if(PanelType as int == 1) {
			// Vista does not report contact sensors inactive... make it automatic
			// virtual contact sensor does not support autoInactive
//			if(isDebug) log.debug("Setting autoInactive for Virtual Contact Sensor for Vista Panel")
//			newDevice.updateSetting("autoInactive",[type:"enum", value:"60"])
		}

	} else if (zoneInfo.zoneType == "1") {
		addChildDevice("hubitat", "Virtual Motion Sensor", zoneInfo.deviceNetworkId, [name: zoneInfo.zoneName, isComponent: true, label: zoneInfo.zoneLabel])
		newDevice = getChildDevice(zoneInfo.deviceNetworkId)
		if(PanelType as int == 0) {
			newDevice.updateSetting("autoInactive",[type:"enum", value:disabled])
		} else {
			// Vista does not report motion sensor inactive... make it automatic
			if(isDebug) log.debug("Setting autoInactive for Virtual Motion Sensor for Vista Panel")
			newDevice.updateSetting("autoInactive",[type:"enum", value:"180"])
		}
	} else if (zoneInfo.zoneType == "2") {
		addChildDevice("hubitat", "Virtual CO Detector", zoneInfo.deviceNetworkId, [name: zoneInfo.zoneName, isComponent: true, label: zoneInfo.zoneLabel])
	} else if (zoneInfo.zoneType == "3") {
		addChildDevice("hubitat", "Virtual Smoke Detector", zoneInfo.deviceNetworkId, [name: zoneInfo.zoneName, isComponent: true, label: zoneInfo.zoneLabel])
	} else if (zoneInfo.zoneType == "4") {
		addChildDevice("dwb", "Virtual GlassBreak Detector", zoneInfo.deviceNetworkId, [name: zoneInfo.zoneName, isComponent: true, label: zoneInfo.zoneLabel])
	}

}

def removeZone(zoneInfo){
	if(isDebug) log.debug("Removing ${zoneInfo.zoneName} with deviceNetworkId = ${zoneInfo.deviceNetworkId}")
	deleteChildDevice(zoneInfo.deviceNetworkId)
}

/***********************************************************************************************************************
*   Compositions
*/
private composeArmAway(){
	if(isDebug) log.debug("composeArmAway")
	state.armState = "arming_away"
	def message = tpiCommands["ArmAway"]
	if(PanelType as int == 1) {
		message = masterCode + "2"
	}
	sendTelnetCommand(message)
}

private composeArmHome(){
	if(isDebug) log.debug("composeArmHome")
	state.armState = "arming_home"
	def message = tpiCommands["ArmHome"]
	if(PanelType as int == 1) {
		message = masterCode + "3"
	}
	sendTelnetCommand(message)
}
//CHANGED THIS TO ALLOW NIGHT ARMING FOR DSC PANELS
private composeArmNight(){
	if(isDebug) log.debug("composeArmNight")
	state.armState = "arming_night"
	def message = tpiCommands["ArmNight"]
	if(PanelType as int == 1) {
		message = masterCode + "3"
	}
	sendTelnetCommand(message)
}
/*
private composeArmNight(){
	if(PanelType as int == 0) {
		if(isDebug) log.debug("composeArmNight - NOT SUPPORTED BY DSC PANEL")
	} else {
		if(isDebug) log.debug("composeArmNight")
		state.armState = "arming_night"
		def message = masterCode + "33"
		sendTelnetCommand(message)
	}
}
*/

private composeBypassZone(int zone){
	String message = String.format("0711*1%02d#", zone)
	if(isDebug) log.debug("Bypass Zone command ${message}")
		sendTelnetCommand(message)
}

private composeChimeToggle(){
	if(isDebug) log.debug("composeChimeToggle")
	def message = tpiCommands["ToggleChime"]
	if(PanelType as int == 1) { message = masterCode + "9" }
	sendTelnetCommand(message)
}

private composeEnterInstallerMode(){
	if(isDebug) log.debug("composeEnterInstallerMode")
	composeKeyStrokes("*8" + installerCode)
}

private composeExitInstallerMode(){
	if(isDebug) log.debug("composeExitInstallerMode")
	composeKeyStrokes("##")
}

private composeDisarm(){
	if(isDebug) log.debug("composeDisarm")
	if (PanelType as int == 0){
		def message = tpiCommands["Disarm"] + masterCode
		sendTelnetCommand(message)
	} else {
		def message = masterCode + "1"
		sendTelnetCommand(message)
		// work around disarm bug in Vista panels
		sendTelnetCommand(message)
	}
}

private composeDeleteUserCode(position){
	if(isDebug) log.debug("composeDeleteUserCode ${position}")
	def codePosition = position.toString()
	codePosition = codePosition.padLeft(2, "0")
	if(isDebug) log.debug("padded code position ${codePosition}")
	state.programmingMode = DELETEUSERCODEINITIALIZE
	state.newCodePosition = codePosition
	composeKeyStrokes("*5" + masterCode)
}

private composeInstallerCode(){
	if(isDebug) log.debug("composeInstallerCode")
	if(PanelType as int == 0) {
		def sendTelnetCommand = tpiCommands["CodeSend"] + installerCode
		if(isDebug) log.debug(sendTelnetCommand)
		sendTelnetCommand(sendTelnetCommand)
	} else {
		if(isDebug) log.debug("Not supported by Vista TPI")
	}
}

private composeKeyStrokes(data){
	if(isDebug) log.debug("composeKeyStrokes: ${data}")
	if(PanelType as int == 0) {
		sendMessage = tpiCommands["SendKeyStroke"]
	} else { sendMessage = "" }
	sendProgrammingMessage(sendMessage + data)
}

private composeMasterCode(){
	if(isDebug) log.debug("composeMasterCode")
	if(PanelType as int == 0) {
        def message = tpiCommands["CodeSend"] + masterCode
		if(isDebug) log.debug(message)
		//sendTelnetCommand(message)
        //EDITED TO SEND TELNET COMMAND A DIFFERENT WAY
        message = generateChksum(message)
        if(isDebug) log.debug(message)
        sendHubCommand(new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET))
	} else {
		if(isDebug) log.debug("Not supported by Vista TPI")
	}
}

private composePoll(){
	if(PanelType as int == 0) {
		def message = tpiCommands["Poll"]
		sendTelnetCommand(message)
	} else {
		sendTelnetCommand("^00,\$")
	}
}

private composeStatusReport(){
	if(isDebug) log.debug("composeStatusReport")
	if(PanelType as int == 0) {
		sendTelnetCommand(tpiCommands["StatusReport"])
	} else {
		if(isDebug) log.debug("Not supported by Vista TPI")
	}
}

private composeSetUserCode(name, position, code){
	if(isDebug) log.debug("composeSetUserCode")
	if(PanelType as int == 0) {
		state.programmingMode = SETUSERCODE
		if(isDebug) log.debug("Current Codes: ${device.currentValue("Codes")}")
		if (!device.currentValue("Codes")){
			def tempMap = [:]
			def tempJson = new groovy.json.JsonBuilder(tempMap)
			send_Event(name:"Codes", value: tempMap, displayed:true, isStateChange: true)
		}
		def codePosition = position.toString()
		codePosition = codePosition.padLeft(2, "0")
		def newCode = code.toString()
		newCode = newCode.padLeft(4, "0")
		if(isDebug) log.debug("padded: ${codePosition} ${newCode}")

		state.newCode = newCode
		state.newCodePosition = codePosition
		state.newName = name
		state.programmingMode = SETUSERCODEINITIALIZE
		composeKeyStrokes("*5" + masterCode)
	} else {
		if(isDebug) log.debug("Not supported by Vista TPI")
	}
}

private composeSetDelays(entry, entry2, exit){
	if(isDebug) log.debug("composeSetDelays")
	if(isDebug) log.debug("Not Yet Implemented")
	// composeEnterInstallerMode()
	// pauseExecution(7000)

	// def result = composeKeyStrokes("005")
	// pauseExecution(5000)

	// composeKeyStrokes("01")
	// pauseExecution(5000)

	// composeKeyStrokes(entry.toString())
	// pauseExecution(5000)

	// composeKeyStrokes(entry2.toString())
	// pauseExecution(5000)

	// composeKeyStrokes(exit.toString())
	// pauseExecution(5000)

	// composeExitInstallerMode()
}

private composeTimeStampToggle(){
	if(PanelType as int == 0) {
		if(isDebug) log.debug("composeTimeStampToggle")

		def message
		if (state.timeStampOn)
		{
			message = tpiCommands["TimeStampOn"]
		} else {
			message = tpiCommands["TimeStampOff"]
		}
		sendTelnetCommand(message)
	} else {
		if(isDebug) log.debug("Not supported by Vista TPI")
	}
}

private composeZeroEntryDelayArm(){
	if(isDebug) log.debug("composeZeroEntryDelayArm")
	def message = tpiCommands["ArmAwayZeroEntry"]
	if(PanelType as int == 1) {
		// equivilent to Arm Stay Instant
		message = masterCode + "7"
	}
	sendTelnetCommand(message)
}

private parseVistaFlags(flagBitMask, flagBeep, alphaDisplay){
	def flags = [
		ARMED_STAY: (flagBitMask & 0x8000) && true,
		LOW_BATTERY: (flagBitMask & 0x4000) && true,
		FIRE_ALARM: (flagBitMask & 0x2000) && true,
		READY: (flagBitMask & 0x1000) && true,
		UNUSED_1: (flagBitMask & 0x0800) && true,
		UNUSED_2: (flagBitMask & 0x0400) && true,
		CHECK_ZONE: (flagBitMask & 0x0200) && true,
		ALARM_FIRE: (flagBitMask & 0x0100) && true,
		ENTRY_DELAY_OFF: (flagBitMask & 0x0080) && true,
		PROGRAMMING_MODE: (flagBitMask & 0x0040) && true,
		CHIME_MODE: (flagBitMask & 0x0020) && true,
		BYPASSED: (flagBitMask & 0x0010) && true,
		AC_PRESENT: (flagBitMask & 0x0008) && true,
		ARMED_AWAY: (flagBitMask & 0x0004) && true,
		ALARM_MEMORY: (flagBitMask & 0x0002) && true,
		ALARM: (flagBitMask & 0x0001) && true,
		PERIMETER_ONLY: (flagBeep & 0x10) && true,
		BEEP: (flagBeep & 0x0F) && true,
		EXIT_DELAY_ACTIVE: alphaDisplay.toLowerCase().contains("may exit") && true,
		ENTRY_DELAY_ACTIVE: alphaDisplay.toLowerCase().contains("alarm occurs") && true,
		ARMED_NIGHT: alphaDisplay.toLowerCase().contains("night-stay") && (flagBitMask & 0x8000) && true
	]

	if (alphaDisplay.toLowerCase().contains("alarm canceled") == 0) { flags.ALARM_MEMORY = true }
	if (flagBeep == 5) { flags.EXIT_DELAY_ACTIVE = true }

	return flags	

}

/***********************************************************************************************************************
*   Telnet
*/

def parse(String message) {
	if(isDebug) log.debug("Parsing Incoming message: [" + message + "]")

	//if(isDebug) log.debug("Response: ${tpiResponses[message.take(3) as int]}")
	//if(isDebug) log.debug("Panel Type: " + PanelType)

	if(PanelType as int == 0) {
		message = preProcessMessage(message)
 		short tpicmd = message.take(3) as short
 		
		switch (tpicmd) {
		
			case 500:			// Command Acknowlendge
				if (state.programmingMode == SETUSERCODESEND){
				setUserCodeSend()
			  }
				if (state.programmingMode == SETUSERCODECOMPLETE){
				  setUserCodeComplete()
				}

			  if (state.programmingMode == DELETEUSERCODE){
				  deleteUserCodeSend()
			  }
			  if (state.programmingMode == DELETEUSERCOMPLETE){
				   	deleteUserCodeComplete()
				}
			  break
			  
			  case 501:
			  	logError(COMMANDERROR)
			  	break
			  
			  case 502:      // System Error
			    systemError(message)
					break
					
				case 510:      // Key Pad LED State
					keypadLedState(message.substring(3,message.size()))
				case 511:			// Keypad LED FLASH state
					break
					
				case 900:
	      case 921:
		        composeMasterCode()
	        break
					
				case 922:
					composeInstallerCode()
					break
					
				case 609:					// Zone Open
					zoneOpen(message)
					break
					
				case 610:					// Zone Restored
					zoneClosed(message)
					break
					
				case 616:					// Bypassed Zones Bitfield Dump
					zonesBypassed(message.substring(4))
				break
				
				case 650:					// Partition Ready
					partitionReady()
					break
					
				case 651:					// Partition Not Ready
					partitionNotReady()
					break
					
				case 653:				//  Partition Ready - Forced Arming Enabled
			 		partitionReadyForForcedArmEnabled()
			 		break
			 		
			 	case 654:				//  Partition in Alarm
					partitionAlarm()
					break
					
				case 655:				//  Partition Disarmed
					partitionDisarmed()
					break
					
			  case 656:				//  Exit Delay in Progress
					exitDelay()
					break
					
				case 657:				//	Entry Delay in Progress
					entryDelay()
					break
					
				case 658:				// Keypad Lock-out
					keypadLockout()
					break	
					
				case 663:				// Chime Enabled
					send_Event(name:"Chime", value: CHIMEENABLED)
					break
				case 664:				// Chime Disabled
					send_Event(name:"Chime", value: CHIMEDISABLED)
					break
					
				case 505:				// Login interaction
					switch (message[3] as int) {
						case 3:			// Request for password
							loginPrompt()
							break
						case 0:			// Password provided is incorrect
							logError(PASSWORDINCORRECT)
							break
						case 1:			//  Password Correct
							if(isDebug) log.debug(LOGINSUCCESSFUL)
							break
						case  2:			//  Time out
							logError(LOGINTIMEOUT)
							break
						}
					break
					
				case 652:			//  Partition Armed
        	switch (message[4] as int) {
	       		case 0:
        		case 2:
							partitionArmedAway()
							break
					
						case 1:
						case 3:
							partitionArmedHome()
					}
					break
					
        case 701:			// Special Closing
        	send_Event(name:"Status", value: SPECIALCLOSING, isStateChange: true)
					break
					
				case 702:			// Partial Closing	
        	send_Event(name:"Status", value: PARTIALCLOSING, isStateChange: true)
					break		
			
				case 750:			// User Opening
					// partitionArmedNight()
            		parseUser(message)
            		break
            
        case 700:			// User Closing
					partitionArmedNight()
          parseUser(message)
          break
          
        case 840:			// Trouble LED On
           if(device.currentValue("Trouble LED") != 'on') {send_Event(name:"Trouble LED", value: 'on')}
           break
         
        case 841:			// Trouble LED Off
           if(device.currentValue("Trouble LED") != 'off') {send_Event(name:"Trouble LED", value: 'off')}
           break
        
        case 670:			//  Invalid Access Code
          send_Event(name:"Status", value: INVALIDACCESSCODE)
          break
          
        case 673:			//  Partition is Busy
        	break
          
        default:
        	log.error("unimplemented response $tpicmd $message")

		}
		

	} else {

		if(isDebug) log.debug("Panel Type: VISTA")
		if(isDebug) log.debug("Processing VISTA message: ")
		if(message.take(6) == "Login:") {
			if(isDebug) log.debug("Received Login: request")
			loginPrompt()
		}

		if(message.take(6) == "FAILED") {
			logError(PASSWORDINCORRECT)
		}

		if(message.take(2) == "OK") {
			if(isDebug) log.debug(LOGINSUCCESSFUL)
		}

		if(message.take(2) == "Timed Out!") {
			logError(LOGINTIMEOUT)
		}


		if(message.take(3) == "%00") {
			if(isDebug) log.debug("Received %00 (Keypad Update) message")
			// [%00,01,1C08,08,00,****DISARMED****  Ready to Arm  $]
			def mPartition = message[4..5]
			def mFlags = Integer.parseInt(message[7..10],16)
			def mUserOrZone = message[12..13]
			def mChime = message[15..16]
			mChime = mChime.isInteger() ? (mChime as int) : 0
			def mDisplay = message[18..49]
			def vistaFlags = parseVistaFlags(mFlags,mChime,mDisplay)
			if(isDebug) log.debug("Vista FLAGS = " + vistaFlags.inspect())

			if ( vistaFlags.ALARM ) { zoneOpen("000" + mUserOrZone.toString(), false); partitionAlarm() }
			else if ( vistaFlags.ALARM_FIRE || vistaFlags.FIRE_ALARM ) { partitionAlarm() }
			else if ( vistaFlags.READY ) { if (state.armState != "disarmed"){ partitionDisarmed() };	partitionReady() }
			else if ( vistaFlags.ENTRY_DELAY_ACTIVE ) { entryDelay() }
			else if ( vistaFlags.EXIT_DELAY_ACTIVE ) { exitDelay() }
			else if ( vistaFlags.ARMED_AWAY ) { partitionArmedAway() }
			else if ( vistaFlags.ARMED_NIGHT ) { partitionArmedNight() }
			else if ( vistaFlags.ARMED_STAY ) { partitionArmedHome() }
			if ( mDisplay.startsWith("Alarm Canceled") ) {
				if(isDebug) log.debug("     Keypad Update: Alarm Canceled!")
				// after a panic alarm, a disarm clears the alarm condition, but an additional
				// disarm is required to return the panel to ready. This additional disarm can
				// not be sent until after the panel announces that the alarm has been cancelled
				composeDisarm()
				partitionDisarmed()
			}
			if ( mDisplay.startsWith("FAULT") ) {
				if(isDebug) log.debug("     Keypad Update: Zone " + mUserOrZone + " Tripped!")
				zoneOpen("000" + mUserOrZone.toString())
			}
			if ( mDisplay.startsWith("BYPAS") ) {
				if(isDebug) log.debug("     Keypad Update: Zone " + mUserOrZone + " Bypassed!")
			}
			if ( mDisplay.startsWith("CHECK") ) {
				// check is fired when the zone is tripped and it is Vista zone type 12 (24hr monitor)
                //log.info "Vista CHECK just ran.." 
				if(isDebug) log.debug("     Keypad Update: Zone " + mUserOrZone + " CHECK notification!")
				zoneOpen("000" + mUserOrZone.toString(), true)
			}
			if ( mDisplay.startsWith("TRBL") ) {
				if(isDebug) log.debug("     Keypad Update: Zone " + mUserOrZone + " TROUBLE/TAMPER notification!")
				zoneTamper("000" + mUserOrZone.toString())
			}
		}  
		if(message.take(3) == "%01") {
			if(isDebug) log.debug("Received %01 (Zone State Change) message")
			def ZoneState = Integer.parseInt(message[18..19] + message[16..17] + message[14..15] + message[12..13] + message[10..11] + message[8..9] + message[6..7] + message[4..5],16)
			//log.info "OLD Zone State Change: Zone String [" + ZoneState + "]" 
            //log.info "OLD ZoneMessage: $message"
			if(isDebug) log.debug("         Zone State Change: Zone String [" + ZoneState + "]")
			for (i = 1; i <65; i++) {
				if ( ZoneState & (2**(i-1)) ) {
					if(isDebug) log.debug ("     Zone State Change: Zone " + i + " Tripped!")
                    //log.info "OLD Zone State Change: Zone " + i + " Open"
					zoneOpen("000" + i.toString())
				} else {
                    //log.info "OLD Zone State Change: Zone " + i + " Closed"
					zoneClosed("000" + i.toString())
				}
			}
		}
		if(message.take(3) == "%02") {
			if(isDebug) log.debug("Received %02 (Partition State Change) message")
			def p1Status = Integer.parseInt(message[4..5])
			def p2Status = Integer.parseInt(message[6..7])
			def p3Status = Integer.parseInt(message[8..9])

			def partitionStates = [
				0:"Partition is not Used/Doesn't Exist",
				1:"Ready",
				2:"Ready to Arm (Zones are Bypasses)",
				3:"Not Ready",
				4:"Armed in Stay Mode",
				5:"Armed in Away Mode",
				6:"Armed Maximum (Armed in Stay Mode - Zero Entry Delay)",
				7:"Exit Delay",
				8:"Partition is in Alarm",
				9:"Alarm Has Occurred (Alarm in Memory)"
			]
			if(isDebug) log.debug("        Partition 1: " + partitionStates[p1Status])
			if(isDebug) log.debug("        Partition 2: " + partitionStates[p2Status])
			if(isDebug) log.debug("        Partition 3: " + partitionStates[p3Status])
			if (p1Status == 1) {
				partitionDisarmed()
			}
			if (p1Status == 2) {
				partitionReady()
			}
			if (p1Status == 3) {
				partitionNotReady()
			}
			if (p1Status == 4) {
				partitionArmedHome()
			}
			if (p1Status == 5) {
				partitionArmedAway()
			}
			if (p1Status == 6) {
				partitionArmedHome()
			}
			if (p1Status == 7) {
				exitDelay()
			}
			if (p1Status == 8) {
				partitionAlarm()
			}
		}
		if(message.take(3) == "%03") {
			if(isDebug) log.debug("Received %03 (Realtime CID Event) message")
			def mQualifier = message[4]
			def mCIDCode = message[5..7]
			def mPartition = message[8..9]
			def mZoneOrUser = message[10..12]
			if(isDebug) log.debug("  Q: [" + mQualifier +"] CID: [" + mCIDCode + "] Partition: [" + mPartition + "] Zone/User: [" + mZoneOrUser + "]")
			def (String mCIDCategory, String mCIDType, String mCIDDataType, String mCIDDescription, String mCIDDetail) = getCIDQualifier(mQualifier, mCIDCode)
			def mUser = ""
			def mUserName = ""
			def mZone = ""
			if (mCIDDataType == "Zone") {
				mZone = mZoneOrUser
			} else if (mCIDDataType == "User") {
				mUser = mZoneOrUser
//				mUserName = PANEL_CONFIG.USER_CODES[tonumber(mUserZone,10)] and PANEL_CONFIG.USER_CODES[tonumber(mUserZone,10)].label or ""
			}
			def CID_DATA = [
				"CID_Code": mCIDCode,
				"CID_Type": mCIDType,
				"CID_Category": mCIDCategory,
				"CID_Description": mCIDDescription,
				"CID_Details": mCIDDetail,
				"CID_Partition": mPartition,
				"CID_UserZone": mZoneOrUser,
				"CID_User": mUser,
				"CID_UserName": mUserName,
				"CID_Zone": mZone,
				"CID_Timestamp": now()
			]
			send_Event(name: "CID_Code", value: mCIDCode, isStateChange: true)
			send_Event(name: "CID_Type", value: mCIDType, isStateChange: true)
			send_Event(name: "CID_Partition", value: mPartition, isStateChange: true)
			send_Event(name: "CID_UserZone", value:mZoneOrUser, isStateChange: true)
			send_Event(name: "CID_DATA", value: CID_DATA, isStateChange: true)
			if(isDebug) log.debug("  CID_Code: [" + mCIDCode +"] CID_Type: [" + mCIDType + "] CID_Partition: [" + mPartition + "] Zone/User: [" + mZoneOrUser + "] CID_DATA: [" + CID_DATA.inspect() + "]")
		
		}
		if(message.take(3) == "%FF") {
			if(isDebug) log.debug("Received %FF (Zone Timer Dump) message")
		}
		if(message.take(2) == "^0") {
			if(isDebug) log.debug("Received command acknowledge message (${message})")
		}
	}
}

private getCIDQualifier(String Event, String Code) {
	if(isDebug) log.debug("getCIDQualifier:   Event: [" + Event +"]  Code: [" + Code + "]")
	def qCode = Code[0..1]
// 	["40"] = {"Open/Close","Opening","Closing"},
//	["400"] = {"Open/Close","User","The specified user has disarmed/armed the system"},
	def qCategory = CIDDescriptions[qCode] ? CIDDescriptions[qCode][0] : ""
	def qTypeE = CIDDescriptions[qCode] ? CIDDescriptions[qCode][1] : ""
	def qTypeR = CIDDescriptions[qCode] ? CIDDescriptions[qCode][2] : ""
	def qType = ""
	if (Event == "1") {
		qType = qTypeE ? qTypeE : "Event / Fault"
	} else {
		qType = qTypeR ? qTypeR : "Restoral"
	}
	def qDescription = CIDDescriptions[Code] ? CIDDescriptions[Code][0] : ""
	def qUZType = CIDDescriptions[Code] ? CIDDescriptions[Code][1] : ""
	def qDetail = CIDDescriptions[Code] ? CIDDescriptions[Code][2] : ""
	if(isDebug) log.debug("getCIDQualifier:   qCategory: [" + qCategory +"]  qType: [" + qType + "]  qUZType: [" + qUZType + "]  qDescription: [" + qDescription + "]  qDetail: [" + qDetail + "]")
	return [qCategory, qType, qUZType, qDescription, qDetail]
}

private sendTelnetLogin(){
	if(isDebug) log.debug("sendTelnetLogin: ${passwd}")
	def cmdToSend =  "${passwd}"
	if(PanelType as int == 0) {
		cmdToSend =  tpiCommands["Login"] + "${passwd}"
		def cmdArray = cmdToSend.toCharArray()
		def cmdSum = 0
		cmdArray.each { cmdSum += (int)it }
		def chkSumStr = DataType.pack(cmdSum, 0x08)
		if(chkSumStr.length() > 2) chkSumStr = chkSumStr[-2..-1]
		cmdToSend += chkSumStr
	}
	cmdToSend = cmdToSend + "\r\n"
	sendHubCommand(new hubitat.device.HubAction(cmdToSend, hubitat.device.Protocol.TELNET))
}

private sendTelnetCommand(String s) {
	if(PanelType as int == 0) {
		s = generateChksum(s)
	}
	if(isDebug) log.debug("sendTelnetCommand $s")
	return new hubitat.device.HubAction(s, hubitat.device.Protocol.TELNET)
}

private sendProgrammingMessage(String s){
	s = generateChksum(s)
	if(isDebug) log.debug("sendProgrammingMessage: ${s}")
	def hubaction = new hubitat.device.HubAction(s, hubitat.device.Protocol.TELNET) 
	sendHubCommand(hubaction);
}

def telnetConnection(){
	sendEvent(name: "networkStatus", value: "offline", isStateChange: true)
	telnetClose()
	pauseExecution(5000)
	try {
		//open telnet connection
		telnetConnect([termChars:[13,10]], ip, 4025, null, null)
		runOnce(new Date(now() + 10000), StatusReport)
	} catch(e) {
		logError("initialize error: ${e.message}")
	}
}

def telnetStatus(String status){
	logError("telnetStatus- error: ${status}")
	if (status != "receive error: Stream is closed"){
		getReTry(true)
		logError("Telnet connection dropped...")
	} else {
		logError("Telnet is restarting...")
	}
	runOnce(new Date(now() + 10000), telnetConnection)
}

/***********************************************************************************************************************
*   Helpers
*/
private isBitSet(byte b, int bit) {
   return (b & (1 << bit)) != 0;
}

private checkTimeStamp(message){
//	if (message =~ timeStampPattern){
		if((message.length() > 9) && (message[2] == ':') && (message[8] == ' ')) {
		if(!state.timeStampOn) {
			if(isDebug) log.debug("Time Stamp Found")
			state.timeStampOn = true;
			}
		//message = message.replaceAll(timeStampPattern, "")
		message = message.substring(9)
		//if(isDebug) log.debug("Time Stamp Remove ${message}")
	} else {
		if(state.timeStampOn) {
			state.timeStampOn = false;
			if(isDebug) log.debug("Time Stamp Not Found")
			}
	}
	return message
}

private deleteUserCodeSend(){
	if(isDebug) log.debug("deleteUserCodeSend")
	state.programmingMode = DELETEUSERCOMPLETE
	pauseExecution(3000)
	composeKeyStrokes("#")
}

private deleteUserCodeComplete(){
	if(isDebug) log.debug("deleteUserCodeComplete")
	state.programmingMode = ""
	def storedCodes = new groovy.json.JsonSlurper().parseText(device.currentValue("Codes"))
	assert storedCodes instanceof Map

	if(isDebug) log.debug("storedCodes: ${storedCodes}")
	def selectedCode = storedCodes[state.newCodePosition]

	if(isDebug) log.debug("Selected Code: ${selectedCode}")
	storedCodes.remove(state.newCodePosition.toString())

	def json = new groovy.json.JsonBuilder(storedCodes)
	send_Event(name:"Codes", value: json, displayed:true, isStateChange: true)
	state.newCode = ""
	state.newCodePosition = ""
	state.name = ""

}

private entryDelay(){
	if(isDebug) log.debug("entryDelay")
	send_Event(name:"Status", value: ENTRYDELAY)
	state.armState = "intrusion"
	parent.speakEntryDelay()
}

private exitDelay(){
	if(isDebug) log.debug("exitDelay")
	send_Event(name:"Status", value: EXITDELAY)
	parent.speakExitDelay()
}

private generateChksum(String cmdToSend){
	if(isDebug) log.debug("generateChksum")
//	def cmdArray = cmdToSend.toCharArray()
//	if(isDebug) log.debug("cmdArray: ${cmdArray}")
	int cmdSum = 0
	cmdToSend.each { cmdSum += (int)it }
	cmdSum &= 0xff
//	def chkSumStr = DataType.pack(cmdSum, 0x08)
	def chkSumStr = String.format("%02X", cmdSum)
//	if(chkSumStr.length() > 2) chkSumStr = chkSumStr[-2..-1]
	cmdToSend += chkSumStr
	return cmdToSend
}

private getReTry(Boolean inc){
	def reTry = (state.reTryCount ?: 0).toInteger()
	if (inc) reTry++
	state.reTryCount = reTry
	return reTry
}

//private ifDebug(msg){
//	parent.ifDebug('Connection Driver: ' + (msg ?: ""))
//}

private loginPrompt(){
	if(isDebug) log.debug("loginPrompt")
	send_Event(name: "networkStatus", value: "online")
	if(isDebug) log.debug("Connection to Envisalink established")
	state.reTryCount = 0
	sendTelnetLogin()
	if(isDebug) log.debug(LOGINPROMPT)
}

private keypadLockout(){
	if(isDebug) log.debug("keypadLockout")
	send_Event(name:"Status", value: KEYPADLOCKOUT)
}

private keypadLedState(ledState){
	if(isDebug) log.debug("keypadLedState ${ledState}")
	 if (ledState == "82" && state.programmingMode == SETUSERCODEINITIALIZE){
		if(isDebug) log.debug("${KEYPADLEDSTATE} ${state.programmingMode}")
		state.programmingMode = SETUSERCODESEND
		composeKeyStrokes(state.newCodePosition + state.newCode)
	}

	if (ledState == "82" && state.programmingMode == DELETEUSERCODEINITIALIZE){
		if(isDebug) log.debug("${KEYPADLEDSTATE} ${state.programmingMode}")
		state.programmingMode = DELETEUSERCODE
		composeKeyStrokes(state.newCodePosition + "*")
	}

	def ledBinary = Integer.toBinaryString(hubitat.helper.HexUtils.hexStringToInt(ledState))
	def paddedBinary = ledBinary.padLeft(8, "0")
	if(isDebug) log.debug("${paddedBinary}")

	if (paddedBinary.substring(7,8) == "0"){
		if(isDebug) log.debug("Partition Ready LED Off")
	}

	if (paddedBinary.substring(7,8) == "1"){
		if(isDebug) log.debug("Partition Ready LED On")
	}
}

private logError(msg){
	parent.logError('Connection Driver: ' + msg)
}

private partitionReady(){
		if(isDebug) log.debug("partitionReady")
    def st = device.currentValue("Status")
    def sw = device.currentValue("switch")
    def co = device.currentValue("contact")
    if (device.currentValue("Status") != PARTITIONREADY) { send_Event(name:"Status", value: PARTITIONREADY, isStateChange: true) }  
	if (device.currentValue("switch") != "off") { send_Event(name: "switch", value: "off", isStateChange: true) }
	if (device.currentValue("contact") != "closed") { send_Event(name:"contact", value: "closed", isStateChange: true) }
    //log.info "partitionReady() state.armState = $state.armState: Status: $st switch: $sw contact: $co"
    state.armState = "disarmed"
	state.newCode = ""
	state.newCodePosition = ""
	state.newName = ""
	state.programmingMode = ""
	if (device.currentValue("tamper") != "clear") {
		send_Event(name:"tamper", value: "clear", displayed:true, isStateChange: true)
		send_Event(name:"tamperZone", value: "", displayed:true, isStateChange: true)
	}

}

private partitionNotReady(){
	if(isDebug) log.debug("partitionNotReady")
    //def st = device.currentValue("Status")
    //def sw = device.currentValue("switch")
    //def co = device.currentValue("contact")
	if (device.currentValue("Status") != PARTITIONNOTREADY) { send_Event(name:"Status", value: PARTITIONNOTREADY, isStateChange: true) }
	if (device.currentValue("contact") != "closed") { send_Event(name:"contact", value: "closed", isStateChange: true) }
    //log.info "partitionNotReady() state.armState = $state.armState: Status: $st switch: $sw contact: $co"

}

private partitionReadyForForcedArmEnabled(){
	 if(isDebug) log.debug("partitionReadyForForcedArmEnabled")
	if (!disablePartReady && (device.currentValue("Status") != PARTITIONNOTREADYFORCEARMINGENABLED)) { send_Event(name:"Status", value: PARTITIONNOTREADYFORCEARMINGENABLED, isStateChange: true) }
	if (device.currentValue("contact") != "closed") { send_Event(name:"contact", value: "closed") }
}

private partitionAlarm(){
	if(isDebug) log.debug("partitionAlarm")
	if (device.currentValue("Status") != PARTITIONINALARM) { send_Event(name:"Status", value: PARTITIONINALARM, isStateChange: true) }
	if (device.currentValue("contact") != "open") { send_Event(name:"contact", value: "open", isStateChange: true) }
	state.armState = "alarming"
	parent.speakAlarm()
}

private partitionDisarmed(){
	if(isDebug) log.debug("partitionDisarmed")
    //def st = device.currentValue("Status")
    //def sw = device.currentValue("switch")
    //def co = device.currentValue("contact")
	//if ((device.currentValue("Status") != PARTITIONDISARMED) && (device.currentValue("Status") != PARTITIONNOTREADY)) { 
    if ((device.currentValue("Status") != PARTITIONDISARMED) && (state.armState != "disarmed")) { 
            send_Event(name:"Status", value: PARTITIONDISARMED, isStateChange: true) 
            //log.info "partitionDisarmed() send Event Status = Disarmed"
    }
  if (device.currentValue("Bypassed Zones") != "none") { send_Event(name:"Bypassed Zones", value: "none", isStateChange: true) }
  if (device.currentValue("switch") != "off") { send_Event(name:"switch", value: "off", isStateChange: true) }
	if (device.currentValue("contact") != "closed") { send_Event(name:"contact", value: "closed", isStateChange: true) }
    //log.info "partitionDisarmed() state.armState = $state.armState: Status: $st switch: $sw contact: $co"
    // partitionDisarmed() state.armState = arming_home: Status: Ready switch: off contact: closed
    if ((state.armState != "disarmed")) { // && (state.alarmState != "arming_home") && (state.alarmState != "armed_home")) {
		if(isDebug) log.debug("disarming")
		state.armState = "disarmed"
		parent.unlockIt()
		parent.switchItDisarmed()
		parent.speakDisarmed()

		if (location.hsmStatus != "disarmed")
		{
            //log.info "partitionDisarmed: location.hsmStatus= $location.hsmStatus setting hsmSetArm=disarm"
			sendLocationEvent(name: "hsmSetArm", value: "disarm"); if(isDebug) log.debug("sendLocationEvent(name:\"hsmSetArm\", value:\"disarm\")")
		}
	}
	if (device.currentValue("CID_Code") != "") {
		send_Event(name: "CID_Code", value: "", isStateChange: true)
		send_Event(name: "CID_Type", value: "", isStateChange: true)
		send_Event(name: "CID_Partition", value: "", isStateChange: true)
		send_Event(name: "CID_UserZone", value: "", isStateChange: true)
		send_Event(name: "CID_DATA", value: "", isStateChange: true)
	}
}

private partitionArmedAway(){
	if(isDebug) log.debug("partitionArmedAway")
	if (device.currentValue("Status") != PARTITIONARMEDAWAY) { send_Event(name:"Status", value: PARTITIONARMEDAWAY, isStateChange: true) }
	if (device.currentValue("switch") != "on") { send_Event(name:"switch", value: "on", isStateChange: true) }
	if (device.currentValue("contact") != "closed") { send_Event(name:"contact", value: "closed", isStateChange: true) }
//	if (state.armState.contains("home")){
//		systemArmedHome()
//	} else {
		systemArmed()
//	}
}

private partitionArmedHome(){
	if(isDebug) log.debug("partitionArmedHome")
	if (device.currentValue("Status") != PARTITIONARMEDHOME) { send_Event(name:"Status", value: PARTITIONARMEDHOME, isStateChange: true) }
	if (device.currentValue("switch") != "on") { send_Event(name:"switch", value: "on", isStateChange: true) }
	if (device.currentValue("contact") != "closed") { send_Event(name:"contact", value: "closed", isStateChange: true) }
//	if (state.armState.contains("home")){
		systemArmedHome()
//	} else {
//		systemArmed()
//	}
}

private partitionArmedBypass(String status){
	if(isDebug) log.debug("partitionArmedBypass")
	if (device.currentValue("Status") != status) { send_Event(name:Status, value: status, isStateChange: true) }
	if (device.currentValue("switch") != "on") { send_Event(name:"switch", value: "on", isStateChange: true) }
	if (device.currentValue("contact") != "closed") { send_Event(name:"contact", value: "closed", isStateChange: true) }
//	if (state.armState.contains("home")){
		systemArmedHome()
//	} else {
		systemArmed()
//	}
}

// CHANGED THIS TO READ A DIFFERENT KEYPAD STATE FOR NIGHT MODE
private partitionArmedNight(){
	if(isDebug) log.debug("partitionArmedNight")
	if (device.currentValue("Status") != USERCLOSING) { send_Event(name:"Status", value: PARTITIONARMEDNIGHT, isStateChange: true) }
	if (device.currentValue("switch") != "on") { send_Event(name:"switch", value: "on", isStateChange: true) }
	if (device.currentValue("contact") != "closed") { send_Event(name:"contact", value: "closed", isStateChange: true) }
	systemArmedNight()
}

private parseUser(message){
	if(isDebug) log.debug("parseUser")
	def length = message.size()
	def userPosition = message.substring(6,length)
	if(isDebug) log.debug("${USEROPENING} - ${userPosition}" )

	send_Event(name:"LastUsedCodePosition", value: userPosition)

	def storedCodes = new groovy.json.JsonSlurper().parseText(device.currentValue("Codes"))
	assert storedCodes instanceof Map

	if(isDebug) log.debug("storedCodes: ${storedCodes}")
	def selectedCode = storedCodes[userPosition.toString()]
	assert selectedCode instanceof Map

	if(isDebug) log.debug("Selected Code: ${selectedCode}")
	if(isDebug) log.debug("Selected Code: ${selectedCode.name}")

	if (selectedCode?.name){
		send_Event(name:"LastUsedCodeName", value: selectedCode.name)
	}

	return userPosition
}

private preProcessMessage(message){
	//if(isDebug) log.debug("Preprocessing Message")
 	message = checkTimeStamp(message)
	//strip checksum
	message = message.take(message.size() - 2)
	//if(isDebug) log.debug("Stripping Checksum: ${message}")
	return message
}

private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}

private setUserCodeSend(){
	if(isDebug) log.debug("setUserCodeSend")
	state.programmingMode = SETUSERCODECOMPLETE
	if(isDebug) log.debug("COMMAND ACCEPTED")
	pauseExecution(3000)
	composeKeyStrokes("#")
}

private setUserCodeComplete(){
	if(isDebug) log.debug("setUserCodeSend")
	state.programmingMode = ""
	def storedCodes = new groovy.json.JsonSlurper().parseText(device.currentValue("Codes"))
	assert storedCodes instanceof Map
	def newCodeMap = [name: (state.newName), code: (state.newCode.toString())]
	storedCodes.put((state.newCodePosition.toString()), (newCodeMap))
	if(isDebug) log.debug("storedCodes: ${storedCodes}")
	def json = new groovy.json.JsonBuilder(storedCodes)
	send_Event(name:"Codes", value: json, displayed:true, isStateChange: true)
	state.newCode = ""
	state.newCodePosition = ""
	state.name = ""
}

private systemArmed(){
	if (state.armState != "armed_away"){
		if(isDebug) log.debug("Armed Away")
		state.armState = "armed_away"
		parent.lockIt()
		parent.switchItArmed()
		parent.speakArmed()

		if (location.hsmStatus != "armedAway")
		{
			sendLocationEvent(name: "hsmSetArm", value: "armAway"); if(isDebug) log.debug("sendLocationEvent(name:\"hsmSetArm\", value:\"armAway\")")
		}
	}
}

private systemArmedHome(){
	if (state.armState != "armed_home"){
		if(isDebug) log.debug("Armed Home")
		state.armState = "armed_home"
		parent.lockIt()
		parent.switchItArmed()
		parent.speakArmed()

		if (location.hsmStatus != "armedHome")
		{
            //log.info "systemArmedHome() hsmStatus=$location.hsmStatus  setting hsmSetArm=armHome"
			sendLocationEvent(name: "hsmSetArm", value: "armHome"); if(isDebug) log.debug("sendLocationEvent(name:\"hsmSetArm\", value:\"armHome\")")
		}
	}
}

private systemArmedNight(){
	if (state.armState != "armed_night"){
		if(isDebug) log.debug("Armed Night")
		state.armState = "armed_night"
		parent.lockIt()
		parent.switchItArmed()
		parent.speakArmed()

		if (location.hsmStatus != "armedNight")
		{
			sendLocationEvent(name: "hsmSetArm", value: "armNight"); if(isDebug) log.debug("sendLocationEvent(name:\"hsmSetArm\", value:\"armNight\")")
		}
	}
}

private systemError(message){
	def errorcode = message[3..5] as int
	logError("System Error: ${errorcode} - ${errorCodes[(errorcode)]}")

	if (errorCodes[(message)] == "Receive Buffer Overrun"){
		composeKeyStrokes("#")
	}
}


private getZoneDevice(zoneId) {
 	def zoneDevice = getChildDevices().find{
 			it.deviceNetworkId[-3..-1] == zoneId
			}
	return zoneDevice
}

private zoneOpen(message, Boolean autoReset = false){
  def zone = message[3..5]
	def zoneDevice = getZoneDevice(zone)
	if (zoneDevice){

		if(isDebug) log.debug(zoneDevice)
		if(zoneDevice.hasAttribute ("contact")) {
            if (zoneDevice.latestValue("contact") != "open") {
                if(isDebug) log.debug("Contact $zone Open")
			    zoneDevice.open()
			    if ((PanelType as int == 1) && autoReset) { zoneDevice.unschedule(); zoneDevice.runIn(60,"close") }
            }
		} else if(zoneDevice.hasAttribute ("motion")) {
          if (zoneDevice.latestValue("motion") != "active") {
			    if(isDebug) log.debug("Motion $zone Active")
			    zoneDevice.active()
			    if ((PanelType as int == 1) && autoReset) { zoneDevice.unschedule(); zoneDevice.runIn(245,"close") }
            }
		} else if(zoneDevice.hasAttribute ("carbonMonoxide")) {
          if (zoneDevice.latestValue("carbonMonoxide") == "clear") {
			    if(isDebug) log.debug("CO Detector $zone Active")
			    zoneDevice.detected()
			    if ((PanelType as int == 1) && autoReset) { zoneDevice.unschedule(); zoneDevice.runIn(60,"clear") }
            }
		} else if(zoneDevice.hasAttribute ("smoke"))  {
          if (zoneDevice.latestValue("smoke") == "clear") {
			    if(isDebug) log.debug("Smoke Detector $zone Active")
			    zoneDevice.detected()
			    if ((PanelType as int == 1) && autoReset) { zoneDevice.unschedule(); zoneDevice.runIn(60,"clear") }
            }
		} else if(zoneDevice.hasAttribute ("shock"))  {
          if (zoneDevice.latestValue("shock") == "clear") {
			    if(isDebug) log.debug("GlassBreak Detector $zone Active")
			    zoneDevice.detected()
			    if ((PanelType as int == 1) && autoReset) { zoneDevice.unschedule(); zoneDevice.runIn(60,"clear") }
            }
		}
	}

}

private zoneClosed(message){
  def zone = message[3..5]
	def zoneDevice = getZoneDevice(zone)
	if (zoneDevice){
		if(isDebug) log.debug(zoneDevice)
		if(zoneDevice.hasAttribute ("contact")) {
            if (zoneDevice.latestValue("contact") != "closed") {
			    if(isDebug) log.debug("Contact Closed")
			    zoneDevice.close()
                if ((PanelType as int == 1) && autoReset) zoneDevice.unschedule()
            }
		} else if(zoneDevice.hasAttribute ("motion")) {
            if (zoneDevice.latestValue("motion") != "inactive") {
			    if(isDebug) log.debug("Motion Inactive")
			    zoneDevice.inactive()
			    if ((PanelType as int == 1) && autoReset) zoneDevice.unschedule()
            }
		} else if(zoneDevice.hasAttribute ("carbonMonoxide")) {
          if (zoneDevice.latestValue("carbonMonoxide") != "clear") {
			    if(isDebug) log.debug("CO Detector $zone Active")
			    zoneDevice.clear()
			    if ((PanelType as int == 1) && autoReset) zoneDevice.unschedule()
            }
		} else if(zoneDevice.hasAttribute ("smoke"))  {
          if (zoneDevice.latestValue("smoke") != "clear") {
			    if(isDebug) log.debug("Smoke Detector $zone Active")
			    zoneDevice.clear()
			    if ((PanelType as int == 1) && autoReset) zoneDevice.unschedule()
            }
		} else if(zoneDevice.hasAttribute ("shock"))  {
          if (zoneDevice.latestValue("shock") != "clear") {
			    if(isDebug) log.debug("GlassBreak Detector $zone Active")
			    zoneDevice.clear()
			    if ((PanelType as int == 1) && autoReset) zoneDevice.unschedule()
            }
		}
	}
}

private zonesBypassed(String zones) {
		int j = 0
		String s = ""
		zones.each { int c = hexdig[it]
	int i = j	
		while (c != 0) {
			++i
			if(c & 1)
			s += String.format("%02d ", i)
			c >>= 1
			}
			j += 4
			}
		if(s.length() == 0) s = "none"
		send_Event(name:"Bypassed Zones", value : s)
}
	
private zoneTamper(message){
	def zone = message[3..5]
	def zoneDevice = getZoneDevice(zone)
	if(isDebug) log.debug(zoneDevice)
	if (zoneDevice){
		if (device.currentValue("tamper") != "detected") {
			send_Event(name:"tamper", value: "detected", displayed:true, isStateChange: true)
			send_Event(name:"tamperZone", value: zone, displayed:true, isStateChange: true)
		}
	}
}

private send_Event(evnt) {
	if(isDebug) log.debug("sendEvent(${evnt})")
	sendEvent(evnt)
}
/***********************************************************************************************************************
*   Variables
*/

@Field String timeStampPattern = ~/^\d{2}:\d{2}:\d{2} /
@Field final Map hexdig = [
	'0'	:	0,
	'1'	:	1,
	'2'	:	2,
	'3'	:	3,
	'4'	:	4,
	'5'	:	5,
	'6'	:	6,
	'7'	:	7,
	'8'	:	8,
	'9'	:	9,
	'A'	:	10,
	'B'	:	11,
	'C'	:	12,
	'D'	:	13,
	'E'	:	14,
	'F'	:15
	]
@Field final Map 	errorCodes = [
	0: 	"No Error",
	1: 	"Receive Buffer Overrun",
	2: 	"Receive Buffer Overflow",
	3: 	"Transmit Buffer Overflow",
	10: "Keybus Transmit Buffer Overrun",
	11: "Keybus Transmit Time Timeout",
	12: "Keybus Transmit Mode Timeout",
	13: "Keybus Transmit Keystring Timeout",
	14: "Keybus Interface Not Functioning (the TPI cannot communicate with the security system)",
	15: "Keybus Busy (Attempting to Disarm or Arm with user code)",
	16: "Keybus Busy - Lockout (The panel is currently in Keypad Lockout - too many disarm attempts)",
	17: "Keybus Busy - Installers Mode (Panel is in installers mode, most functions are unavailable)",
	18: "Keybus Busy - General Busy (The requested partition is busy)",
	20: "API Command Syntax Error",
	21: "API Command Partition Error (Requested Partition is out of bounds)",
	22: "API Command Not Supported",
	23: "API System Not Armed (sent in response to a disarm command)",
	24: "API System Not Ready to Arm (system is either not-secure, in exit-delay, or already armed",
	25: "API Command Invalid Length",
	26: "API User Code not Required",
	27: "API Invalid Characters in Command (no alpha characters are allowed except for checksum"
]

@Field static final String COMMANDACCEPTED = "Command Accepted"
@Field static final String KEYPADLEDSTATE = "Keypad LED State"
@Field static final String PROGRAMMINGON = "Keypad LED State ON"
@Field static final String PROGRAMMINGOFF = "Keypad LED State OFF"
@Field static final String COMMANDERROR = "Command Error"
@Field static final String SYSTEMERROR = "System Error"
@Field static final String LOGININTERACTION = "Login Interaction"
@Field static final String LEDFLASHSTATE = "Keypad LED FLASH state"
@Field static final String TIMEDATEBROADCAST = "Time - Date Broadcast"
@Field static final String RINGDETECTED = "Ring Detected"
@Field static final String INDOORTEMPBROADCAST = "Indoor Temp Broadcast"
@Field static final String OUTDOORTEMPBROADCAST = "Outdoor Temperature Broadcast"
@Field static final String ZONEALARM = "Zone Alarm"
@Field static final String ZONE1ALARM = "Zone 1 Alarm"
@Field static final String ZONE2ALARM = "Zone 2 Alarm"
@Field static final String ZONE3ALARM = "Zone 3 Alarm"
@Field static final String ZONE4ALARM = "Zone 4 Alarm"
@Field static final String ZONE5ALARM = "Zone 5 Alarm"
@Field static final String ZONE6ALARM = "Zone 6 Alarm"
@Field static final String ZONE7ALARM = "Zone 7 Alarm"
@Field static final String ZONE8ALARM = "Zone 8 Alarm"
@Field static final String ZONEALARMRESTORE = "Zone Alarm Restored"
@Field static final String ZONE1ALARMRESTORE = "Zone 1 Alarm Restored"
@Field static final String ZONE2ALARMRESTORE = "Zone 2 Alarm Restored"
@Field static final String ZONE3ALARMRESTORE = "Zone 3 Alarm Restored"
@Field static final String ZONE4ALARMRESTORE = "Zone 4 Alarm Restored"
@Field static final String ZONE5ALARMRESTORE = "Zone 5 Alarm Restored"
@Field static final String ZONE6ALARMRESTORE = "Zone 6 Alarm Restored"
@Field static final String ZONE7ALARMRESTORE = "Zone 7 Alarm Restored"
@Field static final String ZONE8ALARMRESTORE = "Zone 8 Alarm Restored"
@Field static final String ZONETAMPER = "Zone Tamper"
@Field static final String ZONETAMPERRESTORE = "Zone Tamper RestoreD"
@Field static final String ZONEFAULT = "Zone Fault"
@Field static final String ZONEFAULTRESTORED = "Zone Fault RestoreD"
@Field static final String ZONEOPEN = "Zone Open"
@Field static final String ZONERESTORED = "Zone Restored"
@Field static final String TIMERDUMP = "Envisalink Zone Timer Dump"
@Field static final String BYPASSEDZONEBITFIELDDUMP = "Bypassed Zones Bitfield Dump"
@Field static final String DURESSALARM = "Duress Alarm"
@Field static final String FKEYALARM = "Fire Key Alarm"
@Field static final String FKEYRESTORED = "Fire Key Restored"
@Field static final String AKEYALARM = "Aux Key Alarm"
@Field static final String AKEYRESTORED = "Aux Key Restored"
@Field static final String PKEYALARM = "Panic Key Alarm"
@Field static final String PKEYRESTORED = "Panic Key Restored"
@Field static final String TWOWIRESMOKEAUXALARM = "2-Wire Smoke Aux Alarm"
@Field static final String TWOWIRESMOKEAUXRESTORED = "2-Wire Smoke Aux Restored"
@Field static final String PARTITIONREADY = "Ready"
@Field static final String PARTITIONNOTREADY = "Not Ready"
@Field static final String PARTITIONARMEDSTATE = "Armed State"
@Field static final String PARTITIONARMEDAWAY = "Armed Away"
@Field static final String PARTITIONARMEDHOME = "Armed Home"
@Field static final String PARTITIONARMEDNIGHT = "Armed Night"
@Field static final String PARTITIONNOTREADYFORCEARMINGENABLED = "Partition Ready - Force Arming Enabled"
@Field static final String PARTITIONINALARM = "In Alarm"
@Field static final String PARTITIONDISARMED = "Disarmed"
@Field static final String EXITDELAY = "Exit Delay in Progress"
@Field static final String ENTRYDELAY = "Entry Delay in Progress"
@Field static final String KEYPADLOCKOUT = "Keypad Lock-out"
@Field static final String PARTITIONFAILEDTOARM = "Partition Failed to Arm"
@Field static final String PFMOUTPUT = "PFM Output is in Progress"
@Field static final String CHIMEENABLED = "Chime Enabled"
@Field static final String CHIMEDISABLED = "Chime Disabled"
@Field static final String INVALIDACCESSCODE = "Invalid Access Code"
@Field static final String FUNCTIONNOTAVAILABLE = "Function Not Available"
@Field static final String FAILURETOARM = "Failure to Arm"
@Field static final String PARTITIONISBUSY = "Partition is busy"
@Field static final String SYSTEMARMINGPROGRESS = "System Arming Progress"
@Field static final String SYSTEMININSTALLERSMODE = "System in Installers Mode"
@Field static final String USERCLOSING = "User Closing"
@Field static final String SPECIALCLOSING = "Special Closing"
@Field static final String PARTIALCLOSING = "Partial Closing"
@Field static final String USEROPENING = "User Opening"
@Field static final String SPECIALOPENING = "Special Opening"
@Field static final String PANELBATTERYTROUBLE = "Panel Battery Trouble"
@Field static final String PANELBATTERYTROUBLERESTORED = "Panel Battery Trouble Restored"
@Field static final String PANELACTROUBLE = "Panel AC Trouble"
@Field static final String PANELACTROUBLERESTORED = "Panel AC Trouble Restored"
@Field static final String SYSTEMBELLTROUBLE = "System Bell Trouble"
@Field static final String SYSTEMBELLTROUBLERESTORED = "System Bell Trouble Restored"
@Field static final String FTCTROUBLE = "FTC Trouble"
@Field static final String FTCTROUBLERESTORED = "FTC Trouble Restored"
@Field static final String BUFFERNEARFULL = "Buffer Near Full"
@Field static final String GENERALSYSTEMTAMPER = "General System Tamper"
@Field static final String GENERALSYSTEMTAMPERRESTORED = "General System Tamper Restored"
@Field static final String TROUBLELEDON = "Trouble LED ON"
@Field static final String TROUBLELEDOFF = "Trouble LED OFF"
@Field static final String FIRETROUBLEALARM = "Fire Trouble Alarm"
@Field static final String FIRETROUBLEALARMRESTORED = "Fire Trouble Alarm Restored"
@Field static final String VERBOSETROUBLESTATUS = "Verbose Trouble Status"
@Field static final String CODEREQUIRED = "Code Required"
@Field static final String COMMANDOUTPUTPRESSED = "Command Output Pressed"
@Field static final String MASTERCODEREQUIRED = "Master Code Required"
@Field static final String INSTALLERSCODEREQUIRED = "Installers Code Required"
@Field static final String PASSWORDINCORRECT = "TPI Login password provided is incorrect"
@Field static final String LOGINSUCCESSFUL = "Login Successful"
@Field static final String LOGINTIMEOUT = "Time out.  You did not send password within 10 seconds"
@Field static final String APIFAULT = "API Command Syntax Error"
@Field static final String LOGINPROMPT = "Send Login"

@Field static final String SETUSERCODEINITIALIZE = "SETUSERCODEINITIALIZE"
@Field static final String SETUSERCODESEND = "SETUSERCODESEND"
@Field static final String SETUSERCODECOMPLETE = "SETUSERCODECOMPLETE"

@Field static final String DELETEUSERCODEINITIALIZE = "DELETEUSERCODEINITIALIZE"
@Field static final String DELETEUSERCODE = "DELETEUSERCODE"
@Field static final String DELETEUSERCOMPLETE = "DELETEUSERCOMPLETE"

@Field final Map 	tpiResponses = [
    500: COMMANDACCEPTED,
    501: COMMANDERROR,
    502: SYSTEMERROR,
	  502020: APIFAULT,
    505: LOGININTERACTION,
    5050: PASSWORDINCORRECT,
    5051: LOGINSUCCESSFUL,
    5052: LOGINTIMEOUT,
    5053: LOGINPROMPT,
    510: KEYPADLEDSTATE,
    51091: PROGRAMMINGON,
    51090: PROGRAMMINGOFF,
    511: LEDFLASHSTATE,
    550: TIMEDATEBROADCAST,
    560: RINGDETECTED,
    561: INDOORTEMPBROADCAST,
    562: OUTDOORTEMPBROADCAST,
    601: ZONEALARM,
	6011001: ZONE1ALARM,
	6011002: ZONE2ALARM,
	6011003: ZONE3ALARM,
	6011004: ZONE4ALARM,
	6011005: ZONE5ALARM,
	6011006: ZONE6ALARM,
	6011007: ZONE7ALARM,
	6011008: ZONE8ALARM,
    602: ZONEALARMRESTORE,
	6021001: ZONE1ALARMRESTORE,
	6021002: ZONE2ALARMRESTORE,
	6021003: ZONE3ALARMRESTORE,
	6021004: ZONE4ALARMRESTORE,
	6021005: ZONE5ALARMRESTORE,
	6021006: ZONE6ALARMRESTORE,
	6021007: ZONE7ALARMRESTORE,
	6021008: ZONE8ALARMRESTORE,
    603: ZONETAMPER,
	604: ZONETAMPERRESTORE,
    605: ZONEFAULT,
    606: ZONEFAULTRESTORED,
    609: ZONEOPEN,
    610: ZONERESTORED,
    615: TIMERDUMP,
    616: BYPASSEDZONEBITFIELDDUMP,
    620: DURESSALARM,
    621: FKEYALARM,
    622: FKEYRESTORED,
    623: AKEYALARM,
    624: AKEYRESTORED,
    625: PKEYALARM,
    626: PKEYRESTORED,
    631: TWOWIRESMOKEAUXALARM,
    632: TWOWIRESMOKEAUXRESTORED,
    650: PARTITIONREADY,
    651: PARTITIONNOTREADY,
	652: PARTITIONARMEDSTATE,
    65210: PARTITIONARMEDAWAY,
	65211: PARTITIONARMEDHOME,
    653: PARTITIONNOTREADYFORCEARMINGENABLED,
    654: PARTITIONINALARM,
    655: PARTITIONDISARMED,
    656: EXITDELAY,
   	657: ENTRYDELAY,
    658: KEYPADLOCKOUT,
    659: PARTITIONFAILEDTOARM,
    660: PFMOUTPUT,
   	663: CHIMEENABLED,
    664: CHIMEDISABLED,
    670: INVALIDACCESSCODE,
    671: FUNCTIONNOTAVAILABLE,
    672: FAILURETOARM,
    673: PARTITIONISBUSY,
    674: SYSTEMARMINGPROGRESS,
    680: SYSTEMININSTALLERSMODE,
    700: USERCLOSING,
    701: SPECIALCLOSING,
    702: PARTIALCLOSING,
   	750: USEROPENING,
    751: SPECIALOPENING,
    800: PANELBATTERYTROUBLE,
    801: PANELBATTERYTROUBLERESTORED,
    802: PANELACTROUBLE,
   	803: PANELACTROUBLERESTORED,
    806: SYSTEMBELLTROUBLE,
    807: SYSTEMBELLTROUBLERESTORED,
    814: FTCTROUBLE,
    815: FTCTROUBLERESTORED,
    816: BUFFERNEARFULL,
    829: GENERALSYSTEMTAMPER,
    830: GENERALSYSTEMTAMPERRESTORED,
    840: TROUBLELEDON,
    841: TROUBLELEDOFF,
    842: FIRETROUBLEALARM,
    843: FIRETROUBLEALARMRESTORED,
    849: VERBOSETROUBLESTATUS,
    900: CODEREQUIRED,
    912: COMMANDOUTPUTPRESSED,
    921: MASTERCODEREQUIRED,
    922: INSTALLERSCODEREQUIRED
]
@Field final Map	tpiCommands = [
		Login: "005",
		Poll: "000",
		TimeStampOn: "0550",
		TimeStampOff: "0551",
		StatusReport: "001",
		Disarm: "0401",
		ToggleChime: "0711*4",
		BypassZone: "0711*1",
		ArmHome: "0311",
		ArmAway: "0301",
        //ArmNight: "0711*9",
        ArmNight: "0321",
	    ArmAwayZeroEntry: "0321",
	    PanicFire: "0601",
	    PanicAmbulance: "0602",
	    PanicPolice: "0603",
        CodeSend: "200",
        EnterUserCodeProgramming: "0721", 
        SendKeyStroke: "0711"
]

@Field final Map	CIDDescriptions = [
	"10" : ["Medical Alarm","ALARM",""],
	"100" : ["Medical","Zone","A non-specific medical condition exists"],
	"101" : ["Personal Emergency","Zone","Emergency Assistance request"],
	"102" : ["Fail to report in","Zone","A user has failed to activate a monitoring device"],
	"11" : ["Fire Alarm","ALARM",""],
	"110" : ["Fire","Zone","A non-specific fire alarm condition exists"],
	"111" : ["Smoke","Zone","An alarm has been triggered by a smoke detector"],
	"112" : ["Combustion","Zone","An alarm has been triggered by a combustion detector"],
	"113" : ["Water Flow","Zone","An alarm has been triggered by a water flow detector"],
	"114" : ["Heat","Zone","An alarm has been triggered by a heat detector"],
	"115" : ["Pull Station","Zone","A pull station has been activated"],
	"116" : ["Duct","Zone","An alarm has been triggered by a duct detector"],
	"117" : ["Flame","Zone","An alarm has been triggered by a flame detector"],
	"118" : ["Near Alarm","Zone","A near-alarm condition has been detected on a fire sensor"],
	"12" : ["Panic Alarm","ALARM",""],
	"120" : ["Panic","Zone","A non-specific hold-up alarm exists"],
	"121" : ["Duress","User","A duress code has been entered by a user"],
	"122" : ["Silent","Zone","A silent hold-up alarm exists"],
	"123" : ["Audible","Zone","An audible hold-up alarm exists"],
	"124" : ["Duress Â¿ Access granted","Zone","A duress code has been entered and granted at an entry door"],
	"125" : ["Duress Â¿ Egress granted","Zone","A duress code has been entered and granted at an exit door"],
	"126" : ["Hold-up suspicion print","User","A user has activated a trigger to indicate a suspicious condition"],
	"129" : ["Panic Verifier","Zone","A confirmed Hold-up condition exists"],
	"13" : ["Burglar Alarm","ALARM",""],
	"130" : ["Burglary","Zone","A burglary zone has been violated while armed"],
	"131" : ["Perimeter","Zone","A perimeter zone has been violated while armed"],
	"132" : ["Interior","Zone","An interior zone has been violated while armed"],
	"133" : ["24 Hour (Safe)","Zone","A 24 hour burglary zone has been violated"],
	"134" : ["Entry/Exit","Zone","An Entry/Exit zone has been violated while armed"],
	"135" : ["Day/Night","Zone","An trouble by Day / alarm by Night zone has been violated while armed"],
	"136" : ["Outdoor","Zone","An outdoor burglary zone has been violated while armed"],
	"137" : ["Tamper","Zone","A burglary zone has been tampered with while armed"],
	"138" : ["Near alarm","Zone","A burg sensor has detected a condition which will cause it to go into alarm if the condition worsens"],
	"139" : ["Intrusion Verifier","Zone","The specified zone has verified that an intrusion has occurred"],
	"14" : ["General Alarm","Event",""],
	"140" : ["General Alarm","Zone","The specified zone is in an alarm condition"],
	"141" : ["Polling loop open","Zone","An open circuit condition has been detected on a polling loop while the system was armed"],
	"142" : ["Polling loop short","Zone","A short circuit condition has been detected on a polling loop while the system was armed"],
	"143" : ["Expansion module failure","Zone","A general failure condition has been detected on an expansion module while the system was armed"],
	"144" : ["Sensor tamper","Zone","A sensor's tamper has been violated (case opened)"],
	"145" : ["Expansion module tamper","Zone","An expansion module's tamper has been violated (cabinet opened)"],
	"146" : ["Silent Burglary","Zone","A burglary zone has been violated while armed with no audible notification produced"],
	"147" : ["Sensor Supervision Failure","Zone","A sensor's supervisory circuit has reported a failure while the system was armed"],
	"15" : ["24 Hour Non-Burglary","Event",""],
	"150" : ["24 Hour Non-Burglary","Zone","A non-burglary zone has been faulted"],
	"151" : ["Gas detected","Zone","The gas detector assigned to the specified zone has reported a fault condition"],
	"152" : ["Refrigeration","Zone","The refrigeration detector assigned to the specified zone has reported a fault condition"],
	"153" : ["Loss of heat","Zone","The temperature detector assigned to the specified zone has reported a fault condition"],
	"154" : ["Water Leakage","Zone","The water leak detector assigned to the specified zone has reported a fault condition"],
	"155" : ["Foil Break","Zone","The specified zone which is assigned to foil used as glass break detection has reported a fault condition"],
	"156" : ["Day Trouble","Zone","The specified zone which monitors trouble by day has reported a fault condition while disarmed"],
	"157" : ["Low bottled gas level","Zone","The gas level detector assigned to the specified zone has reported a fault condition"],
	"158" : ["High temp","Zone","The over-temperature detector assigned to the specified zone has reported a fault condition"],
	"159" : ["Low temp","Zone","The under-temperature detector assigned to the specified zone has reported a fault condition"],
	"16" : ["24 Hour Non-Burglary","Event",""],
	"161" : ["Loss of air flow","Zone","The air flow detector assigned to the specified zone has reported a fault condition"],
	"162" : ["Carbon Monoxide detected","Zone","The carbon monoxide detector assigned to the specified zone has reported a fault condition"],
	"163" : ["Tank level","Zone","The tank level detector assigned to the specified zone has reported a fault condition"],
	"168" : ["High Humidity","Zone","A High Humidity condition has been detected"],
	"169" : ["Low Humidity","Zone","A Low Humidity condition has been detected"],
	"20" : ["Fire Supervisory","Event",""],
	"200" : ["Fire Supervisory","Zone","The supervisory circuit of the specified fire zone has reported a fault condition"],
	"201" : ["Low water pressure","Zone","The water pressure sensor assigned to the specified zone has reported a fault condition"],
	"202" : ["Low CO2","Zone","The CO2 pressure sensor assigned to the specified zone has reported a fault condition"],
	"203" : ["Gate valve sensor","Zone","The gate valve sensor in the fire sprinkler system assigned to the specified zone has reported a fault condition"],
	"204" : ["Low water level","Zone","The water level sensor assigned to the specified zone has reported a fault condition"],
	"205" : ["Pump activated","Zone","The pump activity detector assigned to the specified zone has reported an active condition"],
	"206" : ["Pump failure","Zone","A pump output monitor assigned to the specified zone has reported a fault condition"],
	"21" : ["Fire Supervisory","",""],
	"30" : ["System Trouble","Event",""],
	"300" : ["System Trouble","Zone","A general system trouble condition has been reported by the specified zone"],
	"301" : ["AC Loss","Zone","AC power loss has been detected at a control or expansion module while the system was disarmed"],
	"302" : ["Low system battery","Zone","A battery has failed a load test while the system was disarmed"],
	"303" : ["RAM Checksum bad","Zone","A test of the system's memory has failed"],
	"304" : ["ROM checksum bad","Zone","A test of the system's executable memory has failed"],
	"305" : ["System reset","Zone","The system has reset and restarted"],
	"306" : ["Panel programming changed","Zone","The programmed configuration of the panel has changed"],
	"307" : ["Self-test failure","Zone","The system has failed a self-test"],
	"308" : ["System shutdown","Zone","The system has been shut down and has stopped functioning"],
	"309" : ["Battery test failure","Zone","The system backup battery has failed a load test while the system was disarmed"],
	"31" : ["System Trouble","",""],
	"310" : ["Ground fault","Zone","The panel has detected a ground fault condition"],
	"311" : ["Battery Missing/Dead","Zone","The system has detected that the backup battery is either missing or completely discharged."],
	"312" : ["Power Supply Overcurrent","Zone","The system power supply has reported an excessive current draw condition"],
	"313" : ["Engineer Reset","User","The specified service person has issued a system reset"],
	"314" : ["Primary Power Supply Failure","Zone","The system's primary power supply has failed a supervision test. Radio devices indicate this when the backup battery charging circuit has failed its supervision test"],
	"316" : ["System Tamper","Zone","The system has been tampered with and may have been compromised"],
	"32" : ["Sounder/Relay Trouble","",""],
	"320" : ["Sounder/Relay","Zone","A trouble condition exists in the system's sounder/relay circuit"],
	"321" : ["Bell 1","Zone","A trouble condition exists in the primary bell circuit"],
	"322" : ["Bell 2","Zone","A trouble condition exists in the secondary bell circuit"],
	"323" : ["Alarm relay","Zone","A trouble condition exists in the system's alarm relay circuit"],
	"324" : ["Trouble relay","Zone","A trouble condition exists in the system's trouble relay circuit"],
	"325" : ["Reversing relay","Zone","The specified TELCO reversing relay has reported a trouble condition"],
	"326" : ["Notification Appliance Ckt. # 3","Zone","A trouble condition exists in the bell #3 circuit"],
	"327" : ["Notification Appliance Ckt. #4","Zone","A trouble condition exists in the bell #4 circuit"],
	"33" : ["System Peripheral Trouble","Event",""],
	"330" : ["System Peripheral trouble","Zone","A system peripheral device has reported a trouble condition"],
	"331" : ["Polling loop open","Zone","An open circuit condition has been detected on a polling loop while the system was disarmed"],
	"332" : ["Polling loop short","Zone","A short circuit condition has been detected on a polling loop while the system was disarmed"],
	"333" : ["Expansion module failure","Zone","A general failure condition has been detected on an expansion module while the system was disarmed"],
	"334" : ["Repeater failure","Zone","A repeater in the system has reported a failure condition while the system was disarmed"],
	"335" : ["Local printer out of paper","Zone","The printer attached to the panel has reported an Out Of Paper condition"],
	"336" : ["Local printer failure","Zone","The printer attached to the panel has reported a failure condition"],
	"337" : ["Exp. Module DC Loss","Zone","An expansion module has detected a DC power loss"],
	"338" : ["Exp. Module Low Batt.","Zone","An expansion module has detected a low battery condition"],
	"339" : ["Exp. Module Reset","Zone","An expansion module has reset"],
	"34" : ["System Peripheral Trouble","Event",""],
	"341" : ["Exp. Module Tamper","Zone","An expansion module has detected its taper switch has been faulted"],
	"342" : ["Exp. Module AC Loss","Zone","An expansion module has detected the loss of AC power"],
	"343" : ["Exp. Module self-test fail","Zone","An expansion module has failed a self-test"],
	"344" : ["RF Receiver Jam Detect","Zone","An RF receiver has detected the presence of a jamming signal, preventing it from receiving normal signals from the system RF devices"],
	"345" : ["AES Encryption disabled/ enabled","Zone","E345 AES Encryption has been disabledR345 AES Encryption has been enabled"],
	"35" : ["Communication Trouble","Event",""],
	"350" : ["Communication trouble","Zone","The system has experienced difficulties communicating with the central station"],
	"351" : ["Telco 1 fault","Zone","The system has detected a fault on the primary dial-up line"],
	"352" : ["Telco 2 fault","Zone","The system has detected a fault on the secondary dial-up line"],
	"353" : ["Long Range Radio xmitter fault","Zone","A fault has been detected in the long range radio subsystem"],
	"354" : ["Failure to communicate event","Zone","The system was unable to communicate an event to the central station"],
	"355" : ["Loss of Radio supervision","Zone","The radio has not reported in its designated supervision interval"],
	"356" : ["Loss of central polling","Zone","The radio has detected a loss in the polling signal from it's associated receiver"],
	"357" : ["Long Range Radio VSWR problem","Zone","The Long Range Radio has reported a transmitter/antenna problem"],
	"358" : ["Periodic Comm Test Fail /Restore","Zone","A periodic Communication path test has failed"],
	"36" : ["Communication Trouble","Event",""],
	"37" : ["Protection Loop Trouble","Event",""],
	"370" : ["Protection loop","Zone","The specified protection loop has reported a trouble condition"],
	"371" : ["Protection loop open","Zone","The specified protection loop has reported an open-loop trouble condition"],
	"372" : ["Protection loop short","Zone","The specified protection loop has reported a shorted-loop trouble condition"],
	"373" : ["Fire trouble","Zone","A fire sensor has detected a trouble condition on the specified zone while the system was disarmed"],
	"374" : ["Exit error alarm (zone)","Zone","An exit error condition has been reported for the specified alarm zone"],
	"375" : ["Panic zone trouble","Zone","The system has detected a trouble condition on the panic zone"],
	"376" : ["Hold-up zone trouble","Zone","The system has detected a trouble condition on the hold-up zone"],
	"377" : ["Swinger Trouble","Zone","A fault has occurred on a zone that has been shut down due to excessive alarms"],
	"378" : ["Cross-zone Trouble","Zone","The specified zone in a cross-zone configuration has faulted without a fault on its corresponding cross-zone in a specific time period"],
	"38" : ["Sensor Trouble","Event",""],
	"380" : ["Sensor trouble","Zone","The specified sensor has reported a trouble condition"],
	"381" : ["Loss of supervision - RF","Zone","The specified zone has failed to report in during its designated supervision period"],
	"382" : ["Loss of supervision - RPM","Zone","An Remote Polled Module assigned to the specified zone has failed supervision"],
	"383" : ["Sensor tamper","Zone","The tamper switch on the specified sensor has been faulted"],
	"384" : ["RF low battery","Zone","The specified battery powered RF zone has reported a low battery condition"],
	"385" : ["Smoke detector Hi sensitivity","Zone","A smoke detector's sensitivity level has drifted to the upper limit"],
	"386" : ["Smoke detector Low sensitivity","Zone","A smoke detector's sensitivity level has drifted to the lower limit"],
	"387" : ["Intrusion detector Hi sensitivity","Zone","An intrusion detector's sensitivity level has drifted to the upper limit"],
	"388" : ["Intrusion detector Low sensitivity","Zone","An intrusion detector's sensitivity level has drifted to the lower limit"],
	"389" : ["Sensor self-test failure","Zone","The specified sensor has failed a self-test"],
	"39" : ["Sensor Trouble","Event",""],
	"391" : ["Sensor Watch trouble","Zone","A motion sensor has not been triggered within a pre-defined time interval"],
	"392" : ["Drift Compensation Error","Zone","A smoke detector cannot automatically adjust its sensitivity"],
	"393" : ["Maintenance Alert","Zone","The specified zone requires maintenance"],
	"394" : ["CO Detector needs replacement","Zone","The specified Carbon Monoxide detector has reached end-of-life"],
	"40" : ["Open/Close","Opening","Closing"],
	"400" : ["Open/Close","User","The specified user has disarmed/armed the system"],
	"401" : ["O/C by user","User","The specified user has disarmed/armed the system"],
	"402" : ["Group O/C","User","A group of zones has been armed or disarmed"],
	"403" : ["Automatic O/C","User","A partition has been automatically armed or disarmed"],
	"404" : ["Late to O/C (Note: use 453 or 454 instead )","User",""],
	"405" : ["Deferred O/C (Obsolete- do not use )","User",""],
	"406" : ["Cancel","User","The specified user has cancelled the previously reported alarm condition"],
	"407" : ["Remote arm/disarm","User","The specified user has armed or disarmed the system from off-premises"],
	"408" : ["Quick arm","User","The specified user has quick-armed the system"],
	"409" : ["Keyswitch O/C","User","The specified user has armed or disarmed the system using a keyswitch"],
	"41" : ["Remote Access","",""],
	"411" : ["Callback request made","User","A remote site (central station) has requested the panel call it back"],
	"412" : ["Successful download/access","User","The configuration data of the system has been successfully downloaded"],
	"413" : ["Unsuccessful access","User","A number of failed attempts have been made to remotely access the system"],
	"414" : ["System shutdown command received","User","A central station has sent a system shutdown command to the panel"],
	"415" : ["Dialer shutdown command received","User","A central station has sent a dialer shutdown command to the panel"],
	"416" : ["Successful Upload","Zone","The configuration data of the system has been successfully uploaded"],
	"42" : ["Access Control","",""],
	"421" : ["Access denied","User","The access control system has denied access to the specified user"],
	"422" : ["Access report by user","User",""],
	"423" : ["Forced Access","Zone","The specified access control door has been forced open"],
	"424" : ["Egress Denied","User","The access control system has denied egress to the specified user"],
	"425" : ["Egress Granted","User","The access control system has granted egress for the specified user"],
	"426" : ["Access Door propped open","Zone","The specified access control door has been held open"],
	"427" : ["Access point Door Status Monitor trouble","Zone","The specified Access Point's Door Status Monitor has reported a trouble condition to the panel"],
	"428" : ["Access point Request To Exit trouble","Zone","The specified Access Point's Request To Exit zone has reported a trouble condition to the panel"],
	"429" : ["Access program mode entry","User","The access control system has been put into program mode"],
	"43" : ["Access Control","",""],
	"430" : ["Access program mode exit","User","The access control system has exited program mode"],
	"431" : ["Access threat level change","User","The access control system's threat level has been changed"],
	"432" : ["Access relay/trigger fail","Zone","The specified access control output device has failed to operate properly"],
	"433" : ["Access RTE shunt","Zone","The specified Request To Exit zone has been shunted and will no longer report activity"],
	"434" : ["Access DSM shunt","Zone","The specified Door Status Monitor zone has been shunted and will no longer report activity"],
	"435" : ["Second Person Access","User","A second person has accessed an access point conforming to Two-Man-Rule requirements"],
	"436" : ["Irregular Access","User",""],
	"44" : ["Open/Close","Opening","Closing"],
	"441" : ["Armed STAY","User","The specified user has armed the system in STAY mode"],
	"442" : ["Keyswitch Armed STAY","User","The specified user has armed the system in STAY mode using a keyswitch"],
	"443" : ["Armed with System Trouble Override","User","The specified user has armed the system while overriding a trouble condition"],
	"45" : ["Open/Close","Opening","Closing"],
	"450" : ["Exception O/C","User","The system has been armed or disarmed outside of the configured time window"],
	"451" : ["Early O/C","User","The system has been disarmed/armed by the specified user before the configured time window has started"],
	"452" : ["Late O/C","User","The system has been disarmed/armed by the specified user after the configured time window has ended"],
	"453" : ["Failed to Open","User","The system has failed to have been disarmed during the designated time window"],
	"454" : ["Failed to Close","User","The system has failed to be armed during the designated time window"],
	"455" : ["Auto-arm Failed","User","The system has failed to automatically arm itself at the designated time"],
	"456" : ["Partial Arm","User","The system has been only partially armed by the specified user"],
	"457" : ["Exit Error (user)","User","The specified user has made an error exiting the premises after arming the system"],
	"458" : ["User on Premises","User","A user has disarmed the system after an alarm has occurred"],
	"459" : ["Recent Close","User","The system had been armed within the last xx minutes"],
	"46" : ["Open/Close","Opening","Closing"],
	"461" : ["Wrong Code Entry","Zone",""],
	"462" : ["Legal Code Entry","User",""],
	"463" : ["Re-arm after Alarm","User",""],
	"464" : ["Auto-arm Time Extended","User","A user has successfully requested that the system delay automatically arming"],
	"465" : ["Panic Alarm Reset","Zone","The specified panic zone has been reset"],
	"466" : ["Service On/Off Premises","User","A service person has entered or left the premises"],
	"50" : ["System Disable","",""],
	"501" : ["Access reader disable","Zone","The credential reader on the specified access point has been disabled"],
	"51" : ["System Disable","",""],
	"52" : ["Sounder/Relay Disable","",""],
	"520" : ["Sounder/Relay Disable","Zone","The specified sounder or relay has been disabled"],
	"521" : ["Bell 1 disable","Zone","The specified output for Bell 1 has been disabled"],
	"522" : ["Bell 2 disable","Zone","The specified output for Bell 2 has been disabled"],
	"523" : ["Alarm relay disable","Zone","The specified alarm relay has been disabled"],
	"524" : ["Trouble relay disable","Zone","The specified trouble relay has been disabled"],
	"525" : ["Reversing relay disable","Zone","The specified reversing relay has been disabled"],
	"526" : ["Notification Appliance Ckt. # 3 disable","Zone","The specified output for Bell 3 has been disabled"],
	"527" : ["Notification Appliance Ckt. # 4 disable","Zone","The specified output for Bell 4 has been disabled"],
	"53" : ["Peripheral Disable","",""],
	"531" : ["Module Added","Zone","The specified access control module has been added to the system"],
	"532" : ["Module Removed","Zone","The specified access control module has been removed from the system"],
	"54" : ["Peripheral Disable","",""],
	"55" : ["Communication Disable","",""],
	"551" : ["Dialer disabled","Zone","The specified dialer has been disabled"],
	"552" : ["Radio transmitter disabled","Zone","The specified radio transmitter has been disabled"],
	"553" : ["Remote Upload/Download disabled","Zone","Remote configuration has been enabled"],
	"56" : ["Communication Disable","",""],
	"57" : ["Bypass","",""],
	"570" : ["Zone/Sensor bypass","Zone","The specified zone or sensor has been bypassed"],
	"571" : ["Fire bypass","Zone","The specified fire zone has been bypassed"],
	"572" : ["24 Hour zone bypass","Zone","The specified 24 hour zone has been bypassed"],
	"573" : ["Burg. Bypass","Zone","The specified burglary zone has been bypassed"],
	"574" : ["Group bypass","User","A group of zones has been bypassed"],
	"575" : ["Swinger bypass","Zone","The specified zone which has reported an excessive number of faults/restores in a short period of time has been bypassed"],
	"576" : ["Access zone shunt","Zone","The specified zone in the access control system has been shunted and will no longer report activity"],
	"577" : ["Access point bypass","Zone","The specified access point in the access control system has been bypassed and will allow the door to open (unsecured)"],
	"578" : ["Vault Bypass","Zone","The specified vault zone has been bypassed"],
	"579" : ["Vent Zone Bypass","Zone","The specified vent zone has been bypassed and will no longer report any activity"],
	"60" : ["Test/Misc","",""],
	"601" : ["Manual trigger test report","Zone","A test report has been triggered manually"],
	"602" : ["Periodic test report","Zone","A periodic test report has been triggered"],
	"603" : ["Periodic RF transmission","Zone","A periodic RF path test report has been triggered"],
	"604" : ["Fire test","User","The specified user has initiated a test of the fire alarm zones"],
	"605" : ["Status report to follow","Zone",""],
	"606" : ["Listen-in to follow","Zone","The system is about to activate a 2-way audio session"],
	"607" : ["Walk test mode","User","The specified user has placed the system into the walk-test mode for testing purposes"],
	"608" : ["Periodic test - System Trouble Present","Zone","A periodic test has been triggered but the fire system has a trouble condition present"],
	"609" : ["Video Xmitter active","Zone","A video look-in session is about to begin"],
	"61" : ["Test/Misc","",""],
	"611" : ["Point tested OK","Zone","The specified point tested successfully"],
	"612" : ["Point not tested","Zone","The specified point has not been tested"],
	"613" : ["Intrusion Zone Walk Tested","Zone","The specified intrusion zone has been successfully walk-tested"],
	"614" : ["Fire Zone Walk Tested","Zone","The specified fire zone has been successfully walk-tested"],
	"615" : ["Panic Zone Walk Tested","Zone","The specified panic zone has been successfully walk-tested"],
	"616" : ["Service Request","Zone","A request has been made for system servicing"],
	"62" : ["Event Log","",""],
	"621" : ["Event Log reset","Zone","The event log has been reset and all stored events have been discarded"],
	"622" : ["Event Log 50% full","Zone","The event log is 50% full"],
	"623" : ["Event Log 90% full","Zone","The event log is 90% full"],
	"624" : ["Event Log overflow","Zone","The event log has overflowed and events have been lost"],
	"625" : ["Time/Date reset","User","The time and date have been reset to a new value by the specified user"],
	"626" : ["Time/Date inaccurate","Zone","The system time and date is known to be in error"],
	"627" : ["Program mode entry","Zone","The system has been placed into program mode"],
	"628" : ["Program mode exit","Zone","The system has exited program mode"],
	"629" : ["32 Hour Event log marker","Zone",""],
	"63" : ["Scheduling","",""],
	"630" : ["Schedule change","Zone","The specified fire/burglary schedule has been changed"],
	"631" : ["Exception schedule change","Zone","The time schedule for event reporting by exception has been changed"],
	"632" : ["Access schedule change","Zone","The specified access control schedule has been changed"],
	"64" : ["Personnel Monitoring","",""],
	"641" : ["Senior Watch Trouble","Zone","A person has not activated a motion sensor in a specified period"],
	"642" : ["Latch-key Supervision","User","A child has disarmed the system (after school)"],
	"65" : ["Miscelaneous","",""],
	"651" : ["Reserved for Ademco Use","Zone",""],
	"652" : ["Reserved for Ademco Use","User",""],
	"653" : ["Reserved for Ademco Use","User",""],
	"654" : ["System Inactivity","Zone","System has not been operated for x days"],
	"655" : ["User Code X modified by Installer","User","The Installer has modified the specified User's code"],
	"70" : ["Miscelaneous","",""],
	"703" : ["Auxiliary #3","Zone",""],
	"704" : ["Installer Test","Zone",""],
	"75" : ["Miscelaneous","",""],
	"750" : ["User Assigned","",""],
	"751" : ["User Assigned","",""],
	"752" : ["User Assigned","",""],
	"753" : ["User Assigned","",""],
	"754" : ["User Assigned","",""],
	"755" : ["User Assigned","",""],
	"756" : ["User Assigned","",""],
	"757" : ["User Assigned","",""],
	"758" : ["User Assigned","",""],
	"759" : ["User Assigned","",""],
	"76" : ["Miscelaneous","",""],
	"760" : ["User Assigned","",""],
	"761" : ["User Assigned","",""],
	"762" : ["User Assigned","",""],
	"763" : ["User Assigned","",""],
	"764" : ["User Assigned","",""],
	"765" : ["User Assigned","",""],
	"766" : ["User Assigned","",""],
	"767" : ["User Assigned","",""],
	"768" : ["User Assigned","",""],
	"769" : ["User Assigned",""],
	"77" : ["Miscelaneous","",""],
	"770" : ["User Assigned","",""],
	"771" : ["User Assigned","",""],
	"772" : ["User Assigned","",""],
	"773" : ["User Assigned","",""],
	"774" : ["User Assigned","",""],
	"775" : ["User Assigned","",""],
	"776" : ["User Assigned","",""],
	"777" : ["User Assigned","",""],
	"778" : ["User Assigned","",""],
	"779" : ["User Assigned","",""],
	"78" : ["Miscelaneous","",""],
	"780" : ["User Assigned","",""],
	"781" : ["User Assigned","",""],
	"782" : ["User Assigned","",""],
	"783" : ["User Assigned","",""],
	"784" : ["User Assigned","",""],
	"785" : ["User Assigned","",""],
	"786" : ["User Assigned","",""],
	"787" : ["User Assigned","",""],
	"788" : ["User Assigned","",""],
	"789" : ["User Assigned","",""],
	"79" : ["Miscelaneous","",""],
	"796" : ["Unable to output signal (Derived Channel)","Zone",""],
	"798" : ["STU Controller down (Derived Channel)","Zone",""],
	"90" : ["Miscelaneous","",""],
	"900" : ["Download Abort","DLID","The specified Downloader ID has aborted a download sequence in progress"],
	"901" : ["Download Start/End","DLID","Downloader has started or ended a download sequence to the panel"],
	"902" : ["Download Interrupted","DLID","A download sequence has been interrupted"],
	"903" : ["Device Flash Update Started/ Completed","Device","A code update for a device has started (E903) or completed successfully (R903)"],
	"904" : ["Device Flash Update Failued","Device","A code update for a device has failed"],
	"91" : ["Miscelaneous","",""],
	"910" : ["Auto-close with Bypass","Zone","An auto-close sequence has been started and the specified zone has been bypassed"],
	"911" : ["Bypass Closing","Zone",""],
	"912" : ["Fire Alarm Silence","Zone","The fire alarm has been silenced"],
	"913" : ["Supervisory Point test Start/End","User","A fire supervisory device has been tested"],
	"914" : ["Hold-up test Start/End","User","The specified user has started or ended a hold-up test"],
	"915" : ["Burg. Test Print Start/End","User","The printed progress of a burglary test has been started or ended"],
	"916" : ["Supervisory Test Print Start/End","User","The printed progress of a supervisory test has been started or ended"],
	"917" : ["Burg. Diagnostics Start/End","Zone","A burglary system diagnostic test has been started or ended"],
	"918" : ["Fire Diagnostics Start/End","Zone","A fire system diagnostic test has been started or ended"],
	"919" : ["Untyped diagnostics","Zone",""],
	"92" : ["Miscelaneous","",""],
	"920" : ["Trouble Closing","User","Closed with burglary during exit"],
	"921" : ["Access Denied Code Unknown","User","Access has been denied because the system did not recognize the supplied access code as valid"],
	"922" : ["Supervisory Point Alarm","Zone","The specified supervisory point has reported an alarm condition"],
	"923" : ["Supervisory Point Bypass","Zone","The specified supervisory point has been bypassed"],
	"924" : ["Supervisory Point Trouble","Zone","The specified supervisory point has reported a trouble condition"],
	"925" : ["Hold-up Point Bypass","Zone","The specified hold-up point has been bypassed"],
	"926" : ["AC Failure for 4 hours","Zone","There has been a loss of AC power for at least four hours"],
	"927" : ["Output Trouble","Zone","The specified output has reported a trouble condition"],
	"928" : ["User code for event","User","This message contains the ID of the user who triggered the previous event"],
	"929" : ["Log-off","User","The specified user has logged-off of the system"],
	"95" : ["Miscelaneous","",""],
	"954" : ["CS Connection Failure","Zone","The specified CS connection has failed/restored"],
	"96" : ["Miscelaneous","",""],
	"961" : ["Rcvr Database Connection Fail/Restore","Zone","The connection to the receiver's database has failed/restored"],
	"962" : ["License Expiration Notify","Zone","The product license has been terminated (7810PC)"]
]

/***********************************************************************************************************************
* version 0.9.0
*		Remove clearallzones - it messed up actual zones when armed with bypassed zones
*   Reactivate Not ready status
*   Add disable Partition ready status option\
*   rewrite most of the processing for better efficiency
*		added BypassZone and Bypassed Zomes attribute
* Version: 0.8.4
*   mark.labuda Added ArmNight code for DSC panel
* 
* Version: 0.8.3
*   Added selective-closing in zoneClose() (only close if open)
*   Added selective-clearing in clearAllZones() (only clear if open)
*   Added selective-opening in zoneOpen() (only open if closed)
*   Changes to partitionDisarm() - shouldn't go to Disarm from ready/unready
*   Shouldn't trigger brief HSM disarm when arming
* 
* Version: 0.8.2
*   Addtional Vista fixes merged from Cybrmage 
*   New device types supported - CO2, Smoke, Glassbreak (requires external driver)
*   CID data now logged to state variables
*
* Version: 0.8.1
*   Fix logging
*   Fix panel type initialization
*   Fix Disarm bug for DSC
*
* Version: 0.8
*   Vista (Honeywell) support
*
* Version: 0.7
*   Override armState if panel sends Armed Home
*
* Version: 0.6
*   Fix LED State, Thanks cybrmage
*
* Version: 0.5
*   Holistic Refactor
*   Program User Codes
*
* Version: 0.4
*   Arm/Disarm user and special parse
* 
* Version: 0.3.6
*   Fixed Initialization
*   Fixed Telnet Failed Routine
*   Fixed 'Backwards' Arm states
*
* Version: 0.3.5
*   Fixed regex matching for timestamps.
*
* Version: 0.3.4
*   Added Armed Away and Armed Home States, including HSM instructions
*   Added Polling Rate - Default State is Disabled
*   If polling is enabled, it should recover from an Envisalink reboot within two time intervals
*   Added Status Report on initialize to re-sync Alarm State
*   Fixed autoInactive value from 0 to disabled, to resolve motion sensor errors
*
* Version: 0.3.3
*	Hubitat suggested changes, removing regex libraries
*
* Version: 0.3.2
*	Fixed disarm state variable getting out of sync because of reboots or crashed hub during events.
*
* Version: 0.3.0
* 	Fixed Login Response Message
*	Improved Connection Routine
*	Improved Error Logging
*	Integrations
*
* Version: 0.2.1
* 	Added Login Command to Map
*
* Version: 0.2.0
* 	Better response and Command Mapping for easy adaption

* Version: 0.17.0
*	Added TTS
* 	Locks
*	Notifications
*	Unified Logging
*	Syncing Versioning
*
* Version: 0.15.0
*	Version Sync with App
*   Add deeper integration with HSM
*
* Version: 0.13.0
*	Fixed Zone Type Conversion (Always setting up Motion Sensor)
*
* Version: 0.13.0
*	Adding debug switch for reducing logging
*	Move this section to the bottom of the file
*
* Version: 0.12.1
*	Spelling Error
*
* Version: 0.12.0
*	Small State Fix for Motion
*
Version: 0.11.0
*	Added Motion Zone Capability
*
* Version: 0.10.0
*
* 	Just the basics.
*		Establish Telnet with Envisalink
* 		Interpret Incoming Messages from Envisalink
*		Arm Away
*		Arm Home
*	 	Disarm
* 		Switch to Arm Away and Disarm
*	 	Status Report
*		Toggle Chime
*		Toggle Timestamp
*	 	Send Raw Message
*		Create Child Virtual Contacts (Zones)
*		Zone Open and Restored (Closed) Implementation
*		Error Codes
*
*/
