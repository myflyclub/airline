function showLoginPage() {
    $('#loginPageOverlay').show();
    $('#loginPageUserName').focus();
    $("#logoutDiv").hide();
}

function hideLoginPage() {
    $('#loginPageOverlay').hide();
    $("#logoutDiv").show();
}

function passwordLoginPage(e) {
    if (e.keyCode === 13) {
        loginFromPage();
    }
}

async function loginFromPage() {
    $('.login-page-btn').addClass('loading');

    const userName = $('#loginPageUserName').val();
    const password = $('#loginPagePassword').val();

    const headers = {
        'Accept': 'application/json',
        'Authorization': 'Basic ' + btoa(userName + ':' + password)
    };

    try {
        const response = await fetch('/user-login', {
            method: 'POST',
            headers: headers,
            credentials: 'same-origin'
        });

        if (!response.ok) {
            if (response.status === 401) {
                showFloatMessage('Incorrect username or password');
            } else if (response.status === 400) {
                showFloatMessage('Session expired. Please log in again');
            } else if (response.status === 403) {
                showFloatMessage('You have been banned for violating the game rules. Please contact admins on Discord for assistance.');
            } else {
                showFloatMessage('Error logging in, error code ' + response.status + ". Please try again.");
            }
            $('.login-page-btn').removeClass('loading');
            return;
        }

        const user = await response.json();

        if (user) {
            $.cookie('sessionActive', 'true', { path: '/' });
            $('#loginPageUserName').val('');
            $('#loginPagePassword').val('');

            showFloatMessage('Successfully logged in');
            await loadPostLoginScripts();

            await doPostLoginSetup(user);

            navigateTo('/map/');
        }

        $('.login-page-btn').removeClass('loading');
    } catch (err) {
        showFloatMessage('Error logging in, please try again.');
        console.error(err);
        $('.login-page-btn').removeClass('loading');
    }
}


/**
 * Restore user session from existing cookie (no credentials needed)
 */
async function loadUser() {
    const headers = {
        'Accept': 'application/json'
    }

    try {
        const response = await fetch('/user-login', {
            method: 'POST',
            headers: headers,
            credentials: 'same-origin'
        })

        if (!response.ok) {
            if (response.status === 400 || response.status === 401) {
                // Session expired or invalid
                console.log('Session restore failed: ' + response.status)
            }
            throw new Error('Session restore failed: ' + response.status)
        }

        const user = await response.json()
        await doPostLoginSetup(user)

        return user
    } catch (err) {
        console.error('Session restore error:', err)
        throw err
    }
}

async function doPostLoginSetup(user) {
    activeUser = user;
    checkWebSocket(selectedAirlineId);

    // Core UI
    $('#tutorialHtml').load('/assets/html/tutorial.html')
    $('#noticeHtml').load('/assets/html/notice.html', initNotices)
    if ($("#floatMessage").val()) {
        showFloatMessage($("#floatMessage").val())
    }
    window.addEventListener('orientationchange', refreshMobileLayout)
    $(window).scroll(function () {
        $('#floatBackButton').animate({ top: ($(window).scrollTop() + 100) + "px" }, { queue: false, duration: 350 });
    });

    $('#chattext').jemoji({
        folder: '/assets/images/emoji/'
    });
    mobileCheck();
    hideLoginPage();
    showUserSpecificElements();
    refreshWallpaper();
    showAnnoucement();
    registerEscape();
    populateTooltips();
    //User
    updateAirlineColors()

    AirlineMap.addMapControls();
    AirlineMap.addMarkers();

    // Airline-specific setup
    if (user.airlineIds && user.airlineIds.length > 0) {
        selectAirline(user.airlineIds[0]);
        initPrompts();
        updateAirlineLabelColors();
        try {
            if (typeof loadAirplaneModels === 'function') await loadAirplaneModels(user.airlineIds[0]);
            if (typeof loadOilPrices === 'function') loadOilPrices();
        } catch (e) {
            console.warn('Airline setup error:', e);
        }
    }

    // Optional features - gracefully handle if not loaded
    try {
        if (typeof loadAirportsDynamic === 'function') loadAirportsDynamic();
        if (typeof initAdminActions === 'function') initAdminActions();
        if (typeof loadAllCountries === 'function') loadAllCountries();
    } catch (e) {
        console.warn('Optional features setup error:', e);
    }
}

function logout() {
	$.ajax
	({
	  type: "POST",
	  url: "/user-logout",
	  async: false,
	  success: function(message) {
	    	console.log(message)
	    	activeUser = null
	    	activeAirline = null
	    	airlineLabelColors = {}
	    	hideUserSpecificElements()
	    	$.removeCookie('sessionActive', { path: '/' })
	    	showFloatMessage("Successfully logged out")
	    	window.location.replace('/login/');
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function showUserSpecificElements() {
	$('.topBarDetails').show()
	// $('.topBarDetails').parent().removeClass('hide-empty') //hack to avoid empty floating div for modern layout
	$('#navPrimary').show()
	$('#mobileTabToggle').show()
}

function hideUserSpecificElements() {
	$('.topBarDetails').hide()
	// $('.topBarDetails').parent().addClass('hide-empty') //hack to avoid empty floating div for modern layout
	$('#navPrimary').hide()
	$('#mobileTabToggle').hide()
}
