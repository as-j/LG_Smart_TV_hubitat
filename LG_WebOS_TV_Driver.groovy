/**
 *  LG Smart TV Device Type
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Original Author: Sam Lalor
 *  Ported to Hubitat by: Mike Magrann, 3/27/2019
 *  Modified to support WebOS SSAP protocol: Cybrmage, 7/18/2019
 *    portions of the websocket code modified from the Logitech Harmony plugin by Dan G Ogorchock 
 *  Refactoring of callbacks, removed hand rolled json: asj, 9/18/2019
 *
***See Release Notes at the bottom***
***********************************************************************************************************************/
public static String version()      {  return "v0.2.5"  }

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

metadata {
	definition (name: "LG WebOS TV", namespace: "asj", author: "Andrew Stanley-Jones")
	{
		capability "Initialize"
		capability "TV"
		capability "AudioVolume"
		capability "Refresh"
		capability "Switch"
		capability "Notification"

		command "on"
		command "off"
		command "refresh"
        command "refreshInputList"
        command "getMouseURI"
		command "externalInput", ["string"]
        command "sendJson", ["string"]
		command "myApps"
		command "ok"
		command "home"
//        command "wake"

		attribute "CurrentInput", "string"
		attribute "sessionId", "string"
//		attribute "mute", "string"
        
        attribute "availableInputs", "list"
		
		attribute "channelDesc", "string"
		attribute "channelName", "string"
		attribute "channelData", "string"

	}

	preferences {
		input name: "televisionIp", type: "text", title: "Television IP Address",  defaultValue: "",  required: true
		input name: "televisionMac", type: "text", title: "Television MAC Address", defaultValue: "",  required: true
		input name: "televisionType", type: "text", title: "Television Type (NETCAST or WEBOS)", defaultValue: "", required: true
		input name: "pairingKey", type: "text", title: "Pairing Key", required: true, defaultValue: ""
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
		input ("channelDetail", "bool", title: "Enable verbose channel data (WebOS Only)", defaultValue: false)
		def reconnectRate = [:]
		reconnectRate << ["5" : "Retry every 5 seconds"]
		reconnectRate << ["10" : "Retry every 10 seconds"]
		reconnectRate << ["15" : "Retry every 15 seconds"]
		reconnectRate << ["30" : "Retry every 30 seconds"]
		reconnectRate << ["45" : "Retry every 45 seconds"]
		reconnectRate << ["60" : "Retry every minute"]
		reconnectRate << ["120" : "Retry every minute"]
		reconnectRate << ["300" : "Retry every 5 minutes"]
		reconnectRate << ["600" : "Retry every 10 minutes"]
		input ("retryDelay", "enum", title: "Device Reconnect delay (WebOS Only)", options: reconnectRate, defaultValue: 60)
	}
}

@Field static Map callbacks = [:]

def log_warn(logMsg) {
	log.warn(logMsg)
}

def log_error(logMsg) {
	log.error(logMsg)
}

def log_debug(logMsg) {
	if ((debug == true) || (descriptionText == true)) { log.debug(logMsg) }
}

def log_info(logMsg) {
	if (descriptionText == true) { log.info(logMsg) }
}

def installed()
{
    log_debug("LG Smart TV Driver - installed - ip: ${televisionIp}  mac: ${televisionMac} type: ${televisionType}  key: ${pairingKey}  debug: ${debug} logText: ${descriptionText}")
    log_debug("LG Smart TV Driver - installed - settings: " + settings.inspect())
//    initialize()
}

def webosRegister() {
    state.pairFailCount = 0

    def payload = [
      pairingType: "PROMPT",
      forcePairing: false,
      'client-key': state?.pairingKey,
      manifest: [
        appVersion: "1.1",
        signed: [
          localizedVendorNames: [
             "": "LG Electronics",              
          ],
          appId: "com.lge.test",
          created: "20140509",
          permissions: [
            "TEST_SECURE",
            "CONTROL_INPUT_TEXT",
            "CONTROL_MOUSE_AND_KEYBOARD",
            "READ_INSTALLED_APPS",
            "READ_LGE_SDX",
            "READ_NOTIFICATIONS",
            "SEARCH",
            "WRITE_SETTINGS",
            "WRITE_NOTIFICATION_ALERT",
            "CONTROL_POWER",
            "READ_CURRENT_CHANNEL",
            "READ_RUNNING_APPS",
            "READ_UPDATE_INFO",
            "UPDATE_FROM_REMOTE_APP",
            "READ_LGE_TV_INPUT_EVENTS",
            "READ_TV_CURRENT_TIME",
          ],
          localizedAppNames: [
             "": "LG Remote App",
             "ko-KR": "리모컨 앱",
             "zxx-XX": "ЛГ Rэмotэ AПП",
          ],
          vendorId: "com.lge",
          serial: "2f930e2d2cfe083771f68e4fe7bb07",
        ],
        permissions: [
          "LAUNCH",
          "LAUNCH_WEBAPP",
          "APP_TO_APP",
          "CLOSE",
          "TEST_OPEN",
          "TEST_PROTECTED",
          "CONTROL_AUDIO",
          "CONTROL_DISPLAY",
          "CONTROL_INPUT_JOYSTICK",
          "CONTROL_INPUT_MEDIA_RECORDING",
          "CONTROL_INPUT_MEDIA_PLAYBACK",
          "CONTROL_INPUT_TV",
          "CONTROL_POWER",
          "READ_APP_STATUS",
          "READ_CURRENT_CHANNEL",
          "READ_INPUT_DEVICE_LIST",
          "READ_NETWORK_STATE",
          "READ_RUNNING_APPS",
          "READ_TV_CHANNEL_LIST",
          "WRITE_NOTIFICATION_TOAST",
          "READ_POWER_STATE",
          "READ_COUNTRY_INFO",
        ],
        manifestVersion: 1,
        signatures: [
          [
            signatureVersion: 1,
            signature: "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbmctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRyaMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQojoa7NQnAtw==",
          ],
        ]
      ]
    ]

    sendWebosCommand(prefix: "register", type: "register", payload: payload, callback: { json ->
        log_debug("webosRegister: got json: ${json}")
        if (json?.type == "registered") {
            pKey = json.payload["client-key"]
            if (pKey != null) {
                log_warn("parseWebsocketResult: received registered client-key: ${pKey}")
                state.pairingKey = pKey
                settings.pairingKey = pKey
                device.updateSetting("pairingKey",[type:"text", value:"${pKey}"])
                pairingKey = pKey
                setPaired(true)
                // Hello doesn't seem to do anything?
                //runInMillis(10, sendHello)
                //runInMillis(10, sendRequestInfo)
                //runInMillis(50, webosSubscribeToStatus)

            }
            return true
        } else if (json?.type == "response") {
            return false
        }
    })
}

def sendHello() {
    log_debug("parseWebsocketResult: requesting HELLO packet")
    sendWebosCommand(type: "hello", prefix: "status", callback: { json_status ->
        log.debug("Got Hello: ${json_status}")
    })
}

def sendRequestInfo() {
    log_info("parseWebsocketResult: requesting SystemInfo packet")
    sendWebosCommand(id: "status", uri: "system/getSystemInfo", callback: { json_status ->
        parseStatus(state, json_status)
    })
}

def refreshInputList() {
    sendWebosCommand(uri: "com.webos.applicationManager/listLaunchPoints", payload: [], callback: { json ->
        def inputList = []
        def nameToInputId = [:]
        json?.payload?.launchPoints.each { app ->
            //log_debug "App Name: ${app.title} System App: ${app.systemApp} ID: ${app.id}"
            inputList += app.title
            nameToInputId[app.title] = app.id
        }
        state.nameToInputId = nameToInputId
        sendEvent(name: "availableInputs", value: inputList);
    })
}

def getMouseURI() {
    sendWebosCommand(uri: "com.webos.service.networkinput/getPointerInputSocket", payload: [], callback: { json ->
        log_debug("getMouseURI: $jon")
        if (json?.payload?.socketPath) {
            log_debug("Pointer Connecting to: ${json.payload.socketPath}")
            def mouseDev = getChildDevice("LG_TV_Mouse_${televisionIp}")
            if(!mouseDev) mouseDev = addChildDevice("asj", "LG Mouse WebSocket Driver", "LG_TV_Mouse__${televisionIp}", null, [label: thisName, name: thisName])
            log_debug("Got mouse dev: $mouseDev")
            mouseDev.setMouseURI(json.payload.socketPath)
        }
    })
}

def sendJson(String json) {
    sendCommand(json);
}

def setPower(boolean newState) {
	state.power = newState
	log_debug("setPower: setting state.power = " + (newState ? "ON":"OFF"))
}

def sendPowerEvent(String onOrOff) {
	state.power = onOrOff
    log_debug("sendPowerEvent: sending state.power ${onOrOff} was: ${state.lastPower}")
	sendEvent(name: "power", value: onOrOff)
	sendEvent(name: "switch", value: onOrOff)
}

def setPaired(boolean newState) {
	state.paired = newState
	log_debug("setPaired: setting state.paired = " + (newState ? "TRUE":"FALSE"))
}

def initialize()

{
    log_debug("LG Smart TV Driver - initialize - ip: ${televisionIp}  mac: ${televisionMac}  type: ${televisionType}  key: ${pairingKey} debug: ${debug} logText: ${descriptionText}")
    log_debug("LG Smart TV Driver - initialize - settings:" + settings.inspect())
    
    callbacks = [:]
    
    state.sequenceNumber = 1
	state.currentInput = ""
	state.lastInput = ""
	state.channel = ""
	state.lastChannel = ""
	state.channelDesc = ""
	state.lastChannelDesc = ""
	state.channelName = ""
	state.channelData = ""
	state.serviceList = []
	state.inputList = []
	sendEvent(name: "channelDesc", value: "", isStateChange: true)
	sendEvent(name: "channel", value: "", isStateChange: true)
	sendEvent(name: "channelName", value: "", isStateChange: true)
	sendEvent(name: "channelData", value: "", isStateChange: true)
	sendEvent(name: "CurrentInput", value: "", isStateChange: true)
    setPaired(false)
    state.pairFailCount = 0
    state.reconnectPending = false
    setPower(false)
	state.webSocket = "initialize"
	unschedule()
    
    def mouseDev = getChildDevice("LG_TV_Mouse_${televisionIp}")
	if(!mouseDev) mouseDev = addChildDevice("asj", "LG Mouse WebSocket Driver", "LG_TV_Mouse_${televisionIp}")



	if (state.webSocket == "open") {
        interfaces.webSocket.close()
	}
    
     try {
        log_debug("Connecting websocket to: \"ws://${televisionIp}:3000/\"")
        interfaces.webSocket.connect("ws://${televisionIp}:3000/")
     } 
     catch(e) {
        //if (logEnable) log.debug "initialize error: ${e.message}"
        log_warn "initialize error: ${e.message}"
        log.error "WebSocket connect failed"
    }
}

def updated()
{
    log_debug("LG Smart TV Driver - updated - ip: ${televisionIp}  mac: ${televisionMac}  type: ${televisionType}  key: ${pairingKey} debug: ${debug} logText: ${descriptionText} state: "+state.inspect())
    log_debug("LG Smart TV Driver - updated - ip: ${settings.televisionIp}  mac: ${settings.televisionMac}  type: ${settings.televisionType}  key: ${settings.pairingKey} debug: ${settings.debug} logText: ${settings.descriptionText} state: "+state.inspect())
	initialize()
}

def setParameters(String IP, String MAC, String TVTYPE, String KEY) {
	log_debug("LG Smart TV Driver - setParameters - ip: ${IP}  mac: ${MAC}  type: ${TVTYPE}  key: ${KEY}")
	state.televisionIp = IP
	settings.televisionIp = IP
	device.updateSetting("televisionIp",[type:"text", value:IP])
	state.televisionMac = MAC
	settings.televisionMac = MAC
	device.updateSetting("televisionMac",[type:"text", value:MAC])
	state.televisionType = TVTYPE
	settings.televisionType = TVTYPE
	device.updateSetting("televisionType",[type:"text", value:TVTYPE])
	if (TVTYPE == "NETCAST") {
		state.pairingKey = KEY
		settings.pairingKey = KEY
		device.updateSetting("pairingKey",[type:"text", value:KEY])
	}
	log_debug("LG Smart TV Driver - Parameters SET- ip: ${televisionIp}  mac: ${televisionMac}  type: ${televisionType}  key: ${pairingKey}")
}

// parse events into attributes
def parse(String description) 
{
    // parse method is shared between HTTP and Websocket implementations
	log_debug "Parsing '${description}'"

    // parse the websocket response
    parseWebsocketResult(description)
}

def parseWebsocketResult(String description){
	log_debug("parseWebsocketResult: $description")
	def json = null
    try{
        json = new JsonSlurper().parseText(description)
        if(json == null){
            log_warn("parseWebsocketResult: String description not parsed")
            return
        }
        //log_debug("json = ${json}")
    }  catch(e) {
        log.error("parseWebsocketResult: Failed to parse json e = ${e}")
        return
    }
    log_debug("Checking for callback")
    if (callbacks[json.id]) {
        log_debug("parseWebsocketResult: callback for json.id: " + json.id)
        callbacks[json.id].delegate = this
        callbacks[json.id].resolveStrategy = Closure.DELEGATE_FIRST
        def done = callbacks[json.id].call(json)
        if ((done instanceof Boolean) && (done == false)) {
            log_debug("Callback[${json.id}]: being kept, done is false")
        } else {
            callbacks[json.id] = null
        }
    } else if (json.type == "registered") {
    } else if (json?.type == "response") {
        if (json.id == "register_0") {
            // this is a response to our pairing request - we are waiting for user authorization at the TV
            if (!(json?.payload["client-key"] == null)){
                pKey = json.payload["client-key"]
                log_warn("parseWebsocketResult: received response client-key: ${pKey}")
                state.pairingKey = pKey
                settings.pairingKey = pKey
				pairingKey = pKey
				device.updateSetting("pairingKey",[type:"text", value:"${pKey}"])
                log_warn("parseWebsocketResult:      set response client-key: ${pairingKey}")
                setPaired(true)
            }
        }
        if (json.id.startsWith("command_")) {
            if (json.payload?.returnValue == true) {
                //we received an afirmative response
                webosPollStatus()
            }
        }
	} else if (json?.type == "hello") {
		if (json?.payload?.protocolVersion) {
		}
		if (json?.payload?.deviceOS) {
			state.deviceOS = json?.payload?.deviceOS
		}
		if (json?.payload?.deviceOSVersion) {
			state.deviceOSVersion = json?.payload?.deviceOSVersion
		}
		if (json?.payload?.deviceOSReleaseVersion) {
			state.deviceOSReleaseVersion = json?.payload?.deviceOSReleaseVersion
		}
		if (json?.payload?.deviceUUID) {
			state.deviceUUID = json?.payload?.deviceUUID
		}
	} else if (json?.type == "error") {
		if (json?.id == "register_0") {
			if (json?.error.take(3) == "403") {
				// 403 error cancels the pairing process
				pairingKey = ""
				setPaired(false)
				state.pairFailCount = state.pairFailCount ? state.pairFailCount + 1 : 1
				log_info("parseWebsocketResult: received register_0 error: ${json.error} fail count: ${state.pairFailCount}")
				if (state.pairFailCount < 6) { webosRegister() }
			}
		} else {
			if (json?.error.take(3) == "401") {
				log_info("parseWebsocketResult: received error: ${json.error}")
				//if (state.registerPending == false) { webosRegister() }
				//webosRegister()
			}
		}
	}
}

def webSocketStatus(String status){
	//if (logEnable) log.debug "webSocketStatus- ${status}"
	log_debug ("webSocketStatus: State: [${state.webSocket}]   Reported Status: [${status}]")

	if(status.startsWith('failure: ')) {
		log_debug("failure message from web socket ${status}")
		setPaired(false)
		if (state.power == false) { state.reconnectDelay = 30 }
		if ((status == "failure: No route to host (Host unreachable)") || (status == "failure: connect timed out")  || status.startsWith("failure: Failed to connect") || status.startsWith("failure: sent ping but didn't receive pong")) {
			log_debug("failure: No route/connect timeout/no pong for websocket protocol")
//			if (state.power) {
//				sendEvent(name: "power", value: "off", displayed:false, isStateChange: true)
//				sendEvent(name: "switch", value: "off", displayed:false, isStateChange: true)
//			}
//			state.power = false
			sendPowerEvent("off")
			//retry every 60 seconds
			state.reconnectDelay = 30
		}
		state.webSocket = "closed"
		reconnectWebSocket()
	} 
	else if(status == 'status: open') {
		log_info("websocket is open")
		// success! reset reconnect delay
		pauseExecution(1000)
		webosPollStatus()
		state.reconnectDelay = 1
		state.webSocket = "open"
		if ((pairingKey == null) || (pairingKey == "")) {
			webosRegister()
		} else {
			setPaired(true)
			webosRegister()
		}
	} 
	else if (status == "status: closing"){
		log_debug("WebSocket connection closing.")
		setPaired(false)
		unschedule()
		if (state.webSocket == 'initialize') {
			log_warn("Ignoring WebSocket close due to initialization.")
		} else {
			if (state.power == true) {
				// TV should be on and reachable - try to reconnect
				reconnectWebSocket()
			} else {
				// We explicitly turned off the TV - reduce the reconnect time and try to reconnect every 60 seconds
				state.reconnectDelay = 30
				reconnectWebSocket()
        	}
		}
		state.webSocket = "closed"
	} 
	else {
		log_error "WebSocket error, reconnecting."
//		if (state.power == true) {
//			sendEvent(name: "power", value: "off", displayed:false, isStateChange: true)
//			sendEvent(name: "switch", value: "off", displayed:false, isStateChange: true)
//		}
//		state.power = false
		sendPowerEvent("off")
		setPaired(false)
		state.webSocket = "closed"
		reconnectWebSocket()
	}
}

def reconnectWebSocket() {
	// first delay is 2 seconds, doubles every time
	if (state.reconnectPending == true) { 
		log_debug("Rejecting additional reconnect request")
		return
	}
	state.reconnectDelay = (retryDelay ?: 60) as int
//	state.reconnectDelay = (state.reconnectDelay ?: 1) * 2
//	don't let delay get too crazy, max it out at 10 minutes
	if(state.reconnectDelay > 600) state.reconnectDelay = 600
	log_info("websocket reconnect - delay = ${state.reconnectDelay}")
	//If the TV is offline, give it some time before trying to reconnect
	state.reconnectPending = true
	log_debug("Scheduling reconnect in ${state.reconnectDelay} seconds")
	runIn(state.reconnectDelay, initialize)
}

def webosSubscribeToStatus() {
	if (state.paired) {
		sendCommand('{"type":"subscribe","id":"status_%d","uri":"ssap://audio/getStatus"}')
		sendCommand('{"type":"subscribe","id":"status_%d","uri":"ssap://com.webos.applicationManager/getForegroundAppInfo"}')
		sendCommand('{"type":"subscribe","id":"status_%d","uri":"ssap://com.webos.service.tv.time/getCurrentTime"}')
	}
	// schedule a poll every 10 minutes to help keep the websocket open			
	runEvery10Minutes("webosPollStatus")
}

def webosPollStatus() {
    log_debug("webosPollStatus - paired = "+(state.paired?"TRUE":"FALSE")+"  currentInput = "+state.CurrentInput)
    if (state.paired) {
        // send webos commands to poll the TV status
        log_debug("webosPollStatus: requesting device status...")
        sendCommand('{"type":"request","id":"status_%d","uri":"ssap://audio/getStatus"}')
        //sendCommand('{"type":"request","id":"status_%d","uri":"ssap://tv/getExternalInputList"}')
        sendCommand('{"type":"request","id":"status_%d","uri":"ssap://com.webos.applicationManager/getForegroundAppInfo"}')
        if (state.CurrentInput == "com.webos.app.livetv") {
            sendCommand('{"type":"request","id":"status_%d","uri":"ssap://tv/getChannelProgramInfo"}')
        }
    }
}

def genericHandler(json) {
	log_debug("genericHandler: got json: ${json}")
}

def deviceNotification(String notifyMessage) {
	if (televisionType != "WEBOS") return
	
	return sendWebosCommand(uri: "system.notifications/createToast",
	                        payload: [message: notifyMessage],
	                        callback: {
		log_debug("Got a callback for the command ${it.id}")
	})
}

def on()
{
	log_debug "Executing 'Power On'"
	sendPowerEvent("on")
//	sendEvent(name: "switch", value: "on", displayed:false, isStateChange: true)
//	sendEvent(name: "power", value: "on", displayed:false, isStateChange: true)
	return wake()
}

def off()
{
	log_debug "Executing 'Power Off'"
	sendPowerEvent("off")
//    sendEvent(name: "switch", value: "off", displayed:false, isStateChange: true)
//	sendEvent(name: "power", value: "off", displayed:false, isStateChange: true)
    if (televisionType == "NETCAST") { 
        return sendCommand(1)
    } else {
        return sendCommand('{"type":"request","id":"command_%d","uri":"ssap://system/turnOff"}')
    }
}

def channelUp() 
{
	log_debug "Executing 'channelUp'"
    if (televisionType == "NETCAST") { 
        return sendCommand(27)
    } else {
        return sendCommand('{"type":"request","id":"command_%d","uri":"ssap://tv/channelUp"}')
    }
}

def channelDown() 
{
	log_debug "Executing 'channelDown'"
    if (televisionType == "NETCAST") { 
        return sendCommand(28)
    } else {
        return sendCommand('{"type":"request","id":"command_%d","uri":"ssap://tv/channelDown"}')
    }
}


// handle commands
def volumeUp() 
{
	log_debug "Executing 'volumeUp'"
    if (televisionType == "NETCAST") { 
        return sendCommand(24)
    } else {
        return sendCommand('{"type":"request","id":"command_%d","uri":"ssap://audio/volumeUp"}')
    }
}

def volumeDown() 
{
	log_debug "Executing 'volumeDown'"
    if (televisionType == "NETCAST") { 
        return sendCommand(25)
    } else {
        return sendCommand('{"type":"request","id":"command_%d","uri":"ssap://audio/volumeDown"}')
    }
}

def setVolume(level) {
	log_debug "Executing 'setVolume' with level '${level}'"
    if (televisionType == "NETCAST") { 
        //return sendCommand(25)
    } else {
        return sendCommand('{"type":"request","id":"command_%d","uri":"ssap://audio/setVolume","payload":{"volume":'+level+'}}')
    }
}

def setLevel(level) { setVolume(level) }


def refresh() {
    log_info("refresh: refreshing System Info")
    webosRegister()
}

def unmute() {
	return mute()
}

def mute() 
{
	log_debug "Executing 'mute'"
//  		sendEvent(name:'mute', value:'On', displayed:false)
    if (televisionType == "NETCAST") { 
        return sendCommand(26)
    } else {
        def newMute = !(state.Mute ?: false)
        return sendCommand('{"type":"request","id":"command_%d","uri":"ssap://audio/setMute","payload":{"mute":'+newMute+'}}')
    }
}

def externalInput(String input)
{
    if (state.nameToInputId[input]) input = state.nameToInputId[input]
    
    return sendWebosCommand(uri: "system.launcher/launch", payload: [id: input], callback: { json ->
        log_debug("externalInfo(): reply is $json")
    })
    //return sendCommand('{"type":"request","id":"command_%d","uri":"ssap://system.launcher/launch","payload":{"id":"' + input + '"}}')
}

def back()
{
}

def up()
{
}

def down()
{
}

def left()
{
}

def right()
{
}

def myApps()
{
    sendCommand('{"type":"request","id":"command_%d","uri":"ssap://system.launcher/launch","payload":{"id":"com.webos.app.discovery"}}')
}

def play()
{    
	sendWebosCommand(uri: "media.controls/play")
}

def pause()
{    
	sendWebosCommand(uri: "media.controls/pause")
}

def ok()
{    
	return sendWebosCommand(uri: "com.webos.service.ime/sendEnterKey")
}

def home()
{
    log_debug("OLD Inputs: ${state.inputList} total length: ${state.toString().length()}")

    state.remove('inputList')
    state.inputList = []
    state.inputListStr = ""
    sendWebosCommand(uri: 'tv/getExternalInputList', callback: { json ->
        json?.payload?.devices?.each { device ->
            log_debug("Found: ${device?.label} $device")
            if (device?.label && (device?.favorite || device?.connected)) {
                state.inputList << ["${device.label}": device.appId]
                if (state.inputListStr != "") state.inputListStr = ", "
                state.inputListStr += device.local
            }
        }
        log_debug("Inputs: ${state.inputList}")
    })

    state.remove('serviceList')
    state.serviceList = []
    sendWebosCommand(uri: 'api/getServiceList', callback: { json ->
        log_debug("getServiceList: ${json?.payload}")
        json?.payload?.services.each { service ->
            state.serviceList << service?.name
        }
        log_debug("Services: ${state.serviceList}")
    })
    sendWebosCommand(uri: 'com.webos.applicationManager/listLaunchPoints', callback: { json->
        log_debug("listLaunchPoints: ${json?.payload}")
    })
    /* Insuficient perms to call listLaunchPoints
    sendWebosCommand(uri: 'com.webos.service.update/getCurrentSWInformation', callback: {
        log_debug("getCurrentSWInfo: ${it}")
    })
*/
    sendWebosCommand(uri: 'com.webos.applicationManager/listLaunchPoints', callback: {
        log_debug("listLaunchPoints: ${it}")
    })
}

def wake() {
	log_debug "Sending Magic Packet to: $televisionMac"
	def result = new hubitat.device.HubAction (
       	"wake on lan $televisionMac",
       	hubitat.device.Protocol.LAN,
       	null,[secureCode: “0000”]
    )
		log_info "Sending Magic Packet to: " + result
	
    return result
	//sendHubCommand(result)
	
}

def sendCommand(cmd)
{
    if (televisionType == "NETCAST") { 
    	def actions = []
    
   	    actions << sessionIdCommand()
   	    actions << tvCommand(cmd)
   
        actions = actions.flatten()
        return actions
    } else {
        def msg = String.format(cmd,state.sequenceNumber)
        log_debug("sendCommand: " + msg)
        // send the command
        try {
            interfaces.webSocket.sendMessage(msg)
        }
        catch (Exception e) 
        {
    		log_warn "Hit Exception $e on sendCommand"
        }
        state.sequenceNumber++
    }
}

def sendWebosCommand(Map params) 
{
	def id = (params.prefix ?: "command") + "_" + state.sequenceNumber++
	
	def cb = params.callback ?: { genericHandler(it) }
	
	def message_data = [
		'id': id,
		'type': params.type ?: "request",
	]

    if (params.uri) {
        message_data.uri = "ssap://" + params.uri
    }
	
	if (params.payload) {
		message_data.payload = params.payload
	}
	
	def json = JsonOutput.toJson(message_data)
	
	log_debug("Sending: $json storing callback: $id")
	
	callbacks[id] = cb
	
	interfaces.webSocket.sendMessage(json)
	
	log_debug("sendWebosCommand sending json: $json")
}

def sessionIdCommand()
{
    def commandText = "<?xml version=\"1.0\" encoding=\"utf-8\"?><auth><type>AuthReq</type><value>$pairingKey</value></auth>"       
    def httpRequest = [
      	method:		"POST",
        path: 		"/roap/api/auth",
        body:		"$commandText",
        headers:	[
        				HOST:			"$televisionIp:8080",
                        "Content-Type":	"application/atom+xml",
                    ]
	]
    
    try 
    {
    	def hubAction = new hubitat.device.HubAction(httpRequest)
        log_warn "hub action: $hubAction"
        return hubAction
    }
    catch (Exception e) 
    {
		log_debug "Hit Exception $e on $hubAction"
	}
}

def tvCommand(cmd)
{
    def commandText = "<?xml version=\"1.0\" encoding=\"utf-8\"?><command><type>HandleKeyInput</type><value>${cmd}</value></command>"

    def httpRequest = [
      	method:		"POST",
        path: 		"/udap/api/command",
        body:		"$commandText",
        headers:	[
        				HOST:			"$televisionIp:8080",
                        "Content-Type":	"application/atom+xml",
                    ]
	]
    
    try 
    {
    	def hubAction = new hubitat.device.HubAction(httpRequest)
        log_debug "hub action: $hubAction"
    	return hubAction
    }
    catch (Exception e) 
    {
		log_debug "Hit Exception $e on $hubAction"
	}
}

private void parseStatus(state, json) {
    def rResp = false
    if ((state.power == false) && !(json?.payload?.subscribed == true)) {
        // when TV has indicated power off, do not process status messages unless they are subscriptions
        log_warn("ignoring unsubscribed status updated during power off...")
        return
    }

    if (json?.payload?.channel) {
        state.lastChannelDesc = state.channelDesc
        state.channel = json?.payload?.channel?.channelNumber
        state.channelDesc = json?.payload?.channel?.channelNumber + " ("+ json?.payload?.channel?.majorNumber + "." + json?.payload?.channel?.minorNumber + "): " + json?.payload?.channel?.channelName
        def cChange = ((state.lastChannelDesc == state.channelDesc)?false:true)
        def cData = json?.payload?.channel
        cData << [channelDesc: state.channelDesc]
        if (!channelDetail) {
            cData = [
                channelDesc: state.channelDesc,
                channelMode: json?.payload?.channel?.channelMode,
                channelNumber: json?.payload?.channel?.channelNumber,
                majorNumber: json?.payload?.channel?.majorNumber,
                minorNumber: json?.payload?.channel?.minorNumber,
                channelName: json?.payload?.channel?.channelName,
            ]
        }
        sendEvent(name: "channelDesc", value: state.channelDesc, displayed:false, isStateChange: cChange)
        sendEvent(name: "channel", value: state.channel, displayed:false, isStateChange: cChange)
        sendEvent(name: "channelName", value: json?.payload?.channel?.channelName, displayed:false, isStateChange: cChange)
        sendEvent(name: "channelData", value: cData, displayed:false, isStateChange: cChange)
        log_info("state.channelDesc = ${state.channelDesc}")
        rResp = true
    }
    if (json?.payload?.returnValue == true) {
        if (json?.payload?.volume) {
            state.lastVolume = state.Volume
            state.Volume = json?.payload?.volume
            sendEvent(name: "volume", value: state.Volume)
            log_info("state.Volume = ${state.Volume}")
            rResp = true
        }
        if (json?.payload?.mute != null) { 
            state.lastMute = state.Mute
            state.Mute = json?.payload?.mute
            sendEvent(name: "mute", value: state.Mute)
            log_info("state.Mute = ${state.Mute}")
            rResp = true
        }
        if (json?.payload?.modelName) { 
            state.ModelName = json?.payload?.modelName
            log_info("state.ModelName = ${state.ModelName}")
            rResp = true
        } 
        if (json?.payload?.appId) { 
            state.lastInput = state.CurrentInput
            state.CurrentInput = json?.payload?.appId
            log.info("state.CurrentInput = ${state.CurrentInput}")
            sendEvent(name: "CurrentInput", value: state.CurrentInput)
            if (!(state.lastInput == state.CurrentInput) && (state.CurrentInput == "com.webos.app.livetv")) {
                sendCommand('{"type":"subscribe","id":"status_channel_0","uri":"ssap://tv/getChannelProgramInfo"}')
            }
            if ((state.lastInput == "com.webos.app.livetv") && !(state.CurrentInput == "com.webos.app.livetv")) {
                sendCommand('{"type":"unsubscribe","id":"status_channel_0","uri":"ssap://tv/getChannelProgramInfo"}')
                state.channel = ""
                state.lastChannel = ""
                state.channelDesc = ""
                state.lastChannelDesc = ""
                state.channelName = ""
                state.channelData = ""
                sendEvent(name: "channelDesc", value: "", displayed:false, isStateChange: true)
                sendEvent(name: "channel", value: "", displayed:false, isStateChange: true)
                sendEvent(name: "channelName", value: "", displayed:false, isStateChange: true)
                sendEvent(name: "channelData", value: "", displayed:false, isStateChange: true)
            }
        rResp = true
        }
        if (rResp == true) {
            sendPowerEvent("on")
        }

        // The last (valid) message sent by the TV when powering off is a subscription response for foreground app status with appId, windowId and processID all NULL
        if (json?.payload?.subscribed) {
            log_debug("appID: " + (description.contains("appId")?"T":"F") + "  windowId: " + (description.contains("windowId")?"T":"F") + "  processId: " + (description.contains("processId")?"T":"F"))
            if (description.contains("appId") && description.contains("windowId") && description.contains("processId")) {
                if ((json?.payload?.appId == null) || (json?.payload?.appId == "")) {
                    // The TV is powering off - change the power state, but leave the websocket to time out
                    sendPowerEvent("off")
                        state.CurrentInput = ""
                        state.lastInput = ""
                        state.channel = ""
                        state.lastChannel = ""
                        state.channelDesc = ""
                        state.lastChannelDesc = ""
                        state.channelName = ""
                        state.channelData = ""
                        sendEvent(name: "channelDesc", value: "", displayed:false, isStateChange: true)
                        sendEvent(name: "channel", value: "", displayed:false, isStateChange: true)
                        sendEvent(name: "channelName", value: "", displayed:false, isStateChange: true)
                        sendEvent(name: "channelData", value: "", displayed:false, isStateChange: true)
                        sendEvent(name: "CurrentInput", value: "", displayed:false, isStateChange: true)
                        log.warn("Received POWER DOWN notification.")
                }
            }
        }
    }
}

def String parseSessionId(bodyString)
{
	def sessionId = ""
	def body = new XmlSlurper().parseText(bodyString)
  	sessionId = body.session.text()

	if (sessionId != null && sessionId != "")
  	{
  		sendEvent(name:'sessionId', value:sessionId, displayed:false)
  		log_debug "session id: $sessionId"
    }
}

private parseHttpHeaders(String headers) 
{
	def lines = headers.readLines()
	def status = lines[0].split()

	def result = [
	  protocol: status[0],
	  status: status[1].toInteger(),
	  reason: status[2]
	]

	if (result.status == 200) {
		log_debug "Authentication successful! : $status"
	}
    else
    {
    	log_debug "Authentication Unsuccessful: $status"
    }

	return result
}

private def delayHubAction(ms) 
{
    log_debug("delayHubAction(${ms})")
    return new hubitat.device.HubAction("delay ${ms}")
}

/***********************************************************************************************************************
*
* Release Notes
*
* 0.2.5
* Fixed - old channel data not removed on TV poweron
* Added - user selectable connection retry time (WebOS only)
*
* 0.2.4
* Fixed - state machine loosing sync with device
* Fixed - more reliable power off detection
* Added - better websocket state handling
* Added - Live TV data handling
*
* 0.2.3
* Fixed - spurious websocket open/close cycling
*
* 0.2.2
* Added - WebOS TV Notification, Status subscriptions, Event propagation, setVolume/setLevel support, Poll device every 
*         10 minute to improve connection stability
*
* 0.2.1
* Fixed - parameters not properly passed to driver
*
* 0.2.0
* Modified to support LG WebOS Smart Tv
*
* 0.1.1
* Ported LG Smart Tv from Smarththings
*
* Issues
* Unable to turn tv on (tried Wake on Lan unsuccessfully) - fixed (wake on lan / Mobile TV On must be enabled on the TV)
* Settings not carrying over from App to Driver
*
***********************************************************************************************************************/
