const statusEndpoint = "/api/collections/status";
const runsEndpoint = "/api/collections/runs?limit=20";
const anomaliesEndpoint = "/api/anomalies";
const refreshIntervalSeconds = 30;

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
    anomalyBody: document.querySelector("#anomalyBody"),
    anomalyCount: document.querySelector("#anomalyCount"),
    runsBody: document.querySelector("#runsBody"),
    runCount: document.querySelector("#runCount"),
    collectionIssueList: document.querySelector("#collectionIssueList"),
    briefingText: document.querySelector("#briefingText"),
    detailModal: document.querySelector("#detailModal"),
    detailModalTitle: document.querySelector("#detailModalTitle"),
    detailModalSummary: document.querySelector("#detailModalSummary"),
    detailTableHead: document.querySelector("#detailTableHead"),
    detailTableBody: document.querySelector("#detailTableBody"),
    toast: document.querySelector("#messageToast"),
    toastBody: document.querySelector("#toastBody")
};

let cachedRuns = [];
let cachedAnomalies = [];
let refreshTimerId;
let secondsUntilRefresh = refreshIntervalSeconds;

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
    return `status-pill status-${String(status || "empty").toLowerCase()}`;
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
    const response = await fetch(url, options);
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

function renderMetrics(status) {
    elements.routeCount.textContent = formatNumber(status.routeCount);
    elements.stopCount.textContent = formatNumber(status.stopCount);
    elements.routeStopCount.textContent = formatNumber(status.routeStopCount);
    elements.locationSnapshotCount.textContent = formatNumber(status.locationSnapshotCount);
    elements.arrivalSnapshotCount.textContent = formatNumber(status.arrivalSnapshotCount);
    elements.latestLocationCollectedAt.textContent = `최근 ${formatDateTime(status.latestLocationCollectedAt)}`;
    elements.latestArrivalCollectedAt.textContent = `최근 ${formatDateTime(status.latestArrivalCollectedAt)}`;
}

function renderRuns(runs) {
    elements.runCount.textContent = `${runs.length}건`;

    if (runs.length === 0) {
        elements.runsBody.innerHTML = `<tr><td class="empty-state" colspan="6">수집 실행 내역이 없습니다.</td></tr>`;
        return;
    }

    elements.runsBody.innerHTML = runs.map((run) => `
        <tr>
            <td><span class="${statusClass(run.status)}">${run.status ?? "-"}</span></td>
            <td class="fw-semibold">${run.apiType ?? "-"}</td>
            <td class="text-secondary">${run.requestKey ?? "-"}</td>
            <td class="text-end">${formatNumber(run.rowCount)}</td>
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
            <td><span class="${severityClass(anomaly.severity)}">${anomaly.severity}</span></td>
            <td class="fw-semibold">${anomaly.routeNo}</td>
            <td>${anomaly.type}</td>
            <td>${anomaly.baseline}</td>
            <td>${anomaly.observed}</td>
            <td>
                <div class="fw-semibold">${anomaly.metric}</div>
                <div class="text-secondary small">${anomaly.area}</div>
            </td>
            <td>${formatDateTime(anomaly.updatedAt)}</td>
            <td class="text-end">
                <button class="btn btn-outline-secondary btn-sm" data-anomaly-id="${anomaly.id}" type="button">근거</button>
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
            <span class="${statusClass(run.status)} mb-2">${run.status}</span>
            <strong>${run.apiType ?? "수집 실행"}</strong>
            <div class="text-secondary small">${run.errorMessage || run.resultMessage || "상세 메시지 없음"}</div>
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
        const response = await fetchJson("/api/briefings/operations", {
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

function renderDetailTable(payload) {
    elements.detailTableHead.innerHTML = `
        <tr>
            ${payload.columns.map((column) => `<th>${column}</th>`).join("")}
        </tr>
    `;

    if (payload.rows.length === 0) {
        elements.detailTableBody.innerHTML = `
            <tr><td class="empty-state" colspan="${payload.columns.length}">조회된 데이터가 없습니다.</td></tr>
        `;
        return;
    }

    elements.detailTableBody.innerHTML = payload.rows.map((row) => `
        <tr>
            ${payload.columns.map((column) => `<td>${formatDetailValue(row[column])}</td>`).join("")}
        </tr>
    `).join("");
}

function openAnomalyEvidence(anomalyId) {
    const anomaly = cachedAnomalies.find((item) => item.id === anomalyId);
    if (!anomaly) {
        showToast("이상징후 근거를 찾지 못했습니다.");
        return;
    }

    elements.detailModalTitle.textContent = `${anomaly.routeNo}번 ${anomaly.type}`;
    elements.detailModalSummary.textContent = `${anomaly.area} · ${anomaly.metric}`;
    elements.detailTableHead.innerHTML = "";
    elements.detailTableBody.innerHTML = `
        <tr>
            <td colspan="5">
                <div class="evidence-summary">
                    <div>
                        <span>원래 배차 간격</span>
                        <strong>${anomaly.baseline}</strong>
                    </div>
                    <div>
                        <span>스냅샷 관측값</span>
                        <strong>${anomaly.observed}</strong>
                    </div>
                    <div>
                        <span>판정 차이</span>
                        <strong>${anomaly.metric}</strong>
                    </div>
                </div>
                <p class="evidence-reason mb-0">${anomaly.reason}</p>
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
                <td class="fw-semibold">${snapshot.vehicleNo}</td>
                <td>${snapshot.nodeName}</td>
                <td>${snapshot.nodeOrder}</td>
                <td class="text-secondary">${snapshot.gps}</td>
            </tr>
        `).join("")}
    `;
    showModal();
}

async function openDetail(button) {
    elements.detailModalTitle.textContent = button.dataset.detailTitle;
    elements.detailModalSummary.textContent = "최대 50건";
    elements.detailTableHead.innerHTML = "";
    elements.detailTableBody.innerHTML = `<tr><td class="empty-state">불러오는 중입니다.</td></tr>`;
    showModal();

    try {
        const payload = await fetchJson(`/api/details/${button.dataset.detailType}?limit=50`);
        elements.detailModalSummary.textContent = `${payload.rows.length}건 표시`;
        renderDetailTable(payload);
    } catch (error) {
        elements.detailTableBody.innerHTML = `<tr><td class="empty-state">상세 데이터를 불러오지 못했습니다.</td></tr>`;
        showToast(`상세 조회 실패: ${error.message}`);
    }
}

async function loadDashboard() {
    const [status, runs, anomalies] = await Promise.all([
        fetchJson(statusEndpoint),
        fetchJson(runsEndpoint),
        fetchJson(anomaliesEndpoint)
    ]);

    cachedRuns = runs;
    cachedAnomalies = Array.isArray(anomalies) ? anomalies : [];
    renderMetrics(status);
    renderAnomalies(cachedAnomalies);
    renderRuns(runs);
    renderCollectionIssues(runs);
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

document.querySelectorAll("[data-detail-type]").forEach((button) => {
    button.addEventListener("click", () => openDetail(button));
});

elements.detailModal.querySelector(".btn-close").addEventListener("click", hideModal);

startAutoRefresh();

loadDashboard().catch((error) => {
    renderAnomalies(cachedAnomalies);
    elements.runsBody.innerHTML = `<tr><td class="empty-state" colspan="6">대시보드 데이터를 불러오지 못했습니다.</td></tr>`;
    elements.collectionIssueList.innerHTML = `<div class="empty-state">API 연결을 확인하세요.</div>`;
    showToast(`로딩 실패: ${error.message}`);
});
