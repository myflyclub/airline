var activeAirline
var activeUser
var selectedLink
var currentTime
var currentCycle
var airlineColors = {}
var airlineLabelColors = {}
var polylines = []
var notes = {}
const GAME_COLORS = {
    discounteconomy: "#78cd6b",
    economy: "#57A34B",
    business: "#4E79A7",
    first: "#D6B018",
    economyEmpty: "#346935",
    businessEmpty: "#36466D",
    firstEmpty: "#7A6925",
    cancelled: "#D66061",
    empty: "#DDDFDF",
    tourist: "#c3a319",
    elite: "#7A4A9D",
    traveler: "#4ebc36",
    travelersmalltown: "#4ebc36",
    olympic: "#a7941d",
    olympics: "#a7941d",
    codeshares: "#4eafa4",
    dealseeker: "#57A34B",
    frequentflyer: "#2163aa",
    brandsensitive: "#5f3a9f",
    satisfaction: "#e3b80d",
    rask: "#1e9b2b",
    cask: "#e90e0e",
};

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
                AirlineMap.closeAirportInfoPopup()
            }
        }
    });
}

function mobileCheck() {
	if (isMobileDevice()) {
        $("#navPrimaryToggle").show();
		currentAnimationStatus = false //turn off animation by default
        registernavPrimaryToggle()
        registerMobileGestures()
	} else {
        $("#navPrimaryToggle").hide()
    }
}

function setNavPrimaryState(expand) {
    const nav = $('#navPrimary');
    const toggle = $('#navPrimaryToggle');

    toggle.attr('aria-expanded', expand);
    nav.toggleClass('active', expand);

    if (expand) {
        toggle.find('.icon-open').hide();
        toggle.find('.icon-close').show();
    } else {
        toggle.find('.icon-open').show();
        toggle.find('.icon-close').hide();
    }
}

function registernavPrimaryToggle() {
    $('#navPrimaryToggle').off('click').on('click', function () {
        const isExpanded = $(this).attr('aria-expanded') === 'true';
        setNavPrimaryState(!isExpanded);
    });
}

function registerMobileGestures() {
    let touchstartX = 0;
    let touchstartXPercent = 0;

    document.addEventListener('touchstart', function(event) {
        touchstartX = event.changedTouches[0].clientX;
        touchstartXPercent = touchstartX / window.innerWidth;
    }, { passive: true });

    document.addEventListener('touchend', function(event) {
        const touchendX = event.changedTouches[0].clientX;
        const deltaX = touchendX - touchstartX;
        const threshold = 50; // minimum distance for swipe

        if (Math.abs(deltaX) > threshold) {
            if (deltaX > 0) { // Swipe right
                setNavPrimaryState(true);
            } else if (touchstartXPercent < 0.2) { // Swipe left starting from left 20%
                setNavPrimaryState(false);
            }
        }
    }, { passive: true });
}

function isMobileDevice() {
    return window.innerWidth < 800
}


function refreshPanels(airlineId) {
	$.ajax({
		type: 'GET',
		url: "/airlines/" + airlineId,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    async: false,
	    success: function(airline) {
            // shallow merge: only replace keys present in the response
            Object.keys(airline).forEach(function(key) {
                activeAirline[key] = airline[key]
            })
	    	refreshTopBar(activeAirline)
	    	if ($("#worldMapCanvas").is(":visible")) {
	    		refreshLinks()
	    	}
	    	if ($("#linkDetails").is(":visible") || $("#linkDetails").hasClass("active")) {
	    		refreshLinkDetails(selectedLink)
	    	}
	    	if ($("#linksCanvas").is(":visible")) {
	    		loadLinksTable(null, true)
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
	$(".currentTime").attr("data-tooltip", "Week " + cycle % 48 + " & Year " + Math.floor(cycle / 48) + " | One week lasts ~ 30min and one year is 48 weeks or 24 hours in realtime.")
	gameTimeStart = (cycle + fraction) * totalmillisecPerWeek

    var initialDurationTillNextTick
	if (cycleDurationEstimation > 0) { //update incrementPerInterval
	    initialDurationTillNextTick = cycleDurationEstimation * (1 - fraction)
	    hasTickEstimation = true
	    cycleDurationMs = cycleDurationEstimation
	}

	var wallClockStart = new Date()

	if (currentTickTimer) {
	    clearInterval(currentTickTimer)
	}

    var updateTimerFunction = function() {
        var currentWallClock = new Date()
        var wallClockDurationSinceStart = currentWallClock.getTime() - wallClockStart.getTime()

        var durationTillNextTick = initialDurationTillNextTick - wallClockDurationSinceStart

        $(".currentTime").text(padBefore(cycle % 48 + "." + Math.floor(cycle / 48), 2))

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
    hideMapOverlays();
	setActiveDiv($('#worldMapCanvas'));
	$('#sidePanel').appendTo($('#worldMapCanvas'))
	if (selectedLink) {
		selectLinkFromMap(selectedLink, !activeAirportPopupInfoWindow) //do not refocus if there's a popup, stay where it is
	}
	checkTutorial('worldMap')
}

var ANNOUNCEMENT_VERSION = "2026-02-25"

function showAnnoucement() {
	if (localStorage.getItem('announcementAgreed') === ANNOUNCEMENT_VERSION) {
		return
	}
	var modal = $('#announcementModal')
	$('#announcementAgreeCheck').prop('checked', false)
	$('#announcementContainer').empty()
	$('#announcementContainer').load('/assets/html/announcement.html')

	modal.fadeIn(1000)
}

function onAnnouncementAgreeChange(checkbox) {
	if (checkbox.checked) {
		localStorage.setItem('announcementAgreed', ANNOUNCEMENT_VERSION)
		closeModal($('#announcementModal'))
	}
}

async function populateTooltips() {
    /**
     * Populate tooltips from server-side data, looks for id "tooltip_{objKey}"
     */
    const url = `${API_PREFIX}/game/tooltips`;
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

    populateDataPropertyTooltips()
}

function populateDataPropertyTooltips() {
    $('[data-tooltip-text]').each(function(index) {
        var width = Math.min(360, $(this).data('tooltipText').length * 5) //approximate width
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

function initLogoUpload($panel, uploadUrl, fileName, successCallback) {
    const $input = $panel.find('.logoInput');
    const $dropZone = $panel.find('.file-drop-zone');
    const $previewContainer = $panel.find('.preview-container');
    const $previewImg = $panel.find('.logo-preview');
    const $prompt = $panel.find('.upload-prompt');
    const $warning = $panel.find('.warning');
    const $actions = $panel.find('.actions');
    const $uploadBtn = $panel.find('.uploadButton');

    // Reset
    resetLogoUpload($panel);

    // File selection handler
    $input.off('change').on('change', function(e) {
        handleFileSelect(e.target.files[0]);
    });

    // Drag and drop handlers
    $dropZone.off('dragover dragenter').on('dragover dragenter', function(e) {
        e.preventDefault();
        e.stopPropagation();
        $(this).addClass('drag-over');
    });

    $dropZone.off('dragleave drop').on('dragleave drop', function(e) {
        e.preventDefault();
        e.stopPropagation();
        $(this).removeClass('drag-over');
        if (e.type === 'drop') {
            handleFileSelect(e.originalEvent.dataTransfer.files[0]);
        }
    });

    function handleFileSelect(file) {
        if (!file) return;

        if (!file.type.startsWith('image/')) {
            showError("Please select an image file.");
            return;
        }

        const reader = new FileReader();
        reader.onload = function(e) {
            $previewImg.attr('src', e.target.result);
            $previewContainer.show();
            $prompt.hide();
            $actions.show();
            $warning.hide();
        };
        reader.readAsDataURL(file);
    }

    function showError(msg) {
        $warning.text(msg).show();
    }

    $uploadBtn.off('click').on('click', function() {
        const file = $input[0].files[0] || (typeof $dropZone.data('file') !== 'undefined' ? $dropZone.data('file') : null);
        if (!file && !$input[0].files[0]) {
            showError("Please select a file first.");
            return;
        }

        const formData = new FormData();
        formData.append(fileName, $input[0].files[0] || $dropZone.data('file'));

        $uploadBtn.prop('disabled', true).text('Uploading...');

        fetch(uploadUrl, {
            method: 'POST',
            body: formData
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.error || 'Upload failed'); });
            }
            return response.json();
        })
        .then(data => {
            if (data.success) {
                successCallback(data);
            } else {
                showError(data.error || "Upload failed");
            }
        })
        .catch(error => {
            showError(error.message);
        })
        .finally(() => {
            $uploadBtn.prop('disabled', false).text('Upload');
        });
    });
}

function resetLogoUpload($panel) {
    $panel.find('.logoInput').val('');
    $panel.find('.preview-container').hide();
    $panel.find('.logo-preview').attr('src', '');
    $panel.find('.upload-prompt').show();
    $panel.find('.actions').hide();
    $panel.find('.warning').hide();
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

