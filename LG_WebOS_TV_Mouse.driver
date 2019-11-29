/*
 * LG Mouse Web Socket Driver
 *
 * 
 */

import groovy.transform.Field

metadata {
    definition(
        name: "LG Mouse WebSocket Driver",
        namespace: "asj",
        author: "asj",
        importUrl: ""
    ) {        
        command "setMouseURI", ["string"]
        command "getURI"
        command "close"

        command "click"
        command "sendButton", ["string"]
        command "move", ["number", "number"]
        command "moveAbsolute", ["number", "number"]
        command "scroll", ["number", "number"]
		command "ok"
		command "home"
        command "left"
        command "right"
        command "red"
        command "blue"
        command "yellow"
        command "green"
    }
}

@Field List = [
        "HOME",
        "BACK",
        "UP",
        "DOWN",
        "LEFT",
        "RIGHT",
        "RED",
        "BLUE",
        "YELLOW",
        "GREEN",
]

@Field queue

preferences {
    section("URIs") {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(1800, logsOff)
}

def getURI() {
    if ((now() - (state.refreshURIAt ?: 15001)) > 15000) {
        parent.getMouseURI()
        state.refreshURIAt = now()
    }
}

def close() {
    log.debug "close(): Closing the socket"
    state.socketStatus = "closing"
    interfaces.webSocket.close()
    // Clear refreshURIAt to allow getURI to work
    // for testing
    state.refreshURIAt = 0
}

def setMouseURI(uri) {
    if ((uri != state.uri) || (state.socketStatus == "closing")) {
        reconnect(uri)
    }
}

def sendMessage(String msg) {
    if (state.socketStatus == "open") {
        interfaces.webSocket.sendMessage(msg)
    } else {
        reconnect()
        if (!state.queue) state.queue = []
        state.queue += [[now(), msg]]
        log.debug("Queue length: ${state.queue.size()}")
    }
}

def click() {
    sendMessage("type:click\n\n")
}

def sendButton(String name) {
    sendMessage("type:button\nname:${name}\n\n")
}

def move(x, y) {
    sendMessage("type:move\ndx:${x}\ndy:${y}\ndrag: 0\n\n")
}

def moveAbsolute(x, y) {
    // Go to 0,0
    for(int i = 0; i < 80; i++) {
        move(-10,-10)
    }
    int dx = x/10
    int dy = y/10
    int max = dx > dy ? dx : dy
    for(int i = 0; i < max; i++) {
        move(dx > 0 ? 10 : 0, dy > 0 ? 10 : 0)
        dx = dx > 0 ? dx - 1 : 0
        dy = dy > 0 ? dy - 1 : 0
    }
    move(dx%10, dy%10)
}



def scroll(dx, dy) {
    sendMessage("type:scroll\ndx:${dx}\ndy:${dy}\n\n");
}

def parse(status) {
    log.debug("Parse update: $status")
}

def reconnect(new_uri = null) {
    log.debug "reconnect(): new uri: ${new_uri} state: ${state.socketStatus}"
    if (!new_uri && (state.socketStatus != "closing")) return
    
    if (new_uri) state.uri = new_uri
    
    close()
    try {
        if (logEnable) log.debug "Pointer Connecting to: ${state.uri}"
        interfaces.webSocket.connect(state.uri)
        state.socketStatus = "opening"
    } catch (e) {
        log.info "Failed to open mouse socket: $e"
    }
}

def flushQueue() {
    if (state.socketStatus == "open" && state.queue) {
        log.debug("Queue length: ${state.queue.size()}")
        def curQueue = state.queue
        state.queue = []
        def flushed = 0
        def skipped = 0
        curQueue.each { it ->
            //log.debug("Got: $it")
            def queuedAt = it[0]
            def msg = it[1]
            //log.debug("Looking at: $it age: ${now() - queuedAt}")
            if ((now() - queuedAt) < 15000) {
                //log.debug("Sending Queued: $msg")
                sendMessage(msg)
                flushed++
            } else {
                skipped++
            }
        }
        log.debug "flushQueue(): sent $flushed skipped (too old): $skipped"
    }
}

def webSocketStatus(String status) {
    log.debug("Web Socket update: $status")
    if (status.startsWith("status:")) {
        state.socketStatus = status.replace("status: ", "")
    } else if (status.startsWith("failure:")) {
        state.socketStatus = "closing"
    }
    log.debug("New status: ${state.socketStatus}")
    
    if (state.socketStatus == "open" && state.queue) {
        runInMillis(250, flushQueue)
    }
    if (state.socketStatus == "closing") {
        getURI()
    }
}

def ok() {
    click()
}


def home() {
    sendButton("HOME")
}

def left() {
    sendButton("LEFT")
}

def right() {
    sendButton("RIGHT")
}

def red() {
    sendButton("RED")
}

def blue() {
    sendButton("BLUE")
}

def yellow() {
    sendButton("YELLOW")
}

def green() {
    sendButton("GREEN")
}





