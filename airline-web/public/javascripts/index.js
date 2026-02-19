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

// AngularJS and scripts that depend on it
const ANGULAR_LIB = 'https://ajax.googleapis.com/ajax/libs/angularjs/1.4.4/angular.min.js';
const ANGULAR_DEPENDENT_SCRIPTS = [
    'chat.js'
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
 * Manually bootstrap AngularJS after all modules are defined.
 */
function bootstrapAngularJS() {
    return new Promise((resolve, reject) => {
        // Use setTimeout(0) to push this to the end of the execution queue,
        // ensuring the DOM has updated with restored 'ng-app' attributes.
        setTimeout(() => {
            if (window.angular) {
                try {
                    const ngAppElements = document.querySelectorAll('[ng-app]');
                    ngAppElements.forEach(element => {
                        const appName = element.getAttribute('ng-app');
                        window.angular.bootstrap(element, [appName]);
                    });
                    resolve();
                } catch (error) {
                    console.warn('Failed to bootstrap AngularJS:', error);
                    reject(error);
                }
            } else {
                reject(new Error('AngularJS not found on window object for bootstrap'));
            }
        }, 0);
    });
}

/**
 * Load scripts that are only needed after login.
 * This includes game features, Angular, and chat functionality.
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

        // Temporarily remove ng-app to prevent auto-bootstrap
        const ngAppElements = document.querySelectorAll('[ng-app]');
        const ngAppBackup = [];
        ngAppElements.forEach(element => {
            ngAppBackup.push({ element, appName: element.getAttribute('ng-app') });
            element.removeAttribute('ng-app');
        });

        // Load Angular, then its dependent scripts, then bootstrap
        await loadScript(ANGULAR_LIB);
        console.log('✓ AngularJS loaded');

        await loadScriptsParallel(ANGULAR_DEPENDENT_SCRIPTS);
        console.log('✓ Angular-dependent scripts loaded');

        // Restore ng-app attributes
        ngAppBackup.forEach(({ element, appName }) => {
            element.setAttribute('ng-app', appName);
        });

        // Manually bootstrap
        await bootstrapAngularJS();
        if (typeof updateChatTabs === 'function') {
            updateChatTabs();
        }
        console.log('✓ AngularJS bootstrapped');

        postLoginScriptsLoaded = true;
    } catch (error) {
        console.error('Failed to load post-login scripts:', error);
        throw error;
    }
}

// Make loadPostLoginScripts available globally for login.js
window.loadPostLoginScripts = loadPostLoginScripts;

// Background load promises — started in initializeApp, awaited by ensureFullBoot
let _bgSessionScripts, _bgAirports, _bgConstants, _bgMaplibre;
let _mapInitialized = false;

async function ensureFullBoot() {
    await Promise.all([_bgSessionScripts, _bgAirports, _bgConstants, _bgMaplibre]);
    if (!_mapInitialized) {
        await waitForMapLibre();
        await loadStylesheet(MAPLIBRE_CSS);
        await initMapModule();
        _mapInitialized = true;
        console.log('✓ Map initialized (deferred)');
    }
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

        const hasSession = checkSessionGuard();

        if (hasSession) {
            // Await heavy assets (already in flight, likely mostly done)
            await Promise.all([_bgSessionScripts, _bgAirports, _bgConstants, _bgMaplibre]);
            console.log('✓ Core assets loaded');

            await waitForMapLibre();
            await loadStylesheet(MAPLIBRE_CSS);
            await initMapModule();
            _mapInitialized = true;
            console.log('✓ Map initialized');

            try {
                await loadPostLoginScripts();
            } catch (err) {
                console.error('Failed to load post-login scripts:', err);
            }

            try {
                await loadUser();
                console.log('✓ User session restored');
            } catch (err) {
                if (err.message && err.message.includes('Session restore failed')) {
                    localStorage.removeItem('sessionActive');
                    document.cookie = "sessionActive=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;"
                    return;
                }
                console.error('Error during session restore:', err);
            }

            initializeRoutes();
        } else {
            // Show login page immediately — heavy assets continue loading in background
            initializeRoutes();
        }
        console.log('✓ Application initialized');
    } catch (error) {
        console.error('Application initialization failed:', error);
    }
}

// Start initialization
initializeApp();
