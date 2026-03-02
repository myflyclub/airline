/**
 * This is the main entry point
 * - routes.js handles routing after initialization
 * - login.js handles login and session restoration
 * - main.js has intial UI and various errata 
 */

let airports = null;
let gameConstants = null;
let postLoginScriptsLoaded = false;
const SCRIPT_BASE_PATH = `${location.origin}/assets/javascripts/`;

const PAGE_JS = 'https://cdnjs.cloudflare.com/ajax/libs/page.js/1.11.6/page.js';
const MAPLIBRE_JS = 'https://unpkg.com/maplibre-gl@5.17.0/dist/maplibre-gl.js';
const MAPLIBRE_CSS = 'https://unpkg.com/maplibre-gl@5.17.0/dist/maplibre-gl.css';

// Only what login/signup pages need
const LOGIN_SCRIPTS = [
    'login.js', 'signup.js', 'routes.js', 'main.js', 'gadgets.js', 'local-storage.js'
].map(s => SCRIPT_BASE_PATH + s);

// Needed once logged in (before routes fire)
const SESSION_SCRIPTS = [
    'airline.js', 'websocket.js', 'prompt.js', 'color.js', 'settings.js'
].map(s => SCRIPT_BASE_PATH + s);

// Scripts loaded after successful login (game features)
const POST_LOGIN_SCRIPTS = [
    'plot-chartjs.js', 'airport.js', 'airplane.js', 'model-config/index.js',
    'delegate.js', 'country.js', 'office.js', 'ranking.js', 'christmas.js',
    'bank.js', 'admin.js', 'oil.js', 'rivals.js', 'alliance.js', 'event.js',
    'search.js', 'profile.js', 'pending-action.js', 'table-utils.js',
].map(s => SCRIPT_BASE_PATH + s);

const DEFERRED_SCRIPTS = [
    'link-history.js', 'confetti.js', 'departures.js', 'campaign.js',
    'log.js', 'mobile.js', 'chat-popup.js', 'tiles.js', 'facility.js',
].map(s => SCRIPT_BASE_PATH + s);

/**
 * Loads a single script dynamically and returns a promise.
 * @param {string} src - The URL of the script to load.
 * @returns {Promise<string>} A promise that resolves with the src on success.
 */
function loadScript(src) {
    return new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = src;
        script.async = true;
        script.onload = () => resolve(src);
        script.onerror = () => reject(new Error(`Failed to load script: ${src}`));
        document.head.appendChild(script);
    });
}

/**
 * Loads a stylesheet dynamically and returns a promise.
 * @param {string} href - The URL of the stylesheet to load.
 * @returns {Promise<string>} A promise that resolves with the href on success.
 */
function loadStylesheet(href) {
    return new Promise((resolve) => {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = href;
        link.onload = () => resolve(href);
        document.head.appendChild(link);
    });
}

/**
 * Loads an array of scripts in parallel.
 * @param {string[]} scripts - An array of script URLs.
 * @returns {Promise<string[]>} A promise that resolves when all scripts are loaded.
 */
function loadScriptsParallel(scripts) {
    const promises = scripts.map(src => loadScript(src));
    return Promise.all(promises);
}

/**
 * Loads airports static data from the server.
 */
async function loadAirportsData() {
    try {
        const response = await fetch('/airports-static', { credentials: 'same-origin' });
        if (!response.ok) {
            throw new Error(`Network response was not ok: ${response.status} ${response.statusText}`);
        }
        airports = await response.json();

        // Set GeoJSON globally for map rendering
        window.airports = airports;

        // Build O(1) lookup maps from GeoJSON features
        window.airportsById = {};
        window.airportsByIata = {};
        if (airports.features) {
            for (const feature of airports.features) {
                const props = feature.properties;
                // Add coordinates from geometry to properties for convenience
                props.longitude = feature.geometry.coordinates[0];
                props.latitude = feature.geometry.coordinates[1];
                window.airportsById[props.id] = props;
                window.airportsByIata[props.iata] = props;
            }
        }

        return airports;
    } catch (error) {
        console.error('Failed to load airports:', error);
        throw error; // Re-throw to be caught by initializeApp
    }
}

async function loadGameConstants() {
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

function waitForMapLibre() {
    return new Promise((resolve) => {
        const checkMapLibre = () => {
            if (window.maplibregl) {
                resolve();
            } else {
                setTimeout(checkMapLibre, 100);
            }
        };
        checkMapLibre();
    });
}

async function initMapModule() {
    const mapModule = await import('./map/index.js');
    const mapInstance = mapModule.initializeMap();

    if (mapInstance) {
        mapInstance.on('load', () => {
            if (airports && airports.features && airports.features.length > 0) {
                mapModule.addMarkers(airports);
            }
        });
    }

    return mapModule;
}

/**
 * Load scripts that are only needed after login.
 * This includes game features.
 */
async function loadPostLoginScripts() {
    if (postLoginScriptsLoaded) {
        console.log('Post-login scripts already loaded');
        return;
    }

    try {
        // Load post-login scripts
        await loadScriptsParallel(POST_LOGIN_SCRIPTS);
        console.log('✓ Post-login scripts loaded');

        // Load deferred scripts in background
        loadScriptsParallel(DEFERRED_SCRIPTS)
            .then(() => console.log('✓ Deferred scripts loaded'))
            .catch(err => console.warn('Deferred script loading failed', err));

        postLoginScriptsLoaded = true;
    } catch (error) {
        console.error('Failed to load post-login scripts:', error);
        throw error;
    }
}

let _chatInitialized = false;
async function loadChatApp() {
    if (_chatInitialized) {
        if (typeof updateChatTabs === 'function') updateChatTabs();
        return;
    }
    try {
        await loadScript(SCRIPT_BASE_PATH + 'chat.js');
        _chatInitialized = true;
        initChat();
        if (typeof updateChatTabs === 'function') {
            updateChatTabs();
        }
        console.log('✓ Chat loaded');
    } catch (error) {
        console.warn('Failed to load Chat App:', error);
    }
}

// Make loadPostLoginScripts available globally for login.js
window.loadPostLoginScripts = loadPostLoginScripts;

// Background load promises — started in initializeApp, awaited by ensureFullBoot
let _bgSessionScripts, _bgAirports, _bgConstants, _bgMaplibre;
let _mapInitialized = false;

window.loadMap = async function() {
    if (_mapInitialized) return;
    
    // Ensure prerequisites are loaded
    await Promise.all([_bgAirports, _bgMaplibre]); 
    await waitForMapLibre();
    await loadStylesheet(MAPLIBRE_CSS);
    
    await initMapModule();
    _mapInitialized = true;
    console.log('✓ Map initialized (on demand)');

    // Perform map setup that was deferred from login
    AirlineMap.addMapControls();
    AirlineMap.addMarkers();
    if (typeof activeAirline !== 'undefined' && activeAirline) {
        AirlineMap.centerOnHQ(activeAirline);
        updateLinksInfo(); // Draw the routes now that the map is ready
    }
};

async function ensureFullBoot() {
    await Promise.all([_bgSessionScripts, _bgAirports, _bgConstants, _bgMaplibre]);
    await window.loadMap();
    await loadChatApp();
}
window.ensureFullBoot = ensureFullBoot;

async function initializeApp() {
    try {
        // Phase 1: Start ALL loads in parallel
        const loginReady = Promise.all([
            loadScriptsParallel(LOGIN_SCRIPTS),
            loadScript(PAGE_JS),
        ]);

        // Heavy assets — start now, await later
        _bgSessionScripts = loadScriptsParallel(SESSION_SCRIPTS);
        _bgAirports = loadAirportsData();
        _bgConstants = loadGameConstants();
        _bgMaplibre = loadScript(MAPLIBRE_JS);

        // Phase 2: Await only what login page needs
        await loginReady;
        console.log('✓ Login scripts ready');

        // Boot heuristic only — not authoritative. Server verified via loadUser() below.
        const mightHaveSession = hasStoredSession();

        if (mightHaveSession) {
            // Await essential session scripts
            await Promise.all([_bgSessionScripts, _bgConstants]);
            console.log('✓ Session ready');

            // NOTE: Map is NOT initialized here. It is loaded on-demand by routes.js

            try {
                await loadPostLoginScripts();
            } catch (err) {
                console.error('Failed to load post-login scripts:', err);
            }

            let sessionRestored = false;
            try {
                await loadUser();
                sessionRestored = true;
                console.log('✓ User session restored');
            } catch (err) {
                // Server rejected the session — clear stale client state
                localStorage.removeItem('sessionActive');
                document.cookie = "sessionActive=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;"
                if (!err.message || !err.message.includes('Session restore failed')) {
                    console.error('Error during session restore:', err);
                }
            }

            initializeRoutes();

            if (!sessionRestored) {
                // activeUser is null, so checkSessionGuard() returns false and the *
                // route guard will redirect to /login/ — but be explicit here too.
                page.redirect('/login/');
                return;
            }

            // Defer Chat App load
            if (window.requestIdleCallback) {
                requestIdleCallback(() => loadChatApp());
            } else {
                setTimeout(() => loadChatApp(), 2000);
            }

        } else {
            // No stored session — show login page immediately
            initializeRoutes();
        }
        console.log('✓ Application initialized');
    } catch (error) {
        console.error('Application initialization failed:', error);
    }
}

// Start initialization
initializeApp();
