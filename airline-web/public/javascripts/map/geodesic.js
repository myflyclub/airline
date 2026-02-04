/**
 * Great circle arc interpolation for curved flight routes.
 * Uses spherical trigonometry to calculate intermediate points along
 * the shortest path between two points on Earth's surface.
 */

const DEG_TO_RAD = Math.PI / 180;
const RAD_TO_DEG = 180 / Math.PI;

/**
 * Interpolate points along a great circle arc.
 * @param {number} fromLng - Starting longitude
 * @param {number} fromLat - Starting latitude
 * @param {number} toLng - Ending longitude
 * @param {number} toLat - Ending latitude
 * @param {number} numPoints - Number of points to interpolate (default: 50)
 * @returns {number[][]} Array of [lng, lat] coordinate pairs
 */
export function interpolateGreatCircle(fromLng, fromLat, toLng, toLat, numPoints = 50) {
    const coords = [];

    const lat1 = fromLat * DEG_TO_RAD;
    const lng1 = fromLng * DEG_TO_RAD;
    const lat2 = toLat * DEG_TO_RAD;
    const lng2 = toLng * DEG_TO_RAD;

    // Calculate the angular distance between points
    const d = 2 * Math.asin(Math.sqrt(
        Math.pow(Math.sin((lat1 - lat2) / 2), 2) +
        Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lng1 - lng2) / 2), 2)
    ));

    // If points are very close, just return a straight line
    if (d < 0.00001) {
        return [[fromLng, fromLat], [toLng, toLat]];
    }

    for (let i = 0; i <= numPoints; i++) {
        const f = i / numPoints;

        // Spherical interpolation formula
        const A = Math.sin((1 - f) * d) / Math.sin(d);
        const B = Math.sin(f * d) / Math.sin(d);

        const x = A * Math.cos(lat1) * Math.cos(lng1) + B * Math.cos(lat2) * Math.cos(lng2);
        const y = A * Math.cos(lat1) * Math.sin(lng1) + B * Math.cos(lat2) * Math.sin(lng2);
        const z = A * Math.sin(lat1) + B * Math.sin(lat2);

        const lat = Math.atan2(z, Math.sqrt(x * x + y * y)) * RAD_TO_DEG;
        const lng = Math.atan2(y, x) * RAD_TO_DEG;

        coords.push([lng, lat]);
    }

    return coords;
}

/**
 * Create a GeoJSON LineString from two points using great circle interpolation.
 * @param {number} fromLng - Starting longitude
 * @param {number} fromLat - Starting latitude
 * @param {number} toLng - Ending longitude
 * @param {number} toLat - Ending latitude
 * @param {number} numPoints - Number of interpolation points
 * @returns {Object} GeoJSON LineString geometry
 */
export function createGreatCircleLine(fromLng, fromLat, toLng, toLat, numPoints = 50) {
    return {
        type: 'LineString',
        coordinates: interpolateGreatCircle(fromLng, fromLat, toLng, toLat, numPoints)
    };
}

/**
 * Handle antimeridian crossing by splitting the line if needed.
 * This prevents lines from wrapping the wrong way around the globe.
 * @param {number[][]} coords - Array of [lng, lat] coordinates
 * @returns {number[][][]} Array of coordinate arrays (may be split at antimeridian)
 */
export function handleAntimeridian(coords) {
    const segments = [];
    let currentSegment = [coords[0]];

    for (let i = 1; i < coords.length; i++) {
        const prev = coords[i - 1];
        const curr = coords[i];
        const lngDiff = curr[0] - prev[0];

        // Check for antimeridian crossing (large longitude jump)
        if (Math.abs(lngDiff) > 180) {
            // Calculate crossing point
            const crossLng = lngDiff > 0 ? -180 : 180;
            const ratio = (crossLng - prev[0]) / (curr[0] - prev[0] + (lngDiff > 0 ? 360 : -360));
            const crossLat = prev[1] + ratio * (curr[1] - prev[1]);

            // End current segment at antimeridian
            currentSegment.push([crossLng, crossLat]);
            segments.push(currentSegment);

            // Start new segment on other side
            currentSegment = [[-crossLng, crossLat], curr];
        } else {
            currentSegment.push(curr);
        }
    }

    segments.push(currentSegment);
    return segments;
}

/**
 * Create a GeoJSON geometry that properly handles antimeridian crossing.
 * Returns a MultiLineString if the route crosses the antimeridian.
 * @param {number} fromLng - Starting longitude
 * @param {number} fromLat - Starting latitude
 * @param {number} toLng - Ending longitude
 * @param {number} toLat - Ending latitude
 * @param {number} numPoints - Number of interpolation points
 * @returns {Object} GeoJSON LineString or MultiLineString geometry
 */
export function createGreatCircleGeometry(fromLng, fromLat, toLng, toLat, numPoints = 50) {
    const coords = interpolateGreatCircle(fromLng, fromLat, toLng, toLat, numPoints);
    const segments = handleAntimeridian(coords);

    if (segments.length === 1) {
        return {
            type: 'LineString',
            coordinates: segments[0]
        };
    }

    return {
        type: 'MultiLineString',
        coordinates: segments
    };
}
