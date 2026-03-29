function showLoginPage(options = {}) {
    $('#loginPageOverlay').show();
    showLoginForm();
    $("#logoutDiv").hide();
    // Store callback for login success
    window.onLoginSuccessCallback = options.onLoginSuccess || null;
}

function hideLoginPage() {
    $('#loginPageOverlay').hide();
    $("#logoutDiv").show();
}

/**
 * Clear client-side session state and show the login screen.
 * Use when any API call returns 401/403 mid-session.
 * Does NOT call /user-logout — the server already considers the session invalid.
 */
function handleUnauthorized() {
    activeUser = null;
    activeAirline = null;
    airlineLabelColors = {};
    localStorage.removeItem('sessionActive');
    document.cookie = "sessionActive=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
    $('.topBarDetails').hide();
    $('#navPrimary').hide();
    $('#navPrimaryToggle').hide();
    navigateTo('/login/');
}

function showLoginForm() {
    $('#signupForm').hide();
    $('#loginForm').show();
    $('#loginPageUserName').focus();
}

function showSignupForm() {
    $('#loginForm').hide();
    $('#signupForm').show();
    $('#signupPageUserName').focus();
}

function passwordLoginPage(e) {
    if (e.keyCode === 13) {
        loginFromPage();
    }
}

function clearLoginErrors() {
    const errorEl = document.getElementById('loginPageError');
    if (errorEl) {
        errorEl.textContent = '';
        errorEl.style.display = 'none';
    }
}

function displayLoginError(message) {
    const errorEl = document.getElementById('loginPageError');
    if (errorEl) {
        errorEl.textContent = message;
        errorEl.style.display = 'block';
    }
}

async function loginFromPage() {
    $('.login-page-btn').addClass('loading');
    clearLoginErrors();

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
                displayLoginError('Incorrect username or password');
            } else if (response.status === 400) {
                displayLoginError('Session expired. Please log in again');
            } else if (response.status === 403) {
                displayLoginError('You have been banned for violating the game rules. Please contact admins on Discord for assistance.');
            } else {
                displayLoginError('Error logging in, error code ' + response.status + '. Please try again.');
            }
            $('.login-page-btn').removeClass('loading');
            return;
        }

        const user = await response.json();

        if (user) {
            localStorage.setItem('sessionActive', 'true');
            $('#loginPageUserName').val('');
            $('#loginPagePassword').val('');

            await loadPostLoginScripts();
            await ensureFullBoot();

            await doPostLoginSetup(user);

            // Use callback if provided, otherwise redirect to original page or /map/
            if (typeof window.onLoginSuccessCallback === 'function') {
                window.onLoginSuccessCallback();
            } else {
                const redirect = localStorage.getItem('postLoginRedirect');
                localStorage.removeItem('postLoginRedirect');
                navigateTo(redirect || '/map/');
            }
        }

        $('.login-page-btn').removeClass('loading');
    } catch (err) {
        displayLoginError('Error logging in, please try again.');
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
            // Session expired or invalid — let the caller clean up and re-route
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
    window.addEventListener('orientationchange', mobileCheck);
    window.addEventListener('resize', mobileCheck);
    $(window).scroll(function () {
        $('#floatBackButton').animate({ top: ($(window).scrollTop() + 100) + "px" }, { queue: false, duration: 350 });
    });

    hideLoginPage();
    $('.topBarDetails').show()
    $('#navPrimary').show()
	$('#navPrimaryToggle').show()
    showAnnoucement();
    registerEscape();
    populateTooltips();
    //User
    updateAirlineColors()

    mobileCheck();
    refreshWallpaper();

    // Airline-specific setup
    if (user.airlineIds && user.airlineIds.length > 0) {
        await selectAirline(user.airlineIds[0]);
        if (window.AirlineMap && activeAirline) {
            AirlineMap.centerOnHQ(activeAirline, 6);
        }
        initPrompts();
        if (typeof initNotificationDrawer === 'function') initNotificationDrawer();
        updateAirlineLabelColors();
        try {
            if (typeof loadAirplaneModels === 'function') loadAirplaneModels(user.airlineIds[0]);
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
            $('.topBarDetails').hide()
	    	$('#navPrimary').hide()
	        $('#navPrimaryToggle').hide()
	    	localStorage.removeItem('sessionActive')
            document.cookie = "sessionActive=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;"
	    	window.location.replace('/login/');
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function showUserSpecificElements() {
	
	// $('.topBarDetails').parent().removeClass('hide-empty') //hack to avoid empty floating div for modern layout
	
}

function hideUserSpecificElements() {
	
	// $('.topBarDetails').parent().addClass('hide-empty') //hack to avoid empty floating div for modern layout
	
}
