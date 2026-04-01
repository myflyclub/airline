var lastMessageId = -1
var firstAllianceMessageId = -1
var firstGeneralMessageId = -1

function updateChatTabs() {
	if (activeUser && activeUser.allianceName && activeUser.allianceRole != 'APPLICANT') {
		$("#allianceChatTab").text(activeUser.allianceName)
		$("#allianceChatTab").data('roomId', activeUser.allianceId)
		$("#allianceChatTab").show()
	} else {
		$("#allianceChatTab").hide()
	}
}

var ws
var _msgCount = 0
var _msgCountResetAt = 0

function sendChatMessage() {
    var msg = $('#chattext').val().trim()
    if (!activeAirline || !msg) return

    var now = Date.now()
    if (now - _msgCountResetAt > 60000) { _msgCount = 0; _msgCountResetAt = now }
    if (_msgCount >= 20) return

    _msgCount++
    $('#chattext').val("")

    var tab = $("li.tab-link.current").attr('data-tab')
    ws.send(JSON.stringify({ room: tab, text: msg, airlineId: activeAirline.id }))
}

function initChat() {
    var port = window.location.port
    var wsProtocol = window.location.protocol == "https:" ? "wss:" : "ws:"
    if (!port) port = window.location.protocol == "https:" ? 443 : 9000

    var wsUri = wsProtocol + "//" + window.location.hostname + ":" + port + "/chat"
    ws = new ReconnectingWebSocket(function() {
        return wsUri + "?reconnect=true&lastMessageId=" + lastMessageId
    })

    ws.onopen = function() {
        $("#live-chat i").css({"background-image": "url(\"/assets/images/icons/chat.svg\")"})
        $('#chat-box #chatBox-1 ul').append('<li class="status">Chat Connected</li>')
        adjustScroll()
    }

    ws.onclose = function() {
        $("#live-chat i").css({"background-image": "url(\"/assets/images/icons/chat-red.svg\")"})
        $('#chat-box #chatBox-1 ul').append('<li class="status">Chat Disconnected</li>')
        adjustScroll()
    }

    ws.onmessage = function(msg) {
        if (msg.data == "ping") return
        var r_msg = JSON.parse(msg.data)
        var $activeHistory = $("#chat-box .chat-history.current")
        $('#chat-box .chat-history.tab-content div.loading').remove()

        if (r_msg.type === 'newSession') {
            $('#chat-box .chat-history.tab-content ul li.message').remove()
            for (var i = 0; i < r_msg.messages.length; i++) pushMessage(r_msg.messages[i])
            if ($('.chat').is(':hidden') && r_msg.unreadMessageCount > 0) {
                $('#live-chat .notify-bubble').show(400).text(r_msg.unreadMessageCount)
            }
            adjustScroll()
        } else if (r_msg.type === 'previous') {
            for (var i = r_msg.messages.length - 1; i >= 0; i--) prependMessage(r_msg.messages[i])
            if (r_msg.messages.length == 0) {
                $activeHistory.find('ul').prepend('<li class="message"><b>No more previous messages</b></li>')
                $activeHistory.data("historyExhausted", true)
            }
        } else {
            var atBottom = Math.round($activeHistory[0].scrollHeight - $activeHistory[0].scrollTop) == $activeHistory[0].clientHeight
            pushMessage(r_msg)
            if ($('.chat').is(':hidden')) {
                $('#live-chat .notify-bubble').show(400).text(parseInt($('#live-chat .notify-bubble').text()) + 1)
            }
            if (atBottom) adjustScroll()
        }

        if ($("#live-chat h4").is(":visible") && r_msg.latest) ackChatId()
    }

    $('#chattext').on('keydown', function(e) {
        if (e.key === 'Enter') { e.preventDefault(); sendChatMessage() }
    })
    $('#chatSend').on('click', sendChatMessage)

    $(".chat-history").on('touchstart', function(e) {
        var startY = e.originalEvent.touches[0].pageY
        $(this).on('touchmove.chatscroll', function(e) {
            if (e.originalEvent.touches[0].pageY - startY > 30 && $(this).scrollTop() <= 0)
                handleScrollChatTop()
        }).one('touchend', function() { $(this).off('touchmove.chatscroll') })
    })

    $(".chat-history").on('wheel', function(e) {
        if (e.originalEvent.deltaY < 0 && $(this).scrollTop() <= 0) handleScrollChatTop()
    })
}

function handleScrollChatTop() {
    var $activeHistory = $("#chat-box .chat-history.current")
    if ($activeHistory.data('historyExhausted')) return
    var $chatTab = $('#chat-box .chat-history.tab-content')
    if ($chatTab.find('.loading').length) return

    $chatTab.prepend("<div class='loading'><img src='/assets/images/icons/spinning-wheel.gif'></div>")
    var activeRoomId = parseInt($('#live-chat .tab-link.current').data('roomId'))
    var firstMessageId = activeRoomId == 0 ? firstGeneralMessageId : firstAllianceMessageId
    ws.send(JSON.stringify({ airlineId: activeAirline.id, firstMessageId: firstMessageId, type: "previous", roomId: activeRoomId }))
}

const monthNames = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]

function buildPrefix(r_msg) {
    var d = new Date(r_msg.timestamp)
    var hh = String(d.getHours()).padStart(2, '0')
    var mm = String(d.getMinutes()).padStart(2, '0')
    return "[" + monthNames[d.getMonth()] + " " + d.getDate() + " " + hh + ":" + mm + "] " + r_msg.airlineName + ": "
}

function prependMessage(r_msg) {
    var box = r_msg.allianceRoomId ? '#chatBox-2' : '#chatBox-1'
    var tracker = r_msg.allianceRoomId ? 'firstAllianceMessageId' : 'firstGeneralMessageId'
    var first = r_msg.allianceRoomId ? firstAllianceMessageId : firstGeneralMessageId
    if (first != -1 && r_msg.id >= first) return
    $('#chat-box ' + box + ' ul').prepend('<li class="message">' + buildPrefix(r_msg) + htmlEncode(r_msg.text) + '</li>')
    if (r_msg.allianceRoomId) firstAllianceMessageId = r_msg.id
    else firstGeneralMessageId = r_msg.id
}

function pushMessage(r_msg) {
    var box = r_msg.allianceRoomId ? '#chatBox-2' : '#chatBox-1'
    $('#chat-box ' + box + ' ul').append('<li class="message">' + buildPrefix(r_msg) + htmlEncode(r_msg.text) + '</li>')
    if (!r_msg.allianceRoomId && (firstGeneralMessageId == -1 || r_msg.id < firstGeneralMessageId)) firstGeneralMessageId = r_msg.id
    if (r_msg.allianceRoomId && (firstAllianceMessageId == -1 || r_msg.id < firstAllianceMessageId)) firstAllianceMessageId = r_msg.id
    if (r_msg.id > lastMessageId) lastMessageId = r_msg.id
}

function ackChatId() {
    if (activeAirline && lastMessageId > -1)
        ws.send(JSON.stringify({ airlineId: activeAirline.id, ackId: lastMessageId, type: "ack" }))
}

function adjustScroll() {
    $(".chat-history").each(function() {
        this.scrollTop = this.scrollHeight
    })
}
