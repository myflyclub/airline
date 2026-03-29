var promptQueue
var tutorialQueues
var promptInterval
var activePrompt
var activeTutorial
var tutorialsCompleted
var noticesShown

function initPrompts() {
    promptQueue = []
    if (promptInterval) {
        clearInterval(promptInterval)
        promptInterval = undefined
    }
    activePrompt = undefined

    tutorialsCompleted = new Set()
    noticesShown = new Set()
    tutorialQueue = []
    $.ajax({
            type: 'GET',
            url: "/airlines/" + activeAirline.id + "/completed-tutorial",
            data: { } ,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(completedTutorials) {
                $.each(completedTutorials, function(index, entry) {
                       tutorialsCompleted.add(entry)
                })
            },
            error: function(jqXHR, textStatus, errorThrown) {
                    console.log(JSON.stringify(jqXHR));
                    console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });

    $('#broadcastMessagePopup').data('function', function(message) {
        $('#broadcastMessagePopup .sendMessage').text(htmlEncode(message))
        $('#broadcastMessagePopup').fadeIn(500)
    })

    $('#airlineMessagePopup').data('function', function(message) {
        $('#airlineMessagePopup .sendMessage').text(htmlEncode(message))
        $('#airlineMessagePopup').fadeIn(500)
    })
}

function queuePrompt(promptId, args) {
    promptQueue.push({ htmlId : promptId, args : args }) //id here determines the ID of the html popup, not the prompt itself
    if (!promptInterval) {
        promptInterval = setInterval('showPrompt()', 100)
    }
}

function queueNotice(noticeJson) {
    //map the category to html element ID here
    var htmlId
    var category = noticeJson.category
    if (category === "LEVEL_UP") {
        htmlId = "levelUpPopup"
    } else if (category === "LOYALIST") {
        htmlId = "loyalistMilestonePopup"
    } else if (category === "GAME_OVER") {
        htmlId = "bankruptcyPopup"
    } else if (category === "OLYMPICS_PRIZE") {
        htmlId = "olympicsPrizePopup"
    } else {
        console.warn("Unhandled notice " + noticeJson)
    }

    if (htmlId) {
        var noticeKey = category + ":" + noticeJson.notificationId
        if (!noticesShown.has(noticeKey)) {
            noticesShown.add(noticeKey)
            queuePrompt(htmlId, noticeJson)
        }
    }
}

function queueTutorialByJson(json) {
    //map the category/id to html element ID here
    var htmlId
    var category = json.category
    if (category === "airlineGrade") {
        htmlId = "tutorialAirlineGrade"
    } else if (category === "loyalist") {
        htmlId = "tutorialLoyalistMilestone"
    } else if (category === "worldMap") {
        htmlId = "tutorialViewAirport"
    } else if (category === "planLink") {
        htmlId = "tutorialSetupLink1"
    } else if (category === "airline") {
        // highlight-only step; no tutorial popup for fleet guidance
    } else if (category === "oil") {
        htmlId = "tutorialOilIntro1"
    } else {
        console.warn("Unhandled tutorial " + JSON.stringify(json))
    }

    if (json.highlight) {
        applyTutorialHighlight(json.highlight)
    }

    if (htmlId) {
        queueTutorial(htmlId)
    }
}

var TUTORIAL_CATEGORIES = {
    'worldMap':    ['tutorialViewAirport'],
    'airport':     ['tutorialAirportDetails', 'tutorialBuildHq'],
    'planLink':    ['tutorialSetupLink1', 'tutorialSetupLink2', 'tutorialSetupLink3'],
    'negotiation': ['tutorialNegotiation1', 'tutorialNegotiation2', 'tutorialNegotiation3', 'tutorialNegotiation4', 'tutorialNegotiation5']
}

function checkTutorial(category) {
    if (activeAirline && activeAirline.skipTutorial) return
    var ids = TUTORIAL_CATEGORIES[category]
    if (ids) {
        ids.forEach(function(id) { queueTutorial(id) })
    }
}

function applyTutorialHighlight(selector) {
    clearTutorialHighlights()
    $(selector).addClass('tutorial-pulse')
}

function clearTutorialHighlights() {
    $('.tutorial-pulse').removeClass('tutorial-pulse')
}

function showPrompt() {
    if (!$('#announcementModal').is(':visible')) {
        if (!activePrompt) {
            if (promptQueue.length > 0) {
                activePrompt = promptQueue.shift()
                var promptId = '#' + activePrompt.htmlId
                if ($(promptId).data("function")) {
                    $(promptId).data("function")(activePrompt.args)
                } else {
                    $(promptId).fadeIn(500)
                }
                if ($(promptId).hasClass('notice')) {
                    $.ajax({
                        type: 'POST',
                        url: "/airlines/" + activeAirline.id + "/notifications/" + $(promptId).data('notificationId') + "/read",
                        data: { } ,
                        contentType: 'application/json; charset=utf-8',
                        dataType: 'json',
                        success: function(result) {

                        },
                        error: function(jqXHR, textStatus, errorThrown) {
                                console.log(JSON.stringify(jqXHR));
                                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
                        }
                    });
                    $(promptId).data('closeCallback', function() {
                        closeNotice($(promptId))
                    })
                } else {
                    $(promptId).data('closeCallback', function() {
                        closePrompt($(promptId))
                    })
                }
            } else if (!activeTutorial && tutorialQueue.length > 0) {
                activeTutorial = tutorialQueue.shift()
                var tutorialId = '#' + activeTutorial
                $(tutorialId).fadeIn(500)
                $(tutorialId).data('closeCallback', function() {
                    closeTutorial($(tutorialId))
                })
            }
        }
    }


    if (promptQueue.length == 0 && tutorialQueue.length == 0) {
        clearInterval(promptInterval)
        promptInterval = undefined
    }
}

function closeNotice($promptModal) {
    closePrompt($promptModal)
}


function closePrompt($promptModal) {
    activePrompt = undefined
    var callback = $promptModal.data('promptCloseCallback')
    if (callback) {
        callback()
    }
}


function queueTutorial(tutorial) {
    if (!tutorialsCompleted.has(tutorial) && !tutorialQueue.includes(tutorial)) {
        tutorialQueue.push(tutorial)
    }
    if (!promptInterval) {
        promptInterval = setInterval('showPrompt()', 100)
    }

}


function closeTutorial($tutorialModal) {
    activeTutorial = undefined
    clearTutorialHighlights()
    tutorialsCompleted.add($tutorialModal.attr('id'))
    $.ajax({
        type: 'POST',
        url: "/airlines/" + activeAirline.id + "/completed-tutorial/" + $tutorialModal.attr('id') + "?category=" + $tutorialModal.data('category'),
        data: { } ,
		contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {

        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function promptSkipTutorial(event) {
    event.stopPropagation()
    promptConfirm("Disable all tutorials?", setSkipTutorial, true)
}


function setSkipTutorial(skipTutorial) {

    if (!skipTutorial) {
        tutorialsCompleted = new Set()
    } else {
        if (activeTutorial) {
            closeModal($('#' + activeTutorial))
        }
    }
    activeAirline.skipTutorial = skipTutorial
    activeTutorial = undefined
    tutorialQueue = []

    $.ajax({
        type: 'POST',
        url: "/airlines/" + activeAirline.id + "/tutorial?skipTutorial=" + skipTutorial,
        data: { } ,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {

        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function initNotices() {
    $('#levelUpPopup').data('function', function(json) {
        $('#levelUpPopup').data('notificationId', json.notificationId)
        showLevelUpPopup(json.level, json.description)
    })
    $('#levelUpPopup').data('promptCloseCallback', stopFirework)

    $('#loyalistMilestonePopup').data('function', function(json) {
        $('#loyalistMilestonePopup').data('notificationId', json.notificationId)
        showLoyalistPopup(json.level, json.description)
    })
    $('#loyalistMilestonePopup').data('promptCloseCallback', stopFirework)

    $('.trackingNoticePopup').each(function() {
        var $this = $(this);
        $this.data('function', function(json) {
            $this.data('notificationId', json.notificationId);
            showTrackingNoticePopup($this, json.description);
        });
    });
}

function showLevelUpPopup(level, description) {
    var $popup = $('#levelUpPopup')
    var $starBar = $popup.find('.levelStarBar')
    $starBar.empty()
    $starBar.append(getGradeStarsImgs(level))

    $('#levelUpPopup .description').text(description)
    updateHeadquartersMap($('#levelUpPopup .headquartersMap'), activeAirline.id)
    $popup.fadeIn(500)

    startFirework(20000, Math.floor(level / 2) + 1)
}

function showLoyalistPopup(level, description) {
    var $popup = $('#loyalistMilestonePopup')

    $('#loyalistMilestonePopup .description').text(description)
    updateHeadquartersMap($('#loyalistMilestonePopup .headquartersMap'), activeAirline.id)
    $popup.fadeIn(500)

    startFirework(20000, level)
}


function showTrackingNoticePopup($popup, description) {
    $popup.fadeIn(500)
    $popup.find('.description').text(description)
}