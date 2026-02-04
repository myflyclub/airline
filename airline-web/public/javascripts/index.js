let airports = null;
const SCRIPT_BASE_PATH = `${location.origin}/assets/javascripts/`;

const CORE_LIBS = [
    'https://cdnjs.cloudflare.com/ajax/libs/page.js/1.11.6/page.js',
    'https://unpkg.com/maplibre-gl@5.17.0/dist/maplibre-gl.js'
];

const APP_REQUIRED_SCRIPTS = [
    'gadgets.js', 'main.js', 'prompt.js',
    'routes.js', 'color.js', 'plot-chartjs.js', 'airport.js',
    'airplane.js', 'model-configuration.js', 'airline.js', 'delegate.js',
    'country.js', 'office.js', 'ranking.js', 'bank.js', 'admin.js',
    'oil.js', 'rivals.js', 'alliance.js', 'event.js', 'search.js',
    'profile.js', 'settings.js', 'pending-action.js',
    'local-storage.js', 'table-utils.js', 'websocket.js', 'christmas.js'
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

/**
 * Polls to check if MapLibre GL is available.
 */
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

/**
 * Load and initialize the map module.
 */
async function initMapModule() {
    // Import the map module
    const mapModule = await import('./map/index.js');

    // Initialize the map
    const mapInstance = mapModule.initializeMap();

    // Wait for map to be ready, then add markers
    if (mapInstance) {
        mapInstance.on('load', () => {
            // airports is now GeoJSON FeatureCollection
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
        }, 0); // No 5-second delay needed
    });
}

async function initializeApp() {
    try {
        const requiredAssetsPromise = Promise.all([
            loadAirportsData(),
            loadScriptsParallel(CORE_LIBS),
            loadScriptsParallel(APP_REQUIRED_SCRIPTS)
        ]);

        await requiredAssetsPromise;
        console.log('✓ Core assets loaded (Airports, Libs, App Scripts)');

        await waitForMapLibre();
        await loadStylesheet('https://unpkg.com/maplibre-gl@5.17.0/dist/maplibre-gl.css');
        console.log('✓ MapLibre GL ready');

        // Initialize map module
        await initMapModule();
        console.log('✓ Map module initialized');

        // If a session cookie exists, restore the user session before initializing the UI
        try {
            if (typeof $ !== 'undefined' && $.cookie && $.cookie('sessionActive')) {
                await loadUser(false)
                console.log('✓ User session restored')
            }
        } catch (err) {
            // If session restore fails, continue initialization but log for debugging
            console.warn('User session restore failed:', err)
        }

        airlineInit();
        initializeRoutes();
        console.log('✓ Main application initialized (Map & Airline)');


        // Temporarily remove ng-app to prevent auto-bootstrap
        const ngAppElements = document.querySelectorAll('[ng-app]');
        const ngAppBackup = [];
        ngAppElements.forEach(element => {
            ngAppBackup.push({ element, appName: element.getAttribute('ng-app') });
            element.removeAttribute('ng-app');
        });

        loadScriptsParallel(DEFERRED_SCRIPTS)
            .then(() => console.log('✓ Deferred scripts loaded'))
            .catch(err => console.warn('Deferred script loading failed', err));

        // Load Angular, then its dependent scripts, then bootstrap
        await loadScript(ANGULAR_LIB);
        console.log('✓ AngularJS loaded');

        await loadScriptsParallel(ANGULAR_DEPENDENT_SCRIPTS);
        console.log('✓ Angular-dependent scripts loaded (chat, confetti)');

        // Restore ng-app attributes
        ngAppBackup.forEach(({ element, appName }) => {
            element.setAttribute('ng-app', appName);
        });

        // Manually bootstrap
        await bootstrapAngularJS();
        updateChatTabs();
        console.log('✓ AngularJS bootstrapped');


        console.log('🎉 Application fully initialized');

    } catch (error) {
        console.error('Application initialization failed:', error);
    }
}

// Start initialization
initializeApp();
