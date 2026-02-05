/**
 * Custom MapLibre controls module.
 * Provides custom UI controls for the map.
 */

import { state } from './state.js';
import { addControl, removeControl } from './core.js';
import { toggleChampionMap } from './markers.js';
import { toggleHeatmap } from './heatmap.js';

// Track added controls for cleanup
const addedControls = new Map();

/**
 * Base class for custom MapLibre controls.
 */
class CustomControl {
    constructor(options = {}) {
        this.options = options;
        this._container = null;
    }

    onAdd(map) {
        this._map = map;
        this._container = document.createElement('div');
        this._container.className = 'maplibregl-ctrl maplibregl-ctrl-group';
        this._buildUI();
        return this._container;
    }

    onRemove() {
        if (this._container && this._container.parentNode) {
            this._container.parentNode.removeChild(this._container);
        }
        this._map = undefined;
    }

    _buildUI() {
        // Override in subclasses
    }
}

/**
 * Icon button control - displays an icon that triggers an action.
 * Supports toggle mode with active class.
 */
class IconButtonControl extends CustomControl {
    constructor(options) {
        super(options);
        this.iconUrl = options.iconUrl;
        this.title = options.title || '';
        this.onClick = options.onClick;
        this.id = options.id;
        this.isToggle = options.isToggle || false;
        this._active = false;
    }

    _buildUI() {
        this._container.id = this.id || '';
        this._container.className = 'maplibregl-ctrl mapIcon button';
        this._container.title = this.title;
        this._container.style.cssText = 'margin-bottom: 10px; cursor: pointer;';

        const img = document.createElement('img');
        img.src = this.iconUrl;
        img.style.cssText = 'margin: auto;';

        this._container.appendChild(img);

        this._container.addEventListener('click', (e) => {
            e.stopPropagation();
            if (this.isToggle) {
                this.toggle();
            }
            if (this.onClick) {
                this.onClick(e, this._active);
            }
        });
    }

    toggle(forceState) {
        this._active = forceState !== undefined ? forceState : !this._active;
        if (this._active) {
            this._container.classList.add('active');
        } else {
            this._container.classList.remove('active');
        }
        return this._active;
    }

    get active() {
        return this._active;
    }

    set active(value) {
        this.toggle(value);
    }
}

/**
 * Text button control - displays a button with text.
 */
class TextButtonControl extends CustomControl {
    constructor(options) {
        super(options);
        this.text = options.text || '';
        this.onClick = options.onClick;
        this.id = options.id;
        this.iconUrl = options.iconUrl;
    }

    _buildUI() {
        this._container.id = this.id || '';
        this._container.className = 'maplibregl-ctrl googleMapButton button';
        this._container.style.cssText = 'cursor: pointer; display: flex; align-items: center; padding: 8px 12px;';

        if (this.iconUrl) {
            const img = document.createElement('img');
            img.src = this.iconUrl;
            img.style.cssText = 'vertical-align: middle; padding-right: 5px; width: 16px; height: 16px;';
            this._container.appendChild(img);
        }

        const span = document.createElement('span');
        span.textContent = this.text;
        this._container.appendChild(span);

        this._container.addEventListener('click', (e) => {
            e.stopPropagation();
            if (this.onClick) {
                this.onClick(e);
            }
        });
    }
}

/**
 * Add champion toggle control.
 * @param {string} position - Control position
 */
export function addChampionControl(position = 'bottom-right') {
    if (!state.map) return;

    const control = new IconButtonControl({
        id: 'toggleChampionButton',
        iconUrl: '/assets/images/icons/crown.png',
        title: 'Toggle champion view',
        isToggle: true,
        onClick: () => toggleChampionMap()
    });

    addedControls.set('champion', control);
    addControl(control, position);
}

/**
 * Add alliance base toggle control.
 * @param {string} position - Control position
 */
export function addAllianceBaseControl(position = 'bottom-right') {
    if (!state.map) return;

    const control = new IconButtonControl({
        id: 'toggleAllianceBaseMapViewButton',
        iconUrl: '/assets/images/icons/puzzle.png',
        title: 'Toggle alliance bases',
        isToggle: true,
        onClick: () => {
            if (typeof toggleAllianceBaseMapViewButton === 'function') {
                toggleAllianceBaseMapViewButton();
            }
        }
    });

    addedControls.set('allianceBase', control);
    addControl(control, position);
}

/**
 * Add heatmap toggle control.
 * @param {string} position - Control position
 */
export function addHeatmapControl(position = 'bottom-right') {
    if (!state.map) return;

    const control = new IconButtonControl({
        id: 'toggleMapHeatmapButton',
        iconUrl: '/assets/images/icons/table-heatmap.png',
        title: 'Toggle heatmap',
        isToggle: true,
        onClick: () => toggleHeatmap()
    });

    addedControls.set('heatmap', control);
    addControl(control, position);
}

/**
 * Add exit button control (for special views like alliance map).
 * @param {string} text - Button text
 * @param {Function} onClick - Click handler
 * @param {string} position - Control position
 */
export function addExitButton(text, onClick, position = 'top-center') {
    if (!state.map) return;

    // Remove existing exit button first
    removeExitButton();

    const control = new TextButtonControl({
        id: 'exitMapViewButton',
        text: text,
        iconUrl: '/assets/images/icons/arrow-return.png',
        onClick: onClick
    });

    addedControls.set('exit', control);

    // MapLibre doesn't have top-center, so we add to top-left and style it
    addControl(control, 'top-left');

    // Center the control
    setTimeout(() => {
        const container = document.getElementById('exitMapViewButton');
        if (container) {
            container.style.cssText = 'position: absolute; left: 50%; transform: translateX(-50%); cursor: pointer;';
        }
    }, 100);
}

export function removeExitButton() {
    const control = addedControls.get('exit');
    if (control) {
        removeControl(control);
        addedControls.delete('exit');
    }
}

/**
 * Create a map button element (for compatibility).
 * @param {Object} map - Map instance (unused, for compat)
 * @param {string} text - Button text
 * @param {string} onclickAction - Click handler name
 * @param {string} id - Button ID
 * @returns {jQuery} jQuery element
 */
export function createMapButton(map, text, onclickAction, id) {
    const div = document.createElement('div');
    div.id = id;
    div.className = 'mapButton button';
    div.style.cssText = 'cursor: pointer; display: flex; align-items: center; padding: 8px 12px;';
    div.onclick = () => {
        if (typeof window[onclickAction.replace('()', '')] === 'function') {
            window[onclickAction.replace('()', '')]();
        } else {
            eval(onclickAction);
        }
    };

    const img = document.createElement('img');
    img.src = '/assets/images/icons/arrow-return.png';
    img.style.cssText = 'vertical-align: middle; padding-right: 5px;';
    div.appendChild(img);

    const span = document.createElement('span');
    span.textContent = text;
    div.appendChild(span);

    // Return jQuery-wrapped for compat
    if (typeof $ !== 'undefined') {
        return $(div);
    }
    return div;
}

/**
 * Search input control for finding airports on the map.
 */
class SearchControl extends CustomControl {
    constructor(options = {}) {
        super(options);
        this.placeholder = options.placeholder || 'Search airports...';
    }

    _buildUI() {
        this._container.id = 'mapSearch';
        this._container.className = 'maplibregl-ctrl';
        this._container.style.cssText = 'margin-top: 36px; margin-left: 0px;';

        const wrapper = document.createElement('div');
        wrapper.id = 'mapSearchWrapper';
        wrapper.className = 'mapSearchWrapper';

        const input = document.createElement('input');
        input.type = 'text';
        input.id = 'mapSearchInput';
        input.className = 'mapSearchInput';
        input.placeholder = this.placeholder;
        input.autocomplete = 'off';

        const submitBtn = document.createElement('button');
        submitBtn.type = 'button';
        submitBtn.id = 'mapSearchSubmit';
        submitBtn.className = 'mapSearchSubmit';
        submitBtn.setAttribute('aria-label', 'submit');

        const icon = document.createElement('img');
        icon.src = '/assets/images/icons/airplane.svg';
        icon.alt = '';
        submitBtn.appendChild(icon);

        wrapper.appendChild(input);
        wrapper.appendChild(submitBtn);
        this._container.appendChild(wrapper);
    }
}

/**
 * Initialize map search functionality.
 * Attaches event handlers to the #mapSearch elements for finding airports.
 */
function initializeMapSearch() {
    const container = document.getElementById('mapSearch');
    const searchWrapper = document.getElementById('mapSearchWrapper');
    const input = document.getElementById('mapSearchInput');

    if (!container || !searchWrapper || !input) {
        console.warn('Map search elements not found');
        return;
    }

    // Check if already initialized
    if (searchWrapper.querySelector('.mapSearchResults')) {
        return;
    }

    // Create results dropdown
    const resultsDiv = document.createElement('div');
    resultsDiv.className = 'mapSearchResults';
    resultsDiv.style.display = 'none';

    searchWrapper.appendChild(resultsDiv);

    // Track selected result
    let selectedAirport = null;

    // Handle input changes
    input.addEventListener('input', function() {
        const phrase = this.value.trim();
        selectedAirport = null;

        if (phrase.length < 2) {
            resultsDiv.style.display = 'none';
            resultsDiv.innerHTML = '';
            return;
        }

        // Search airports using existing search infrastructure
        const results = searchCachedData('airport', phrase);

        resultsDiv.innerHTML = '';

        if (results.length === 0) {
            resultsDiv.innerHTML = '<div class="mapSearchNoResults">No airports found</div>';
            resultsDiv.style.display = 'block';
            return;
        }

        results.forEach((airport, index) => {
            const item = document.createElement('div');
            item.className = 'mapSearchResultItem';
            if (index === 0) item.classList.add('selected');

            const text = getAirportTextEntry(airport);
            item.innerHTML = highlightText(text, phrase);
            item.dataset.airportId = airport.airportId;

            item.addEventListener('click', function() {
                selectMapSearchResult(airport, input, resultsDiv);
            });

            item.addEventListener('mouseenter', function() {
                resultsDiv.querySelectorAll('.mapSearchResultItem.selected').forEach(el => el.classList.remove('selected'));
                this.classList.add('selected');
            });

            resultsDiv.appendChild(item);
        });

        resultsDiv.style.display = 'block';
    });

    // Handle keyboard navigation using shared function
    input.addEventListener('keydown', function(e) {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            navigateSearchResults(1, resultsDiv, '.mapSearchResultItem');
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            navigateSearchResults(-1, resultsDiv, '.mapSearchResultItem');
        } else if (e.key === 'Enter') {
            e.preventDefault();
            const selectedItem = resultsDiv.querySelector('.mapSearchResultItem.selected');
            if (selectedItem) {
                const airportId = parseInt(selectedItem.dataset.airportId);
                const airport = searchCachedData('airport', input.value.trim())
                    .find(a => a.airportId === airportId);
                if (airport) {
                    selectMapSearchResult(airport, input, resultsDiv);
                }
            }
        } else if (e.key === 'Escape') {
            resultsDiv.style.display = 'none';
            input.blur();
        }
    });

    // Hide results when clicking outside
    document.addEventListener('click', function(e) {
        if (!container.contains(e.target)) {
            resultsDiv.style.display = 'none';
        }
    });

    // Show results when focusing if there's text
    input.addEventListener('focus', function() {
        if (this.value.trim().length >= 2 && resultsDiv.children.length > 0) {
            resultsDiv.style.display = 'block';
        }
    });

    // Map search submit
    const submitBtn = document.getElementById('mapSearchSubmit');
    if (submitBtn) {
        submitBtn.addEventListener('click', function() {
            const selectedItem = resultsDiv.querySelector('.mapSearchResultItem.selected');
            if (selectedItem) {
                const airportId = parseInt(selectedItem.dataset.airportId);
                const airport = searchCachedData('airport', input.value.trim()).find(a => a.airportId === airportId);
                if (airport) {
                    selectMapSearchResult(airport, input, resultsDiv);
                }
            }
        });
    }
}

/**
 * Handle selection of an airport from map search results.
 * @param {Object} airport
 * @param {HTMLElement} input
 * @param {HTMLElement} resultsDiv
 */
function selectMapSearchResult(airport, input, resultsDiv) {
    input.value = getAirportShortText(airport);
    resultsDiv.style.display = 'none';

    // Get airport coordinates - airportsById has coords added during load
    const airportData = window.airportsById?.[airport.airportId];
    if (airportData && window.AirlineMap) {
        AirlineMap.flyTo(airportData.longitude, airportData.latitude, 8);
        AirlineMap.showAirportPopup(airportData, {lat: airportData.latitude, lng: airportData.longitude});
    }
}

/**
 * Add search control to the map.
 * @param {string} position - Control position (default: top-left)
 */
export function addSearchControl(position = 'top-left') {
    if (!state.map) return;

    const control = new SearchControl({
        placeholder: 'Go to airport...'
    });

    addedControls.set('search', control);
    addControl(control, position);
    initializeMapSearch();
}

/**
 * Add airline-specific map controls.
 */
export function addMapControls() {
    if (!state.map) return;

    const mapHeight = document.getElementById('map')?.clientHeight || 0;
    const position = mapHeight > 500 ? 'bottom-right' : 'bottom-left';

    addHeatmapControl(position);
    addSearchControl('top-left');
    addAllianceBaseControl(position);
    addChampionControl(position);
}

/**
 * Clear top center controls.
 */
export function clearTopCenterControls() {
    removeExitButton();
}

/**
 * Remove a control by key.
 * @param {string} key - Control key
 */
export function removeControlByKey(key) {
    const control = addedControls.get(key);
    if (control) {
        removeControl(control);
        addedControls.delete(key);
    }
}

/**
 * Remove all custom controls.
 */
export function removeAllControls() {
    addedControls.forEach((control, key) => {
        removeControl(control);
    });
    addedControls.clear();
}

/**
 * Get a control by key.
 * @param {string} key - Control key
 * @returns {CustomControl|undefined} The control instance
 */
export function getControl(key) {
    return addedControls.get(key);
}

/**
 * Set the active state of a toggle control.
 * @param {string} key - Control key
 * @param {boolean} active - Active state
 */
export function setControlActive(key, active) {
    const control = addedControls.get(key);
    if (control && typeof control.toggle === 'function') {
        control.toggle(active);
    }
}
