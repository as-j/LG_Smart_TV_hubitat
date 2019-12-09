/**
 *  LG WebOs TV Device Type
 *
 * ImportURL: https://raw.githubusercontent.com/as-j/LG_Smart_TV_hubitat/master/LG_WebOS_TV_Driver.groovy
 *
 *  Notifcation icons are fetched from: 
 *  https://github.com/pasnox/oxygen-icons-png/tree/master/oxygen/32x32
 * 
 *  They are named without extention as: <directory>/<file>
 *  
 *  For example:
 *    - file status/battery-low.png has an icon name: status/battery-low
 *    - file actions/checkbox.ping has an icon name: actions/checkbox
 * 
 *  They can be used in a notifcation message formated as:
 *  [icon name]Notification Message
 * 
 *  For example:
 *  [status/battery-low]My Battery is low!
 * 
 *  Or you can use the custom command "notificationIcon" which takes 2 strings
 *  the message, and icon name
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
public static String version()      {  return "v0.3.0"  }

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

		command "off"
		command "refresh"
        command "refreshInputList"
        command "getMouseURI"
		command "externalInput", ["string"]
        command "sendJson", ["string"]
		command "myApps"
		command "ok"
		command "home"
        command "left"
        command "right"
        command "up"
        command "down"
        command "back"
        command "enter"
        command "notificationIcon", ["string", "string"]
        command "setIcon", ["string", "string"]
        command "clearIcons"
        
		attribute "input", "string"        
        attribute "availableInputs", "list"
		
		attribute "channelDesc", "string"
		attribute "channelName", "string"
        attribute "channelFullNumber", "string"
	}

	preferences {
		input name: "televisionIp", type: "text", title: "Television IP Address",  defaultValue: "",  required: true
		input name: "televisionMac", type: "text", title: "Television MAC Address", defaultValue: "",  required: true
		input name: "pairingKey", type: "text", title: "Pairing Key", required: true, defaultValue: ""
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
		input name: "retryDelay", type: "enum", title: "Device Reconnect delay", options: reconnectRate, defaultValue: 60
        //standard logging options
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "logInfoEnable", type: "bool", title: "Enable info logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false


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
	if (logEnable) log.debug(logMsg)
}

def log_info(logMsg) {
	if (logInfoEnable) log.info(logMsg)
}

def installed()
{
    log_debug("LG Smart TV Driver - installed - ip: ${televisionIp}  mac: ${televisionMac} key: ${pairingKey}  debug: ${debug} logText: ${descriptionText}")
    log_debug("LG Smart TV Driver - installed - settings: " + settings.inspect())
//    initialize()
}

def refresh() {
    log_info "refresh: refreshing System Info"
    state.deviceInfo = null
    state.televisionModel = null
    state.nameToInputId = null
    
    webosRegister()
}

def webosRegister() {
    log_info "webosRegister(): pairing key: ${state.pairingKey}"
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

    sendWebosCommand(type: "register", payload: payload, callback: { json ->
        log_debug("webosRegister: got json: ${json}")
        if (json?.type == "registered") {
            pKey = json.payload["client-key"]
            if (pKey != null) {
                log_debug("parseWebsocketResult: received registered client-key: ${pKey}")
                state.pairingKey = pKey
                device.updateSetting("pairingKey",[type:"text", value:"${pKey}"])
                // Hello doesn't seem to do anything?
                if (!state.deviceInfo) runInMillis(10, sendHello)
                if (!state.televisionModel) runInMillis(25, sendRequestInfo)
                if (!state.nameToInputId) runInMillis(50, refreshInputList)                
                runInMillis(75, webosSubscribeToStatus)
                runInMillis(100, getMouseURI)

            }
            return true
        } else if (json?.type == "response") {
            return false
        }
    })
}

def sendHello() {
    log_info "sendHello: requesting HELLO packet"
    sendWebosCommand(type: "hello", id: "hello")
}

def handler_hello(data) {
    log_debug "Got Hello: ${data}"
    state.deviceInfo = data
}

def sendRequestInfo() {
    log_info "sendRequestInfo: requesting SystemInfo packet"
    sendWebosCommand(uri: "system/getSystemInfo", callback: { json ->
        log_debug "sendRequestInfo(): Got: $json"
        state.televisionModel = json.payload?.modelName
        state.televisionReceiver = json.payload?.receiverType
    })
}

def refreshInputList() {
    log_info "refreshInputList: current list size: ${state.nameToInputId?.size()}"
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

def getMouseChild() {
    try {
        log_debug "LG_TV_Mouse_${televisionIp}"
        def mouseDev = getChildDevice("LG_TV_Mouse_${televisionIp}")
        if(!mouseDev) mouseDev = addChildDevice("asj", "LG Mouse WebSocket Driver", "LG_TV_Mouse_${televisionIp}")
        return mouseDev
    } catch(e) {
        log_info("Failed to get mouse dev: $e")
    }
    return null
}

def getMouseURI() {
    def mouseDev = getMouseChild()
    log_info "getMouseURI(): called -> ${mouseDev}"
    sendWebosCommand(uri: "com.webos.service.networkinput/getPointerInputSocket", payload: [], callback: { json ->
        log_debug("getMouseURI: $jon")
        if (json?.payload?.socketPath) {
            log_debug("Send Mouse driver URI: ${json.payload.socketPath}")
            mouseDev?.setMouseURI(json.payload.socketPath)
        }
    })
}

def sendJson(String json) {
    sendCommand(json);
}

def sendPowerEvent(String onOrOff, String type = "digital") {
	state.power = onOrOff
    def descriptionText = "${device.displayName} is ${onOrOff}"
    log_info "${descriptionText} [$type]" 
    sendEvent(name: "switch", value: onOrOff, descriptionText: descriptionText, unit: unit, type: type)
    if (type == "physical")
        sendEvent(name: "power", value: onOrOff, descriptionText: descriptionText, unit: unit, type: type)
}

def initialize() {
    log_info("LG Smart TV Driver - initialize - ip: ${televisionIp}  mac: ${televisionMac}  key: ${pairingKey} debug: ${debug} logText: ${descriptionText}")
    log_debug("LG Smart TV Driver - initialize - settings:" + settings.inspect())

    // Websocket has closed/errored, erase all callbacks
    callbacks = [:]
    
    // Set some basic state, clear channel info
    state.sequenceNumber = 1
	state.lastChannel = [:]
    state.pairFailCount = 0

    // When reconnectPending is true it stops reconnectWebsocket 
    // from rescheudling initialize()
    state.reconnectPending = false
	state.webSocket = "initialize"

    unschedule()
    
    def mouseDev = getMouseChild()

    interfaces.webSocket.close()
    
    if(!televisionMac) {
        def mac = getMACFromIP(televisionIp)
        if (mac) device.updateSetting("televisionMac",[value:mac,type:"string"])
    }

    try {
        log_debug("Connecting websocket to: \"ws://${televisionIp}:3000/\"")
        interfaces.webSocket.connect("ws://${televisionIp}:3000/")
    } catch(e) {
        //if (logEnable) log.debug "initialize error: ${e.message}"
        log_warn "initialize error: ${e.message}"
        log.error "WebSocket connect failed"
    }
}

def webSocketStatus(String status){
	//if (logEnable) log.debug "webSocketStatus- ${status}"
	log_debug ("webSocketStatus: State: [${state.webSocket}]   Reported Status: [${status}]")

	if(status.startsWith('failure: ')) {
		log_debug("failure message from web socket ${status}")
		if ((status == "failure: No route to host (Host unreachable)") || (status == "failure: connect timed out")  || status.startsWith("failure: Failed to connect") || status.startsWith("failure: sent ping but didn't receive pong")) {
			log_debug("failure: No route/connect timeout/no pong for websocket protocol")
			sendPowerEvent("off", "physical")
		}
		state.webSocket = "closed"
		reconnectWebSocket()
	} 
	else if(status == 'status: open') {
		log_info("websocket is open")
		// success! reset reconnect delay
        sendPowerEvent("on", "physical")
		state.webSocket = "open"
        webosRegister()
        state.reconnectDelay = 2
	} 
	else if (status == "status: closing"){
		log_debug("WebSocket connection closing.")
		unschedule()
		if (state.webSocket == ' lize') {
			log_warn("Ignoring WebSocket close due to initialization.")
		} else {
			if (state.power == "on") {                
				// TV should be on and reachable - try to reconnect
				reconnectWebSocket(1)
			} else {
                reconnectWebSocket()
            }
        }
        state.webSocket = "closed"
	} else {
		log_error "WebSocket error, reconnecting."
		sendPowerEvent("off", "physical")
		state.webSocket = "closed"
		reconnectWebSocket()
	}
}

def reconnectWebSocket(delay = null) {
	// first delay is 2 seconds, doubles every time
	if (state.reconnectPending == true) { 
		log_debug("Rejecting additional reconnect request")
		return
	}
    delay = delay ?: state.reconnectDelay
    state.reconnectDelay = delay * 2
    settings_retryDelay = settings.retryDelay.toInteger()
    // don't let delay get too crazy, max it out at user setting
    if (state.reconnectDelay > settings_retryDelay) state.reconnectDelay = settings_retryDelay

	log_info("websocket reconnect - delay = ${delay}")
	//If the TV is offline, give it some time before trying to reconnect
	state.reconnectPending = true
	runIn(delay, initialize)
}

def updated()
{
    log_info "LG Smart TV Driver - updated - ip: ${settings.televisionIp}  mac: ${settings.televisionMac}  key: ${settings.pairingKey} debug: ${settings.logEnable} logText: ${settings.descriptionText}"
    if (logEnable) runIn(1800, "logsStop")
	initialize()
}

def logsStop(){
    log_info "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def setParameters(String IP, String MAC, String TVTYPE, String KEY) {
	log_info "LG Smart TV Driver - setParameters - ip: ${IP}  mac: ${MAC}  type: ${TVTYPE}  key: ${KEY}"
    
	state.televisionIp = IP
	device.updateSetting("televisionIp",[type:"text", value:IP])
    
	state.televisionMac = MAC
	device.updateSetting("televisionMac",[type:"text", value:MAC])
	log_debug("LG Smart TV Driver - Parameters SET- ip: ${televisionIp}  mac: ${televisionMac} key: ${pairingKey}")
}

// parse events into attributes
def parse(String description) 
{
    // parse method is shared between HTTP and Websocket implementations
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
    
    if (this."handler_${json.id}") {
        this."handler_${json.id}"(json.payload)
    } else if (this."handler_${json.type}") {
        this."handler_${json.type}"(json.payload)
    } else if (callbacks[json.id]) {
        log_debug("parseWebsocketResult: callback for json.id: " + json.id)
        callbacks[json.id].delegate = this
        callbacks[json.id].resolveStrategy = Closure.DELEGATE_FIRST
        def done = callbacks[json.id].call(json)
        if ((done instanceof Boolean) && (done == false)) {
            log_debug("Callback[${json.id}]: being kept, done is false")
        } else {
            callbacks[json.id] = null
        }
	} else if (json?.type == "error") {
		if (json?.id == "register_0") {
			if (json?.error.take(3) == "403") {
				// 403 error cancels the pairing process
				pairingKey = ""
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

def webosSubscribeToStatus() {
    sendWebosCommand(uri: 'audio/getStatus', type: 'subscribe', id: 'audio_getStatus')
    sendWebosCommand(uri: 'com.webos.applicationManager/getForegroundAppInfo', type: 'subscribe', id: 'getForegroundAppInfo')
    sendWebosCommand(uri: 'tv/getChannelProgramInfo', type: 'subscribe', id: 'getChannelProgramInfo')
    //sendCommand('{"type":"subscribe","id":"status_%d","uri":"ssap://com.webos.applicationManager/getForegroundAppInfo"}')
    sendCommand('{"type":"subscribe","id":"status_%d","uri":"ssap://com.webos.service.tv.time/getCurrentTime"}')

	// schedule a poll every 10 minutes to help keep the websocket open			
	runEvery10Minutes("webosSubscribeToStatus")
}

def handler_audio_getStatus(data) {
    log_debug("handler_audio_getStatus: got: $data")
    def descriptionText = "${device.displayName} volume is ${data.volume}"
    if (txtEnabled) log_info "${descriptionText}" 
    sendEvent(name: "volume", value: data.volume, descriptionText: descriptionText)
}

def handler_getForegroundAppInfo(data) {
    log_debug("handler_getForegroundAppInfo: got: $data")
    
    def appId = data.appId
    def niceName = appId
    state.nameToInputId.each { name, id ->
        if (appId == id) niceName = name
    }
    
    def descriptionText = "${device.displayName} channelName is ${niceName}"
    if (txtEnabled) log_info "${descriptionText}" 
    sendEvent(name: "channelName", value: niceName, descriptionText: descriptionText)
    
    state.lastApp = niceName
    
    if (niceName == "LiveTV") {
        runIn(3, "getChannelInfo")
    } else {
        state.lastChannel = [:]
    }
}

def getChannelInfo() {
    sendWebosCommand(uri: 'tv/getChannelProgramInfo', id: 'getChannelProgramInfo')
}

def handler_getChannelProgramInfo(data) {
    log_debug("handler_getChannelProgramInfo: got: $data")
    
    if (data.errorCode) {
        def lastChannel = [:]
        lastChannel.description = "${data.errorText}"
        state.lastChannel = lastChannel
        sendEvent(name: "channelDesc", value: lastChannel.channelDesc)
        // Resubscribe, after error subscription appears to be ended
        if (device.currentChannelName == "LiveTV")
            runIn(15, "getChannelInfo")
        return
    }
    
    def lastChannel = [
        description: "${data.channel.channelNumber}/${data.channel.channelName}",
        number: data.channel.channelNumber,
        majorNumber: data.channel.majorNumber ?: data.channel.channelNumber,
        minorNumber: data.channel.minorNumber ?: 0,
        name: data.channel.channelName ?: "",
    ]
    
    state.lastChannel = lastChannel
    sendEvent(name: "channelDesc", value: lastChannel.channelDesc)
    // This is defined as a number, not a decimal so send the major number
    sendEvent(name: "channel", value: lastChannel.majorNumber)
    sendEvent(name: "channelName", value: lastChannel.channelName)
}

def genericHandler(json) {
	log_debug("genericHandler: got json: ${json}")
}

def deviceNotification(String notifyMessage) {
    def icon_info = notifyMessage =~ /^\[(.+?)\](.+)/
    log_info "deviceNotification(): new message $notifyMessage found icon: ${icon_info != null}"
    if (!icon_info) {      
        sendWebosCommand(uri: "system.notifications/createToast",
                         payload: [message: notifyMessage])
    } else {
        log_debug "deviceNotification(): icon_name match $icon_name"
        def icon_name = icon_info[0][1]
        def msg = icon_info[0][2]
        notificationIcon(msg, icon_name)
    }
}

def setIcon(String icon_name, String data) {
    state.icon_data[icon_name] = data
}

def clearIcons() {
    state.icon_data = [:]
}

def notificationIcon(String notifyMessage, String icon_name) {
    def base_url = "https://raw.githubusercontent.com/pasnox/oxygen-icons-png/master/oxygen/32x32"
    def icon_extention = "png"
    
    def full_uri = "${base_url}/${icon_name}.png"
    
    if (!state.icon_data) state.icon_data = [:]
    
    if (!state.icon_data[icon_name]) {
        try {
            log_info "notificationIcon(): asking for $full_uri"
            def start_time = now()
            httpGet(full_uri, { resp ->
                handleIconResponse(resp, [
                    icon_extention: icon_extention,
                    icon_name: icon_name,
                    notify_message: notifyMessage,
                    start_time: start_time,
                ])
            })
            /*
            def postParams = [
                uri: address_card_base_url,
                requestContentType: "image/png",
                timeout: 10]
            asynchttpGet('handleIconResponse', postParams, [
                icon_extention: icon_extention,
                icon_name: icon_name,
                notify_message: notifyMessage,
                start_time: now(),
            ])
            */
        } catch (Exception e) {
            log.warn "notificationIcon(): Failed to fetch icon: ${e.message} sending blank"
            deviceNotification("<Failed to find icon: ${e.message}>${notifyMessage}")
        }
    } else {
        String icon = state.icon_data[icon_name]
        log_debug "notificationIcon(): icon size: ${icon.size()} sending notifcation: $notifyMessage name: ${icon_name} icon: ${state.icon_data[icon_name]}"
        sendWebosCommand(uri: "system.notifications/createToast",
                         payload: [message: notifyMessage,
                                   iconData: icon,
                                   iconExtension: icon_extention])
    }
}

def handleIconResponse(resp, data) {
    int n = resp.data?.available()
    log_info "handleIconResponse(): resp.status: ${resp.status} took: ${now() - data.start_time}ms size: $n"

    byte[] bytes = new byte[n]
    resp.data.read(bytes, 0, n)
    def base64String = bytes.encodeBase64().toString()
    log_debug "handleIconResponse(): size of b64: ${base64String.size()}"
    
    state.icon_data[data.icon_name] = base64String
    notificationIcon(data.notify_message, data.icon_name)
}

def on()
{
	log_info "on(): Executing 'Power On'"
	sendPowerEvent("on")
    def mac = settings.televisionMac ?: state.televisionMac
    if (!mac) {
        log_error "No mac address know for TV, can't send wake on lan"
        return
    }
	log_debug "Sending Magic Packet to: $mac"
	def result = new hubitat.device.HubAction (
       	"wake on lan $mac",
       	hubitat.device.Protocol.LAN,
       	null,[secureCode: “0000”]
    )
    log_info "Sending Magic Packet to: " + result
	
    return result
}

def off()
{
	log_info "off(): Executing 'Power Off'"
	sendPowerEvent("off")

    sendWebosCommand(uri: 'system/turnOff')
}

def channelUp() 
{
	log_info "channelUp(): Executing 'channelUp'"
    sendWebosCommand(uri: 'tv/channelUp')
}

def channelDown() 
{
	log_info "channelDown(): Executing 'channelDown'"
    sendWebosCommand(uri: 'tv/channelDown')
}


// handle commands
def volumeUp() 
{
	log_info "volumeUp(): Executing 'volumeUp'"
    sendWebosCommand(uri: 'audio/volumeUp')
}

def volumeDown() 
{
	log_info "volumeDown(): Executing 'volumeDown'"
    sendWebosCommand(uri: 'audio/volumeDown')
}

def setVolume(level) {
	log_info "setVolume(): Executing 'setVolume' with level '${level}'"
    sendWebosCommand(uri: 'audio/setVolume', payload: [volume: level])
}

def setLevel(level) { setVolume(level) }

def sendMuteEvent(muted) {
    def descriptionText = "${device.displayName} mute is ${muted}"
    if (txtEnabled) log_info "${descriptionText}" 
    sendEvent(name: "mute", value: muted, descriptionText: descriptionText)
}

def unmute() {
    log_info "Executing mute false"
    sendWebosCommand(uri: 'audio/setMute', payload: [mute: false], callback: { json ->
        log_debug("unmute(): reply is $json")
        if (json?.payload?.returnValue) sendMuteEvent("unmuted")
    })
}

def mute() {
    log_info "Executing: mute true"
    sendWebosCommand(uri: 'audio/setMute', payload: [mute: true], callback: { json ->
        log_debug("mute(): reply is $json")
        if (json?.payload?.returnValue) sendMuteEvent("muted")
    })
}

def externalInput(String input)
{
    if (state.nameToInputId && state.nameToInputId[input]) input = state.nameToInputId[input]
    
    sendWebosCommand(uri: "system.launcher/launch", payload: [id: input], callback: { json ->
        log_debug("externalInfo(): reply is $json")
    })
}

def enter() {
    def mouseDev = getMouseChild()
    mouseDev?.sendButton('ENTER')
	//return sendWebosCommand(uri: "com.webos.service.ime/sendEnterKey")
}

def back()
{
    def mouseDev = getMouseChild()
    mouseDev?.sendButton('BACK')
}

def up()
{
    def mouseDev = getMouseChild()
    mouseDev?.sendButton('UP')
}

def down()
{
    def mouseDev = getMouseChild()
    mouseDev?.sendButton('DOWN')
}

def left()
{
    def mouseDev = getMouseChild()
    mouseDev?.left()
}

def right()
{
    def mouseDev = getMouseChild()
    mouseDev?.right()
}

def myApps()
{
    sendWebosCommand(uri: 'system.launcher/launch', payload: [id: 'com.webos.app.discovery'])
}

def play()
{    
	sendWebosCommand(uri: "media.controls/play")
}

def pause()
{    
	sendWebosCommand(uri: "media.controls/pause")
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

def sendCommand(cmd)
{
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

def sendWebosCommand(Map params) 
{
	def id = params.id ?: ("command_" + state.sequenceNumber++)
	
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

private void parseStatus(state, json) {
    def rResp = false
    if ((state.power == "off") && !(json?.payload?.subscribed == true)) {
        // when TV has indicated power off, do not process status messages unless they are subscriptions
        log_warn("ignoring unsubscribed status updated during power off... message: $json")
        return
    }

    if (json?.payload?.returnValue == true) {
        // The last (valid) message sent by the TV when powering off is a subscription response for foreground app status with appId, windowId and processID all NULL
        if (json?.payload?.subscribed) {
            log_debug("appID: " + (description.contains("appId")?"T":"F") + "  windowId: " + (description.contains("windowId")?"T":"F") + "  processId: " + (description.contains("processId")?"T":"F"))
            if (description.contains("appId") && description.contains("windowId") && description.contains("processId")) {
                if ((json?.payload?.appId == null) || (json?.payload?.appId == "")) {
                    // The TV is powering off - change the power state, but leave the websocket to time out
                    sendPowerEvent("off", "physical")
                    sendEvent(name: "channelDesc", value: "")
                    sendEvent(name: "channelName", value: "")
                    sendEvent(name: "input", value: "")
                    log_info("Received POWER DOWN notification.")
                }
            }
        }
    }
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

