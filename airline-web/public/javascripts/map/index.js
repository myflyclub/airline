/**
 * Map module public API.
 * Exports a single AirlineMap object for cleaner global access.
 */

// Import all modules
import { state, pathOpacityByStyle } from './state.js';
import { initMap, getMap, centerOnHQ, flyTo, drawCircle, removeCircle, applyMapProjection, applyMapCentering } from './core.js';
import { initStyles, getMapStyle, getCurrentStyle, toggleMapLight, updateMapStyle, getPathOpacity } from './styles.js';
import { initMarkers, addMarkers, removeMarkers, updateAirportBaseMarkers, updateAirportMarkers, toggleChampionMap } from './markers.js';
import { initRoutes, drawFlightPath, refreshFlightPath, getLinkColor, highlightPath, unhighlightPath, highlightLink, unhighlightLink, clearAllPaths, clearPathEntry, drawTempPath, removeTempPath, drawAirportLinkPath, clearAirportLinkPaths, drawAllianceLink, drawLinkHistoryPath, showLinkHistory, clearHistoryPaths, setRoutesFromGeoJSON, getHistoryRoutesClickLayerId, ensureHistoryRoutesLayers, setRouteSelectable } from './routes.js';
import { showAirportPopup, closeAirportPopup, closeAirportInfoPopup, showLinkPopup, showAirportLinkPopup, showAllianceBasePopup, showLinkHistoryPopup, closePopup, closeAlliancePopups, closeAllianceLinkPopup } from './popups.js';
import { addMapControls, addExitButton, removeExitButton, createMapButton, clearTopCenterControls } from './controls.js';
import { updateHeatmap, clearHeatmap, toggleHeatmap, showHeatmap, closeHeatmap, updateHeatmapArrows, initHeatmapControls } from './heatmap.js';

/**
 * Full map initialization function.
 */
function initializeMap() {
    const mapInstance = initMap();
    if (mapInstance) {
        mapInstance.on('load', () => {
            initMarkers();
            initRoutes();
            initHeatmapControls();
        });
    }
    return mapInstance;
}

/**
 * AirlineMap - Single global object for all map operations.
 */
const AirlineMap = {
    // State access
    get state() { return state; },
    get map() { return state.map; },
    pathOpacityByStyle,

    // Initialization
    init: initializeMap,
    initStyles,

    // Core map operations
    getMap,
    centerOnHQ,
    flyTo,
    applyMapProjection,
    applyMapCentering,
    getMapStyle,
    getCurrentStyle,
    toggleMapLight,
    updateMapStyle,

    // Markers
    addMarkers,
    removeMarkers,
    updateAirportMarkers,
    updateAirportBaseMarkers,
    toggleChampionMap,

    // Routes/Paths
    drawFlightPath,
    refreshFlightPath,
    clearAllPaths,
    clearPathEntry,
    getLinkColor,
    setRoutesFromGeoJSON,
    setRouteSelectable,

    // Path highlighting
    highlightPath,
    unhighlightPath,
    highlightLink,
    unhighlightLink,

    // Temp paths (route planning)
    drawTempPath,
    removeTempPath,

    // Airport links view
    drawAirportLinkPath,
    clearAirportLinkPaths,
    toggleAirportLinksView() {
        clearAirportLinkPaths();
        if (typeof window.deselectLink === 'function') window.deselectLink();
        if (typeof window.toggleAirportLinks === 'function' && window.activeAirport) {
            window.toggleAirportLinks(window.activeAirport);
        }
    },
    hideAirportLinksView() {
        clearAirportLinkPaths();
        if (typeof window.updateLinksInfo === 'function') window.updateLinksInfo();
        document.getElementById('topAirportLinksPanel').style.display = 'none';
    },

    // Alliance
    drawAllianceLink,
    closeAlliancePopups,
    closeAllianceLinkPopup,
    showAllianceMap() { clearAllPaths(); },
    hideAllianceMap() {
        clearTopCenterControls();
        clearAllPaths();
        updateAirportBaseMarkers([]);
        closeAlliancePopups();
        if (typeof window.setActiveDiv === 'function') window.setActiveDiv($('#allianceCanvas'));
    },

    // Link history
    drawLinkHistoryPath,
    showLinkHistory,
    clearHistoryPaths,
    getHistoryRoutesClickLayerId,
    ensureHistoryRoutesLayers,
    clearHistoryFlightMarkers() {
        state.historyFlightMarkers = [];
        if (state.historyFlightMarkerAnimation) {
            clearInterval(state.historyFlightMarkerAnimation);
            state.historyFlightMarkerAnimation = null;
        }
    },

    // Popups
    showAirportPopup,
    closeAirportPopup,
    closeAirportInfoPopup,
    showLinkPopup,
    showAirportLinkPopup,
    showAllianceBasePopup,
    showLinkHistoryPopup,
    closePopup,

    // Heatmap
    updateHeatmap,
    clearHeatmap,
    toggleHeatmap,
    showHeatmap,
    closeHeatmap,
    updateHeatmapArrows,

    // Controls
    addExitButton(label, callback) {
        addExitButton(label || 'Exit Alliance Flight Map', callback || (() => {
            if (typeof window.hideAllianceMap === 'function') window.hideAllianceMap();
        }));
    },
    removeExitButton,
    createMapButton,
    addMapControls,
    clearTopCenterControls,

    // Circle overlays
    drawCircle,
    removeCircle,

    // Utility - link deselection
    deselectLink() {
        if (window.selectedLink) {
            unhighlightLink();
            window.selectedLink = undefined;
        }
        removeTempPath();
        $('#sidePanel').fadeOut(200);
    }
};

// Bind state properties to window for legacy access
['map', 'markers', 'flightPaths', 'polylines', 'baseMarkers', 'contestedMarkers',
 'airportLinkPaths', 'historyPaths', 'championMapMode', 'currentStyles', 'tempPath',
 'heatmapPositive', 'heatmapNegative', 'historyFlightMarkers'].forEach(prop => {
    try {
        delete window[prop];
        Object.defineProperty(window, prop, {
            get: () => state[prop],
            set: (v) => { state[prop] = v; },
            configurable: true
        });
    } catch (e) {
        if (window[prop] !== undefined) state[prop] = window[prop];
    }
});

// Export the single global object
window.AirlineMap = AirlineMap;

// Also keep initMap for the main initialization call
window.initMap = initializeMap;

export default AirlineMap;
export { AirlineMap, initializeMap, addMarkers, state, pathOpacityByStyle };
