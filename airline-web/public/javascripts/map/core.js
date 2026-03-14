/**
 * Core MapLibre initialization module.
 * Handles map creation, projection switching, and basic map operations.
 */

import { state, setMap } from './state.js';
import { initStyles, getMapStyle } from './styles.js';

const MAX_ZOOM = 12;

/**
 * Initialize the MapLibre map instance.
 * @returns {maplibregl.Map} The initialized map instance
 */
export function initMap() {
    initStyles();

    // Register PMTiles protocol handler for Protomaps tiles
    if (window.pmtiles && !state.pmtilesRegistered) {
        const protocol = new pmtiles.Protocol();
        maplibregl.addProtocol('pmtiles', protocol.tile);
        state.pmtilesRegistered = true;
    }

    const mapElement = document.getElementById('map');
    if (!mapElement) {
        console.error('Map container element not found');
        return null;
    }

    let mapInstance;
    try {
        mapInstance = new maplibregl.Map({
            container: 'map',
            style: getMapStyle(),
            center: [50.57,79.12],
            zoom: 2,
            maxZoom: MAX_ZOOM,
            renderWorldCopies: true
        });
    } catch (e) {
        console.warn('Map initialization failed (WebGL unavailable?):', e.message);
        return null;
    }

    // Store the map instance in state
    setMap(mapInstance);
    state.map = mapInstance;

    // Set globe projection after style loads and add padding so it's left centered
    mapInstance.setPadding({
        left: 0,
        right: mapInstance.getContainer().offsetWidth * 0.48,
        top: 0,
        bottom: 0
    });
    mapInstance.on('style.load', () => {
        mapInstance.setProjection({ type: 'globe' });
        window.dispatchEvent(new CustomEvent('mapReady', { detail: { map: mapInstance } }));
    });

    // Also handle the initial load
    mapInstance.on('load', () => {
        window.dispatchEvent(new CustomEvent('mapLoaded', { detail: { map: mapInstance } }));
    });

    // Add tile error handling
    mapInstance.on('error', (e) => {
        console.warn('MapLibre GL error:', e.error);
    });

    // mapInstance.on('sourcedataabort', (e) => {
    //     console.warn('Tile request aborted:', e.sourceId, e.tile);
    // });

    return mapInstance;
}

/**
 * Get the current map instance.
 * @returns {maplibregl.Map|null} The map instance or null
 */
export function getMap() {
    return state.map;
}

/**
 * Get the current zoom level.
 * @returns {number} Current zoom level
 */
export function getZoom() {
    return state.map ? state.map.getZoom() : 2;
}

/**
 * Set the map center.
 * @param {number} lng - Longitude
 * @param {number} lat - Latitude
 * @param {Object} options - Optional animation options
 */
export function setCenter(lng, lat, options = {}) {
    if (state.map) {
        if (options.animate !== false) {
            state.map.flyTo({
                center: [lng, lat],
                ...options
            });
        } else {
            state.map.setCenter([lng, lat]);
        }
    }
}

/**
 * Fit the map to specified bounds.
 * @param {number[][]} bounds - [[minLng, minLat], [maxLng, maxLat]]
 * @param {Object} options - Padding and animation options
 */
export function fitBounds(bounds, options = {}) {
    if (state.map) {
        state.map.fitBounds(bounds, {
            padding: 50,
            ...options
        });
    }
}

/**
 * Center the map on the airline's headquarters.
 * @param {Object} airline - Airline object with headquarterAirport
 * @param {number} zoom - Zoom level (default 6)
 */
export function centerOnHQ(airline, zoom = 6) {
    if (!state.map) return;

    let center = [0, 0];
    let targetZoom = 2;

    // Try to get HQ coordinates using O(1) lookup
    if (airline?.headquarterAirport?.airportId) {
        const airport = window.airportsById?.[airline.headquarterAirport.airportId];
        if (airport) {
            center = [airport.longitude, airport.latitude];
            targetZoom = zoom;
        }
    }

    state.map.flyTo({
        center: center,
        zoom: targetZoom,
        bearing: 0,
        pitch: 0
    });
}

/**
 * Add a custom control to the map.
 * @param {Object} control - MapLibre control instance
 * @param {string} position - 'top-left', 'top-right', 'bottom-left', 'bottom-right'
 */
export function addControl(control, position = 'bottom-right') {
    if (state.map) {
        state.map.addControl(control, position);
    }
}

/**
 * Remove a control from the map.
 * @param {Object} control - The control instance to remove
 */
export function removeControl(control) {
    if (state.map) {
        state.map.removeControl(control);
    }
}

/**
 * Check if a source exists on the map.
 * @param {string} sourceId - The source ID to check
 * @returns {boolean} True if source exists
 */
export function hasSource(sourceId) {
    return state.map && state.map.getSource(sourceId) !== undefined;
}

/**
 * Check if a layer exists on the map.
 * @param {string} layerId - The layer ID to check
 * @returns {boolean} True if layer exists
 */
export function hasLayer(layerId) {
    return state.map && state.map.getLayer(layerId) !== undefined;
}

/**
 * Safely add a source to the map.
 * @param {string} sourceId - The source ID
 * @param {Object} sourceConfig - The source configuration
 */
export function addSource(sourceId, sourceConfig) {
    if (state.map && !hasSource(sourceId)) {
        state.map.addSource(sourceId, sourceConfig);
    }
}

/**
 * Safely add a layer to the map.
 * @param {Object} layerConfig - The layer configuration
 * @param {string} beforeId - Optional layer ID to insert before
 */
export function addLayer(layerConfig, beforeId) {
    if (state.map && !hasLayer(layerConfig.id)) {
        state.map.addLayer(layerConfig, beforeId);
    }
}

/**
 * Remove a layer from the map.
 * @param {string} layerId - The layer ID to remove
 */
export function removeLayer(layerId) {
    if (state.map && hasLayer(layerId)) {
        state.map.removeLayer(layerId);
    }
}

/**
 * Remove a source from the map.
 * @param {string} sourceId - The source ID to remove
 */
export function removeSource(sourceId) {
    if (state.map && hasSource(sourceId)) {
        state.map.removeSource(sourceId);
    }
}

/**
 * Update source data.
 * @param {string} sourceId - The source ID
 * @param {Object} data - New GeoJSON data
 */
export function setSourceData(sourceId, data) {
    if (state.map && hasSource(sourceId)) {
        state.map.getSource(sourceId).setData(data);
    }
}

/**
 * Register an event handler on the map.
 * @param {string} event - Event name
 * @param {string} layerId - Optional layer ID for layer-specific events
 * @param {Function} handler - Event handler function
 */
export function on(event, layerId, handler) {
    if (!state.map) return;

    if (typeof layerId === 'function') {
        // No layer specified
        state.map.on(event, layerId);
    } else {
        state.map.on(event, layerId, handler);
    }
}

/**
 * Remove an event handler from the map.
 * @param {string} event - Event name
 * @param {string} layerId - Optional layer ID
 * @param {Function} handler - Event handler function
 */
export function off(event, layerId, handler) {
    if (!state.map) return;

    if (typeof layerId === 'function') {
        state.map.off(event, layerId);
    } else {
        state.map.off(event, layerId, handler);
    }
}

/**
 * Convert a point from screen coordinates to lng/lat.
 * @param {Object} point - {x, y} screen coordinates
 * @returns {maplibregl.LngLat} Geographic coordinates
 */
export function unproject(point) {
    if (state.map) {
        return state.map.unproject(point);
    }
    return null;
}

/**
 * Convert lng/lat to screen coordinates.
 * @param {number[]} lngLat - [lng, lat]
 * @returns {maplibregl.Point} Screen coordinates
 */
export function project(lngLat) {
    if (state.map) {
        return state.map.project(lngLat);
    }
    return null;
}

/**
 * Get features at a point.
 * @param {Object} point - {x, y} screen coordinates
 * @param {Object} options - Query options (layers, filter, etc.)
 * @returns {Object[]} Array of features
 */
export function queryRenderedFeatures(point, options = {}) {
    if (state.map) {
        return state.map.queryRenderedFeatures(point, options);
    }
    return [];
}

/**
 * Set the cursor style.
 * @param {string} cursor - CSS cursor value
 */
export function setCursor(cursor) {
    if (state.map) {
        state.map.getCanvas().style.cursor = cursor;
    }
}

/**
 * Fly to a specific location on the map.
 * @param {number} lng - Longitude
 * @param {number} lat - Latitude
 * @param {number} zoom - Target zoom level (default 8)
 * @param {Object} options - Additional flyTo options
 */
export function flyTo(lng, lat, zoom = 8, options = {}) {
    if (state.map) {
        state.map.flyTo({
            center: [lng, lat],
            zoom: zoom,
            ...options
        });
    }
}

function _createCircleGeoJSON(lng, lat, radiusMeters) {
    const points = 64;
    const coords = [];
    const earthRadius = 6371000;
    const latRad = lat * Math.PI / 180;
    const lngRad = lng * Math.PI / 180;
    const angularDistance = radiusMeters / earthRadius;

    for (let i = 0; i <= points; i++) {
        const bearing = (i / points) * 2 * Math.PI;

        const newLatRad = Math.asin(
            Math.sin(latRad) * Math.cos(angularDistance) +
            Math.cos(latRad) * Math.sin(angularDistance) * Math.cos(bearing)
        );

        const newLngRad = lngRad + Math.atan2(
            Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(latRad),
            Math.cos(angularDistance) - Math.sin(latRad) * Math.sin(newLatRad)
        );

        coords.push([
            newLngRad * 180 / Math.PI,
            newLatRad * 180 / Math.PI
        ]);
    }

    return {
        type: 'Feature',
        properties: {},
        geometry: {
            type: 'Polygon',
            coordinates: [coords]
        }
    };
}

/**
 * Draw a filled circle on the map identified by a string id.
 * Safe to call multiple times — removes any existing circle with the same id first.
 * @param {string} id - Unique identifier (used as source id and layer id prefix)
 * @param {number} lng - Center longitude
 * @param {number} lat - Center latitude
 * @param {number} radiusMeters - Radius in metres
 * @param {Object} options - Optional: color, fillOpacity, lineWidth, lineOpacity
 */
export function drawCircle(id, lng, lat, radiusMeters, options = {}) {
    if (!state.map) return;
    if (!state.map.isStyleLoaded()) {
        state.map.once('style.load', () => drawCircle(id, lng, lat, radiusMeters, options));
        return;
    }
    removeCircle(id);
    try {
        const geo = _createCircleGeoJSON(lng, lat, radiusMeters);
        const color = options.color || '#32CF47';
        state.map.addSource(id, { type: 'geojson', data: geo });
        state.map.addLayer({
            id: id + '-fill', type: 'fill', source: id,
            paint: { 'fill-color': color, 'fill-opacity': options.fillOpacity ?? 0.2 }
        });
        state.map.addLayer({
            id: id + '-outline', type: 'line', source: id,
            paint: { 'line-color': color, 'line-width': options.lineWidth ?? 2, 'line-opacity': options.lineOpacity ?? 0.6 }
        });
    } catch (e) {
        console.error('drawCircle failed:', e);
    }
}

/**
 * Remove a circle previously added with drawCircle().
 * @param {string} id - The same id used in drawCircle()
 */
export function removeCircle(id) {
    if (!state.map) return;
    try {
        if (state.map.getLayer(id + '-fill')) state.map.removeLayer(id + '-fill');
        if (state.map.getLayer(id + '-outline')) state.map.removeLayer(id + '-outline');
        if (state.map.getSource(id)) state.map.removeSource(id);
    } catch (e) {
        console.warn('removeCircle failed:', e);
    }
}
