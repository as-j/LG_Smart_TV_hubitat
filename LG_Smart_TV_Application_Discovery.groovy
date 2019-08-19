/**
 *  LG Smart TV Discovery
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
 *
***See Release Notes at the bottom***
***********************************************************************************************************************/
public static String version()      {  return "v0.2.4"  }

definition(
    name: "LG Smart TV Discovery 2012+",
    namespace: "ekim",
    author: "Sam Lalor",
    description: "Discovers an LG Smart TV (2012+)",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)


preferences 
{
	page(name:"televisionDiscovery", title:"LG TV Setup", content:"televisionDiscovery", refreshTimeout:5)
	page(name:"televisionAuthenticate", title:"LG TV Pairing", content:"televisionAuthenticate", refreshTimeout:5)
}

def televisionDiscovery() 
{
    int tvRefreshCount = !state.bridgeRefreshCount ? 0 : state.bridgeRefreshCount as int
    state.bridgeRefreshCount = tvRefreshCount + 1
    def refreshInterval = 10

    def options = televisionsDiscovered() ?: []
    def numFound = options.size() ?: 0

    if(!state.subscribe) {
        subscribe(location, null, deviceLocationHandler, [filterEvents:false])    
        state.subscribe = true
    }

    // Television discovery request every 15 seconds
    if((tvRefreshCount % 5) == 0) {
        findTv()
    }

    return dynamicPage(name:"televisionDiscovery", title:"LG TV Search Started!", nextPage:"televisionAuthenticate", refreshInterval:refreshInterval, uninstall: true){
        section("Please wait while we discover your LG TV. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered."){
            input "selectedTv", "enum", required:false, title:"Select LG TV (${numFound} found)", multiple:false, options:options
        }
    }
}

def televisionAuthenticate() 
{
    if (!selectedTv?.trim()) {
        log.warn("No TV selected")
        return televisionDiscovery()
    }
    def settingsInfo = selectedTv.split("!")
    if (settingsInfo[3] == "NETCAST") {
        tvRequestPairingKey()
    
        return dynamicPage(name:"televisionAuthenticate", title:"LG TV Pairing Started!", nextPage:"", install:true){
            section("We sent a pairing request to your TV. Please enter the pairing key and click Done."){
        	    input "pairingKey", "string", defaultValue:"DDTYGF", required:true, title:"Pairing Key", multiple:false
            }
        }
    } else {
        return dynamicPage(name:"televisionAuthenticate", title:"LG TV Search Started!", nextPage:"", install:true){
            section("WebOS TVs can not be paired from the application. The driver will attempt to pair with the TV when it is initialized. Please authorize the pairing from the TV using the TV remote control. Please click Done."){}
        }
    }
}

Map televisionsDiscovered() 
{
	def vbridges = getLGTvs()
	def map = [:]
	vbridges.each {
    	log.debug "Discovered List: $it"
        def value = "$it"
        def key = it.value
        
        if (key.contains("!")) {
            def settingsInfo = key.split("!")
            def deviceIp = convertHexToIP(settingsInfo[1])
			value = "LG TV (${deviceIp} - ${settingsInfo[3]})"
        }
        
        map["${key}"] = value
	}
	map
}

def installed() 
{
	log.debug "Installed with settings: ${settings}"
    
	initialize()
}

def updated() 
{
	log.debug "Updated with settings: ${settings}"

	initialize()
}

def initialize() 
{
	// Remove UPNP Subscription
	unsubscribe()
	state.subscribe = false
    
    addDevice()

    log.debug "Application Initialized"
    log.debug "Selected TV: $selectedTv"
}

def addDevice()
{ 
  def deviceSettings = selectedTv.split("!")
  def macAddress = deviceSettings[0]
  def ipAddressHex = deviceSettings[1]
  def ipAddress = convertHexToIP(ipAddressHex)
  def pairKey = "$pairingKey"
	if (pairKey == null) { pairKey = "x"}
  def tvType = deviceSettings[3]

	def dni = "${ipAddressHex}_${macAddress}_${tvType}_${convertPortToHex(8080)}"
  if (tvType == "WEBOS") { dni = "${ipAddressHex}_${macAddress}_${tvType}_${convertPortToHex(3000)}" }

   log.debug("LG Smart TV Discovery - addDevice - ip: ${ipAddress}  mac: ${macAddress} type: ${tvType}  pairKey: ${pairKey}  dni: ${dni}")
    
  def d = getChildDevice(dni)
  if(!d) 
  {
  	log.debug "Hub: " + location.hubs[0].id
  	log.debug "Ekim: " + ipAddress + pairKey
    addChildDevice("ekim", "LG Smart TV", dni, null, [name: "LG Smart TV", isComponent: true, label: "LG Smart TV"])
	d = getChildDevice(dni)
	d.updateSetting("televisionIp",[type:"text", value:ipAddress])
	d.updateSetting("televisionMac",[type:"text", value:macAddress])
	d.updateSetting("televisionType",[type:"text", value:tvType])
	if (tvType == "NETCAST") {
		d.updateSetting("pairingKey",[type:"text", value:"${pairKey}"])
	}
//	d.setParameters(ipAddress,macAddress,tvType,pairkey)

	log.debug "created ${d.displayName} with id ${d.deviceNetworkId}"
  } 
  else 
  {
    log.debug "Device with id ${dni} already created - Updating"
	d.updateSetting("televisionIp",[type:"text", value:ipAddress])
	d.updateSetting("televisionMac",[type:"text", value:macAddress])
	d.updateSetting("televisionType",[type:"text", value:tvType])
	if (tvType == "NETCAST") {
		d.updateSetting("pairingKey",[type:"text", value:"${pairKey}"])
	}
//	d.setParameters(ipAddress,macAddress,tvType,pairkey)
    log.debug "updated ${d.displayName} with id ${d.deviceNetworkId}"
  }
  getLgDevice().updated()
}

def getLgDevice(){
	log.debug("getLgDevice")
	def childDevices = getChildDevices()
	log.debug("childDevices: ${childDevices}")
	def LgDevice = childDevices[0]
	log.debug("childDevices: ${LgDevice}")
	return LgDevice
}

def castLgDeviceStates(){
  	log.debug("Casting to State Variables")
    state.televisionType = televisionType
    log.debug("Setting state.televisionType ${state.televisionType}")
    state.televisionIp = televisionIp
    log.debug("Setting state.televisionIp ${state.televisionIp}")
    state.televisionMac = televisionMac
    log.debug("Setting state.televisionMac ${state.televisionMac}")
    state.pairingKey = pairingKey ?: ""
    log.debug("Setting state.pairingKey ${state.pairingKey}")
    if (getLgDevice()){
        log.debug("Found a Child LG ${getLgDevice().label}")
    }
    else{
     	log.debug("Did not find a Parent LG")
    }
}

// Returns a list of the found LG TVs from UPNP discovery
def getLGTvs()
{
	state.televisions = state.televisions ?: [:]
}

// Sends out a UPNP request, looking for the LG TV. Results are sent to [deviceLocationHandler]
private findTv() 
{
    // send ssdp search for NetCast TVs (2012 - 2015 models)
    sendHubCommand(new hubitat.device.HubAction("lan discovery urn:schemas-udap:service:netrcu:1", hubitat.device.Protocol.LAN))
    // send ssdp search for WebOS TVs (2016 and newer models)
    sendHubCommand(new hubitat.device.HubAction("lan discovery urn:lge-com:service:webos-second-screen:1", hubitat.device.Protocol.LAN))
    log.debug "Looking for TV's"
}

// Parses results from [findTv], looking for the specific UPNP result that clearly identifies the TV we can use
def deviceLocationHandler(evt) 
{
	log.debug "Device Location Event: " + evt.inspect()
	def upnpResult = parseEventMessage(evt.description)
    log.debug "upnp: $upnpResult"
    
    def hub = evt?.hubId
    log.debug "hub: $hub"
    
    if (upnpResult?.ssdpUSN?.contains("urn:lge-com:service:webos-second-screen:1")) {
        // found a WebOS TV
        log.debug "Found WebOS TV: ${upnpResult}"
        state.televisions << [device:"${upnpResult.mac}!${upnpResult.ip}!${hub}!WEBOS"]
    }
    if (upnpResult?.ssdpPath?.contains("udap/api/data")) {
        // found a NetCast TV
        log.debug "Found TV: ${upnpResult}"
        state.televisions << [device:"${upnpResult.mac}!${upnpResult.ip}!${hub}!NETCAST"]
    }
}

// Display pairing key on TV
private tvRequestPairingKey()
{
	log.debug "Display pairing key"
    
    def deviceSettings = selectedTv.split("!")
    def ipAddressHex = deviceSettings[1]
    def ipAddress = convertHexToIP(ipAddressHex)
    
    if (deviceSettings[3] == "NETCAST") {
        // Netcast TV pairing
        def reqKey = "<?xml version=\"1.0\" encoding=\"utf-8\"?><auth><type>AuthKeyReq</type></auth>"
    
        def httpRequest = [
      	    method:		"POST",
            path: 		"/roap/api/auth",
            body:		"$reqKey",
            headers:	[
        	    HOST:			"$ipAddress:8080",
                "Content-Type":	"application/atom+xml",
            ]
	    ]

	    log.debug "HTTP REQUEST"
        log.debug "${shttpRequest}"
    
	    def hubAction = new hubitat.device.HubAction(httpRequest)
	    sendHubCommand(hubAction)
    } else {
        // WebOS pairing - WebSockets can not be opened from an APP - Defer pairing to the device initialization
    }
}

private def parseEventMessage(String description) 
{
	def event = [:]
	def parts = description.split(',')
	parts.each { part ->
		part = part.trim()
		if (part.startsWith('devicetype:')) {
			def valueString = part.split(":")[1].trim()
			event.devicetype = valueString
		}
		else if (part.startsWith('mac:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.mac = valueString
			}
		}
		else if (part.startsWith('networkAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ip = valueString
			}
		}
		else if (part.startsWith('ssdpPath:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ssdpPath = valueString
				log.debug "Found ssdpPath: " + valueString
			}
		}
		else if (part.startsWith('ssdpUSN:')) {
			part -= "ssdpUSN:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpUSN = valueString
				log.debug "Found ssdpUSN: " + valueString
			}
		}
		else if (part.startsWith('ssdpTerm:')) {
			part -= "ssdpTerm:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpTerm = valueString
				log.debug "Found ssdpTerm: " + valueString
			}
		}
	}

	event
}

private Integer convertHexToInt(hex) 
{
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) 
{
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private String convertIPtoHex(ipAddress) 
{ 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    trace("IP address entered is $ipAddress and the converted hex code is $hex")
    return hex
}

private String convertPortToHex(port) 
{
	String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}
private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}

def uninstalled() {
	removeChildDevices(getChildDevices())
}

/***********************************************************************************************************************
*
* Release Notes
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
* fixed - parameters not properly passed to driver
*
* 0.2.0
* Modified to support LG WebOS Smart Tv
*
* 0.1.1
* Ported LG Smart Tv from Smarththings
*
***********************************************************************************************************************/
