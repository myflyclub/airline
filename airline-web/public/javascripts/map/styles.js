/**
 * Map styling module using Protomaps tiles.
 * Provides dark and light themes with localStorage persistence.
 */

import { state, pathOpacityByStyle } from './state.js';

// Color palettes - easy to customize
const PALETTES = {
    dark: {
        background: '#0a0f14',
        earth: '#1b1d2f',
        water: '#0d121e',
        urban: '#2f2f30',
        park: '#1a2a1a',
        aerodrome: '#1a1a1a',
        taxiwayFill: '#3a3a3a',
        runwayFill: '#4a4a4a',
        boundary: '#5b6270',
        boundaryOpacity: 0.3,
        runwayGlow: '#ffffff',
        runway: '#cccccc',
        taxiway: '#888888',
        roads: '#404040',
        rail: '#5588bb',
        buildings: '#555555',
        buildingsLandmark: '#777777',
        countryLabel: '#a0b0c0',
        localityLabel: '#8898a8',
        labelHalo: '#0a0f14',
        urbanOpacity: 0.6,
        parkOpacity: 0.3
    },
    light: {
        background: '#b4c2d0',
        earth: '#e8ecf0',
        water: '#96adc4',
        urban: '#ccd4dc',
        park: '#c4d4c8',
        aerodrome: '#bcc8d4',
        taxiwayFill: '#aab4c0',
        runwayFill: '#98a4b0',
        boundary: '#7890a8',
        boundaryOpacity: 0.7,
        runwayGlow: '#ffffff',
        runway: '#4c545e',
        taxiway: '#6f7b89',
        roads: '#97a2ac',
        rail: '#5588bb',
        buildings: '#bcc4d0',
        buildingsLandmark: '#9aa4b4',
        countryLabel: '#354870',
        localityLabel: '#445870',
        labelHalo: '#e8ecf2',
        urbanOpacity: 0.3,
        parkOpacity: 0.3
    }
};

// Locality label text-field expression: shows local script with English in parentheses
const localityTextField = [
    'case',
    // If has English name different from local name, show "Local (English)"
    ['all', ['has', 'name'], ['has', 'name:en'], ['!=', ['get', 'name'], ['get', 'name:en']]],
    ['format',
        ['get', 'name'], {},
        ' (', {},
        ['get', 'name:en'], {},
        ')', {}
    ],
    // Otherwise just show the name
    ['coalesce', ['get', 'name:en'], ['get', 'name']]
];

/**
 * Generate layers array from color palette.
 * All themes use the same detailed layer structure.
 */
function createLayers(c) {
    return [
        { id: 'background', type: 'background', paint: { 'background-color': c.background } },
        { id: 'earth', type: 'fill', source: 'protomaps', 'source-layer': 'earth',
            paint: { 'fill-color': c.earth } },
        { id: 'water', type: 'fill', source: 'protomaps', 'source-layer': 'water',
            filter: ['==', ['geometry-type'], 'Polygon'],
            paint: { 'fill-color': c.water } },
        { id: 'landuse-urban', type: 'fill', source: 'protomaps', 'source-layer': 'landuse',
            filter: ['in', ['get', 'kind'], ['literal', ['urban_area', 'residential', 'commercial', 'industrial']]],
            paint: { 'fill-color': c.urban, 'fill-opacity': c.urbanOpacity } },
        { id: 'landuse-park', type: 'fill', source: 'protomaps', 'source-layer': 'landuse',
            filter: ['in', ['get', 'kind'], ['literal', ['national_park', 'park', 'forest', 'grassland']]],
            paint: { 'fill-color': c.park, 'fill-opacity': c.parkOpacity } },
        { id: 'landuse-aerodrome', type: 'fill', source: 'protomaps', 'source-layer': 'landuse', minzoom: 8,
            filter: ['==', ['get', 'kind'], 'aerodrome'],
            paint: { 'fill-color': c.aerodrome, 'fill-opacity': 0.7 } },
        { id: 'landuse-taxiway-fill', type: 'fill', source: 'protomaps', 'source-layer': 'landuse', minzoom: 10,
            filter: ['==', ['get', 'kind'], 'taxiway'],
            paint: { 'fill-color': c.taxiwayFill } },
        { id: 'landuse-runway-fill', type: 'fill', source: 'protomaps', 'source-layer': 'landuse', minzoom: 9,
            filter: ['==', ['get', 'kind'], 'runway'],
            paint: { 'fill-color': c.runwayFill } },
        { id: 'boundaries-country', type: 'line', source: 'protomaps', 'source-layer': 'boundaries',
            filter: ['==', ['get', 'kind'], 'country'],
            paint: { 'line-color': c.boundary, 'line-width': ['interpolate', ['linear'], ['zoom'], 0, 1, 4, 1.5, 10, 2.5], 'line-opacity': c.boundaryOpacity } },
        { id: 'runway-glow', type: 'line', source: 'protomaps', 'source-layer': 'roads', minzoom: 9,
            filter: ['==', ['get', 'kind_detail'], 'runway'],
            paint: { 'line-color': c.runwayGlow, 'line-opacity': 0.12,
                'line-width': ['interpolate', ['exponential', 1.6], ['zoom'], 9, 3, 12, 12] } },
        { id: 'runway', type: 'line', source: 'protomaps', 'source-layer': 'roads', minzoom: 9,
            filter: ['==', ['get', 'kind_detail'], 'runway'],
            paint: { 'line-color': c.runway,
                'line-width': ['interpolate', ['exponential', 1.6], ['zoom'], 8, 1, 12, 6] } },
        { id: 'taxiway', type: 'line', source: 'protomaps', 'source-layer': 'roads', minzoom: 9,
            filter: ['==', ['get', 'kind_detail'], 'taxiway'],
            paint: { 'line-color': c.taxiway, 'line-opacity': 0.7,
                'line-width': ['interpolate', ['exponential', 1.6], ['zoom'], 9, 0.5, 12, 2] } },
        { id: 'roads', type: 'line', source: 'protomaps', 'source-layer': 'roads', minzoom: 5,
            filter: ['in', ['get', 'kind'], ['literal', ['highway', 'major_road', 'minor_road']]],
            paint: { 'line-color': c.roads, 'line-width': ['interpolate', ['exponential', 1.5], ['zoom'], 5, 0.4, 10, 1, 15, 3, 20, 8] } },
        { id: 'rail', type: 'line', source: 'protomaps', 'source-layer': 'roads',
            filter: ['==', ['get', 'kind'], 'rail'],
            paint: { 'line-color': c.rail, 'line-dasharray': [0.3, 0.75], 'line-opacity': 0.8,
                'line-width': ['interpolate', ['exponential', 1.6], ['zoom'], 3, 0, 6, 0.3, 12, 1.5] } },
        { id: 'buildings', type: 'fill', source: 'protomaps', 'source-layer': 'buildings', minzoom: 10,
            paint: { 'fill-color': c.buildings, 'fill-opacity': 0.8 } },
        { id: 'buildings-landmark', type: 'fill', source: 'protomaps', 'source-layer': 'buildings', minzoom: 10,
            filter: ['in', ['get', 'kind'], ['literal', ['landmark', 'stadium', 'terminal', 'hangar']]],
            paint: { 'fill-color': c.buildingsLandmark, 'fill-opacity': 0.9 } },
        { id: 'country-labels', type: 'symbol', source: 'protomaps', 'source-layer': 'places',
            filter: ['==', ['get', 'kind'], 'country'],
            layout: { 'text-field': ['format', ['coalesce', ['get', 'name:en'], ['get', 'name']], {}],
                'text-font': ['Noto Sans Medium'], 'text-size': ['interpolate', ['linear'], ['zoom'], 1, 10, 4, 12, 8, 14],
                'text-transform': 'uppercase', 'text-letter-spacing': 0.1 },
            paint: { 'text-color': c.countryLabel, 'text-halo-color': c.labelHalo, 'text-halo-width': 2 } },
        { id: 'locality-labels', type: 'symbol', source: 'protomaps', 'source-layer': 'places', minzoom: 5,
            filter: ['==', ['get', 'kind'], 'locality'],
            layout: { 'text-field': localityTextField,
                'text-font': ['Noto Sans Regular'], 'text-size': ['interpolate', ['linear'], ['zoom'], 5, 10, 10, 14, 15, 16] },
            paint: { 'text-color': c.localityLabel, 'text-halo-color': c.labelHalo, 'text-halo-width': 1.5 } }
    ];
}

/**
 * Create a complete map style from a palette.
 */
function createTheme(name) {
    const palette = PALETTES[name];
    const isDark = name === 'dark';
    return {
        version: 8,
        name: `Protomaps ${name.charAt(0).toUpperCase() + name.slice(1)}`,
        sources: { 
            protomaps: {
                type: 'vector',
                tiles: [
                    'https://maps.myfly.club/mfc/{z}/{x}/{y}.mvt'
                ],
                maxzoom: 11,
                attribution: '<a href="https://protomaps.com">Protomaps</a> © <a href="https://openstreetmap.org">OpenStreetMap</a>'
            }
        },
        glyphs: 'https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf',
        layers: createLayers(palette, isDark)
    };
}

// Generate themes from palettes
const darkTheme = createTheme('dark');
const lightTheme = createTheme('light');

/**
 * Initialize styles from localStorage or default.
 */
export function initStyles() {
    const theme = localStorage.getItem("themeMode");
    state.currentStyles = theme === 'dark' ? 'dark' : 'dark';
}

/**
 * Get the current map style object.
 */
export function getMapStyle() {
    return state.currentStyles === 'light' ? lightTheme : darkTheme;
}

/**
 * Get the current style name.
 */
export function getCurrentStyle() {
    return state.currentStyles;
}

/**
 * Toggle between dark and light map styles.
 */
export function toggleMapLight() {
    const newStyle = state.currentStyles === 'dark' ? 'light' : 'dark';
    updateMapStyle(newStyle);
}

/**
 * Update the map style to a specific theme.
 * @param {string} theme - 'dark' or 'light'
 */
export function updateMapStyle(theme) {
    if (state.currentStyles === theme) return;
    
    state.currentStyles = theme;

    if (state.map) {
        state.map.setStyle(getMapStyle());
        state.map.once('style.load', () => {
            window.dispatchEvent(new CustomEvent('mapStyleChanged', { detail: { style: state.currentStyles } }));
        });
    }

    if (typeof refreshLinks === 'function') refreshLinks(false);
}

/**
 * Get path opacity based on current style.
 */
export function getPathOpacity(type = 'normal') {
    return pathOpacityByStyle[state.currentStyles][type];
}
