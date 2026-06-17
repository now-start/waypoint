const baseUrl = normalizeBaseUrl(window.baseUrl || "/");
window.baseUrl = baseUrl;

const statusEndpoint = "api/collections/status";
const runsEndpoint = "api/collections/runs?limit=20";
const anomaliesEndpoint = "api/anomalies";
const mapEndpoint = "api/operations/map";
const briefingOptionsEndpoint = "api/briefings/options";
const refreshIntervalSeconds = 30;
const detailSliceSize = 50;
const detailScrollThresholdPx = 80;
const mapWidth = 1200;
const mapHeight = 490;
const routePalette = ["#008c72", "#276ef1", "#e34f26", "#c51b7d", "#7b61ff", "#0097a7", "#d62828", "#2f855a"];
const routeOrderCollator = new Intl.Collator("ko-KR", {numeric: true, sensitivity: "base"});
const graphPadding = {top: 44, right: 56, bottom: 52, left: 56};
const maxOverviewRoutePathPoints = 34;
const maxSelectedRoutePathPoints = 78;
const maxOverviewSimplifyInputPoints = 120;
const maxSelectedSimplifyInputPoints = 220;
const minMapLatitudeSpan = 0.05;
const minMapLongitudeSpan = 0.06;
const maxMapZoom = 5;
const mapZoomStep = 1.35;
const minMapMarkerScale = 0.26;
const mapMarkerScalePower = 1.1;
const unmatchedVehicleLaneY = 452;

const elements = {
    updatedAt: document.querySelector("#updatedAt"),
    autoRefreshToggle: document.querySelector("#autoRefreshToggle"),
    refreshCountdown: document.querySelector("#refreshCountdown"),
    routeCount: document.querySelector("#routeCount"),
    stopCount: document.querySelector("#stopCount"),
    routeStopCount: document.querySelector("#routeStopCount"),
    locationSnapshotCount: document.querySelector("#locationSnapshotCount"),
    arrivalSnapshotCount: document.querySelector("#arrivalSnapshotCount"),
    latestLocationCollectedAt: document.querySelector("#latestLocationCollectedAt"),
    latestArrivalCollectedAt: document.querySelector("#latestArrivalCollectedAt"),
    mapUpdatedAt: document.querySelector("#mapUpdatedAt"),
    operationMap: document.querySelector("#operationMap"),
    routeLayer: document.querySelector("#routeLayer"),
    stopLayer: document.querySelector("#stopLayer"),
    vehicleLayer: document.querySelector("#vehicleLayer"),
    mapEmptyState: document.querySelector("#mapEmptyState"),
    mapZoomControls: document.querySelector(".map-zoom-controls"),
    mapZoomIndicator: document.querySelector("#mapZoomIndicator"),
    mapRouteCount: document.querySelector("#mapRouteCount"),
    mapRouteScope: document.querySelector("#mapRouteScope"),
    mapVehicleCount: document.querySelector("#mapVehicleCount"),
    mapVehicleScope: document.querySelector("#mapVehicleScope"),
    mapDelayedVehicleCount: document.querySelector("#mapDelayedVehicleCount"),
    routeFilterList: document.querySelector("#routeFilterList"),
    vehicleFeed: document.querySelector("#vehicleFeed"),
    anomalyList: document.querySelector("#anomalyList"),
    anomalyCount: document.querySelector("#anomalyCount"),
    runList: document.querySelector("#runList"),
    runCount: document.querySelector("#runCount"),
    briefingText: document.querySelector("#briefingText"),
    briefingProvider: document.querySelector("#briefingProvider"),
    briefingModel: document.querySelector("#briefingModel"),
    detailModal: document.querySelector("#detailModal"),
    detailModalBody: document.querySelector("#detailModal .modal-body"),
    detailModalTitle: document.querySelector("#detailModalTitle"),
    detailModalSummary: document.querySelector("#detailModalSummary"),
    detailTableHead: document.querySelector("#detailTableHead"),
    detailTableBody: document.querySelector("#detailTableBody"),
    toast: document.querySelector("#messageToast"),
    toastBody: document.querySelector("#toastBody")
};

let cachedRuns = [];
let cachedAnomalies = [];
let cachedMap = null;
let cachedStatus = null;
let refreshTimerId;
let secondsUntilRefresh = refreshIntervalSeconds;
let detailState = null;
let selectedRouteIds = new Set();
let mapViewBox = {x: 0, y: 0, width: mapWidth, height: mapHeight};
let mapPanState = null;
let suppressMapClick = false;
let briefingOptions = {
    defaultProvider: "",
    providers: [
        {provider: "ollama", label: "Ollama", defaultModel: ""},
        {provider: "openai", label: "OpenAI", defaultModel: ""}
    ]
};

function normalizeBaseUrl(value) {
    if (!value) {
        return "/";
    }

    const prefixed = value.startsWith("/") ? value : `/${value}`;
    return prefixed.endsWith("/") ? prefixed : `${prefixed}/`;
}

function buildUrl(path) {
    if (/^https?:\/\//i.test(path)) {
        return path;
    }

    const normalizedPath = path.startsWith("/") ? path.slice(1) : path;
    return `${baseUrl}${normalizedPath}`;
}

function formatNumber(value) {
    return Number(value ?? 0).toLocaleString("ko-KR");
}

function formatDateTime(value) {
    if (!value) {
        return "-";
    }

    return new Intl.DateTimeFormat("ko-KR", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(value));
}

function statusClass(status) {
    const statusSuffix = String(status || "empty").toLowerCase().replace(/[^a-z0-9_-]/g, "-");
    return `status-pill status-${statusSuffix}`;
}

function severityClass(severity) {
    return `severity-pill severity-${severity === "위험" ? "danger" : "warning"}`;
}

function showToast(message) {
    elements.toastBody.textContent = message;
    if (window.bootstrap?.Toast) {
        bootstrap.Toast.getOrCreateInstance(elements.toast).show();
        return;
    }

    elements.toast.classList.add("show");
    window.setTimeout(() => elements.toast.classList.remove("show"), 3200);
}

function showModal() {
    if (window.bootstrap?.Modal) {
        bootstrap.Modal.getOrCreateInstance(elements.detailModal).show();
        return;
    }

    elements.detailModal.classList.add("show");
    elements.detailModal.style.display = "block";
    elements.detailModal.removeAttribute("aria-hidden");
}

function hideModal() {
    if (window.bootstrap?.Modal) {
        bootstrap.Modal.getOrCreateInstance(elements.detailModal).hide();
        return;
    }

    elements.detailModal.classList.remove("show");
    elements.detailModal.style.display = "none";
    elements.detailModal.setAttribute("aria-hidden", "true");
}


async function fetchJson(url, options = {}) {
    const response = await fetch(buildUrl(url), options);
    if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}`);
    }
    return response.json();
}

function formatDetailValue(value) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }

    if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value)) {
        return formatDateTime(value);
    }

    return value;
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;"
    })[character]);
}

function routeColor(index) {
    return routePalette[index % routePalette.length];
}

function freshnessLabel(value) {
    if (value === "normal") {
        return "정상";
    }
    if (value === "delayed") {
        return "지연";
    }
    return "오래됨";
}

function formatAge(value) {
    if (!value) {
        return "-";
    }

    const elapsedSeconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
    if (elapsedSeconds < 60) {
        return `${elapsedSeconds}초 전`;
    }
    const elapsedMinutes = Math.floor(elapsedSeconds / 60);
    if (elapsedMinutes < 60) {
        return `${elapsedMinutes}분 전`;
    }
    return `${Math.floor(elapsedMinutes / 60)}시간 전`;
}

function numericOrder(value) {
    if (value === null || value === undefined || value === "") {
        return Number.MAX_SAFE_INTEGER;
    }

    const number = Number(value);
    return Number.isFinite(number) ? number : Number.MAX_SAFE_INTEGER;
}

function routeFilterLabel(route) {
    return String(route?.routeNo || route?.sourceRouteId || "");
}

function compareRoutes(left, right) {
    const labelDiff = routeOrderCollator.compare(routeFilterLabel(left), routeFilterLabel(right));
    if (labelDiff !== 0) {
        return labelDiff;
    }

    return String(left?.sourceRouteId || "").localeCompare(String(right?.sourceRouteId || ""));
}

function allRoutesSelected() {
    return selectedRouteIds.size === 0;
}

function routeSelected(routeId) {
    return allRoutesSelected() || selectedRouteIds.has(routeId);
}

function selectedRouteLabel() {
    return allRoutesSelected() ? "전체" : `${formatNumber(selectedRouteIds.size)}개 선택`;
}

function normalizedRouteStops(route) {
    return [...(Array.isArray(route.stops) ? route.stops : [])].sort((left, right) => {
        const orderDiff = numericOrder(left.nodeOrder) - numericOrder(right.nodeOrder);
        if (orderDiff !== 0) {
            return orderDiff;
        }

        return String(left.sourceNodeId || "").localeCompare(String(right.sourceNodeId || ""));
    });
}

function finiteCoordinateNumber(value) {
    if (value === null || value === undefined || value === "") {
        return null;
    }

    const number = Number(value);
    return Number.isFinite(number) ? number : null;
}

function validCoordinate(latitude, longitude) {
    const parsedLatitude = finiteCoordinateNumber(latitude);
    const parsedLongitude = finiteCoordinateNumber(longitude);
    return parsedLatitude !== null
        && parsedLongitude !== null
        && parsedLatitude >= -90
        && parsedLatitude <= 90
        && parsedLongitude >= -180
        && parsedLongitude <= 180;
}

function normalizedMapBounds(bounds) {
    if (!bounds) {
        return null;
    }

    const minLatitude = finiteCoordinateNumber(bounds.minLatitude);
    const maxLatitude = finiteCoordinateNumber(bounds.maxLatitude);
    const minLongitude = finiteCoordinateNumber(bounds.minLongitude);
    const maxLongitude = finiteCoordinateNumber(bounds.maxLongitude);
    if (
        minLatitude === null
        || maxLatitude === null
        || minLongitude === null
        || maxLongitude === null
        || minLatitude >= maxLatitude
        || minLongitude >= maxLongitude
        || !validCoordinate(minLatitude, minLongitude)
        || !validCoordinate(maxLatitude, maxLongitude)
    ) {
        return null;
    }

    return {minLatitude, maxLatitude, minLongitude, maxLongitude};
}

function routeOffset(routeIndex) {
    const distance = ((routeIndex % 5) - 2) * 2.2;
    const angle = ((routeIndex * 47) % 360) * (Math.PI / 180);
    return {
        x: Math.cos(angle) * distance,
        y: Math.sin(angle) * distance
    };
}

function coordinateWithinBounds(coordinate, bounds) {
    if (!bounds) {
        return true;
    }

    return coordinate.latitude >= bounds.minLatitude
        && coordinate.latitude <= bounds.maxLatitude
        && coordinate.longitude >= bounds.minLongitude
        && coordinate.longitude <= bounds.maxLongitude;
}

function boundsWithMinimumSpan(bounds) {
    const latitudeSpan = bounds.maxLatitude - bounds.minLatitude;
    const longitudeSpan = bounds.maxLongitude - bounds.minLongitude;
    const latitudePadding = Math.max(latitudeSpan * 0.08, 0.006);
    const longitudePadding = Math.max(longitudeSpan * 0.08, 0.006);
    const centerLatitude = (bounds.minLatitude + bounds.maxLatitude) / 2;
    const centerLongitude = (bounds.minLongitude + bounds.maxLongitude) / 2;
    const nextLatitudeSpan = Math.max(latitudeSpan + (latitudePadding * 2), minMapLatitudeSpan);
    const nextLongitudeSpan = Math.max(longitudeSpan + (longitudePadding * 2), minMapLongitudeSpan);

    return {
        minLatitude: centerLatitude - (nextLatitudeSpan / 2),
        maxLatitude: centerLatitude + (nextLatitudeSpan / 2),
        minLongitude: centerLongitude - (nextLongitudeSpan / 2),
        maxLongitude: centerLongitude + (nextLongitudeSpan / 2)
    };
}

function clampBoundsToFallback(bounds, fallback) {
    if (!fallback) {
        return bounds;
    }

    const clamped = {
        minLatitude: Math.max(bounds.minLatitude, fallback.minLatitude),
        maxLatitude: Math.min(bounds.maxLatitude, fallback.maxLatitude),
        minLongitude: Math.max(bounds.minLongitude, fallback.minLongitude),
        maxLongitude: Math.min(bounds.maxLongitude, fallback.maxLongitude)
    };

    return clamped.minLatitude < clamped.maxLatitude && clamped.minLongitude < clamped.maxLongitude
        ? clamped
        : fallback;
}

function dataBounds(routes, vehicles, fallbackBounds) {
    const fallback = normalizedMapBounds(fallbackBounds);
    let coordinates = routes.flatMap((route) => normalizedRouteStops(route)
        .filter((stop) => validCoordinate(stop.latitude, stop.longitude))
        .map((stop) => ({
            latitude: Number(stop.latitude),
            longitude: Number(stop.longitude)
        })));
    vehicles
        .filter((vehicle) => validCoordinate(vehicle.latitude, vehicle.longitude))
        .forEach((vehicle) => coordinates.push({
            latitude: Number(vehicle.latitude),
            longitude: Number(vehicle.longitude)
        }));
    coordinates = coordinates.filter((coordinate) => coordinateWithinBounds(coordinate, fallback));

    if (coordinates.length === 0) {
        if (fallback) {
            return fallback;
        }
        coordinates.push(
            {latitude: 34.88, longitude: 128.32},
            {latitude: 35.45, longitude: 129.02}
        );
    }

    const minLatitude = Math.min(...coordinates.map((coordinate) => coordinate.latitude));
    const maxLatitude = Math.max(...coordinates.map((coordinate) => coordinate.latitude));
    const minLongitude = Math.min(...coordinates.map((coordinate) => coordinate.longitude));
    const maxLongitude = Math.max(...coordinates.map((coordinate) => coordinate.longitude));
    return clampBoundsToFallback(boundsWithMinimumSpan({
        minLatitude,
        maxLatitude,
        minLongitude,
        maxLongitude
    }), fallback);
}

function projectCoordinate(latitude, longitude, bounds, offset = {x: 0, y: 0}) {
    const usableWidth = mapWidth - graphPadding.left - graphPadding.right;
    const usableHeight = mapHeight - graphPadding.top - graphPadding.bottom;
    const longitudeRange = Math.max(bounds.maxLongitude - bounds.minLongitude, 0.0001);
    const latitudeRange = Math.max(bounds.maxLatitude - bounds.minLatitude, 0.0001);
    const x = graphPadding.left + ((Number(longitude) - bounds.minLongitude) / longitudeRange * usableWidth);
    const y = graphPadding.top + ((bounds.maxLatitude - Number(latitude)) / latitudeRange * usableHeight);

    return {
        x: Math.min(mapWidth - graphPadding.right, Math.max(graphPadding.left, x + offset.x)),
        y: Math.min(mapHeight - graphPadding.bottom, Math.max(graphPadding.top, y + offset.y))
    };
}

function buildRouteGraphLayouts(routes, bounds, fallbackBounds) {
    return [...routes].sort(compareRoutes).map((route, routeIndex) => {
        const offset = routeOffset(routeIndex);
        const stopPoints = normalizedRouteStops(route)
            .filter((stop) => validCoordinate(stop.latitude, stop.longitude))
            .filter((stop) => coordinateWithinBounds({
                latitude: Number(stop.latitude),
                longitude: Number(stop.longitude)
            }, fallbackBounds))
            .map((stop, stopIndex) => {
                const point = projectCoordinate(stop.latitude, stop.longitude, bounds, offset);
                return {
                    route,
                    routeIndex,
                    stop,
                    stopIndex,
                    order: numericOrder(stop.nodeOrder),
                    x: point.x,
                    y: point.y
                };
            });

        return {
            route,
            routeIndex,
            color: routeColor(routeIndex),
            stopPoints
        };
    });
}

function routeLabelWidth(label) {
    return Math.max(36, Math.min(78, String(label || "").length * 10 + 18));
}

function routeBadge(point, route, color, side = "start") {
    const label = route.routeNo || route.sourceRouteId || "-";
    const width = routeLabelWidth(label);
    const preferredLeft = side === "start";
    const shouldPlaceRight = preferredLeft && point.x < width + 26;
    const shouldPlaceLeft = !preferredLeft && point.x > mapWidth - width - 26;
    const x = shouldPlaceRight ? 14 : shouldPlaceLeft ? -width - 14 : preferredLeft ? -width - 14 : 14;

    return `
        <g class="route-label" transform="translate(${point.x.toFixed(1)} ${point.y.toFixed(1)})">
            <g class="map-scaled-label">
                <rect x="${x}" y="-11" width="${width}" height="22" rx="11" fill="${color}"></rect>
                <text x="${x + (width / 2)}" y="4" text-anchor="middle">${escapeHtml(label)}</text>
            </g>
        </g>
    `;
}

function pointDistance(left, right) {
    return Math.hypot(left.x - right.x, left.y - right.y);
}

function perpendicularDistance(point, lineStart, lineEnd) {
    const lineLength = pointDistance(lineStart, lineEnd);
    if (lineLength === 0) {
        return pointDistance(point, lineStart);
    }

    const numerator = Math.abs(
        ((lineEnd.y - lineStart.y) * point.x)
        - ((lineEnd.x - lineStart.x) * point.y)
        + (lineEnd.x * lineStart.y)
        - (lineEnd.y * lineStart.x)
    );
    return numerator / lineLength;
}

function simplifyByDistance(points, tolerance) {
    if (points.length <= 2) {
        return points;
    }

    let maxDistance = 0;
    let pivotIndex = 0;
    const first = points[0];
    const last = points[points.length - 1];

    for (let index = 1; index < points.length - 1; index += 1) {
        const distance = perpendicularDistance(points[index], first, last);
        if (distance > maxDistance) {
            maxDistance = distance;
            pivotIndex = index;
        }
    }

    if (maxDistance <= tolerance) {
        return [first, last];
    }

    const left = simplifyByDistance(points.slice(0, pivotIndex + 1), tolerance);
    const right = simplifyByDistance(points.slice(pivotIndex), tolerance);
    return [...left.slice(0, -1), ...right];
}

function limitPathPoints(points, maxPoints) {
    if (points.length <= maxPoints) {
        return points;
    }

    const interval = Math.max(1, Math.ceil(points.length / maxPoints));
    return points.filter((point, index) =>
        index === 0 || index === points.length - 1 || index % interval === 0
    );
}

function schematicPathPoints(stopPoints, selected) {
    const tolerance = selected ? 7 : 15;
    const maxPoints = selected ? maxSelectedRoutePathPoints : maxOverviewRoutePathPoints;
    const maxInputPoints = selected ? maxSelectedSimplifyInputPoints : maxOverviewSimplifyInputPoints;
    const boundedPoints = limitPathPoints(stopPoints, maxInputPoints);
    const simplified = simplifyByDistance(boundedPoints, tolerance);
    return limitPathPoints(simplified, maxPoints);
}

function buildStopUseCounts(routeLayouts) {
    return routeLayouts.reduce((counts, layout) => {
        const routeStopIds = new Set(
            layout.stopPoints
                .map((point) => point.stop.sourceNodeId)
                .filter(Boolean)
        );
        routeStopIds.forEach((sourceNodeId) => counts.set(sourceNodeId, (counts.get(sourceNodeId) || 0) + 1));
        return counts;
    }, new Map());
}

function hashValue(value) {
    return String(value || "").split("").reduce((hash, character) => {
        return ((hash << 5) - hash) + character.charCodeAt(0);
    }, 0);
}

function vehicleGraphPoint(vehicle, routeLayouts, bounds, fallbackBounds, vehicleIndex) {
    const layout = routeLayouts.find((item) => item.route.sourceRouteId === vehicle.sourceRouteId);
    if (validCoordinate(vehicle.latitude, vehicle.longitude) && coordinateWithinBounds({
        latitude: Number(vehicle.latitude),
        longitude: Number(vehicle.longitude)
    }, fallbackBounds)) {
        const offset = layout ? routeOffset(layout.routeIndex) : {x: 0, y: 0};
        return projectCoordinate(vehicle.latitude, vehicle.longitude, bounds, offset);
    }

    if (!layout || layout.stopPoints.length === 0) {
        const visibleIndex = vehicleIndex % 18;
        return {
            x: graphPadding.left + (visibleIndex * 58),
            y: unmatchedVehicleLaneY - ((Math.floor(vehicleIndex / 18) % 2) * 24),
            unmatched: true
        };
    }

    const nodeOrder = numericOrder(vehicle.nodeOrder);
    let anchor = null;
    if (nodeOrder !== Number.MAX_SAFE_INTEGER) {
        anchor = layout.stopPoints.reduce((closest, candidate) => {
            if (!closest) {
                return candidate;
            }
            return Math.abs(candidate.order - nodeOrder) < Math.abs(closest.order - nodeOrder)
                ? candidate
                : closest;
        }, null);
    }

    if (!anchor && vehicle.sourceNodeId) {
        anchor = layout.stopPoints.find((point) => point.stop.sourceNodeId === vehicle.sourceNodeId);
    }

    const fallback = layout.stopPoints[Math.floor(layout.stopPoints.length / 2)];
    const stackOffset = ((Math.abs(hashValue(vehicle.vehicleNo || vehicleIndex)) % 5) - 2) * 5;
    const yOffset = allRoutesSelected() ? -11 : -18;
    const point = anchor || fallback;
    return {
        x: point.x,
        y: point.y + yOffset + stackOffset
    };
}

function routeFilteredVehicles(vehicles) {
    return vehicles.filter((vehicle) => routeSelected(vehicle.sourceRouteId));
}

function visibleMapVehicles(vehicles) {
    return routeFilteredVehicles(vehicles);
}

function clampValue(value, min, max) {
    return Math.min(max, Math.max(min, value));
}

function currentMapZoom() {
    return mapWidth / mapViewBox.width;
}

function currentMapMarkerScale() {
    return clampValue(1 / Math.pow(currentMapZoom(), mapMarkerScalePower), minMapMarkerScale, 1);
}

function clampMapViewBox(viewBox) {
    const width = clampValue(viewBox.width, mapWidth / maxMapZoom, mapWidth);
    const height = clampValue(viewBox.height, mapHeight / maxMapZoom, mapHeight);
    return {
        x: clampValue(viewBox.x, 0, mapWidth - width),
        y: clampValue(viewBox.y, 0, mapHeight - height),
        width,
        height
    };
}

function setMapViewBox(viewBox) {
    mapViewBox = clampMapViewBox(viewBox);
    elements.operationMap.setAttribute(
        "viewBox",
        `${mapViewBox.x.toFixed(1)} ${mapViewBox.y.toFixed(1)} ${mapViewBox.width.toFixed(1)} ${mapViewBox.height.toFixed(1)}`
    );
    elements.operationMap.style.setProperty("--map-marker-scale", currentMapMarkerScale().toFixed(3));
    elements.mapZoomIndicator.textContent = `${Math.round(currentMapZoom() * 100)}%`;
}

function resetMapZoom() {
    setMapViewBox({x: 0, y: 0, width: mapWidth, height: mapHeight});
}

function mapPointFromEvent(event) {
    const rect = elements.operationMap.getBoundingClientRect();
    const ratioX = clampValue((event.clientX - rect.left) / rect.width, 0, 1);
    const ratioY = clampValue((event.clientY - rect.top) / rect.height, 0, 1);
    return {
        x: mapViewBox.x + (ratioX * mapViewBox.width),
        y: mapViewBox.y + (ratioY * mapViewBox.height)
    };
}

function zoomMap(factor, center = null) {
    const nextZoom = clampValue(currentMapZoom() * factor, 1, maxMapZoom);
    const nextWidth = mapWidth / nextZoom;
    const nextHeight = mapHeight / nextZoom;
    const zoomCenter = center || {
        x: mapViewBox.x + (mapViewBox.width / 2),
        y: mapViewBox.y + (mapViewBox.height / 2)
    };
    const centerRatioX = (zoomCenter.x - mapViewBox.x) / mapViewBox.width;
    const centerRatioY = (zoomCenter.y - mapViewBox.y) / mapViewBox.height;

    setMapViewBox({
        x: zoomCenter.x - (nextWidth * centerRatioX),
        y: zoomCenter.y - (nextHeight * centerRatioY),
        width: nextWidth,
        height: nextHeight
    });
}

function handleMapWheel(event) {
    event.preventDefault();
    zoomMap(event.deltaY < 0 ? mapZoomStep : 1 / mapZoomStep, mapPointFromEvent(event));
}

function startMapPan(event) {
    if (event.button !== 0) {
        return;
    }

    mapPanState = {
        pointerId: event.pointerId,
        startClientX: event.clientX,
        startClientY: event.clientY,
        startViewBox: {...mapViewBox},
        moved: false
    };
    elements.operationMap.setPointerCapture(event.pointerId);
    elements.operationMap.classList.add("is-panning");
}

function moveMapPan(event) {
    if (!mapPanState || event.pointerId !== mapPanState.pointerId) {
        return;
    }

    const rect = elements.operationMap.getBoundingClientRect();
    const deltaX = (event.clientX - mapPanState.startClientX) / rect.width * mapPanState.startViewBox.width;
    const deltaY = (event.clientY - mapPanState.startClientY) / rect.height * mapPanState.startViewBox.height;
    if (Math.abs(event.clientX - mapPanState.startClientX) > 4 || Math.abs(event.clientY - mapPanState.startClientY) > 4) {
        mapPanState.moved = true;
        suppressMapClick = true;
    }

    setMapViewBox({
        ...mapPanState.startViewBox,
        x: mapPanState.startViewBox.x - deltaX,
        y: mapPanState.startViewBox.y - deltaY
    });
    event.preventDefault();
}

function endMapPan(event) {
    if (!mapPanState || event.pointerId !== mapPanState.pointerId) {
        return;
    }

    elements.operationMap.releasePointerCapture(event.pointerId);
    elements.operationMap.classList.remove("is-panning");
    mapPanState = null;
}

function renderMetrics(status) {
    cachedStatus = status;
    elements.routeCount.textContent = formatNumber(status.routeCount);
    elements.stopCount.textContent = formatNumber(status.stopCount);
    elements.routeStopCount.textContent = formatNumber(status.routeStopCount);
    elements.locationSnapshotCount.textContent = formatNumber(status.locationSnapshotCount);
    elements.arrivalSnapshotCount.textContent = formatNumber(status.arrivalSnapshotCount);
    elements.latestLocationCollectedAt.textContent = `최근 ${formatDateTime(status.latestLocationCollectedAt)}`;
    elements.latestArrivalCollectedAt.textContent = `최근 ${formatDateTime(status.latestArrivalCollectedAt)}`;
}

function renderTransitMap(payload) {
    cachedMap = payload;
    const routes = Array.isArray(payload.routes) ? payload.routes : [];
    const vehicles = Array.isArray(payload.vehicles) ? payload.vehicles : [];

    elements.mapUpdatedAt.textContent = `지도 기준 ${formatDateTime(payload.generatedAt)}`;
    const fallbackBounds = normalizedMapBounds(payload.bounds);
    const bounds = dataBounds(routes, vehicles, fallbackBounds);
    const routeLayouts = buildRouteGraphLayouts(routes, bounds, fallbackBounds);
    const drawableRouteLayouts = routeLayouts.filter((layout) => layout.stopPoints.length >= 2);
    const drawableRouteIds = new Set(drawableRouteLayouts.map((layout) => layout.route.sourceRouteId));
    selectedRouteIds = new Set([...selectedRouteIds].filter((routeId) => drawableRouteIds.has(routeId)));
    const drawableRouteCount = drawableRouteLayouts.length;
    const visibleRouteCount = allRoutesSelected()
        ? drawableRouteCount
        : drawableRouteLayouts.filter((layout) => selectedRouteIds.has(layout.route.sourceRouteId)).length;
    const totalRouteCount = Number(cachedStatus?.routeCount);
    elements.mapRouteCount.textContent = formatNumber(visibleRouteCount);
    elements.mapRouteScope.textContent = Number.isFinite(totalRouteCount) && totalRouteCount > 0
        ? allRoutesSelected()
            ? totalRouteCount === drawableRouteCount
                ? `전체 ${formatNumber(totalRouteCount)}개 표시`
                : `전체 ${formatNumber(totalRouteCount)}개 중 화면 ${formatNumber(drawableRouteCount)}개`
            : `전체 ${formatNumber(totalRouteCount)}개 중 ${selectedRouteLabel()}`
        : "실제 그려진 노선";
    const visibleVehicles = visibleMapVehicles(vehicles);
    const routeVehicleTotal = routeFilteredVehicles(vehicles).length;
    const visibleDelayedVehicleCount = visibleVehicles.filter((vehicle) => vehicle.freshness !== "normal").length;
    elements.mapVehicleCount.textContent = formatNumber(visibleVehicles.length);
    elements.mapVehicleScope.textContent = routeVehicleTotal > visibleVehicles.length
        ? `전체 ${formatNumber(routeVehicleTotal)}대 중 화면 ${formatNumber(visibleVehicles.length)}대`
        : routeVehicleTotal > 0 ? `전체 ${formatNumber(routeVehicleTotal)}대 표시` : "화면 표시 차량";
    elements.mapDelayedVehicleCount.textContent = formatNumber(visibleDelayedVehicleCount);
    elements.mapEmptyState.textContent = "지도에 표시할 노선 또는 차량 위치 데이터가 없습니다.";
    elements.mapEmptyState.classList.toggle("show", drawableRouteCount === 0 && vehicles.length === 0);

    renderRouteFilters(drawableRouteLayouts, vehicles);
    renderRouteLayer(routeLayouts);
    renderStopLayer(routeLayouts);
    renderVehicleLayer(visibleVehicles, routeLayouts, bounds, fallbackBounds);
    renderVehicleFeed(vehicles);
}

function renderRouteFilters(routeLayouts, vehicles) {
    const routeVehicleCounts = vehicles.reduce((counts, vehicle) => {
        counts.set(vehicle.sourceRouteId, (counts.get(vehicle.sourceRouteId) || 0) + 1);
        return counts;
    }, new Map());
    const routeButtons = routeLayouts.map((layout) => {
        const {route, color} = layout;
        const vehicleCount = routeVehicleCounts.get(route.sourceRouteId) || 0;
        const active = selectedRouteIds.has(route.sourceRouteId);
        const activeClass = active ? " active" : "";
        return `
            <button class="route-filter${activeClass}" data-route-id="${escapeHtml(route.sourceRouteId)}"
                    aria-pressed="${active}"
                    style="--route-color: ${color}" type="button">
                ${escapeHtml(route.routeNo || route.sourceRouteId)}
                <span class="text-secondary">${escapeHtml(vehicleCount)}대</span>
            </button>
        `;
    }).join("");

    const allActive = allRoutesSelected();
    const allActiveClass = allActive ? " active" : "";
    elements.routeFilterList.innerHTML = `
        <button class="route-filter${allActiveClass}" data-route-id="all" aria-pressed="${allActive}" style="--route-color: #344054" type="button">지도 전체</button>
        ${routeButtons || `<span class="text-secondary small">표시할 노선이 없습니다.</span>`}
    `;
}

function renderRouteLayer(routeLayouts) {
    const allSelected = allRoutesSelected();
    const visibleLayouts = allSelected
        ? routeLayouts
        : routeLayouts.filter((layout) => selectedRouteIds.has(layout.route.sourceRouteId));
    const overviewBadgeInterval = allSelected
        ? Math.max(1, Math.ceil(visibleLayouts.length / 48))
        : 1;

    elements.routeLayer.innerHTML = visibleLayouts.map((layout, layoutIndex) => {
        const {route, color, stopPoints} = layout;
        if (stopPoints.length < 2) {
            return "";
        }

        const selected = !allSelected && selectedRouteIds.has(route.sourceRouteId);
        const muted = false;
        const classes = [
            "route-path",
            selected ? "is-selected" : "",
            muted ? "is-muted" : ""
        ].filter(Boolean).join(" ");
        const pointText = schematicPathPoints(stopPoints, selected)
            .map((point) => `${point.x.toFixed(1)},${point.y.toFixed(1)}`)
            .join(" ");
        const firstPoint = stopPoints[0];
        const lastPoint = stopPoints[stopPoints.length - 1];
        const showOverviewBadge = allSelected && layoutIndex % overviewBadgeInterval === 0;
        const showStartBadge = selected || showOverviewBadge;
        return `
            <g class="route-track${muted ? " is-muted" : ""}${selected ? " is-selected" : ""}">
                <polyline class="${classes}" points="${pointText}" stroke="${color}">
                    <title>${escapeHtml(route.routeNo || route.sourceRouteId)} ${escapeHtml(route.startNodeName || "")} - ${escapeHtml(route.endNodeName || "")}</title>
                </polyline>
                ${showStartBadge ? routeBadge(firstPoint, route, color) : ""}
                ${selected ? routeBadge(lastPoint, route, color, "end") : ""}
            </g>
        `;
    }).join("");
}

function renderStopLayer(routeLayouts) {
    const stopUseCounts = buildStopUseCounts(routeLayouts);
    const allSelected = allRoutesSelected();
    const visibleLayouts = allSelected
        ? routeLayouts
        : routeLayouts.filter((layout) => selectedRouteIds.has(layout.route.sourceRouteId));

    elements.stopLayer.innerHTML = visibleLayouts.flatMap((layout) => {
        const selected = !allSelected && selectedRouteIds.has(layout.route.sourceRouteId);
        const muted = false;
        const interval = selected
            ? Math.max(1, Math.ceil(layout.stopPoints.length / 72))
            : Math.max(1, Math.ceil(layout.stopPoints.length / 12));

        return layout.stopPoints.map((point, index) => {
            const sourceNodeId = point.stop.sourceNodeId;
            const interchange = sourceNodeId && stopUseCounts.get(sourceNodeId) > 1;
            const terminal = index === 0 || index === layout.stopPoints.length - 1;
            const showDot = selected || terminal || interchange || index % interval === 0;
            if (!showDot) {
                return "";
            }

            const labelInterval = Math.max(1, Math.ceil(layout.stopPoints.length / 10));
            const showLabel = selected && (terminal || interchange || index % labelInterval === 0);
            const classes = [
                "stop-dot",
                interchange ? "is-interchange" : "",
                muted ? "is-muted" : ""
            ].filter(Boolean).join(" ");
            return `
                <g class="stop-node" transform="translate(${point.x.toFixed(1)} ${point.y.toFixed(1)})">
                    <g class="map-scaled-marker">
                        <circle class="${classes}" r="${interchange ? "5.4" : "3.9"}">
                            <title>${escapeHtml(point.stop.nodeName || sourceNodeId)} · 순번 ${escapeHtml(point.stop.nodeOrder ?? "-")}</title>
                        </circle>
                    </g>
                    ${showLabel ? `<text class="stop-label map-scaled-label" x="0" y="20" text-anchor="middle">${escapeHtml(point.stop.nodeName || sourceNodeId || "-")}</text>` : ""}
                </g>
            `;
        });
    }).join("");
}

function renderVehicleLayer(visibleVehicles, routeLayouts, bounds, fallbackBounds) {
    const allSelected = allRoutesSelected();
    elements.vehicleLayer.innerHTML = visibleVehicles.map((vehicle, vehicleIndex) => {
        const point = vehicleGraphPoint(vehicle, routeLayouts, bounds, fallbackBounds, vehicleIndex);
        if (!point) {
            return "";
        }
        const routeNo = vehicle.routeNo || "-";
        const freshness = ["normal", "delayed", "stale"].includes(vehicle.freshness) ? vehicle.freshness : "stale";
        const compactClass = allSelected ? " is-compact" : "";
        const markerRadius = allSelected ? 9 : 14;
        const markerPath = allSelected ? "M -5 -6 L 7 0 L -5 6 Z" : "M -7 -8 L 10 0 L -7 8 Z";
        return `
            <g class="vehicle-marker ${escapeHtml(freshness)}${compactClass}" data-route-id="${escapeHtml(vehicle.sourceRouteId)}"
               transform="translate(${point.x.toFixed(1)} ${point.y.toFixed(1)})">
                <g class="map-scaled-marker">
                    <circle class="vehicle-halo" r="${markerRadius}"></circle>
                    <path class="vehicle-arrow" d="${markerPath}"></path>
                </g>
                ${allSelected ? "" : `<text class="map-scaled-label" x="17" y="5">${escapeHtml(routeNo)}</text>`}
                <title>${escapeHtml(routeNo)}번 ${escapeHtml(vehicle.vehicleNo || "-")} · ${point.unmatched ? "표시 노선 밖 · " : ""}${escapeHtml(freshnessLabel(vehicle.freshness))} · ${escapeHtml(formatAge(vehicle.collectedAt))}</title>
            </g>
        `;
    }).join("");
}

function renderVehicleFeed(vehicles) {
    const visibleVehicles = vehicles
        .filter((vehicle) => routeSelected(vehicle.sourceRouteId))
        .slice(0, 8);

    if (visibleVehicles.length === 0) {
        elements.vehicleFeed.innerHTML = `<div class="empty-state">표시할 차량 위치가 없습니다.</div>`;
        return;
    }

    elements.vehicleFeed.innerHTML = visibleVehicles.map((vehicle) => `
        <article class="vehicle-item">
            <header>
                <span class="vehicle-route">${escapeHtml(vehicle.routeNo || "-")}번 · ${escapeHtml(vehicle.vehicleNo || "-")}</span>
                <span class="freshness-pill freshness-${escapeHtml(vehicle.freshness)}">${escapeHtml(freshnessLabel(vehicle.freshness))}</span>
            </header>
            <div class="text-secondary small">
                순번 ${escapeHtml(vehicle.nodeOrder ?? "-")} · 정류소 ${escapeHtml(vehicle.sourceNodeId || "-")} · ${escapeHtml(formatAge(vehicle.collectedAt))}
            </div>
        </article>
    `).join("");
}

function renderMapLoadError(error) {
    cachedMap = null;
    elements.mapUpdatedAt.textContent = "지도 조회 실패";
    elements.mapRouteCount.textContent = "0";
    elements.mapRouteScope.textContent = "지도 API 연결 실패";
    elements.mapVehicleCount.textContent = "0";
    elements.mapVehicleScope.textContent = "지도 API 연결 실패";
    elements.mapDelayedVehicleCount.textContent = "0";
    elements.routeLayer.innerHTML = "";
    elements.stopLayer.innerHTML = "";
    elements.vehicleLayer.innerHTML = "";
    elements.routeFilterList.innerHTML = `<span class="text-secondary small">지도 API 연결을 확인하세요.</span>`;
    elements.vehicleFeed.innerHTML = `<div class="empty-state">차량 위치를 불러오지 못했습니다.</div>`;
    elements.mapEmptyState.textContent = `지도 데이터를 불러오지 못했습니다: ${error.message}`;
    elements.mapEmptyState.classList.add("show");
}

function renderRuns(runs) {
    elements.runCount.textContent = `${runs.length}건`;

    if (runs.length === 0) {
        elements.runList.innerHTML = `<div class="empty-state compact">수집 실행 내역이 없습니다.</div>`;
        return;
    }

    const leadingRuns = runs.slice(0, 6);
    const outOfWindowIssues = runs.slice(6).filter((run) => ["FAILED", "PARTIAL"].includes(run.status));
    const visibleRunIds = new Set();
    const visibleRuns = [...leadingRuns, ...outOfWindowIssues]
        .filter((run) => {
            const key = `${run.apiType ?? ""}|${run.requestKey ?? ""}|${run.startedAt ?? ""}|${run.status ?? ""}`;
            if (visibleRunIds.has(key)) {
                return false;
            }
            visibleRunIds.add(key);
            return true;
        })
        .slice(0, 10);
    const hiddenIssueCount = runs
        .filter((run) => ["FAILED", "PARTIAL"].includes(run.status))
        .filter((run) => !visibleRuns.includes(run))
        .length;
    const hiddenRunCount = Math.max(0, runs.length - visibleRuns.length);
    const moreLabel = hiddenRunCount > 0
        ? `<div class="operations-more">외 ${formatNumber(hiddenRunCount)}건${hiddenIssueCount > 0 ? ` · 미표시 이슈 ${formatNumber(hiddenIssueCount)}건` : ""}</div>`
        : "";

    elements.runList.innerHTML = `${visibleRuns.map((run) => `
        <article class="operations-item${["FAILED", "PARTIAL"].includes(run.status) ? " is-issue" : ""}">
            <header>
                <span class="${statusClass(run.status)}">${escapeHtml(run.status ?? "-")}</span>
                <strong>${escapeHtml(run.apiType ?? "-")}</strong>
            </header>
            <div class="operations-meta">${escapeHtml(run.requestKey ?? "-")}</div>
            ${["FAILED", "PARTIAL"].includes(run.status) ? `
                <div class="operations-detail">${escapeHtml(run.errorMessage || run.resultMessage || "상세 메시지 없음")}</div>
            ` : ""}
            <div class="operations-foot">
                <span>${escapeHtml(formatNumber(run.rowCount))}행</span>
                <span>${formatDateTime(run.startedAt)}</span>
            </div>
        </article>
    `).join("")}${moreLabel}`;
}

function renderAnomalies(anomalies) {
    elements.anomalyCount.textContent = `${anomalies.length}건`;

    if (anomalies.length === 0) {
        elements.anomalyList.innerHTML = `<div class="empty-state compact">현재 표시할 이상징후가 없습니다.</div>`;
        return;
    }

    const visibleAnomalies = anomalies.slice(0, 8);
    const moreLabel = anomalies.length > visibleAnomalies.length
        ? `<div class="operations-more">외 ${formatNumber(anomalies.length - visibleAnomalies.length)}건</div>`
        : "";

    elements.anomalyList.innerHTML = `${visibleAnomalies.map((anomaly) => `
        <article class="operations-item anomaly-item">
            <header>
                <span class="${severityClass(anomaly.severity)}">${escapeHtml(anomaly.severity)}</span>
                <strong>${escapeHtml(anomaly.routeNo)}</strong>
            </header>
            <div class="operations-title">${escapeHtml(anomaly.type)}</div>
            <div class="operations-meta">${escapeHtml(anomaly.observed)} · ${escapeHtml(anomaly.metric)}</div>
            <div class="operations-foot">
                <span>${escapeHtml(anomaly.area)}</span>
                <span>${formatDateTime(anomaly.updatedAt)}</span>
            </div>
            <button class="operations-action" data-anomaly-id="${escapeHtml(anomaly.id)}" type="button">근거</button>
        </article>
    `).join("")}${moreLabel}`;

    elements.anomalyList.querySelectorAll("[data-anomaly-id]").forEach((button) => {
        button.addEventListener("click", () => openAnomalyEvidence(button.dataset.anomalyId));
    });
}

function updateRefreshCountdown() {
    elements.refreshCountdown.textContent = elements.autoRefreshToggle.checked
        ? `${secondsUntilRefresh}초`
        : "꺼짐";
}

function resetRefreshCountdown() {
    secondsUntilRefresh = refreshIntervalSeconds;
    updateRefreshCountdown();
}

function startAutoRefresh() {
    window.clearInterval(refreshTimerId);
    resetRefreshCountdown();

    refreshTimerId = window.setInterval(async () => {
        if (!elements.autoRefreshToggle.checked) {
            updateRefreshCountdown();
            return;
        }

        secondsUntilRefresh -= 1;
        if (secondsUntilRefresh > 0) {
            updateRefreshCountdown();
            return;
        }

        resetRefreshCountdown();
        try {
            await loadDashboard();
        } catch (error) {
            showToast(`자동 갱신 실패: ${error.message}`);
        }
    }, 1000);
}

function setBriefingText(message) {
    elements.briefingText.textContent = message;
    elements.briefingText.title = message;
}

function selectedBriefingProviderOption() {
    return briefingOptions.providers.find((provider) => provider.provider === elements.briefingProvider.value)
        || briefingOptions.providers[0];
}

function syncBriefingModelToProvider(force = false) {
    const provider = selectedBriefingProviderOption();
    if (!provider) {
        return;
    }

    if (force || !elements.briefingModel.value.trim()) {
        elements.briefingModel.value = provider.defaultModel || "";
    }
}

function renderBriefingOptions() {
    const providers = Array.isArray(briefingOptions.providers) && briefingOptions.providers.length > 0
        ? briefingOptions.providers
        : [{provider: "ollama", label: "Ollama", defaultModel: ""}];
    const selectedProvider = providers.some((provider) => provider.provider === briefingOptions.defaultProvider)
        ? briefingOptions.defaultProvider
        : providers[0].provider;

    elements.briefingProvider.replaceChildren(...providers.map((provider) => {
        const option = new Option(provider.label || provider.provider, provider.provider);
        option.selected = provider.provider === selectedProvider;
        return option;
    }));
    syncBriefingModelToProvider(true);
}

async function loadBriefingOptions() {
    try {
        const options = await fetchJson(briefingOptionsEndpoint);
        if (options && Array.isArray(options.providers) && options.providers.length > 0) {
            briefingOptions = options;
        }
    } catch (error) {
        showToast(`AI 설정 조회 실패, 기본값 사용: ${error.message}`);
    } finally {
        renderBriefingOptions();
    }
}

function renderFallbackBriefing() {
    if (cachedAnomalies.length > 0) {
        const top = cachedAnomalies[0];
        setBriefingText(`${top.routeNo}번 ${top.type}이 우선 확인 대상입니다. ${top.observed}, ${top.metric} 차이로 현장 확인이 필요합니다.`);
        return;
    }

    const failed = cachedRuns.filter((run) => run.status === "FAILED");
    const partial = cachedRuns.filter((run) => run.status === "PARTIAL");
    const latestSuccess = cachedRuns.find((run) => run.status === "SUCCESS");

    if (failed.length === 0 && partial.length === 0) {
        setBriefingText(latestSuccess
            ? `최근 ${latestSuccess.apiType} 수집은 정상 완료되었습니다. 우선 확인 대상은 없고 최신 갱신 시각만 계속 확인하면 됩니다.`
            : "아직 수집 실행 데이터가 없습니다. 기준 데이터와 위치, 도착정보 수집 후 브리핑을 생성할 수 있습니다.");
        return;
    }

    const items = [...failed, ...partial].slice(0, 2).map((run) => `${run.apiType} ${run.status}`).join(", ");
    setBriefingText(`${items} 실행을 먼저 확인해야 합니다. 오류 메시지와 수집 행 수 기준으로 응답 누락 여부를 점검하세요.`);
}

async function renderBriefing() {
    const button = document.querySelector("#briefingButton");
    const originalText = button.textContent;
    button.disabled = true;
    button.textContent = "생성 중";
    setBriefingText("AI 브리핑을 생성하고 있습니다.");

    try {
        const response = await fetchJson("api/briefings/operations", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                provider: elements.briefingProvider.value,
                model: elements.briefingModel.value.trim(),
                anomalies: cachedAnomalies.map(toBriefingAnomaly)
            })
        });
        setBriefingText(response.content || "AI 브리핑 결과가 비어 있습니다.");
    } catch (error) {
        renderFallbackBriefing();
        showToast(`AI 브리핑 실패, 로컬 요약 사용: ${error.message}`);
    } finally {
        button.disabled = false;
        button.textContent = originalText;
    }
}

function renderDetailHeader(columns) {
    elements.detailTableHead.innerHTML = `
        <tr>
            ${columns.map((column) => `<th>${escapeHtml(column)}</th>`).join("")}
        </tr>
    `;
}

function detailRowsHtml(columns, rows) {
    return rows.map((row) => `
        <tr>
            ${columns.map((column) => `<td>${escapeHtml(formatDetailValue(row[column]))}</td>`).join("")}
        </tr>
    `).join("");
}

function renderDetailTable(payload) {
    renderDetailHeader(payload.columns);
    if (payload.rows.length === 0) {
        elements.detailTableBody.innerHTML = `
            <tr><td class="empty-state" colspan="${payload.columns.length}">조회된 데이터가 없습니다.</td></tr>
        `;
        return;
    }

    elements.detailTableBody.innerHTML = detailRowsHtml(payload.columns, payload.rows);
}

function appendDetailRows(payload, state) {
    if (payload.rows.length === 0) {
        return;
    }

    elements.detailTableBody.insertAdjacentHTML("beforeend", detailRowsHtml(state.columns, payload.rows));
}

function updateDetailSummary(state) {
    if (detailState !== state) {
        return;
    }

    elements.detailModalSummary.textContent = state.hasMore
        ? `${state.loadedRows}건 표시 · 아래로 스크롤하면 더 조회`
        : `${state.loadedRows}건 표시`;
}

async function loadDetailSlice(state = detailState) {
    if (!state || state.loading) {
        return;
    }

    state.loading = true;
    const initialLoad = state.loadedRows === 0;
    if (!initialLoad) {
        elements.detailModalSummary.textContent = `${state.loadedRows}건 표시 · 추가 조회 중`;
    }

    try {
        const asOfParam = state.asOf ? `&asOf=${encodeURIComponent(state.asOf)}` : "";
        const payload = await fetchJson(
            `api/details/${state.type}?limit=${state.limit}&offset=${state.offset}${asOfParam}`
        );
        if (detailState !== state) {
            return;
        }

        state.asOf = payload.asOf || state.asOf;
        if (initialLoad) {
            state.columns = payload.columns;
            renderDetailTable(payload);
        } else {
            appendDetailRows(payload, state);
        }

        state.loadedRows += payload.rows.length;
        state.offset = payload.offset + payload.rows.length;
        state.hasMore = payload.hasMore;
        updateDetailSummary(state);
    } catch (error) {
        if (detailState !== state) {
            return;
        }
        if (!initialLoad) {
            updateDetailSummary(state);
        }
        throw error;
    } finally {
        if (detailState === state) {
            state.loading = false;
        }
    }
}

function openAnomalyEvidence(anomalyId) {
    const anomaly = cachedAnomalies.find((item) => item.id === anomalyId);
    if (!anomaly) {
        showToast("이상징후 근거를 찾지 못했습니다.");
        return;
    }

    detailState = null;
    elements.detailModalBody.scrollTop = 0;
    elements.detailModalTitle.textContent = `${anomaly.routeNo}번 ${anomaly.type}`;
    elements.detailModalSummary.textContent = `${anomaly.area} · ${anomaly.metric}`;
    elements.detailTableHead.innerHTML = "";
    elements.detailTableBody.innerHTML = `
        <tr>
            <td colspan="5">
                <div class="evidence-summary">
                    <div>
                        <span>원래 배차 간격</span>
                        <strong>${escapeHtml(anomaly.baseline)}</strong>
                    </div>
                    <div>
                        <span>스냅샷 관측값</span>
                        <strong>${escapeHtml(anomaly.observed)}</strong>
                    </div>
                    <div>
                        <span>판정 차이</span>
                        <strong>${escapeHtml(anomaly.metric)}</strong>
                    </div>
                </div>
                <p class="evidence-reason mb-0">${escapeHtml(anomaly.reason)}</p>
            </td>
        </tr>
        <tr class="evidence-subhead">
            <td>수집시각</td>
            <td>차량번호</td>
            <td>정류소</td>
            <td>순번</td>
            <td>좌표</td>
        </tr>
        ${anomaly.snapshots.map((snapshot) => `
            <tr>
                <td>${formatDateTime(snapshot.collectedAt)}</td>
                <td class="fw-semibold">${escapeHtml(snapshot.vehicleNo)}</td>
                <td>${escapeHtml(snapshot.nodeName)}</td>
                <td>${escapeHtml(snapshot.nodeOrder)}</td>
                <td class="text-secondary">${escapeHtml(snapshot.gps)}</td>
            </tr>
        `).join("")}
    `;
    showModal();
}

async function openDetail(button) {
    const state = {
        type: button.dataset.detailType,
        columns: [],
        asOf: null,
        offset: 0,
        limit: detailSliceSize,
        loadedRows: 0,
        hasMore: true,
        loading: false
    };
    detailState = state;
    elements.detailModalBody.scrollTop = 0;
    elements.detailModalTitle.textContent = button.dataset.detailTitle;
    elements.detailModalSummary.textContent = "불러오는 중";
    elements.detailTableHead.innerHTML = "";
    elements.detailTableBody.innerHTML = `<tr><td class="empty-state">불러오는 중입니다.</td></tr>`;
    showModal();

    try {
        await loadDetailSlice(state);
    } catch (error) {
        if (detailState !== state) {
            return;
        }
        elements.detailModalSummary.textContent = "조회 실패";
        elements.detailTableBody.innerHTML = `<tr><td class="empty-state">상세 데이터를 불러오지 못했습니다.</td></tr>`;
        showToast(`상세 조회 실패: ${error.message}`);
    }
}

function handleDetailScroll() {
    if (!detailState || detailState.loading || !detailState.hasMore) {
        return;
    }

    const remainingScroll = elements.detailModalBody.scrollHeight
        - elements.detailModalBody.scrollTop
        - elements.detailModalBody.clientHeight;
    if (remainingScroll > detailScrollThresholdPx) {
        return;
    }

    const state = detailState;
    loadDetailSlice(state).catch((error) => showToast(`추가 조회 실패: ${error.message}`));
}

async function loadDashboard() {
    const [status, runs, anomalies, transitMap] = await Promise.allSettled([
        fetchJson(statusEndpoint),
        fetchJson(runsEndpoint),
        fetchJson(anomaliesEndpoint),
        fetchJson(mapEndpoint)
    ]);

    if (status.status === "rejected" || runs.status === "rejected" || anomalies.status === "rejected") {
        throw status.reason || runs.reason || anomalies.reason;
    }

    cachedRuns = runs.value;
    cachedAnomalies = Array.isArray(anomalies.value) ? anomalies.value : [];
    renderMetrics(status.value);
    if (transitMap.status === "fulfilled") {
        renderTransitMap(transitMap.value);
    } else {
        renderMapLoadError(transitMap.reason);
        showToast(`지도 조회 실패: ${transitMap.reason.message}`);
    }
    renderAnomalies(cachedAnomalies);
    renderRuns(runs.value);
    elements.updatedAt.textContent = `기준 ${formatDateTime(new Date())}`;
}

async function collect(url, button) {
    const originalText = button.textContent;
    button.disabled = true;
    button.textContent = "실행 중";

    try {
        const result = await fetchJson(url, {method: "POST"});
        showToast(`${result.apiType} ${result.status}: ${formatNumber(result.rowCount)}건`);
        await loadDashboard();
    } catch (error) {
        showToast(`수집 실패: ${error.message}`);
    } finally {
        button.disabled = false;
        button.textContent = originalText;
    }
}

function toBriefingAnomaly(anomaly) {
    return {
        severity: anomaly.severity,
        routeNo: anomaly.routeNo,
        type: anomaly.type,
        area: anomaly.area,
        baseline: anomaly.baseline,
        observed: anomaly.observed,
        metric: anomaly.metric,
        reason: anomaly.reason,
        snapshots: anomaly.snapshots
    };
}

document.querySelector("#refreshButton").addEventListener("click", () => {
    resetRefreshCountdown();
    loadDashboard().catch((error) => showToast(`새로고침 실패: ${error.message}`));
});

elements.autoRefreshToggle.addEventListener("change", resetRefreshCountdown);

document.querySelectorAll("[data-collect-url]").forEach((button) => {
    button.addEventListener("click", () => collect(button.dataset.collectUrl, button));
});

document.querySelector("#briefingButton").addEventListener("click", renderBriefing);
elements.briefingProvider.addEventListener("change", () => syncBriefingModelToProvider(true));

elements.mapZoomControls.addEventListener("click", (event) => {
    const button = event.target.closest("[data-map-zoom]");
    if (!button) {
        return;
    }

    if (button.dataset.mapZoom === "in") {
        zoomMap(mapZoomStep);
    } else if (button.dataset.mapZoom === "out") {
        zoomMap(1 / mapZoomStep);
    } else {
        resetMapZoom();
    }
});

elements.operationMap.addEventListener("wheel", handleMapWheel, {passive: false});
elements.operationMap.addEventListener("pointerdown", startMapPan);
elements.operationMap.addEventListener("pointermove", moveMapPan);
elements.operationMap.addEventListener("pointerup", endMapPan);
elements.operationMap.addEventListener("pointercancel", endMapPan);
elements.operationMap.addEventListener("click", () => {
    if (suppressMapClick) {
        suppressMapClick = false;
    }
});

elements.routeFilterList.addEventListener("click", (event) => {
    const button = event.target.closest("[data-route-id]");
    if (!button || !cachedMap) {
        return;
    }

    const routeId = button.dataset.routeId;
    if (routeId === "all") {
        selectedRouteIds.clear();
    } else if (selectedRouteIds.has(routeId)) {
        selectedRouteIds.delete(routeId);
    } else {
        selectedRouteIds.add(routeId);
    }
    renderTransitMap(cachedMap);
});

elements.vehicleLayer.addEventListener("click", (event) => {
    if (suppressMapClick) {
        suppressMapClick = false;
        return;
    }

    const marker = event.target.closest("[data-route-id]");
    if (!marker || !cachedMap) {
        return;
    }

    selectedRouteIds = new Set([marker.dataset.routeId]);
    renderTransitMap(cachedMap);
});

document.querySelectorAll("[data-detail-type]").forEach((button) => {
    button.addEventListener("click", () => openDetail(button));
});

elements.detailModal.querySelector(".btn-close").addEventListener("click", hideModal);
elements.detailModalBody.addEventListener("scroll", handleDetailScroll);

setMapViewBox(mapViewBox);
startAutoRefresh();
renderBriefingOptions();

loadBriefingOptions()
    .finally(() => loadDashboard())
    .catch((error) => {
        elements.mapEmptyState.classList.add("show");
        renderAnomalies(cachedAnomalies);
        elements.runList.innerHTML = `<div class="empty-state compact">대시보드 데이터를 불러오지 못했습니다.</div>`;
        showToast(`로딩 실패: ${error.message}`);
    });
