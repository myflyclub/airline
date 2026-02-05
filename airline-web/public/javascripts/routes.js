/**
 * Check if user has valid session, redirect to /login/ if on protected route without session.
 * @returns {boolean} true if session exists, false otherwise
 */
function checkSessionGuard() {
    // Use $.cookie if available (consistent with rest of app), fallback to document.cookie
    let sessionVal;
    if (typeof $ !== 'undefined' && $.cookie) {
        sessionVal = $.cookie('sessionActive');
    } else {
        const match = document.cookie.match(/(?:^|; )sessionActive=([^;]*)/);
        sessionVal = match ? decodeURIComponent(match[1]) : null;
    }
    const sessionActive = sessionVal === true || sessionVal === 'true' || sessionVal === '1';

    console.log('✓ Session check:', sessionActive);
    return sessionActive;
}

function initializeRoutes() {
    page('*', (ctx, next) => {
        const sessionVal = (typeof $ !== 'undefined' && $.cookie) ? $.cookie('sessionActive') : null;
        const isLoggedIn = sessionVal === true || sessionVal === 'true' || sessionVal === '1';

        if (!isLoggedIn && ctx.path !== '/login/') {
            page.redirect('/login/');
        } else {
            next();
        }
    });

    page('/login/', () => {
        document.title = 'Login';
        showLoginPage();
    });

    page('/logout/', () => {
        document.title = 'Logout';
        logout();
    });

    page('/', () => {
        document.title = `MFC Airline Game`;
        // If logged in, show map; otherwise redirect to login
        const sessionVal = (typeof $ !== 'undefined' && $.cookie) ? $.cookie('sessionActive') : null;
        const isLoggedIn = sessionVal === true || sessionVal === 'true' || sessionVal === '1';
        if (isLoggedIn) {
            showWorldMap();
        } else {
            page.redirect('/login/');
        }
    });

    page('/airport/:iata?', (ctx) => {
        const iata = ctx.params.iata ?? null;
        document.title = iata ? `${iata.toUpperCase()} Airport` : 'Airport';
        id = getAirportByAttribute(iata.toUpperCase(), 'iata').id || null;
        showAirportDetails(id);
    });

    page('/map/', () => {
        document.title = `${activeAirline.name} route map`
        showWorldMap();
        $('#sidePanel').fadeOut(200);
    });

    page('/map/:iata?', (ctx) => {
        const iata = ctx.params.iata ?? null;
        document.title = `${iata} | ${activeAirline.name} route map`
        showWorldMap();
        airports.getAirportByAttribute
        const airport = getAirportByIata(iata.toUpperCase());
        AirlineMap.flyTo(airport.longitude, airport.latitude);
        AirlineMap.showAirportPopup(airport, {lat: airport.latitude, lng: airport.longitude});
    });

    page('/search/:iata?', (ctx) => {
        const iata = ctx.params.iata ?? null;
        document.title = iata ? `${iata.toUpperCase()} Search` : 'Search';
        showSearchCanvas();
    });

    page('/flights/:link?', (ctx) => {
        const link = ctx.params.link ?? null;
        document.title = link ? `${link} Flights` : 'Flights';
        showLinksCanvas(link, false);
    });

    page('/hangar/', () => {
        document.title = 'Hangar';
        showAirplaneCanvas("hangar");
    });
    page('/hangar/:model', (ctx) => {
        document.title = 'Hangar';
        if (ctx.params.model) {
            showAirplaneCanvas("market", pathToString(ctx.params.model));
        } else {
            document.title = 'Hangar';
            showAirplaneCanvas("hangar");
        }
    });

    page('/aircraft/', () => {
        document.title = 'Aircraft Market';
        showAirplaneCanvas("market");
    });
    page('/aircraft/:model', (ctx) => {
        document.title = 'Aircraft Market';
        if (ctx.params.model) {
            showAirplaneCanvas("market", pathToString(ctx.params.model));
        } else {
            showAirplaneCanvas("market");
        }
    });

    page('/office/', () => {
        document.title = 'Office';
        showOfficeCanvas();
    });

    page('/champions/', () => {
        document.title = 'Champions';
        showRankingCanvas();
    });

    page('/bank/', () => {
        document.title = 'Bank';
        showBankCanvas();
    });

    page('/oil/', () => {
        document.title = 'Oil';
        showOilCanvas();
    });

    page('/rivals/:rivalId?', (ctx) => {
        const rivalId = ctx.params.rivalId ?? null;
        document.title = rivalId ? `${rivalId} Rivals` : 'Rivals';
        showRivalsCanvas(rivalId);
    });

    page('/alliance/', () => {
        document.title = 'Alliance';
        showAllianceCanvas();
    });

    page('/country/:countryCode?', (ctx) => {
        const code = ctx.params.countryCode ? ctx.params.countryCode.toUpperCase() : null;
        showCountryCanvas(code);
    });

    page('/log/', () => {
        document.title = 'Log';
        showLogCanvas();
    });

    page('/olympics/', () => {
        document.title = 'Olympics';
        showEventCanvas();
    });

    // Start page.js routing
    page.start();
}

/**
 * Navigate to a route programmatically
 * @param {string} path - The path to navigate to
 */
function navigateTo(path) {
    page.show(path);
}

function stringToPath(string) {
    return string.replaceAll(' ', '_')
}
function pathToString(path) {
    return path.replaceAll('_', ' ')
}