var activeAirline
var activeUser
var selectedLink
var currentTime
var currentCycle
var airlineColors = {}
var airlineLabelColors = {}
var polylines = []
var gameConstants
var notes = {}

function airlineInit() {
  $('#tutorialHtml').load('/assets/html/tutorial.html')
  $('#noticeHtml').load('/assets/html/notice.html', initNotices)

    if ($.cookie('sessionActive')) {
        loadUser(false)
    } else {
        hideUserSpecificElements()
        refreshLoginBar()
        showAbout();
        refreshWallpaper()
    }
    
    registerEscape()
    updateAirlineColors()
    initTabGroup()

    loadOilPrices();
    getGameConstants()
    populateTooltips()

    window.addEventListener('orientationchange', refreshMobileLayout)
    mobileCheck()

	if ($("#floatMessage").val()) {
		showFloatMessage($("#floatMessage").val())
	}
	$(window).scroll(function(){
  		$('#floatBackButton').animate({top: ($(window).scrollTop() + 100) + "px" },{queue: false, duration: 350});
	});

	$('#chattext').jemoji({
    folder : '/assets/images/emoji/'
  });

//   Splitting(); are we using this?
}

async function getGameConstants() {
    const url = "/game/constants";
    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Response status: ${response.status}`);
        }

        gameConstants = await response.json();
    } catch (error) {
        console.error(error.message);
    }
}

$(window).on('focus', function () {
    if (selectedAirlineId) {
        checkWebSocket(selectedAirlineId)
    }
})

function registerEscape() {
    const modals = document.getElementsByClassName('modal')

    for (let i = 0; i < modals.length; i++) {
        const modal = modals[i];
        modal.addEventListener('click', function (event) {
            // 'event.target' is the specific element that was clicked; if the clicked element is the modal itself (and not a child element), hide the modal.
            if (event.target === modal) {
                closeModal($(modal));
            }
        });
    }

    $(document).keyup(function (e) {
        if (e.key === "Escape") { // escape key maps to keycode `27`
            var $topModal = $(".modal:visible").last()
            if ($topModal.length > 0) {
                closeModal($topModal)
            } else {
                closeAirportInfoPopup()
            }
        }
    });
}


function mobileCheck() {
	if (isMobileDevice()) { //assume it's a less powerful device
		refreshMobileLayout()

		//turn off animation by default
		currentAnimationStatus = false
	}
}

function isMobileDevice() {
    return window.screen.availWidth < 1024
}

function refreshMobileLayout() {
	if (window.screen.availWidth < window.screen.availHeight) { //only toggle layout change if it's landscape
		$("#reputationLevel").hide()
    } else {
        $("#reputationLevel").show()
	}
	delete(map)
	//yike, what if we miss something...the list below is kinda random
    addMarkers()
	if (activeAirline) {
	    updateLinksInfo()
	    updateAirportMarkers(activeAirline)
    }
}

function showFloatMessage(message, timeout) {
	timeout = timeout || 3000
	$("#floatMessageBox").text(message)
	$("#floatMessageBox").css({top:"-=20px",left:0,opacity:100})
	$("#floatMessageBox").show()
	$("#floatMessageBox").animate({ top:"34px" }, "fast", function() {
		if (timeout > 0) {
			setTimeout(function() {
				console.log("closing")
				$('#floatMessageBox').animate({ top:"-=20px",opacity:0 }, "slow", function() {
					$('#floatMessageBox').hide()
				})
			}, timeout)
		}
	})

	//scroll the message box to the top offset of browser's scroll bar
	$(window).scroll(function()
	{
  		$('#floatMessageBox').animate({top:$(window).scrollTop()+"px" },{queue: false, duration: 350});
	});
}

function refreshLoginBar() {
	if (!activeUser) {
		$("#loginDiv").show();
		$("#logoutDiv").hide();
	} else {
		$("#currentUserName").empty()
		$("#currentUserName").append(activeUser.userName + getUserLevelImg(activeUser.level))
		$("#logoutDiv").show();
        $("#loginDiv").hide();
	}
}


async function loadUser(isLogin) {
    // Build headers (include JSON accept and optional Basic auth for login)
    const headers = {
        'Accept': 'application/json'
    }

    if (isLogin) {
        const userName = $("#loginUserName").val()
        const password = $("#loginPassword").val()
        headers['Authorization'] = 'Basic ' + btoa(userName + ':' + password)
    }

    try {
        const response = await fetch('/login', {
            method: 'POST',
            headers: headers,
            credentials: 'same-origin'
        })

        if (!response.ok) {
            if (response.status === 401) {
                showFloatMessage('Incorrect username or password')
            } else if (response.status === 400) {
                showFloatMessage('Session expired. Please log in again')
            } else if (response.status === 403) {
                showFloatMessage('You have been banned for violating the game rules. Please contact admins on Discord for assistance.')
            } else {
                showFloatMessage('Error logging in, error code ' + response.status + ". Please try again. Contact admins on Discord if the issue persists.")
                // try to log response body for debugging
                const text = await response.text().catch(() => null)
                if (text) console.log(text)
                console.log('Fetch error: ' + response.status + ' : ' + response.statusText)
            }
            $('.button.login').removeClass('loading')
            throw new Error('Login failed: ' + response.status)
        }

        const user = await response.json()

        if (user) {
            closeAbout()
            activeUser = user
            $.cookie('sessionActive', 'true');
            $("#loginUserName").val("")
            $("#loginPassword").val("")

            if (isLogin) {
                showFloatMessage('Successfully logged in')
                showAnnoucement()
            }
            loadAirportsDynamic();
            refreshWallpaper()
            refreshLoginBar()
            addMarkers()
            showUserSpecificElements();
            initAdminActions()
        }

        if (user && user.airlineIds && user.airlineIds.length > 0) {
            selectAirline(user.airlineIds[0])
            await loadAirplaneModels(user.airlineIds[0])
            addAirlineSpecificMapControls(map)
            initPrompts()
            updateAirlineLabelColors()
        }

        loadAllCountries() //load country after airline
        $('.button.login').removeClass('loading')

        return user
    } catch (err) {
        // network or other unexpected errors
        if (err && err.message && err.message.indexOf('Login failed:') === -1) {
            showFloatMessage('Error logging in, please try again. Contact admins on Discord if the issue persists.')
            console.error(err)
        }
        $('.button.login').removeClass('loading')
        throw err
    }
}

function passwordLogin(e) {
	if (e.keyCode === 13) {  //checks whether the pressed key is "Enter"
		login()
	}
}

function login()  {
    $('.button.login').addClass('loading')
    loadUser(true)
}

function logout() {
	$.ajax
	({
	  type: "POST",
	  url: "/logout",
	  async: false,
	  success: function(message) {
	    	console.log(message)
	    	activeUser = null
	    	activeAirline = null
	    	airlineLabelColors = {}
	    	hideUserSpecificElements()
	    	$.removeCookie('sessionActive')
	    	//refreshLoginBar()
	    	//showFloatMessage("Successfully logged out")
	    	location.reload();
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});

	removeMarkers()
}

function showUserSpecificElements() {
	$('.user-specific-tab').show()
	$('.topBarDetails').show()
	$('.topBarDetails').parent().removeClass('hide-empty') //hack to avoid empty floating div for modern layout
}

function hideUserSpecificElements() {
	$('.user-specific-tab').hide()
	$('.topBarDetails').hide()
	$('.topBarDetails').parent().addClass('hide-empty') //hack to avoid empty floating div for modern layout
}

// function LinkHistoryControl(controlDiv, map) {
//     // Set CSS for the control border.
//     var controlUI = document.createElement('div');
//     controlUI.style.backgroundColor = '#fff';
//     controlUI.style.border = '2px solid #fff';
//     controlUI.style.borderRadius = '3px';
//     controlUI.style.boxShadow = ' 0px 1px 4px -1px rgba(0,0,0,.3)';
//     //controlUI.style.cursor = 'pointer';
//     controlUI.style.marginBottom = '22px';
//     controlUI.style.textAlign = 'center';
//     controlUI.title = 'Click to recenter the map';
//     controlUI.style.padding = '8px';
//     controlUI.style.margin= '10px';
//     controlUI.style.verticalAlign = 'middle';
//     controlDiv.appendChild(controlUI);


//     $(controlUI).append("<img src='/assets/images/icons/24-arrow-180.png' class='button' onclick='toggleLinkHistoryView(false)'  title='Toggle passenger history view'/>")
//     // Set CSS for the control interior.
//     $(controlUI).append("<span id='linkHistoryText' style='color: rgb(86, 86, 86); font-family: Roboto, Arial, sans-serif; font-size: 11px;'></span>");

//     $(controlUI).append("<img src='/assets/images/icons/24-arrow.png' class='button' onclick='toggleLinkHistoryView(false)'  title='Toggle passenger history view'/>")

//     // Setup the click event listeners: simply set the map to Chicago.
//     controlUI.addEventListener('click', function() {
//       map.setCenter(chicago);
//     });

//   }


function updateAllPanels(airlineId) {
	updateAirlineInfo(airlineId)
}

function refreshPanels(airlineId) {
	$.ajax({
		type: 'GET',
		url: "/airlines/" + airlineId,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    async: false,
	    success: function(airline) {
            // merge returned fields into existing activeAirline instead of replacing the whole object
            if (activeAirline && typeof activeAirline === 'object') {
                // shallow merge: only replace keys present in the response
                Object.keys(airline).forEach(function(key) {
                    activeAirline[key] = airline[key]
                })
            } else {
                activeAirline = airline
            }
	    	refreshTopBar(activeAirline)
	    	if ($("#worldMapCanvas").is(":visible")) {
	    		refreshLinks()
	    	}
	    	if ($("#linkDetails").is(":visible") || $("#linkDetails").hasClass("active")) {
	    		refreshLinkDetails(selectedLink)
	    	}
	    	if ($("#linksCanvas").is(":visible")) {
	    		loadLinksTable()
	    	}
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

var totalmillisecPerWeek = 7 * 24 * 60 * 60 * 1000
var refreshInterval = 5000 //every 5 second
var hasTickEstimation = false
var days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

var currentTickTimer
var tickTimerCreator

function updateTime(cycle, fraction, cycleDurationEstimation) {
	$(".currentTime").attr("title", "Day " + Math.floor(cycle / 48) + " & " + cycle % 48 + " cycles")
	gameTimeStart = (cycle + fraction) * totalmillisecPerWeek

    var initialDurationTillNextTick
	if (cycleDurationEstimation > 0) { //update incrementPerInterval
	    initialDurationTillNextTick = cycleDurationEstimation * (1 - fraction)
	    hasTickEstimation = true
	}

	var wallClockStart = new Date()

	if (currentTickTimer) {
	    clearInterval(currentTickTimer)
	}

    var updateTimerFunction = function() {
        var currentWallClock = new Date()
        var wallClockDurationSinceStart = currentWallClock.getTime() - wallClockStart.getTime()

        var durationTillNextTick = initialDurationTillNextTick - wallClockDurationSinceStart

        $(".currentTime").text(padBefore(Math.floor(cycle / 48) + "." + cycle % 48, 2))

        if (hasTickEstimation) {
          var minutesLeft = Math.round(durationTillNextTick / 1000 / 60)
          if (minutesLeft <= 0) {
              $(".nextTickEstimation").text("Very soon")
          } else if (minutesLeft == 1) {
              $(".nextTickEstimation").text("1 minute")
          } else {
              $(".nextTickEstimation").text(minutesLeft + " minutes")
          }
        }
    }
    tickTimerCreator = function() {
        updateTimerFunction()
        var newTimer = setInterval(updateTimerFunction, refreshInterval);
        return newTimer
    }

	currentTickTimer = tickTimerCreator()
}


// Handle browser tab visibility change
document.addEventListener('visibilitychange', function () {
    clearInterval(currentTickTimer);
    if (!document.hidden && tickTimerCreator) {
        console.log("Recreating tick timer!")
        currentTickTimer = tickTimerCreator()
    }
});


function showWorldMap() {
    $('#searchCanvas').hide();
	setActiveDiv($('#worldMapCanvas'));
	highlightTab($('.worldMapCanvasTab'))
	$('#sidePanel').appendTo($('#worldMapCanvas'))
	if (selectedLink) {
		selectLinkFromMap(selectedLink, !activeAirportPopupInfoWindow) //do not refocus if there's a popup, stay where it is
	}
	checkTutorial('worldMap')
}

//switch to map view w/o considering leaving current tab
function switchMap() {
    var mapCanvas = $('#worldMapCanvas')
    var existingActiveDiv = mapCanvas.siblings(":visible").filter(function (index) {
		return $(this).css("clear") != "both"
	})
    if (existingActiveDiv.length > 0) {
        existingActiveDiv.fadeOut(200, function() {
            mapCanvas.fadeIn(200)
        })
    }
}

function showAnnoucement() {
	var modal = $('#announcementModal')
	// Get the <span> element that closes the modal
	$('#announcementContainer').empty()
	$('#announcementContainer').load('/assets/html/announcement.html')

	modal.fadeIn(1000)
}

async function populateTooltips() {
    /**
     * Populate tooltips from server-side data, looks for id "tooltip_{objKey}"
     */
    const url = "/game/tooltips";
    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Response status: ${response.status}`);
        }

        const tooltips = await response.json();

        for (const [objKey, tooltipArray] of Object.entries(tooltips)) {
            const element = document.getElementById("tooltip_" + objKey);
            if (element) {
                const ulElement = document.createElement('ul');
                ulElement.classList.add('list-disc');

                tooltipArray.forEach(tooltipText => {
                    const liElement = document.createElement('li');
                    liElement.textContent = tooltipText;
                    ulElement.appendChild(liElement);
                });

                element.innerHTML = '';
                element.appendChild(ulElement);
            }
        }
    } catch (error) {
        console.error("Error loading tooltips:", error.message);
    }

    //scan for all tooltips
    $.each($(".tooltip"), function() {
        var htmlSource = $(this).data("html")
        if (htmlSource) { //then load the html, otherwise leave it alone (older tooltips)
            $(this).empty()
            $(this).load("/assets/html/tooltip/" + htmlSource + ".html")
        }
    })

    populateDelegatesTooltips()
    populateDataPropertyTooltips()
}

function populateDelegatesTooltips() {
    var $html = $("<div></div>")
    $html.append("<p>Gained by leveling up your airline. Airline grade is determined by reputation points.</p>")
    $html.append("<p>Delegates conduct various tasks, such as Flight negotiations, Country relationship improvements, Advertisement campaigns etc.</p>")

    addTooltipHtml($('.delegatesTooltip'), $html, {'width' : '350px'})
}

function populateDataPropertyTooltips() {
    $('[data-tooltip-text]').each(function(index) {
        var width = Math.min(400, $(this).data('tooltipText').length * 5) //approximate width
        var css = { width : width + 'px'}
        addTooltip($(this), $(this).data('tooltipText'), css)
    })

}

function showTutorial() {
	var modal = $('#tutorialModal')
	modal.fadeIn(1000)
}

function promptConfirm(prompt, targetFunction, param) {
	$('#confirmationModal .confirmationButton').data('targetFunction', targetFunction)
	if (typeof param != 'undefined') {
		$('#confirmationModal .confirmationButton').data('targetFunctionParam', param)
	}
	$('#confirmationPrompt').html(prompt)
	$('#confirmationModal').fadeIn(200)
}

function executeConfirmationTarget() {
	var targetFunction = $('#confirmationModal .confirmationButton').data('targetFunction')
	var targetFunctionParam = $('#confirmationModal .confirmationButton').data('targetFunctionParam')
	if (typeof targetFunctionParam != 'undefined') {
		targetFunction(targetFunctionParam)
	} else {
		targetFunction()
	}
}

function promptSelection(question, choices, targetFunction) {
	$('#selectionModal .question').text(question)
	$('#selectionModal .selections').empty()
	$.each(choices, function(index, choice) {
	    var $selectionButton = $('<div class="button">' + choice + '</div>')
        $('#selectionModal .selections').append($selectionButton)
        $selectionButton.click(function() {
            targetFunction(choice)
            closeModal($('#selectionModal'))
        })
	})

	$('#selectionModal').fadeIn(200)
}


function updateAirlineColors() {
	var url = "/colors"
    $.ajax({
		type: 'GET',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	airlineColors = result
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function updateAirlineLabelColors(callback) {
    //get airline label color
    airlineLabelColors = {}
    $.ajax({
            type: 'GET',
            url: "/airlines/" + activeAirline.id + "/airline-label-colors",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                $.each(result, function(index, entry) {
                    airlineLabelColors[index] = entry
                })
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

function assignAirlineColors(dataSet, colorProperty) {
	$.each(dataSet, function(index, entry) {
		if (entry[colorProperty]) {
			var airlineColor = airlineColors[entry[colorProperty]]
			if (airlineColor) {
				entry.color = airlineColor
			}
		}
	})
}

let tabGroupState = {}

function setMobileToggleState(isOpen) {
    const $btn = $('#mobileTabToggle');
    $btn.attr('aria-expanded', !!isOpen);
    $btn.toggleClass('open', !!isOpen);
    // Toggle inline icons
    $btn.find('.icon-open').css('display', isOpen ? 'none' : 'inline-block');
    $btn.find('.icon-close').css('display', isOpen ? 'inline-block' : 'none');
}

function showTabGroup() {
    if (tabGroupState.hideTimeout) {
        clearTimeout(tabGroupState.hideTimeout)
        tabGroupState.hideTimeout = undefined
    }
    $('#tabGroup').fadeIn(200)
    setMobileToggleState(true)
}

function hideTabGroup(waitDuration) {
    if (tabGroupState.hideTimeout) {
        clearTimeout(tabGroupState.hideTimeout)
    }
    var timeout = setTimeout(() => {
        $('#tabGroup').fadeOut(500);
        setMobileToggleState(false);
    }, waitDuration ? waitDuration : 2000)
    tabGroupState.hideTimeout = timeout
}

function initTabGroup() {
    $("#tabGroup .tab-icon").on('mouseenter touchstart', function() {
        $(this).closest('.left-tab').find('.label').show();
    });

    $("#tabGroup .tab-icon").on('mouseleave touchend', function() {
        $(this).closest('.left-tab').find('.label').hide();
    });

    $("#canvas").on('touchstart', function(e) {
        var swipe = e.originalEvent.touches,
        startX = swipe[0].pageX;
        startY = swipe[0].pageY;
        $(this).on('touchmove', function(e) {
            var contact = e.originalEvent.touches,
            endX = contact[0].pageX,
            endY = contact[0].pageY,
            distanceX = endX - startX;
            distanceY = endY - startY
            if (Math.abs(distanceX) > Math.abs(distanceY) && distanceX > 30 && $('#main')[0].scrollLeft == 0) {
                showTabGroup()
                hideTabGroup(5000)
            }
        })
        .one('touchend', function() {
            $(this).off('touchmove touchend');
        });
    });

    $("#tabGroupCue").on('mouseenter touchstart',
        function() {
            showTabGroup()
        }
    )
    $("#tabGroupCue").on('mouseleave touchend',
        function() {
             hideTabGroup(5000)
        }
    )

    $("#tabGroup").on('mouseenter touchstart',
        function() {
            showTabGroup()
        }
    )
    $("#tabGroup").on('mouseleave touchend',
        function() {
             hideTabGroup()
        }
    )

    const $toggle = $('#mobileTabToggle');
    if ($toggle.length) {
        // initialize icon state
        // setMobileToggleState($('#tabGroup').is(':visible'))
        $toggle.on('click', function() {
            const isOpen = $('#tabGroup').is(':visible');
            if (isOpen) {
                if (tabGroupState.hideTimeout) { clearTimeout(tabGroupState.hideTimeout) }
                $('#tabGroup').stop(true, true).fadeOut(300)
                setMobileToggleState(false)
            } else {
                showTabGroup()
                hideTabGroup(5000) // auto-hide after a while
            }
        })
    }
}

window.addEventListener('popstate', function(e) {
    if (e.state) {
        if (e.state.onbackFunction) {
            eval(e.state.onbackFunction) //onback has higher precedence
        } else if (e.state.onclickFunction) {
            eval(e.state.onclickFunction)
        }
    }
});
