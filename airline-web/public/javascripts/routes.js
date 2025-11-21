function initializeRoutes() {
    
    function getCookie(name) {
        const match = document.cookie.match(new RegExp('(?:^|; )' + name.replace(/([.$?*|{}()\[\]()+^\\\\/])/g, '\\$1') + '=([^;]*)'));
        return match ? decodeURIComponent(match[1]) : null;
    }
    // Early session check: if sessionActive cookie is missing/false, redirect to home login
    const sessionVal = getCookie('sessionActive');
    const sessionActive = sessionVal === true || sessionVal === 'true' || sessionVal === '1';
    if (!sessionActive) {
        const currentPath = window.location.pathname || '/';
        // Avoid redirect loop if already on home/login
        if (currentPath !== '/' && currentPath !== '/login') {
            window.location.replace('/');
            return;
        }
    }

    page('/', () => {
        document.title = 'MFC Map';
        showWorldMap();
    });

    page('/airport/:iata?', (ctx) => {
        const iata = ctx.params.iata ?? null;
        document.title = iata ? `${iata.toUpperCase()} Airport` : 'Airport';
        id = getAirportByAttribute(iata.toUpperCase(), 'iata').id || null;
        showAirportDetails(id);
    });

    page('/map/:iata?', (ctx) => {
        const iata = ctx.params.iata ?? null;
        document.title = 'MFC Map';
        showWorldMap();
        // Optionally focus on specific airport if needed
        // focusMapOnAirport(iata);
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