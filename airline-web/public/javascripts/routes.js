/**
 * Check if user has valid session, redirect to /login/ if on protected route without session.
 * @returns {boolean} true if session exists, false otherwise
 */
function checkSessionGuard() {
    // Check localStorage first, then fallback to cookie (for server-side set cookies)
    let sessionActive = localStorage.getItem('sessionActive') === 'true';
    
    if (!sessionActive) {
        const match = document.cookie.match(/(?:^|; )sessionActive=([^;]*)/);
        const sessionVal = match ? decodeURIComponent(match[1]) : null;
        sessionActive = sessionVal === true || sessionVal === 'true' || sessionVal === '1';
        
        if (sessionActive) { // Migrate to localStorage
             localStorage.setItem('sessionActive', 'true');
        }
    }

    console.log('✓ Session check:', sessionActive);
    return sessionActive;
}

function initializeRoutes() {
    page('*', (ctx, next) => {
        const isLoggedIn = checkSessionGuard();

        if (!isLoggedIn && ctx.path !== '/login/' || ctx.path === '/signup') {
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
        const isLoggedIn = checkSessionGuard();
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
        document.title = activeAirline ? `${activeAirline.name} route map` : 'Route map'
        showWorldMap();
        $('#sidePanel').fadeOut(200);
    });

    page('/map/reset', () => {
        document.title = activeAirline ? `${activeAirline.name} route map` : 'Route map'
        showWorldMap();
        $('#sidePanel').fadeOut(200);
        if (activeAirline) AirlineMap.centerOnHQ(activeAirline);
    });

    page('/map/:iata?', (ctx) => {
        const iata = ctx.params.iata ?? null;
        document.title = activeAirline ? `${iata} | ${activeAirline.name} route map` : `${iata} route map`
        showWorldMap();
        const airport = getAirportByIata(iata.toUpperCase());
        AirlineMap.flyTo(airport.longitude, airport.latitude);
        AirlineMap.showAirportPopup(airport, {lat: airport.latitude, lng: airport.longitude});
    });

    page('/search/', (ctx) => {
        showSearchCanvas();
    });

    page('/flights/:link?', (ctx) => {
        const linkId = ctx.params.link ?? null;
        if (linkId && loadedLinksById[linkId]) {
            var l = loadedLinksById[linkId];
            document.title = l.fromAirportCode + '-' + l.toAirportCode + ' flights';
        } else {
            document.title = linkId ? `Flight ${linkId}` : (activeAirline ? `${activeAirline.name} flights` : 'Flights');
        }
        showLinksCanvas(linkId, false);
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

    page('/rivals/', () => {
        document.title = 'Rivals';
        Rivals.show();
    });

    page('/rivals/:airlineId', (ctx) => {
        const airlineId = parseInt(ctx.params.airlineId);
        document.title = 'Rivals';
        Rivals.show(airlineId);
    });

    page('/map/rival/:airlineId', (ctx) => {
        const airlineId = parseInt(ctx.params.airlineId);
        document.title = 'Rival Map';
        // Load rivals first to populate data, then show map
        Rivals.show(airlineId);
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