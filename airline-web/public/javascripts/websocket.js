var wsUri = (function() {
    var protocol = window.location.protocol == "https:" ? "wss:" : "ws:"
    var port = window.location.port || (window.location.protocol == "https:" ? 443 : 80)
    return protocol + "//" + window.location.hostname + ":" + port + "/wsWithActor"
})()

var websocket
var selectedAirlineId
var reconnectAttempts = 0
var reconnectTimer = null
var cycleDurationMs = 0
var maxReconnectDelay = 60000
var connectionOpened = false
// 'idle'    — no refresh needed or scheduled
// 'needed'  — cycleCompleted fired while tab hidden; refresh on next focus
// 'pending' — refresh is queued via setTimeout
var refreshState = 'idle'

function scheduleRefresh(minDelay, maxJitter) {
    if (refreshState === 'pending') return
    refreshState = 'pending'
    var capturedId = selectedAirlineId
    var jitter = Math.floor(Math.random() * maxJitter) + minDelay
    setTimeout(function() {
        refreshState = 'idle'
        updateAirlineInfo(capturedId)
        loadAirportsDynamic()
        loadNotificationBadge()
    }, jitter)
}

document.addEventListener('visibilitychange', function() {
    clearInterval(currentTickTimer)
    if (!document.hidden && tickTimerCreator) {
        console.log("Recreating tick timer!")
        currentTickTimer = tickTimerCreator()
    }
    if (!document.hidden && refreshState === 'needed' && selectedAirlineId) {
        scheduleRefresh(500, 4000)
    }
})

function checkWebSocket(airlineId) {
    if (!websocket || websocket.readyState === WebSocket.CLOSED) {
        connectWebSocket(airlineId)
    }
}

function connectWebSocket(airlineId) {
    if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
    }
    if (websocket) {
        websocket.onclose = null
        if (websocket.readyState !== WebSocket.CLOSED) {
            websocket.close()
        }
    }
    connectionOpened = false
    websocket = new WebSocket(wsUri)
    websocket.onopen = function() {
        connectionOpened = true
        var isReconnect = reconnectAttempts > 0
        reconnectAttempts = 0
        wsSend(airlineId)
        console.log("websocket open for airline " + airlineId)
        if (isReconnect && selectedAirlineId && !document.hidden) {
            scheduleRefresh(500, 4000)
        }
    }
    websocket.onclose = function() {
        if (selectedAirlineId && connectionOpened) {
            var delay = Math.min(5000 * Math.pow(2, reconnectAttempts), maxReconnectDelay)
            reconnectAttempts++
            reconnectTimer = setTimeout(function() {
                reconnectTimer = null
                connectWebSocket(selectedAirlineId)
            }, delay)
        } else if (!connectionOpened) {
            console.warn("websocket rejected (session expired or server restarted) — reload the page to reconnect")
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
                if (document.hidden) {
                    if (refreshState !== 'pending') refreshState = 'needed'
                } else {
                    scheduleRefresh(5000, 25000)
                }
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
    var changed = selectedAirlineId !== airlineId
    selectedAirlineId = airlineId
    if (changed) reconnectAttempts = 0
    if (!websocket || websocket.readyState === WebSocket.CLOSED || changed) {
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
