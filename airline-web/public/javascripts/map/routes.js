/**
 * Flight routes/paths module using GeoJSON with great circle arcs.
 * Handles drawing and interaction with flight paths.
 */

import { state, pathOpacityByStyle } from './state.js';
import { hasSource, hasLayer, addSource, addLayer, removeLayer, removeSource, setSourceData, on, off, setCursor } from './core.js';
import { createGreatCircleGeometry } from './geodesic.js';
import { getCurrentStyle, getPathOpacity } from './styles.js';

let tempPathData = null;
let highlightedLinkId = null;
let highlightAnimation = null;
let hoveredRouteId = null;
let routeSelectable = true;

/**
 * Generic route layer factory - creates source, layers, and refresh function.
 */
function createRouteLayer(sourceId, layerId, options = {}) {
    const clickLayerId = options.clickLayer ? `${layerId}-click` : null;

    function ensure() {
        if (!state.map || hasSource(sourceId)) return;
        // Insert route layers below airport markers if they exist
        const beforeId = hasLayer('airports-layer') ? 'airports-layer' : undefined;
        addSource(sourceId, { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
        addLayer({
            id: layerId, type: 'line', source: sourceId,
            layout: options.layout || { 'line-cap': 'round', 'line-join': 'round' },
            paint: options.paint || {
                'line-color': ['get', 'color'],
                'line-width': options.hoverWidth ? ['case', ['boolean', ['feature-state', 'hover'], false], 2, 1.5] : 1.5,
                'line-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 0.9, ['get', 'opacity']]
            }
        }, beforeId);
        if (clickLayerId) {
            addLayer({
                id: clickLayerId, type: 'line', source: sourceId,
                layout: { 'line-cap': 'round', 'line-join': 'round' },
                paint: { 'line-color': '#000', 'line-width': options.clickWidth || 15, 'line-opacity': 0.001 }
            }, beforeId);
        }
    }

    function refresh(features) {
        ensure();
        if (hasSource(sourceId)) setSourceData(sourceId, { type: 'FeatureCollection', features });
    }

    function clear() {
        if (hasSource(sourceId)) setSourceData(sourceId, { type: 'FeatureCollection', features: [] });
    }

    return { sourceId, layerId, clickLayerId, ensure, refresh, clear };
}

// Create route layer instances
const flightRoutes = createRouteLayer('flight-routes', 'flight-routes-layer', { clickLayer: true, hoverWidth: true });
const airportLinks = createRouteLayer('airport-links', 'airport-links-layer', { clickLayer: true, clickWidth: 10 });
const allianceRoutes = createRouteLayer('alliance-routes', 'alliance-routes-layer', {});
const historyRoutes = createRouteLayer('history-routes', 'history-routes-layer', { clickLayer: true, clickWidth: 10 });

/**
 * Get link color based on profit and revenue.
 * @param {number} profit - Link profit
 * @param {number} revenue - Link revenue
 * @returns {string} Hex color string
 */
export function getLinkColor(profit, revenue) {
    if (profit === undefined) {
        return '#DCDC20'; // No history yet - yellow
    }

    const maxProfitFactor = 0.5;
    const minProfitFactor = -0.5;
    let profitFactor;

    if (revenue > 0) {
        profitFactor = profit / revenue;
    } else if (profit < 0) {
        profitFactor = minProfitFactor;
    } else {
        profitFactor = 0;
    }

    profitFactor = Math.max(minProfitFactor, Math.min(maxProfitFactor, profitFactor));

    let redHex, greenHex;
    if (profitFactor > 0) {
        redHex = 220 * (1 - (profitFactor / maxProfitFactor));
    } else {
        redHex = 220;
    }

    if (profitFactor < 0) {
        greenHex = 220 * (1 + (profitFactor / maxProfitFactor));
    } else {
        greenHex = 220;
    }

    const currentStyle = getCurrentStyle();
    if (currentStyle === 'light') {
        redHex -= 50;
        greenHex -= 50;
    }

    redHex = Math.max(0, Math.round(redHex));
    greenHex = Math.max(0, Math.round(greenHex));

    const redHexString = redHex.toString(16).padStart(2, '0');
    const greenHexString = greenHex.toString(16).padStart(2, '0');

    return `#${redHexString}${greenHexString}20`;
}

/**
 * Initialize the routes system.
 */
export function initRoutes() {
    if (!state.map) return;

    // Listen for style changes to re-add layers
    window.addEventListener('mapStyleChanged', () => {
        // Re-add route layers after style change
        if (Object.keys(state.flightPaths).length > 0) {
            refreshAllRoutes();
        }
    });
}

/**
 * Convert flight links to GeoJSON FeatureCollection.
 * @param {Object} flightPaths - Object of flight path data keyed by link ID
 * @returns {Object} GeoJSON FeatureCollection
 */
function flightPathsToGeoJSON(flightPaths) {
    const features = [];

    // Add temporary path if it exists
    if (tempPathData) {
        features.push({
            type: 'Feature',
            id: -1,
            geometry: tempPathData.geometry,
            properties: {
                id: -1,
                color: tempPathData.color,
                opacity: tempPathData.opacity || getPathOpacity('highlight'),
                highlighted: highlightedLinkId == -1
            }
        });
    }

    Object.entries(flightPaths).forEach(([linkId, pathEntry]) => {
        const link = pathEntry.link;
        if (!link) return;

        const geometry = createGreatCircleGeometry(
            link.fromLongitude, link.fromLatitude,
            link.toLongitude, link.toLatitude,
            50
        );

        features.push({
            type: 'Feature',
            id: parseInt(linkId),
            geometry: geometry,
            properties: {
                id: parseInt(linkId),
                color: getLinkColor(link.profit, link.revenue),
                opacity: getPathOpacity('normal'),
                fromAirportId: link.fromAirportId,
                toAirportId: link.toAirportId,
                fromAirportCode: link.fromAirportCode,
                toAirportCode: link.toAirportCode,
                fromAirportCity: link.fromAirportCity,
                toAirportCity: link.toAirportCity,
                frequency: link.frequency,
                highlighted: linkId == highlightedLinkId
            }
        });
    });

    return {
        type: 'FeatureCollection',
        features: features
    };
}

/**
 * Enhance backend GeoJSON with great circle geometry and derived properties.
 * @param {Object} geojson - GeoJSON FeatureCollection from backend
 * @param {string|null} colorOverride - Optional fixed color for all routes
 * @returns {Object} Enhanced GeoJSON with great circle arcs
 */
function enhanceLinksGeoJSON(geojson, colorOverride = null) {
    const enhancedFeatures = geojson.features.map(feature => {
        const props = feature.properties;
        const coords = feature.geometry.coordinates;

        // Convert simple LineString to great circle arc
        const geometry = createGreatCircleGeometry(
            coords[0][0], coords[0][1],  // from longitude, latitude
            coords[1][0], coords[1][1],  // to longitude, latitude
            50
        );

        return {
            type: 'Feature',
            id: feature.id,
            geometry: geometry,
            properties: {
                ...props,
                color: colorOverride || getLinkColor(props.profit, props.revenue),
                opacity: getPathOpacity('normal'),
                highlighted: feature.id === highlightedLinkId
            }
        };
    });

    return {
        type: 'FeatureCollection',
        features: enhancedFeatures
    };
}

/**
 * Set all routes from backend GeoJSON.
 * @param {Object} geojson - GeoJSON FeatureCollection from backend
 * @param {string|null} colorOverride - Optional fixed color for all routes
 */
export function setRoutesFromGeoJSON(geojson, colorOverride = null) {
    if (!state.map) return;
    ensureRoutesLayers();

    // Store links in flightPaths, including source coordinates so
    // flightPathsToGeoJSON can recreate geometry if refreshRoutesGeoJSON is called later
    geojson.features.forEach(feature => {
        const props = feature.properties;
        const coords = feature.geometry.coordinates;
        const color = colorOverride || getLinkColor(props.profit, props.revenue);
        state.flightPaths[props.id] = {
            link: {
                ...props,
                fromLongitude: coords[0][0],
                fromLatitude: coords[0][1],
                toLongitude: coords[1][0],
                toLatitude: coords[1][1]
            },
            color,
            colorOverride,
            opacity: getPathOpacity('normal')
        };
    });

    // Enhance and set the GeoJSON
    flightRoutes.refresh(enhanceLinksGeoJSON(geojson, colorOverride).features);
}

/**
 * Ensure routes source and layers exist.
 */
function ensureRoutesLayers() {
    if (!state.map) return;
    const wasNew = !hasSource(flightRoutes.sourceId);
    flightRoutes.ensure();
    if (wasNew) setupRouteInteractions();
}

/**
 * Set up click and hover interactions for routes.
 */
function setupRouteInteractions() {
    if (!state.map) return;

    // Use mousemove to continuously check closest feature
    on('mousemove', flightRoutes.clickLayerId, (e) => {
        setCursor('pointer');
        if (e.features.length > 0) {
            // Features are sorted by distance, first is closest
            const closestFeature = e.features[0];
            const linkId = closestFeature.properties.id;
            const featureId = closestFeature.id;

            // Only update if hovering a different route
            if (hoveredRouteId !== featureId) {
                if (hoveredRouteId !== null) {
                    state.map.setFeatureState({ source: flightRoutes.sourceId, id: hoveredRouteId }, { hover: false });
                    if (highlightedLinkId && !state.selectedLink) unhighlightPath(highlightedLinkId);
                }
                hoveredRouteId = featureId;
                state.map.setFeatureState({ source: flightRoutes.sourceId, id: hoveredRouteId }, { hover: true });
                highlightPath(linkId, false);
            }
        }
    });

    on('mouseleave', flightRoutes.clickLayerId, () => {
        setCursor('');
        if (hoveredRouteId !== null) {
            state.map.setFeatureState({ source: flightRoutes.sourceId, id: hoveredRouteId }, { hover: false });
            hoveredRouteId = null;
        }
        if (highlightedLinkId && !state.selectedLink) unhighlightPath(highlightedLinkId);
    });

    on('click', flightRoutes.clickLayerId, (e) => {
        if (!routeSelectable) return;
        // Don't select a route if the click is on an airport marker
        const airportFeatures = state.map.queryRenderedFeatures(e.point, {
            layers: ['airports-layer', 'airports-layer-bases']
        });
        if (airportFeatures.length > 0) return;
        if (e.features.length > 0) {
            const linkId = e.features[0].properties.id;
            if (typeof selectLinkFromMap === 'function') selectLinkFromMap(linkId, false);
        }
    });
}

/**
 * Draw a flight path on the map.
 * @param {Object} link - Link data
 * @param {string} linkColor - Optional override color
 * @returns {Object} Path entry object
 */
export function drawFlightPath(link, linkColor) {
    if (!state.map) return null;

    if (!link.id) {
        return drawTempPath(link, linkColor || '#2658d3');
    }

    ensureRoutesLayers();

    if (!linkColor) {
        linkColor = getLinkColor(link.profit, link.revenue);
    }

    // Create path entry for compatibility
    const pathEntry = {
        id: link.id,
        path: link.id, // for compatibility with legacy code
        link: link,
        color: linkColor,
        opacity: getPathOpacity('normal')
    };

    state.flightPaths[link.id] = pathEntry;
    refreshRoutesGeoJSON();

    return pathEntry;
}

/**
 * Refresh the routes GeoJSON with current flight paths.
 */
function refreshRoutesGeoJSON() {
    if (!state.map) return;
    flightRoutes.refresh(flightPathsToGeoJSON(state.flightPaths).features);
}

/**
 * Refresh all routes (after style change).
 */
function refreshAllRoutes() {
    ensureRoutesLayers();
    refreshRoutesGeoJSON();
}

/**
 * Refresh a specific flight path's appearance.
 * @param {Object} link - Link data
 * @param {boolean} forceRedraw - Force full redraw
 */
export function refreshFlightPath(link, forceRedraw = false) {
    if (!state.flightPaths[link.id]) return;

    state.flightPaths[link.id].link = link;
    state.flightPaths[link.id].color = getLinkColor(link.profit, link.revenue);
    state.flightPaths[link.id].opacity = getPathOpacity('normal');

    refreshRoutesGeoJSON();
}

/**
 * Highlight a flight path.
 * @param {number} linkId - Link ID to highlight
 * @param {boolean} refocus - Whether to pan to the path
 */
export function highlightPath(linkId, refocus = false) {
    if (!state.map) return;

    let pathEntry;
    if (linkId === -1) {
        pathEntry = tempPathData;
    } else {
        pathEntry = state.flightPaths[linkId];
    }

    if (!pathEntry) return;

    highlightedLinkId = linkId;
    const link = pathEntry.link;

    if (refocus && link) {
        state.map.flyTo({
            center: [link.fromLongitude, link.fromLatitude],
            duration: 500
        });
    }

    pathEntry.opacity = getPathOpacity('highlight');
    refreshRoutesGeoJSON();

    // Start color animation
    startHighlightAnimation(linkId);
}

/**
 * Start the highlight color animation.
 * @param {number} linkId - Link ID being highlighted
 */
function startHighlightAnimation(linkId) {
    if (highlightAnimation) {
        cancelAnimationFrame(highlightAnimation);
    }

    const pathEntry = state.flightPaths[linkId];
    if (!pathEntry) return;

    const originalColor = pathEntry.color;
    const startTime = performance.now();
    const duration = 2000; // 2 second cycle

    function animate(currentTime) {
        if (highlightedLinkId != linkId) return;

        const elapsed = (currentTime - startTime) % duration;
        const progress = elapsed / duration;

        // Pulse from original color to white and back
        const pulseValue = Math.sin(progress * Math.PI * 2) * 0.5 + 0.5;

        // Parse original color
        const r = parseInt(originalColor.slice(1, 3), 16);
        const g = parseInt(originalColor.slice(3, 5), 16);
        const b = parseInt(originalColor.slice(5, 7), 16);

        // Interpolate to white
        const newR = Math.round(r + (255 - r) * pulseValue);
        const newG = Math.round(g + (255 - g) * pulseValue);
        const newB = Math.round(b + (255 - b) * pulseValue);

        const animatedColor = `#${newR.toString(16).padStart(2, '0')}${newG.toString(16).padStart(2, '0')}${newB.toString(16).padStart(2, '0')}`;

        pathEntry.color = animatedColor;
        refreshRoutesGeoJSON();

        highlightAnimation = requestAnimationFrame(animate);
    }

    highlightAnimation = requestAnimationFrame(animate);
}

/**
 * Unhighlight a flight path.
 * @param {number} linkId - Link ID to unhighlight
 */
export function unhighlightPath(linkId) {
    if (highlightAnimation) {
        cancelAnimationFrame(highlightAnimation);
        highlightAnimation = null;
    }

    highlightedLinkId = null;

    let pathEntry;
    if (linkId === -1) {
        pathEntry = tempPathData;
    } else {
        pathEntry = state.flightPaths[linkId];
    }

    if (pathEntry) {
        const link = pathEntry.link;
        pathEntry.color = pathEntry.colorOverride || getLinkColor(link?.profit, link?.revenue);
        pathEntry.opacity = getPathOpacity('normal');
        refreshRoutesGeoJSON();
    }
}

/**
 * Highlight a link (high-level API).
 * @param {number} linkId - Link ID
 * @param {boolean} refocus - Whether to pan map
 */
export function highlightLink(linkId, refocus = false) {
    // Remove temp path if exists and we're not highlighting it
    if (linkId !== -1) {
        removeTempPath();
    }
    highlightPath(linkId, refocus);
}

/**
 * Unhighlight the currently highlighted link.
 */
export function unhighlightLink() {
    Object.keys(state.flightPaths).forEach(linkId => {
        if (highlightedLinkId === parseInt(linkId)) {
            unhighlightPath(parseInt(linkId));
        }
    });
}

/**
 * Clear all flight paths from the map.
 */
export function clearAllPaths() {
    state.flightPaths = {};
    flightRoutes.clear();
    state.polylines = [];
}

/**
 * Clear path entry (compatibility).
 * @param {Object} pathEntry - Path entry to clear
 */
export function clearPathEntry(pathEntry) {
    if (pathEntry && pathEntry.link && pathEntry.link.id) {
        delete state.flightPaths[pathEntry.link.id];
        refreshRoutesGeoJSON();
    }
}

/**
 * Draw a temporary path (for route planning).
 * @param {Object} link - Link data
 * @param {string} color - Path color
 * @returns {Object} Temp path data
 */
export function drawTempPath(link, color = '#FFFF00') {
    tempPathData = {
        id: -1,
        path: -1, // for compatibility
        link: link,
        color: color,
        geometry: createGreatCircleGeometry(
            link.fromLongitude, link.fromLatitude,
            link.toLongitude, link.toLatitude,
            50
        )
    };

    // Add temp path to the route source
    refreshRoutesGeoJSON();
    return tempPathData;
}

/**
 * Remove the temporary path.
 */
export function removeTempPath() {
    if (tempPathData) {
        tempPathData = null;
        refreshRoutesGeoJSON();
    }
}

/**
 * Draw an airport link path (showing all routes from an airport).
 * @param {Object} localAirport - Local airport
 * @param {Object} details - Link details
 */
export function drawAirportLinkPath(localAirport, details) {
    if (!state.map) return;

    const remoteAirport = details.remoteAirport;
    const pathKey = remoteAirport.id;

    const totalCapacity = details.capacity.total;
    let opacity;
    if (totalCapacity < 2000) {
        opacity = 0.2 + (totalCapacity / 2000) * 0.6;
    } else {
        opacity = 0.8;
    }

    state.airportLinkPaths[pathKey] = {
        localAirport: localAirport,
        remoteAirport: remoteAirport,
        details: details,
        color: '#DC83FC',
        opacity: opacity,
        geometry: createGreatCircleGeometry(
            localAirport.longitude, localAirport.latitude,
            remoteAirport.longitude, remoteAirport.latitude,
            50
        )
    };

    refreshAirportLinksGeoJSON();
}

/**
 * Refresh airport links GeoJSON.
 */
function refreshAirportLinksGeoJSON() {
    if (!state.map) return;
    const features = Object.entries(state.airportLinkPaths).map(([key, pathData]) => ({
        type: 'Feature',
        properties: { id: key, color: pathData.color, opacity: pathData.opacity,
            fromAirport: pathData.localAirport, toAirport: pathData.remoteAirport, details: pathData.details },
        geometry: pathData.geometry
    }));
    airportLinks.refresh(features);
}

/**
 * Clear all airport link paths.
 */
export function clearAirportLinkPaths() {
    state.airportLinkPaths = {};
    airportLinks.clear();
}

/**
 * Draw an alliance link path.
 * @param {Object} link - Link data
 * @returns {Object} Path entry
 */
export function drawAllianceLink(link) {
    if (!state.map) return null;

    const strokeColor = window.airlineColors?.[link.airlineId] || '#DC83FC';

    const maxOpacity = 0.7;
    const minOpacity = 0.1;
    const standardCapacity = 10000;
    let strokeOpacity;

    if (link.capacity.total < standardCapacity) {
        strokeOpacity = minOpacity + link.capacity.total / standardCapacity * (maxOpacity - minOpacity);
    } else {
        strokeOpacity = maxOpacity;
    }

    const pathEntry = {
        link: link,
        color: strokeColor,
        opacity: strokeOpacity,
        geometry: createGreatCircleGeometry(
            link.fromLongitude, link.fromLatitude,
            link.toLongitude, link.toLatitude,
            50
        )
    };

    // Store and refresh
    if (!state.alliancePaths) state.alliancePaths = {};
    const pathKey = `${link.fromAirportId}-${link.toAirportId}-${link.airlineId}`;
    state.alliancePaths[pathKey] = pathEntry;

    refreshAllianceRoutesGeoJSON();

    return { path: pathEntry, shadow: pathEntry };
}

/**
 * Refresh alliance routes GeoJSON.
 */
function refreshAllianceRoutesGeoJSON() {
    if (!state.map) return;
    const features = Object.entries(state.alliancePaths || {}).map(([key, pathData]) => ({
        type: 'Feature',
        properties: { color: pathData.color, opacity: pathData.opacity, link: pathData.link },
        geometry: pathData.geometry
    }));
    allianceRoutes.refresh(features);
}

/**
 * Draw a link history path.
 * @param {Object} link - Link data
 * @param {boolean} inverted - Whether path is inverted
 * @param {number} watchedLinkId - The main link being watched
 * @param {number} step - Step in the journey
 */
export function drawLinkHistoryPath(link, inverted, watchedLinkId, step) {
    if (!state.map) return;

    const pathKey = `${link.fromAirportId}|${link.toAirportId}|${inverted}`;
    const isWatchedLink = link.linkId === watchedLinkId;

    if (!state.historyPaths[pathKey]) {
        state.historyPaths[pathKey] = {
            link: link,
            inverted: inverted,
            watched: isWatchedLink,
            step: step,
            color: '#DC83FC',
            opacity: 0.8,
            thisAirlinePassengers: 0,
            thisAlliancePassengers: 0,
            otherAirlinePassengers: 0,
            geometry: createGreatCircleGeometry(
                link.fromLongitude, link.fromLatitude,
                link.toLongitude, link.toLatitude,
                50
            )
        };
    }

    // Add passenger counts
    const historyPath = state.historyPaths[pathKey];
    if (link.airlineId === window.activeAirline?.id) {
        historyPath.thisAirlinePassengers += link.passenger;
    } else if (window.currentAirlineAllianceMembers?.includes(link.airlineId)) {
        historyPath.thisAlliancePassengers += link.passenger;
    } else {
        historyPath.otherAirlinePassengers += link.passenger;
    }
}

/**
 * Show link history paths with filtering.
 * Call this after setting visible/color/opacity on historyPaths entries.
 */
export function showLinkHistory() {
    // This function is called after all history paths are drawn
    // It updates visibility based on filter options
    refreshHistoryRoutesGeoJSON();
}

/**
 * Get the history routes layer ID for event binding.
 */
export function getHistoryRoutesClickLayerId() {
    return `${historyRoutes.layerId}-click`;
}

/**
 * Ensure history routes layers exist.
 */
export function ensureHistoryRoutesLayers() {
    historyRoutes.ensure();
    if (state.map && !hasLayer('history-routes-arrows')) {
        addLayer({
            id: 'history-routes-arrows',
            type: 'symbol',
            source: historyRoutes.sourceId,
            layout: {
                'symbol-placement': 'line',
                'symbol-spacing': 150,
                'text-field': '➤',
                'text-size': 12,
                'text-font': ['Noto Sans Regular'],
                'text-keep-upright': false,
                'text-rotation-alignment': 'map',
                'text-allow-overlap': true,
                'text-ignore-placement': true
            },
            paint: {
                'text-color': ['get', 'color'],
                'text-opacity': ['get', 'opacity']
            }
        });
    }
}

/**
 * Refresh history routes GeoJSON.
 */
function refreshHistoryRoutesGeoJSON() {
    if (!state.map) return;
    const features = [];

    Object.entries(state.historyPaths).forEach(([key, pathData]) => {
        // Skip paths marked as not visible
        if (pathData.visible === false) return;

        const link = pathData.link;
        let geometry = pathData.geometry;
        
        if (pathData.inverted) {
            // Reverse geometry for symbol-placement: line to point in correct direction
            if (geometry.type === 'LineString') {
                geometry = {
                    type: 'LineString',
                    coordinates: [...geometry.coordinates].reverse()
                };
            } else if (geometry.type === 'MultiLineString') {
                geometry = {
                    type: 'MultiLineString',
                    coordinates: geometry.coordinates.map(line => [...line].reverse()).reverse()
                };
            }
        }

        features.push({
            type: 'Feature',
            properties: {
                color: pathData.color,
                opacity: pathData.opacity,
                inverted: pathData.inverted,
                watched: pathData.watched,
                thisAirlinePassengers: pathData.thisAirlinePassengers,
                thisAlliancePassengers: pathData.thisAlliancePassengers,
                otherAirlinePassengers: pathData.otherAirlinePassengers,
                // Airport details for popups
                fromAirportCode: link?.fromAirportCode,
                fromAirportCity: link?.fromAirportCity,
                fromCountryCode: link?.fromCountryCode,
                toAirportCode: link?.toAirportCode,
                toAirportCity: link?.toAirportCity,
                toCountryCode: link?.toCountryCode
            },
            geometry: geometry
        });
    });

    historyRoutes.refresh(features);
}

/**
 * Clear history paths.
 */
export function clearHistoryPaths() {
    state.historyPaths = {};
    historyRoutes.clear();
}

/**
 * Get all flight paths.
 * @returns {Object} Flight paths object
 */
export function getFlightPaths() {
    return state.flightPaths;
}

/**
 * Enable or disable click-to-select on flight routes.
 * @param {boolean} selectable
 */
export function setRouteSelectable(selectable) {
    routeSelectable = selectable;
}
