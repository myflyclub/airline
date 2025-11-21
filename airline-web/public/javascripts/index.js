let airports = null;
const SCRIPT_BASE_PATH = `${location.origin}/assets/javascripts/`;

const CORE_LIBS = [
    'https://cdnjs.cloudflare.com/ajax/libs/page.js/1.11.6/page.js',
    `https://maps.googleapis.com/maps/api/js?v=weekly&key=${window.googleMapKey}&libraries=geometry,drawing,visualization`
];

const APP_REQUIRED_SCRIPTS = [
    'gadgets.js', 'map.js', 'map-style.js', 'map-button.js', 'main.js', 'prompt.js',
    'routes.js', 'color.js', 'plot-chartjs.js', 'airport.js',
    'airplane.js', 'model-configuration.js', 'airline.js', 'delegate.js',
    'country.js', 'office.js', 'ranking.js', 'bank.js', 'admin.js',
    'oil.js', 'rivals.js', 'alliance.js', 'event.js', 'search.js',
    'profile.js', 'settings.js', 'pending-action.js', 'about.js',
    'local-storage.js', 'table-utils.js', 'websocket.js', 'christmas.js'
].map(s => SCRIPT_BASE_PATH + s);

// AngularJS and scripts that depend on it
const ANGULAR_LIB = 'https://ajax.googleapis.com/ajax/libs/angularjs/1.4.4/angular.min.js';
const ANGULAR_DEPENDENT_SCRIPTS = [
    'chat.js'
].map(s => SCRIPT_BASE_PATH + s);

const DEFERRED_SCRIPTS = [
    'heatmap.js', 'link-history.js', 'confetti.js', 'departures.js', 'campaign.js',
    'log.js', 'mobile.js', 'ui-theme.js', 'chat-popup.js', 'tiles.js', 'facility.js',
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
        return airports;
    } catch (error) {
        console.error('Failed to load airports:', error);
        throw error; // Re-throw to be caught by initializeApp
    }
}

/**
 * Polls to check if google.maps is available.
 */
function waitForGoogleMaps() {
    return new Promise((resolve) => {
        const checkMaps = () => {
            if (window.google && window.google.maps) {
                resolve();
            } else {
                setTimeout(checkMaps, 100);
            }
        };
        checkMaps();
    });
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

        await waitForGoogleMaps();
        console.log('✓ Google Maps ready');
        initMap();

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