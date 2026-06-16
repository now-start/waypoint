const baseUrl = normalizeBaseUrl(window.baseUrl || "/");
window.baseUrl = baseUrl;

const statusEndpoint = "api/collections/status";
const runsEndpoint = "api/collections/runs?limit=20";
const anomaliesEndpoint = "api/anomalies";
const mapEndpoint = "api/operations/map";
const refreshIntervalSeconds = 30;
const detailSliceSize = 50;
const detailScrollThresholdPx = 80;
const mapWidth = 1000;
const mapHeight = 640;
const routePalette = ["#0f766e", "#2563eb", "#e11d48", "#a16207", "#7c3aed", "#0891b2", "#be123c", "#4d7c0f"];

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
    routeLayer: document.querySelector("#routeLayer"),
    stopLayer: document.querySelector("#stopLayer"),
    vehicleLayer: document.querySelector("#vehicleLayer"),
    mapEmptyState: document.querySelector("#mapEmptyState"),
    mapRouteCount: document.querySelector("#mapRouteCount"),
    mapVehicleCount: document.querySelector("#mapVehicleCount"),
    mapDelayedVehicleCount: document.querySelector("#mapDelayedVehicleCount"),
    routeFilterList: document.querySelector("#routeFilterList"),
    vehicleFeed: document.querySelector("#vehicleFeed"),
    anomalyBody: document.querySelector("#anomalyBody"),
    anomalyCount: document.querySelector("#anomalyCount"),
    runsBody: document.querySelector("#runsBody"),
    runCount: document.querySelector("#runCount"),
    collectionIssueList: document.querySelector("#collectionIssueList"),
    briefingText: document.querySelector("#briefingText"),
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
let refreshTimerId;
let secondsUntilRefresh = refreshIntervalSeconds;
let detailState = null;
let selectedRouteId = "all";

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

function projectPoint(latitude, longitude, bounds) {
    const lat = Number(latitude);
    const lng = Number(longitude);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
        return null;
    }

    const xRatio = (lng - bounds.minLongitude) / (bounds.maxLongitude - bounds.minLongitude);
    const yRatio = 1 - ((lat - bounds.minLatitude) / (bounds.maxLatitude - bounds.minLatitude));
    const x = Math.min(mapWidth, Math.max(0, xRatio * mapWidth));
    const y = Math.min(mapHeight, Math.max(0, yRatio * mapHeight));
    return {x, y};
}

function renderMetrics(status) {
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
    const bounds = payload.bounds;
    const selectedRoute = selectedRouteId === "all"
        ? null
        : routes.find((route) => route.sourceRouteId === selectedRouteId);

    if (selectedRouteId !== "all" && !selectedRoute) {
        selectedRouteId = "all";
    }

    elements.mapUpdatedAt.textContent = `지도 기준 ${formatDateTime(payload.generatedAt)}`;
    elements.mapRouteCount.textContent = formatNumber(payload.summary?.routeCount ?? routes.length);
    elements.mapVehicleCount.textContent = formatNumber(payload.summary?.vehicleCount ?? vehicles.length);
    elements.mapDelayedVehicleCount.textContent = formatNumber(payload.summary?.delayedVehicleCount ?? 0);
    elements.mapEmptyState.textContent = "지도에 표시할 노선 또는 차량 위치 데이터가 없습니다.";
    elements.mapEmptyState.classList.toggle("show", routes.length === 0 && vehicles.length === 0);

    renderRouteFilters(routes, vehicles);
    renderRouteLayer(routes, bounds);
    renderStopLayer(routes, bounds);
    renderVehicleLayer(vehicles, bounds);
    renderVehicleFeed(vehicles);
}

function renderRouteFilters(routes, vehicles) {
    const routeVehicleCounts = vehicles.reduce((counts, vehicle) => {
        counts.set(vehicle.sourceRouteId, (counts.get(vehicle.sourceRouteId) || 0) + 1);
        return counts;
    }, new Map());
    const routeButtons = routes.map((route, index) => {
        const vehicleCount = routeVehicleCounts.get(route.sourceRouteId) || 0;
        const activeClass = selectedRouteId === route.sourceRouteId ? " active" : "";
        return `
            <button class="route-filter${activeClass}" data-route-id="${escapeHtml(route.sourceRouteId)}"
                    style="--route-color: ${routeColor(index)}" type="button">
                ${escapeHtml(route.routeNo || route.sourceRouteId)}
                <span class="text-secondary">${escapeHtml(vehicleCount)}대</span>
            </button>
        `;
    }).join("");

    const allActiveClass = selectedRouteId === "all" ? " active" : "";
    elements.routeFilterList.innerHTML = `
        <button class="route-filter${allActiveClass}" data-route-id="all" style="--route-color: #344054" type="button">전체</button>
        ${routeButtons || `<span class="text-secondary small">표시할 노선이 없습니다.</span>`}
    `;
}

function renderRouteLayer(routes, bounds) {
    elements.routeLayer.innerHTML = routes.map((route, index) => {
        const points = route.stops
            .map((stop) => projectPoint(stop.latitude, stop.longitude, bounds))
            .filter(Boolean);
        if (points.length < 2) {
            return "";
        }

        const selected = selectedRouteId === route.sourceRouteId;
        const muted = selectedRouteId !== "all" && !selected;
        const classes = [
            "route-path",
            selected ? "is-selected" : "",
            muted ? "is-muted" : ""
        ].filter(Boolean).join(" ");
        const pointText = points.map((point) => `${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(" ");
        return `
            <polyline class="${classes}" points="${pointText}" stroke="${routeColor(index)}" stroke-width="5">
                <title>${escapeHtml(route.routeNo || route.sourceRouteId)} ${escapeHtml(route.startNodeName || "")} - ${escapeHtml(route.endNodeName || "")}</title>
            </polyline>
        `;
    }).join("");
}

function renderStopLayer(routes, bounds) {
    const stopRoutes = selectedRouteId === "all"
        ? routes.filter((route) => route.stops.length > 0).slice(0, 4)
        : routes.filter((route) => route.sourceRouteId === selectedRouteId);

    elements.stopLayer.innerHTML = stopRoutes.flatMap((route) => route.stops).slice(0, 220).map((stop) => {
        const point = projectPoint(stop.latitude, stop.longitude, bounds);
        if (!point) {
            return "";
        }
        return `
            <circle class="stop-dot" cx="${point.x.toFixed(1)}" cy="${point.y.toFixed(1)}" r="4">
                <title>${escapeHtml(stop.nodeName || stop.sourceNodeId)} · 순번 ${escapeHtml(stop.nodeOrder ?? "-")}</title>
            </circle>
        `;
    }).join("");
}

function renderVehicleLayer(vehicles, bounds) {
    const visibleVehicles = vehicles
        .filter((vehicle) => selectedRouteId === "all" || vehicle.sourceRouteId === selectedRouteId)
        .slice(0, 180);

    elements.vehicleLayer.innerHTML = visibleVehicles.map((vehicle) => {
        const point = projectPoint(vehicle.latitude, vehicle.longitude, bounds);
        if (!point) {
            return "";
        }
        const routeNo = vehicle.routeNo || "-";
        return `
            <g class="vehicle-marker ${escapeHtml(vehicle.freshness)}" data-route-id="${escapeHtml(vehicle.sourceRouteId)}"
               transform="translate(${point.x.toFixed(1)} ${point.y.toFixed(1)})">
                <circle r="14"></circle>
                <circle class="vehicle-core" r="6"></circle>
                <text x="18" y="6">${escapeHtml(routeNo)}</text>
                <title>${escapeHtml(routeNo)}번 ${escapeHtml(vehicle.vehicleNo || "-")} · ${escapeHtml(freshnessLabel(vehicle.freshness))} · ${escapeHtml(formatAge(vehicle.collectedAt))}</title>
            </g>
        `;
    }).join("");
}

function renderVehicleFeed(vehicles) {
    const visibleVehicles = vehicles
        .filter((vehicle) => selectedRouteId === "all" || vehicle.sourceRouteId === selectedRouteId)
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
    elements.mapVehicleCount.textContent = "0";
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
        elements.runsBody.innerHTML = `<tr><td class="empty-state" colspan="6">수집 실행 내역이 없습니다.</td></tr>`;
        return;
    }

    elements.runsBody.innerHTML = runs.map((run) => `
        <tr>
            <td><span class="${statusClass(run.status)}">${escapeHtml(run.status ?? "-")}</span></td>
            <td class="fw-semibold">${escapeHtml(run.apiType ?? "-")}</td>
            <td class="text-secondary">${escapeHtml(run.requestKey ?? "-")}</td>
            <td class="text-end">${escapeHtml(formatNumber(run.rowCount))}</td>
            <td>${formatDateTime(run.startedAt)}</td>
            <td>${formatDateTime(run.finishedAt)}</td>
        </tr>
    `).join("");
}

function renderAnomalies(anomalies) {
    elements.anomalyCount.textContent = `${anomalies.length}건`;

    if (anomalies.length === 0) {
        elements.anomalyBody.innerHTML = `<tr><td class="empty-state" colspan="8">현재 표시할 이상징후가 없습니다.</td></tr>`;
        return;
    }

    elements.anomalyBody.innerHTML = anomalies.map((anomaly) => `
        <tr>
            <td><span class="${severityClass(anomaly.severity)}">${escapeHtml(anomaly.severity)}</span></td>
            <td class="fw-semibold">${escapeHtml(anomaly.routeNo)}</td>
            <td>${escapeHtml(anomaly.type)}</td>
            <td>${escapeHtml(anomaly.baseline)}</td>
            <td>${escapeHtml(anomaly.observed)}</td>
            <td>
                <div class="fw-semibold">${escapeHtml(anomaly.metric)}</div>
                <div class="text-secondary small">${escapeHtml(anomaly.area)}</div>
            </td>
            <td>${formatDateTime(anomaly.updatedAt)}</td>
            <td class="text-end">
                <button class="btn btn-outline-secondary btn-sm" data-anomaly-id="${escapeHtml(anomaly.id)}" type="button">근거</button>
            </td>
        </tr>
    `).join("");

    elements.anomalyBody.querySelectorAll("[data-anomaly-id]").forEach((button) => {
        button.addEventListener("click", () => openAnomalyEvidence(button.dataset.anomalyId));
    });
}

function renderCollectionIssues(runs) {
    const issues = runs.filter((run) => ["FAILED", "PARTIAL"].includes(run.status)).slice(0, 5);

    if (issues.length === 0) {
        elements.collectionIssueList.innerHTML = `<div class="empty-state">확인할 실패 또는 부분 성공 실행이 없습니다.</div>`;
        return;
    }

    elements.collectionIssueList.innerHTML = issues.map((run) => `
        <div class="incident-item">
            <span class="${statusClass(run.status)} mb-2">${escapeHtml(run.status)}</span>
            <strong>${escapeHtml(run.apiType ?? "수집 실행")}</strong>
            <div class="text-secondary small">${escapeHtml(run.errorMessage || run.resultMessage || "상세 메시지 없음")}</div>
        </div>
    `).join("");
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

function renderFallbackBriefing() {
    if (cachedAnomalies.length > 0) {
        const top = cachedAnomalies[0];
        elements.briefingText.textContent = `${top.routeNo}번 ${top.area} 구간에서 ${top.type} 징후가 우선 확인 대상입니다. 원래 배차는 ${top.baseline}이고, 최근 스냅샷에서는 ${top.observed}으로 관측되어 ${top.metric} 차이가 있습니다. 실제 원인은 현장 상황과 추가 수집 데이터를 함께 확인해야 합니다.`;
        return;
    }

    const failed = cachedRuns.filter((run) => run.status === "FAILED");
    const partial = cachedRuns.filter((run) => run.status === "PARTIAL");
    const latestSuccess = cachedRuns.find((run) => run.status === "SUCCESS");

    if (failed.length === 0 && partial.length === 0) {
        elements.briefingText.textContent = latestSuccess
            ? `최근 ${latestSuccess.apiType} 수집은 정상 완료되었습니다. 현재 우선 확인 대상은 없으며, 최신 위치와 도착정보 갱신 시각을 계속 확인하면 됩니다.`
            : "아직 수집 실행 데이터가 없습니다. 기준 데이터와 위치, 도착정보 수집 후 브리핑을 생성할 수 있습니다.";
        return;
    }

    const items = [...failed, ...partial].slice(0, 3).map((run) => `${run.apiType} ${run.status}`).join(", ");
    elements.briefingText.textContent = `${items} 실행을 먼저 확인해야 합니다. 오류 메시지와 수집 행 수를 기준으로 API 응답 누락, 부분 저장, 외부 호출 실패 여부를 점검하는 것이 좋습니다.`;
}

async function renderBriefing() {
    const button = document.querySelector("#briefingButton");
    const originalText = button.textContent;
    button.disabled = true;
    button.textContent = "생성 중";
    elements.briefingText.textContent = "AI 브리핑을 생성하고 있습니다.";

    try {
        const response = await fetchJson("api/briefings/operations", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({anomalies: cachedAnomalies.map(toBriefingAnomaly)})
        });
        elements.briefingText.textContent = response.content || "AI 브리핑 결과가 비어 있습니다.";
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
    renderCollectionIssues(runs.value);
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

elements.routeFilterList.addEventListener("click", (event) => {
    const button = event.target.closest("[data-route-id]");
    if (!button || !cachedMap) {
        return;
    }

    selectedRouteId = button.dataset.routeId;
    renderTransitMap(cachedMap);
});

elements.vehicleLayer.addEventListener("click", (event) => {
    const marker = event.target.closest("[data-route-id]");
    if (!marker || !cachedMap) {
        return;
    }

    selectedRouteId = marker.dataset.routeId;
    renderTransitMap(cachedMap);
});

document.querySelectorAll("[data-detail-type]").forEach((button) => {
    button.addEventListener("click", () => openDetail(button));
});

elements.detailModal.querySelector(".btn-close").addEventListener("click", hideModal);
elements.detailModalBody.addEventListener("scroll", handleDetailScroll);

startAutoRefresh();

loadDashboard().catch((error) => {
    elements.mapEmptyState.classList.add("show");
    renderAnomalies(cachedAnomalies);
    elements.runsBody.innerHTML = `<tr><td class="empty-state" colspan="6">대시보드 데이터를 불러오지 못했습니다.</td></tr>`;
    elements.collectionIssueList.innerHTML = `<div class="empty-state">API 연결을 확인하세요.</div>`;
    showToast(`로딩 실패: ${error.message}`);
});
