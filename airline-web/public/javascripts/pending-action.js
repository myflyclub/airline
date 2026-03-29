function handlePendingActions(pendingActions) {
    $('.nav-link .pendingAction').remove()
    $.each(pendingActions, function(index, pendingAction) {
        if (pendingAction === 'OLYMPICS_VOTE') {
            var $pendingActionDiv = $('<div style="position: absolute; right: -5px; bottom: -5px; height: 20px; width: 20px;" class="pendingAction"></div>').appendTo($('.nav-link[data-link="event"]'))
            var $icon = $('<img src="/assets/images/icons/exclamation.png">')
            $icon.attr('title', "Olympics Voting Active")
            $pendingActionDiv.append($icon)
        } else if (pendingAction === 'ALLIANCE_PENDING_APPLICATION') {
            var $pendingActionDiv = $('<div style="position: absolute; right: -5px; bottom: -5px; height: 20px; width: 20px;" class="pendingAction"></div>').appendTo($('.nav-link[data-link="alliance"]'))
            var $icon = $('<img src="/assets/images/icons/exclamation.png">')
            $icon.attr('title', "Pending Application")
            $pendingActionDiv.append($icon)
        } else if (pendingAction === 'OLYMPICS_PRIZE') {
            var $pendingActionDiv = $('<div style="position: absolute; right: -5px; bottom: -5px; height: 20px; width: 20px;" class="pendingAction"></div>').appendTo($('.nav-link[data-link="event"]'))
            var $icon = $('<img src="/assets/images/icons/exclamation.png">')
            $icon.attr('title', "Olympics prize available")
            $pendingActionDiv.append($icon)
        }
    })
}

function checkPendingActions() {
    if (activeAirline) {
        $.ajax({
            type: 'POST',
            url: "/broadcaster-direct/trigger-prompts-check/" + activeAirline.id,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(dummy) {
                // this simply triggers it directly on the Broadcaster, which will push the update via ws channel
            },
            error: function(jqXHR, textStatus, errorThrown) {
                    console.log(JSON.stringify(jqXHR));
                    console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }
}

