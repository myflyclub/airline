function adminAction(action, targetUserId, callback) {
 adminActionWithData(action, targetUserId, {}, callback)
}

function adminActionWithData(action, targetUserId, data, callback) {
	var url = "/admin-action/" + action + "/" + targetUserId
	var selectedAirlineId =  $("#rivalDetailsModal .adminActions").data("airlineId")

	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
            Rivals.show(selectedAirlineId)
            if (callback) {
                callback()
            }
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function adminMultiAction(action, targetUserIds, callback) {
	var url = "/admin-multi-action/" + action
	var selectedAirlineId =  $("#rivalDetailsModal .adminActions").data("airlineId")

    var data = {
        "userIds" : targetUserIds
     }
	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	        Rivals.show(selectedAirlineId)
	        if (callback) {
                callback()
            }
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function invalidateImage(imageType) {
	var url = "/admin/invalidate-image/" + activeAirportId +  "/" + imageType
	$.ajax({
		type: 'POST',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
            showAirportDetails(activeAirportId)
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}
function isAdmin() {
    return activeUser && activeUser.adminStatus
}

function isSuperAdmin() {
    return activeUser && activeUser.adminStatus === "SUPER_ADMIN"
}

function initAdminActions() {
    if (isAdmin()) {
        $(".adminActions").show()
    } else {
        $(".adminActions").hide()
    }
    if (isSuperAdmin()) {
        $(".superAdminActions").show()
    } else {
        $(".superAdminActions").hide()
    }
}


function showAdminActions(airline) {
    $("#rivalDetailsModal .adminActions").data("userId", airline.userId)
    $("#rivalDetailsModal .adminActions").data("airlineId", airline.id)
    $("#rivalDetailsModal .adminActions .username").text(airline.username)
    $("#rivalDetailsModal .adminActions .userId").text(airline.userId)

    if (airline.userModifiers) {
        $("#rivalDetailsModal .adminActions .userModifiers").text(airline.userModifiers.join(";"))
    } else {
        $("#rivalDetailsModal .adminActions .userModifiers").text('-')
    }
    if (airline.airlineModifiers) {
        $("#rivalDetailsModal .adminActions .airlineModifiers").text(airline.airlineModifiers.join(";"))
    } else {
        $("#rivalDetailsModal .adminActions .airlineModifiers").text('-')
    }
    $("#rivalDetailsModal .adminActions .ips").empty()
    $("#rivalDetailsModal .adminActions .uuids").empty()
    $.ajax({
        type: 'GET',
        url: "/admin/user-ips/" + airline.userId,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(ips) {
            $.each(ips, function(index, ipEntry) {
                var ip = ipEntry[0]
                var occurrence = ipEntry[1]
                $("#rivalDetailsModal .adminActions .ips").append("<div style='padding-right : 10px; float: left' class='clickable' onclick='showAirlinesByIp(\"" + ip + "\")'>" + ip + "(" + occurrence + ")</div>")
            })
            $("#rivalDetailsModal .adminActions .ips").append("<div style='clear : both;'></div>")

        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
    $.ajax({
        type: 'GET',
        url: "/admin/user-uuids/" + airline.userId,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $.each(result, function(index, entry) {
                var uuid = entry[0]
                var occurrence = entry[1]
                $("#rivalDetailsModal .adminActions .uuids").append("<div style='padding-right : 10px; float: left' class='clickable' onclick='showAirlinesByUuid(\"" + uuid + "\")'>" + uuid.substring(0, 8) + "(" + occurrence + ")</div>")
            })
            $("#rivalDetailsModal .adminActions .uuids").append("<div style='clear : both;'></div>")

        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });

}

function showAirlinesByIp(ip) {
    $.ajax({
        type: 'GET',
        url: "/admin/ip-airlines/" + ip,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $("#airlinesByIpModal .ip").text(ip)
            $("#airlinesByIpModal .airlineByIpTable div.table-row").remove()
            $.each(result, function(index, entry) {
               var $row = $("<div class='table-row'></div>")
               var airline = entry.airline
               var modifiersSpan = getUserModifiersSpan(entry.userModifiers) + getAirlineModifiersSpan(entry.airlineModifiers)
               if (modifiersSpan === "") {
                   modifiersSpan = "<span>-</span>"
               }
               $row.append("<div class='cell'><input type='checkbox' checked='checked' data-user-id='" + entry.userId + "' data-airline-id='" + entry.airlineId + "'></div>")
               $row.append("<div class='cell clickable' onclick='Rivals.showDetails(null," + entry.airlineId + "); closeModal($(\"#airlinesByUuidModal\"))'>" + getAirlineLogoImg(entry.airlineId) +  entry.airlineName + "</div>")
               $row.append("<div class='cell'>" + (entry.hqAirport ? getAirportText(entry.hqAirport.city, entry.hqAirport.iata) : "-") + "</div>")
               $row.append("<div class='cell'>" + entry.username + getUserLevelImg(entry.userLevel) + "</div>")
               $row.append("<div class='cell'>" + entry.userStatus + "</div>")
               $row.append("<div class='cell'>" + modifiersSpan + "</div>")
               $row.append("<div class='cell'>" + entry.lastUpdated + "</div>")
               $row.append("<div class='cell' align='right'>" + entry.occurrence + "</div>")
               $("#airlinesByIpModal .airlineByIpTable").append($row)
            })
            $("#airlinesByIpModal").fadeIn(500)
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });

}

function showAirlinesByUuid(uuid) {
    $.ajax({
        type: 'GET',
        url: "/admin/uuid-airlines/" + uuid,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $("#airlinesByUuidModal .uuid").text(uuid)
            $("#airlinesByUuidModal .airlineByUuidTable div.table-row").remove()
            $.each(result, function(index, entry) {
                var $row = $("<div class='table-row'></div>")
                var airline = entry.airline
                var modifiersSpan = getUserModifiersSpan(entry.userModifiers) + getAirlineModifiersSpan(entry.airlineModifiers)
               if (modifiersSpan === "") {
                   modifiersSpan = "<span>-</span>"
               }
                $row.append("<div class='cell'><input type='checkbox' checked='checked' data-user-id='" + entry.userId + "' data-airline-id='" + entry.airlineId + "'></div>")
                $row.append("<div class='cell clickable' onclick='Rivals.showDetails(null," + entry.airlineId + "); closeModal($(\"#airlinesByUuidModal\"))'>" + getAirlineLogoImg(entry.airlineId) +  entry.airlineName + "</div>")
                $row.append("<div class='cell'>" + (entry.hqAirport ? getAirportText(entry.hqAirport.city, entry.hqAirport.iata) : "-") + "</div>")
                $row.append("<div class='cell'>" + entry.username + getUserLevelImg(entry.userLevel) + "</div>")
                $row.append("<div class='cell'>" + entry.userStatus + "</div>")
                $row.append("<div class='cell'>" + modifiersSpan + "</div>")
                $row.append("<div class='cell'>" + entry.lastUpdated + "</div>")
                $row.append("<div class='cell' align='right'>" + entry.occurrence + "</div>")
                 $("#airlinesByUuidModal .airlineByUuidTable").append($row)
            })
            $("#airlinesByUuidModal").fadeIn(500)
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });

}

function banWarning() {
    adminAction("warn", $("#rivalDetailsModal .adminActions").data("userId"))
}

function ban() {
    adminAction("ban", $("#rivalDetailsModal .adminActions").data("userId"))
}
function banAndReset() {
    adminAction("ban-reset", $("#rivalDetailsModal .adminActions").data("userId"))
}
function nerf() {
    adminAction("nerf", $("#rivalDetailsModal .adminActions").data("userId"))
}
function restore() {
    adminAction("restore", $("#rivalDetailsModal .adminActions").data("userId"))
}
function banChat() {
    adminAction("ban-chat", $("#rivalDetailsModal .adminActions").data("userId"))
}
function setBannerWinner() {
    adminActionWithData("set-banner-winner", $("#rivalDetailsModal .adminActions").data("userId"),
    {
        "strength" : parseInt($("#rivalDetailsModal .bannerLoyaltyBonus").val()),
        "airlineId" : parseInt($("#rivalDetailsModal .adminActions").data("airlineId")) //airline specific
    })
}

function setUserLevel() {
    adminActionWithData("set-user-level", $("#rivalDetailsModal .adminActions").data("userId"),
    {
        "level" : parseInt($("#rivalDetailsModal .setUserLevel").val()),
    })
}

function adminSetUsers(action, $modal) {
    var targetUserIds = []
    $.each($modal.find('input:checked'), function(index, input) {
        targetUserIds.push($(input).data('userId'))
    })

    adminMultiAction(action, targetUserIds, function() {
        closeModal($modal)
    })
}

function invalidateCustomization() {
    var airlineId = $("#rivalDetailsModal .adminActions").data("airlineId")
    var url = "/admin/invalidate-customization/" + airlineId
    $.ajax({
        type: 'POST',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            Rivals.show(selectedAirlineId)
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function switchUser() {
    adminAction("switch", $("#rivalDetailsModal .adminActions").data("userId"), function() { loadUser(false)})
}

function promptAirlineMessage() {
    var selectedAirlineId =  $("#rivalDetailsModal .adminActions").data("airlineId")
    var airline = Rivals.loadedById[selectedAirlineId]
    $('#sendAirlineMessageModal .airlineName').text(airline.name)
    $('#sendAirlineMessageModal .sendMessage').val('')
    $('#sendAirlineMessageModal').fadeIn(500)
}

function sendAirlineMessage() {
    var selectedAirlineId = $("#rivalDetailsModal .adminActions").data("airlineId")
    var url = "/admin/send-airline-message/" + selectedAirlineId

    var data = { "message" : $('#sendAirlineMessageModal .sendMessage').val() }
    $.ajax({
        type: 'PUT',
        url: url,
        data: JSON.stringify(data),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            closeModal($('#sendAirlineMessageModal'))
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function promptBroadcastMessage() {
    $('#sendBroadcastMessageModal .sendMessage').val('')
    $('#sendBroadcastMessageModal').fadeIn(500)
}

function sendBroadcastMessage() {
    var url = "/admin/send-broadcast-message"
    var data = { "message" : $('#sendBroadcastMessageModal .sendMessage').val() }
    	$.ajax({
    		type: 'PUT',
    		url: url,
    	    data: JSON.stringify(data),
    	    contentType: 'application/json; charset=utf-8',
    	    dataType: 'json',
    	    success: function(result) {
                closeModal($('#sendBroadcastMessageModal'))
    	    },
            error: function(jqXHR, textStatus, errorThrown) {
    	            console.log(JSON.stringify(jqXHR));
    	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
    	    }
    	});
}
