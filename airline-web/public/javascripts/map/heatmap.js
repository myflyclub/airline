import { state } from './state.js';
import { hasSource, hasLayer, addSource, addLayer, removeLayer, removeSource, setSourceData } from './core.js';

const SRC_POS = 'heatmap-positive', SRC_NEG = 'heatmap-negative';
const LYR_POS = 'heatmap-positive-layer', LYR_NEG = 'heatmap-negative-layer';

const RAMP_LOYALIST = ['interpolate', ['linear'], ['heatmap-density'], 0, 'rgba(128,133,242,0)', 0.2, 'rgba(167,169,245,0.7)', 0.5, 'rgba(255,255,255,1)', 0.7, 'rgba(255,245,155,1)', 1, 'rgba(255,237,52,1)'];
const RAMP_TREND_POS = ['interpolate', ['linear'], ['heatmap-density'], 0, 'rgba(40,60,128,0)', 0.5, 'rgba(80,120,255,0.8)', 1, 'rgba(80,150,255,1)'];
const RAMP_TREND_NEG = ['interpolate', ['linear'], ['heatmap-density'], 0, 'rgba(128,60,40,0)', 0.5, 'rgba(255,120,80,0.8)', 1, 'rgba(255,150,80,1)'];

const toGeoJSON = (points) => ({
    type: 'FeatureCollection',
    features: points.map(p => ({
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [p.lng, p.lat] },
        properties: { weight: Math.abs(p.weight) }
    }))
});

export function updateHeatmap(airlineId) {
    const $panel = $('#heatmapControlPanel');
    const cycleDelta = $panel.data('cycleDelta') || 0;
    const type = $('input[name=heatmapType]:checked', '#heatmapControlPanel').val() || 'loyalistImpact';

    $.ajax({
        type: 'GET',
        url: `/airlines/${airlineId}/heatmap-data?heatmapType=${type}&cycleDelta=${cycleDelta}`,
        dataType: 'json',
        success: (res) => {
            clearHeatmap();
            if (!$panel.is(':visible')) return;

            const pos = res.points.filter(p => p.weight >= 0);
            const neg = res.points.filter(p => p.weight < 0).map(p => ({...p, weight: p.weight * -1}));
            
            const isLoyalist = type === 'loyalistImpact';
            const posRamp = isLoyalist ? RAMP_LOYALIST : RAMP_TREND_POS;
            
            if (pos.length) addHeatmapLayer(SRC_POS, LYR_POS, toGeoJSON(pos), posRamp, res.maxIntensity);
            if (neg.length && !isLoyalist) addHeatmapLayer(SRC_NEG, LYR_NEG, toGeoJSON(neg), RAMP_TREND_NEG, res.maxIntensity);

            updateHeatmapArrows(res.minDeltaCount, airlineId);
        },
        error: (x, s, e) => console.error('Heatmap error:', s, e)
    });
}

function addHeatmapLayer(srcId, layerId, data, color, maxVal) {
    if (!state.map) return;
    
    hasSource(srcId) ? setSourceData(srcId, data) : addSource(srcId, { type: 'geojson', data });
    
    if (!hasLayer(layerId)) {
        addLayer({
            id: layerId, type: 'heatmap', source: srcId,
            paint: {
                // Weight: Standardize intensity based on API max
                'heatmap-weight': ['interpolate', ['linear'], ['get', 'weight'], 0, 0, maxVal || 1, 1],
                // Intensity: Increase slightly with zoom to keep peaks visible
                'heatmap-intensity': ['interpolate', ['linear'], ['zoom'], 0, 1, 9, 3],
                'heatmap-color': color,
                'heatmap-radius': ['interpolate', ['linear'], ['zoom'], 
                    0, 8,  // Broad start    
                    3, 50,  // Broad start
                    6, 150,  // Mid-zoom merging
                    9, 250,  // Mid-zoom merging
                    12, 1200 // High zoom large coverage
                ],
                'heatmap-opacity': 0.75
            }
        });
    }
}

export function clearHeatmap() {
    if (!state.map) return;
    [LYR_POS, LYR_NEG].forEach(l => removeLayer(l));
    [SRC_POS, SRC_NEG].forEach(s => removeSource(s));
}

export function toggleHeatmap() {
    $('#heatmapControlPanel').is(':visible') ? closeHeatmap() : showHeatmap();
}

export function showHeatmap() {
    const $panel = $('#heatmapControlPanel');
    $panel.data('cycleDelta', 0).css('display', 'flex');
    const airlineId = window.rivalMapAirlineId || window.activeAirline?.id;
    if (airlineId) updateHeatmap(airlineId);
}

export function closeHeatmap() {
    clearHeatmap();
    $('#heatmapControlPanel').hide();
}

export function updateHeatmapArrows(minDelta, airlineId) {
    const delta = $('#heatmapControlPanel').data('cycleDelta');
    $('#heatmapControlPanel span.cycleDeltaText').text(delta * -10);

    const config = [
        { sel: 'prev', check: delta > minDelta, val: -1 },
        { sel: 'prevPrev', check: delta - 10 >= minDelta, val: -10 },
        { sel: 'next', check: delta < 0, val: 1 },
        { sel: 'nextNext', check: delta + 10 <= 0, val: 10 }
    ];

    config.forEach(({ sel, check, val }) => {
        const $el = $(`#heatmapControlPanel img.${sel}`);
        $el.off('click').toggleClass('blackAndWhite', !check).toggleClass('clickable', check);
        if (check) $el.on('click', () => {
            $('#heatmapControlPanel').data('cycleDelta', delta + val);
            updateHeatmap(airlineId);
        });
    });
}

export function initHeatmapControls() {
    $(() => {
        $('#heatmapControlPanel input[name=heatmapType]').change(() => {
            const aid = window.rivalMapAirlineId || window.activeAirline?.id;
            if (aid) updateHeatmap(aid);
        });
    });
}