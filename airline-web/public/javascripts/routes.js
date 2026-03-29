/**
 * Check if user has an active, server-verified session.
 * Uses the in-memory activeUser set by loadUser() — not localStorage,
 * which can be stale after session expiry.
 * @returns {boolean} true if session is verified, false otherwise
 */
function checkSessionGuard() {
    return typeof activeUser !== 'undefined' && activeUser !== null;
}

/**
 * Quick boot-time heuristic: did the user previously log in?
 * Used only to decide whether to optimistically load session scripts.
 * NOT authoritative — the server always has the final say via loadUser().
 */
function hasStoredSession() {
    if (localStorage.getItem('sessionActive') === 'true') return true;
    const match = document.cookie.match(/(?:^|; )sessionActive=([^;]*)/);
    const val = match ? decodeURIComponent(match[1]) : null;
    if (val === 'true' || val === '1') {
        localStorage.setItem('sessionActive', 'true'); // migrate
        return true;
    }
    return false;
}

async function requireMap() {
    if (window.loadMap) {
        await window.loadMap();
    }
}

function scheduleIdleLoad(fn, initialDelay = 1000) {
    setTimeout(() => {
        if (window.requestIdleCallback) {
            requestIdleCallback(fn);
        } else {
            setTimeout(fn, 1000);
        }
    }, initialDelay);
}

function backgroundLoadMap() {
    if (window.loadMap) {
        scheduleIdleLoad(() => window.loadMap());
    }
}

function backgroundLoad() {
    backgroundLoadMap();
    scheduleIdleLoad(() => {
        Rivals.prefetch();
        Alliance.prefetch();
    }, 2000);
}

function initializeRoutes() {
    page('*', (ctx, next) => {
        const isLoggedIn = checkSessionGuard();
        // If not logged in and not already on login/signup, store original path and redirect
        if (!isLoggedIn && ctx.path !== '/login/' && ctx.path !== '/signup' && ctx.path !== '/logout/') {
            localStorage.setItem('postLoginRedirect', ctx.path);
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

    page('/', async () => {
        document.title = `MFC Airline Game`;
        // If logged in, show map; otherwise redirect to login
        const isLoggedIn = checkSessionGuard();
        if (isLoggedIn) {
            await requireMap();
            showWorldMap();
        } else {
            page.redirect('/login/');
        }
    });

    page('/map/', async () => {
        await requireMap();
        document.title = activeAirline ? `${activeAirline.name} route map` : 'Route map'
        showWorldMap();
        $('#sidePanel').fadeOut(200);
    });

    page('/map/reset', async () => {
        await requireMap();
        document.title = activeAirline ? `${activeAirline.name} route map` : 'Route map'
        showWorldMap();
        $('#sidePanel').fadeOut(200);
        if (activeAirline) AirlineMap.centerOnHQ(activeAirline);
    });

    page('/map/:iata?', async (ctx) => {
        await requireMap();
        const iata = ctx.params.iata ?? null;
        document.title = activeAirline ? `${iata} | ${activeAirline.name} route map` : `${iata} route map`
        showWorldMap();
        const airport = getAirportByIata(iata.toUpperCase());
        AirlineMap.flyTo(airport.longitude, airport.latitude);
        AirlineMap.showAirportPopup(airport, {lat: airport.latitude, lng: airport.longitude});
    });

    page('/search/', async (ctx) => {
        await requireMap();
        showSearchCanvas();
    });

    page('/airport/:iata?', async (ctx) => {
        backgroundLoad();
        const iata = ctx.params.iata ?? null;
        document.title = iata ? `${iata.toUpperCase()} Airport` : 'Airport';
        id = getAirportByAttribute(iata.toUpperCase(), 'iata').id || null;
        showAirportDetails(id);
    });

    page('/flights/:link?', (ctx) => {
        backgroundLoad();
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
        backgroundLoad();
        document.title = 'Hangar';
        showAirplaneCanvas("hangar");
    });
    page('/hangar/:model', (ctx) => {
        backgroundLoad();
        document.title = 'Hangar';
        if (ctx.params.model) {
            showAirplaneCanvas("market", pathToString(ctx.params.model));
        } else {
            document.title = 'Hangar';
            showAirplaneCanvas("hangar");
        }
    });
    page('/aircraft/', () => {
        backgroundLoad();
        document.title = 'Aircraft Market';
        showAirplaneCanvas("market");
    });
    page('/aircraft/:model', (ctx) => {
        backgroundLoad();
        document.title = 'Aircraft Market';
        if (ctx.params.model) {
            showAirplaneCanvas("market", pathToString(ctx.params.model));
        } else {
            showAirplaneCanvas("market");
        }
    });
    page('/aircraft-discounts/', () => {
        backgroundLoad();
        document.title = 'Aircraft Discounts';
        showAirplaneCanvas("discounts");
    });

    page('/office/', () => {
        backgroundLoad();
        document.title = 'Office';
        showOfficeCanvas();
    });

    page('/champions/', () => {
        backgroundLoad();
        document.title = 'Champions';
        showRankingCanvas();
    });

    page('/bank/', () => {
        backgroundLoad();
        document.title = 'Bank';
        showBankCanvas();
    });

    page('/oil/', () => {
        backgroundLoad();
        document.title = 'Oil';
        showOilCanvas();
    });

    page('/rivals/', () => {
        backgroundLoadMap();
        document.title = 'Rivals';
        Rivals.show();
    });

    page('/rivals/:airlineId', (ctx) => {
        backgroundLoadMap();
        const airlineId = parseInt(ctx.params.airlineId);
        document.title = 'Rivals';
        Rivals.show(airlineId);
    });

    page('/map/rival/:airlineId', async (ctx) => {
        await requireMap();
        const airlineId = parseInt(ctx.params.airlineId);
        document.title = 'Rival Map';
        // Load rivals first to populate data, then show map
        Rivals.show(airlineId);
    });

    page('/alliance/', () => {
        backgroundLoadMap();
        document.title = 'Alliance';
        showAllianceCanvas();
    });

    page('/country/:countryCode?', (ctx) => {
        backgroundLoad();
        const code = ctx.params.countryCode ? ctx.params.countryCode.toUpperCase() : null;
        showCountryCanvas(code);
    });

    page('/olympics/', () => {
        backgroundLoad();
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