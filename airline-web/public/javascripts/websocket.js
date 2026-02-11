var port = window.location.port

var wsProtocol

if (window.location.protocol == "https:"){
	wsProtocol = "wss:"
	if (!port) {
		port = 443
	}
} else {
	wsProtocol = "ws:"
	if (!port) {
		port = 80
	}
}

var wsUri = wsProtocol + "//" +  window.location.hostname + ":" + port + "/wsWithActor";
var websocket;
var selectedAirlineId
var reconnectAttempts = 0
var maxReconnectDelay = 30000

function checkWebSocket(selectedAirlineId) {
    if (!websocket || websocket.readyState === WebSocket.CLOSED) {
        connectWebSocket(selectedAirlineId)
    }
}

function connectWebSocket(airlineId) {
	websocket = new WebSocket(wsUri);
	websocket.onopen = function(evt) {
		reconnectAttempts = 0
		sendMessage(airlineId)  //send airlineId to indicate we want to listen to messages for this airline Id
		console.log("successfully open socket on airline " + airlineId)
	};
	websocket.onclose = function(evt) { onClose(evt) };
	websocket.onmessage = function(evt) { onMessage(evt) };
	websocket.onerror = function(evt) { onError(evt) };
}

function initWebSocket(airlineId) {
	selectedAirlineId = airlineId
	connectWebSocket(airlineId)
}

function onClose(evt) {
	if (selectedAirlineId) {
		var delay = Math.min(1000 * Math.pow(2, reconnectAttempts), maxReconnectDelay)
		reconnectAttempts++
		// console.log("WebSocket closed. Reconnecting in " + delay + "ms (attempt " + reconnectAttempts + ")")
		setTimeout(function() {
			connectWebSocket(selectedAirlineId)
		}, delay)
	}
}
function onMessage(evt) { //right now the message is just the cycle #, so refresh the panels
	var json = JSON.parse(evt.data)
	if (json.ping) { //ok
	    console.debug("ping : " + json.ping)
        return
    }
	console.log("websocket received message : " + evt.data)
	
	if (json.messageType == "cycleInfo") { //update time
		updateTime(json.cycle, json.fraction, json.cycleDurationEstimation)
	} else if (json.messageType == "cycleCompleted") {
		if (selectedAirlineId) {
			var jitter = Math.floor(Math.random() * 8000)
			setTimeout(function() { updateAirlineInfo(selectedAirlineId) }, jitter)
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
function onError(evt) {
	console.log(evt)
} 

function sendMessage(message) {
	if (websocket.readyState === 1) {
		websocket.send(message);
	} else {
		setTimeout(function() { sendMessage(message) }, 1000)
	}
}