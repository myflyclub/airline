var wsUri = (function() {
    var protocol = window.location.protocol == "https:" ? "wss:" : "ws:"
    var port = window.location.port || (window.location.protocol == "https:" ? 443 : 80)
    return protocol + "//" + window.location.hostname + ":" + port + "/wsWithActor"
})()

var websocket
var selectedAirlineId
var reconnectAttempts = 0
var maxReconnectDelay = 30000

function checkWebSocket(airlineId) {
    if (!websocket || websocket.readyState === WebSocket.CLOSED) {
        connectWebSocket(airlineId)
    }
}

function connectWebSocket(airlineId) {
    websocket = new WebSocket(wsUri)
    websocket.onopen = function() {
        reconnectAttempts = 0
        wsSend(airlineId)
        console.log("websocket open for airline " + airlineId)
    }
    websocket.onclose = function() {
        if (selectedAirlineId) {
            var delay = Math.min(1000 * Math.pow(2, reconnectAttempts), maxReconnectDelay)
            reconnectAttempts++
            setTimeout(function() { connectWebSocket(selectedAirlineId) }, delay)
        }
    }
    websocket.onmessage = function(evt) {
        var json = JSON.parse(evt.data)
        if (json.ping) { console.debug("ping : " + json.ping); return }
        console.log("websocket message : " + evt.data)
        if (json.messageType == "cycleInfo") {
            updateTime(json.cycle, json.fraction, json.cycleDurationEstimation)
        } else if (json.messageType == "cycleCompleted") {
            if (selectedAirlineId) {
                var jitter = Math.floor(Math.random() * 8000)
                setTimeout(function() { updateAirlineInfo(selectedAirlineId); loadAirportsDynamic() }, jitter)
            }
        } else if (json.messageType == "broadcastMessage") {
            queuePrompt("broadcastMessagePopup", json.message)
        } else if (json.messageType == "airlineMessage") {
            queuePrompt("airlineMessagePopup", json.message)
        } else if (json.messageType == "notice") {
            queueNotice(json)
        } else if (json.messageType == "tutorial") {
            queueTutorialByJson(json)
        } else if (json.messageType == "pendingAction") {
            handlePendingActions(json.actions)
        } else {
            console.warn("unknown message type " + evt.data)
        }
    }
    websocket.onerror = function(evt) { console.log(evt) }
}

function initWebSocket(airlineId) {
    selectedAirlineId = airlineId
    if (!websocket || websocket.readyState === WebSocket.CLOSED) {
        connectWebSocket(airlineId)
    }
}

function wsSend(message) {
    if (websocket.readyState === 1) {
        websocket.send(message)
    } else {
        setTimeout(function() { wsSend(message) }, 1000)
    }
}
