/**
 * Shared state for the map module.
 * All mutable state is centralized here to avoid global variable pollution.
 */

export const state = {
    map: null,
    markers: {},
    baseMarkers: [],
    contestedMarkers: [],
    flightPaths: {},
    airportLinkPaths: {},
    historyPaths: {},
    polylines: [],
    airportMapCircle: null,
    championMapMode: false,
    toggleStateAllianceBaseMapView: false,
    currentStyles: 'dark',
    currentPopup: null,
    activeAirportPopupInfoWindow: null,
    heatmapPositive: null,
    heatmapNegative: null,
    historyFlightMarkers: [],
    historyFlightMarkerAnimation: null,
    pmtilesRegistered: false
};

export const pathOpacityByStyle = {
    dark: {
        highlight: 0.9,
        normal: 0.5
    },
    light: {
        highlight: 1.0,
        normal: 0.8
    }
};

export function getMap() {
    return state.map;
}

export function setMap(mapInstance) {
    state.map = mapInstance;
}
