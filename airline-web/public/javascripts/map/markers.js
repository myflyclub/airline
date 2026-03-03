import { state } from './state.js';
import { hasSource, addSource, addLayer, removeLayer, removeSource, setSourceData, on, setCursor } from './core.js';
import { showAirportPopup } from './popups.js';

const AIRPORTS_SOURCE = 'airports';
const AIRPORTS_LAYER = 'airports-layer';
const AIRPORTS_LAYER_BASES = 'airports-layer-bases';
const AIRPORTS_LAYER_BASES_LOGO = 'airports-layer-bases-logo';
const FLIGHT_ROUTES_SOURCE = 'flight-routes';

let airportsData = null;
let hoveredRouteIds = [];
let iconsLoaded = false;
let pendingMarkerUpdate = null;

async function loadMarkerIcons() {
    if (iconsLoaded || !state.map) return;

    const mapElement = document.getElementById('map');
    if (!mapElement) return;

    const iconConfigs = [
        { name: 'headquarter', attr: 'headquarterMarker' },
        { name: 'base', attr: 'baseMarker' },
        { name: 'base-alliance', attr: 'baseAllianceMarker' },
        { name: 'base-alliance-hq', attr: 'baseAlliancehqMarker' },
        { name: 'airport-darker', attr: 'largeAirportMarker' },
        { name: 'airport', attr: 'mediumAirportMarker' },
        { name: 'airport-lighter', attr: 'smallAirportMarker' },
        { name: 'airport-gateway', attr: 'gatewayAirportMarker' },
        { name: 'airport-domestic', attr: 'domesticAirportMarker' },
        { name: 'airport-transparent', attr: 'disabledAirportMarker' }
    ];

    const loadPromises = iconConfigs.map(async ({ name, attr }) => {
        const url = mapElement.dataset[attr];
        if (!url || state.map.hasImage(name)) return;

        try {
            const response = await fetch(url);
            const svgText = await response.text();
            const img = new Image();
            const blob = new Blob([svgText], { type: 'image/svg+xml' });
            const imgUrl = URL.createObjectURL(blob);

            await new Promise((resolve, reject) => {
                img.onload = () => {
                    if (!state.map.hasImage(name)) {
                        state.map.addImage(name, img, { sdf: false });
                    }
                    URL.revokeObjectURL(imgUrl);
                    resolve();
                };
                img.onerror = reject;
                img.src = imgUrl;
            });
        } catch (error) {
            console.warn(`Failed to load icon ${name}:`, error);
        }
    });

    await Promise.all(loadPromises);
    iconsLoaded = true;

    if (pendingMarkerUpdate) {
        const { airports, options } = pendingMarkerUpdate;
        pendingMarkerUpdate = null;
        addMarkers(airports, options);
    }
}

export function initMarkers() {
    if (!state.map) return;

    if (state.map.loaded()) {
        loadMarkerIcons();
    } else {
        state.map.on('load', loadMarkerIcons);
    }

    window.addEventListener('mapStyleChanged', () => {
        iconsLoaded = false;
        loadMarkerIcons();
    });
}

function getAirportIconName(airport, baseInfo = null) {
    if (baseInfo) {
        const isActiveAirline = window.activeAirline && baseInfo.airlineId === window.activeAirline.id;
        if (isActiveAirline) {
            return baseInfo.headquarter ? 'headquarter' : 'base';
        }
        return baseInfo.headquarter ? 'base-alliance-hq' : 'base-alliance';
    }

    if (airport.isGateway) return 'airport-gateway';
    if (airport.isOrangeAirport) return 'airport-domestic';
    if (airport.size <= 3) return 'airport-lighter';
    if (airport.size <= 6) return 'airport';
    return 'airport-darker';
}

function enhanceAirportsGeoJSON(geojson, options = {}) {
    const { bases = {}, champions = {} } = options;

    return {
        type: 'FeatureCollection',
        features: geojson.features.map(feature => {
            const props = feature.properties;
            const airportId = props.id;
            const baseInfo = bases[airportId];
            const isBase = !!baseInfo;
            const championInfo = champions[airportId];

            let sortKey = props.size * 10;
            sortKey += Math.min(10, Math.floor(props.population / 1000000));
            sortKey += (props.isOrangeAirport || props.isGateway) ? 15 : 0;
            if (isBase) sortKey = 999;

            const icon = (state.championMapMode && championInfo)
                ? `airline-logo-${championInfo.championAirlineId}`
                : getAirportIconName(props, baseInfo);

            return {
                ...feature,
                id: airportId, // Required for setFeatureState
                properties: {
                    ...props,
                    icon: icon,
                    sortKey: sortKey,
                    isBase: isBase,
                    logoIcon: isBase ? `airline-logo-${baseInfo.airlineId}` : '',
                    isChampion: !!championInfo,
                    championAirlineId: championInfo?.championAirlineId,
                    championAirlineName: championInfo?.championAirlineName,
                    contested: championInfo?.contested
                }
            };
        })
    };
}

export function addMarkers(airportsGeoJSON, options = {}) {
    if (!state.map || !airportsGeoJSON?.features?.length) return;

    if (!iconsLoaded) {
        pendingMarkerUpdate = { airports: airportsGeoJSON, options };
        return;
    }

    airportsData = airportsGeoJSON;
    const geojson = enhanceAirportsGeoJSON(airportsGeoJSON, options);

    if (hasSource(AIRPORTS_SOURCE)) {
        setSourceData(AIRPORTS_SOURCE, geojson);
    } else {
        addSource(AIRPORTS_SOURCE, { type: 'geojson', data: geojson });

        addLayer({
            id: AIRPORTS_LAYER,
            type: 'symbol',
            source: AIRPORTS_SOURCE,
            filter: ['all',
                ['==', ['get', 'isBase'], false],
                ['any',
                    ['==', ['get', 'countryCode'], 'AQ'],
                    ['>=', ['get', 'size'], ['-', 12, ['*', 2, ['zoom']]]]
                ]
            ],
            layout: {
                'icon-image': ['get', 'icon'],
                'icon-size': ['interpolate', ['linear'], ['zoom'], 2, 0.8, 5, 0.95, 8, 1.1],
                'icon-allow-overlap': true,
                'icon-anchor': 'bottom',
                'symbol-sort-key': ['get', 'sortKey']
            },
            paint: {
                'icon-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 1.0, 0.8]
            }
        });

        addLayer({
            id: AIRPORTS_LAYER_BASES,
            type: 'symbol',
            source: AIRPORTS_SOURCE,
            filter: ['==', ['get', 'isBase'], true],
            layout: {
                'icon-image': ['get', 'icon'],
                'icon-size': 1.0,
                'icon-allow-overlap': true,
                'icon-anchor': 'center',
                'symbol-sort-key': 999
            },
            paint: {
                'icon-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 1.0, 0.9]
            }
        });

        addLayer({
            id: AIRPORTS_LAYER_BASES_LOGO,
            type: 'symbol',
            source: AIRPORTS_SOURCE,
            filter: ['all',
                ['==', ['get', 'isBase'], true],
                ['!=', ['get', 'logoIcon'], '']
            ],
            layout: {
                'icon-image': ['get', 'logoIcon'],
                'icon-size': 0.2,
                'icon-allow-overlap': true,
                'icon-anchor': 'center',
                'symbol-sort-key': 1000
            },
            paint: {
                'icon-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 1.0, 0.9]
            }
        });

        setupMarkerInteractions();

        // Ensure marker layers are on top of all route layers
        if (state.map.getLayer(AIRPORTS_LAYER)) {
            state.map.moveLayer(AIRPORTS_LAYER);
        }
        if (state.map.getLayer(AIRPORTS_LAYER_BASES)) {
            state.map.moveLayer(AIRPORTS_LAYER_BASES);
        }
        if (state.map.getLayer(AIRPORTS_LAYER_BASES_LOGO)) {
            state.map.moveLayer(AIRPORTS_LAYER_BASES_LOGO);
        }
    }

    // Cache markers in state
    airportsGeoJSON.features.forEach(feature => {
        const airport = feature.properties;
        state.markers[airport.id] = {
            airport: airport,
            isBase: !!options.bases?.[airport.id],
            baseInfo: options.bases?.[airport.id]
        };
    });
}

let hoveredAirportId = null;

function setupMarkerInteractions() {
    if (!state.map) return;

    // Regular airports layer interactions
    on('mouseenter', AIRPORTS_LAYER, (e) => {
        setCursor('pointer');
        if (e.features.length > 0) {
            if (hoveredAirportId !== null) {
                state.map.setFeatureState({ source: AIRPORTS_SOURCE, id: hoveredAirportId }, { hover: false });
            }
            hoveredAirportId = e.features[0].id;
            state.map.setFeatureState({ source: AIRPORTS_SOURCE, id: hoveredAirportId }, { hover: true });
        }
    });

    on('mouseleave', AIRPORTS_LAYER, () => {
        setCursor('');
        if (hoveredAirportId !== null) {
            state.map.setFeatureState({ source: AIRPORTS_SOURCE, id: hoveredAirportId }, { hover: false });
            hoveredAirportId = null;
        }
    });

    on('click', AIRPORTS_LAYER, (e) => {
        if (e.features.length === 0) return;
        const feature = e.features[0];
        const airportId = feature.properties.id;
        const airport = window.airportsById?.[airportId] || feature.properties;

        if (airport) {
            window.activeAirport = airport;
            if (window.activeAirline && typeof updateBaseInfo === 'function') {
                updateBaseInfo(airport.id);
            }
            showAirportPopup(airport, e.lngLat);
        }
    });

    // Base airports layer interactions (with route highlighting)
    on('mouseenter', AIRPORTS_LAYER_BASES, (e) => {
        setCursor('pointer');
        if (e.features.length > 0) {
            if (hoveredAirportId !== null) {
                state.map.setFeatureState({ source: AIRPORTS_SOURCE, id: hoveredAirportId }, { hover: false });
            }
            hoveredAirportId = e.features[0].id;
            state.map.setFeatureState({ source: AIRPORTS_SOURCE, id: hoveredAirportId }, { hover: true });

            // Highlight routes from/to this base airport
            const airportId = e.features[0].properties.id;
            highlightRoutesFromAirport(airportId);
        }
    });

    on('mouseleave', AIRPORTS_LAYER_BASES, () => {
        setCursor('');
        if (hoveredAirportId !== null) {
            state.map.setFeatureState({ source: AIRPORTS_SOURCE, id: hoveredAirportId }, { hover: false });
            hoveredAirportId = null;
        }
        // Clear route highlighting
        clearRouteHighlights();
    });

    on('click', AIRPORTS_LAYER_BASES, (e) => {
        if (e.features.length === 0) return;
        const feature = e.features[0];
        const airportId = feature.properties.id;
        const airport = window.airportsById?.[airportId] || feature.properties;

        if (airport) {
            window.activeAirport = airport;
            if (window.activeAirline && typeof updateBaseInfo === 'function') {
                updateBaseInfo(airport.id);
            }
            showAirportPopup(airport, e.lngLat);
        }
    });
}

/**
 * Highlight all routes connected to a specific airport.
 * @param {number} airportId - The airport ID to highlight routes for
 */
function highlightRoutesFromAirport(airportId) {
    if (!state.map || !hasSource(FLIGHT_ROUTES_SOURCE)) return;

    // Clear any previous highlights
    clearRouteHighlights();
    hoveredRouteIds = [];

    // Find all routes connected to this airport
    Object.entries(state.flightPaths).forEach(([linkId, pathEntry]) => {
        const link = pathEntry.link;
        if (link && (link.fromAirportId === airportId || link.toAirportId === airportId)) {
            const featureId = parseInt(linkId);
            hoveredRouteIds.push(featureId);
            state.map.setFeatureState(
                { source: FLIGHT_ROUTES_SOURCE, id: featureId },
                { hover: true }
            );
        }
    });
}

/**
 * Clear route highlighting from base airport hover.
 */
function clearRouteHighlights() {
    if (!state.map || !hasSource(FLIGHT_ROUTES_SOURCE)) return;

    hoveredRouteIds.forEach(featureId => {
        state.map.setFeatureState(
            { source: FLIGHT_ROUTES_SOURCE, id: featureId },
            { hover: false }
        );
    });
    hoveredRouteIds = [];
}

export function removeMarkers() {
    if (!state.map) return;
    removeLayer(AIRPORTS_LAYER);
    removeLayer(AIRPORTS_LAYER_BASES);
    removeLayer(AIRPORTS_LAYER_BASES_LOGO);
    removeSource(AIRPORTS_SOURCE);
    state.markers = {};
}

function getCurrentBases() {
    const bases = {};
    state.baseMarkers.forEach(markerInfo => {
        if (markerInfo.baseInfo) {
            bases[markerInfo.airport.id] = markerInfo.baseInfo;
        }
    });
    return bases;
}

async function useBaseLogos(bases) {
    if (!state.map) return;

    const uniqueAirlineIds = [...new Set(Object.values(bases).map(b => b.airlineId))].filter(id => id !== undefined);
    const loadPromises = uniqueAirlineIds.map(async (airlineId) => {
        const imageName = `airline-logo-${airlineId}`;
        if (state.map.hasImage(imageName)) return;

        try {
            const url = `/airlines/${airlineId}/logo`;
            const response = await fetch(url);
            const blob = await response.blob();
            const imgUrl = URL.createObjectURL(blob);
            const img = new Image();

            await new Promise((resolve, reject) => {
                img.onload = () => {
                    if (!state.map.hasImage(imageName)) {
                        state.map.addImage(imageName, img);
                    }
                    URL.revokeObjectURL(imgUrl);
                    resolve();
                };
                img.onerror = reject;
                img.src = imgUrl;
            });
        } catch (error) {
            console.warn(`Failed to load logo for airline ${airlineId}:`, error);
        }
    });

    await Promise.all(loadPromises);
}

async function useChampionLogos(champions) {
    if (!state.map) return;

    const uniqueAirlineIds = [...new Set(Object.values(champions).map(c => c.championAirlineId))].filter(id => id !== undefined);
    const loadPromises = uniqueAirlineIds.map(async (airlineId) => {
        const imageName = `airline-logo-${airlineId}`;
        if (state.map.hasImage(imageName)) return;

        try {
            const url = `/airlines/${airlineId}/logo`;
            const response = await fetch(url);
            const blob = await response.blob();
            const imgUrl = URL.createObjectURL(blob);
            const img = new Image();

            await new Promise((resolve, reject) => {
                img.onload = () => {
                    if (!state.map.hasImage(imageName)) {
                        state.map.addImage(imageName, img);
                    }
                    URL.revokeObjectURL(imgUrl);
                    resolve();
                };
                img.onerror = reject;
                img.src = imgUrl;
            });
        } catch (error) {
            console.warn(`Failed to load logo for airline ${airlineId}:`, error);
        }
    });

    await Promise.all(loadPromises);
}

export async function refreshMarkers() {
    if (!state.map || !hasSource(AIRPORTS_SOURCE) || !airportsData) return;

    const champions = window.airportsLatestData?.champions || {};
    const bases = getCurrentBases();

    await useBaseLogos(bases);

    if (state.championMapMode) {
        await useChampionLogos(champions);
    }

    const geojson = enhanceAirportsGeoJSON(airportsData, { bases, champions });
    setSourceData(AIRPORTS_SOURCE, geojson);
}

export function updateMarkersVisibility() {
    refreshMarkers();
}

export function updateAirportBaseMarkers(baseAirports) {
    state.baseMarkers = [];
    baseAirports.forEach(baseAirport => {
        if (!baseAirport) return;
        const airport = window.airportsById?.[baseAirport.airportId];
        if (airport) {
            state.baseMarkers.push({ airport: airport, isBase: true, baseInfo: baseAirport });
        }
    });

    refreshMarkers();
    return state.baseMarkers;
}

export function updateAirportMarkers(airline) {
    if (!state.map || Object.keys(state.markers).length === 0) {
        setTimeout(() => updateAirportMarkers(airline), 500);
        return;
    }
    updateAirportBaseMarkers(airline?.baseAirports || []);
}

export async function toggleChampionMap() {
    state.championMapMode = !state.championMapMode;
    await refreshMarkers();
}

export function getMarker(airportId) {
    return state.markers[airportId];
}

export function getAllMarkers() {
    return state.markers;
}
