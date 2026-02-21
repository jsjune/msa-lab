registerPage('traffic', async function(app) {
    let cyInstance = null;

    const now = new Date();
    const from24 = new Date(now.getTime() - 86400000);
    let currentFrom = toLocalISOString(from24);
    let currentTo = toLocalISOString(now);

    app.innerHTML = '';

    const picker = createPeriodPicker((from, to) => {
        currentFrom = from;
        currentTo = to;
        loadGraph();
    });
    app.appendChild(picker);

    // 처리율 요약 카드
    const summaryCard = document.createElement('div');
    summaryCard.className = 'card traffic-summary';
    summaryCard.innerHTML = `
        <div class="summary-stats">
            <div class="summary-stat">
                <div class="summary-val" id="sum-total">-</div>
                <div class="summary-key">총 요청 수</div>
            </div>
            <div class="summary-stat">
                <div class="summary-val" id="sum-avg">-</div>
                <div class="summary-key">평균 처리율</div>
            </div>
            <div class="summary-stat">
                <div class="summary-val" id="sum-max">-</div>
                <div class="summary-key">최대 처리율</div>
            </div>
        </div>
    `;
    app.appendChild(summaryCard);

    // 그래프 + 사이드 패널 레이아웃
    const layout = document.createElement('div');
    layout.className = 'traffic-layout';
    layout.innerHTML = `
        <div class="traffic-graph-card card">
            <div class="card-header-row">
                <div class="card-title">Service Traffic Graph</div>
                <div class="traffic-legend">
                    <span class="legend-item"><span class="legend-dot" style="background:#28a745"></span>정상 (&lt;10%)</span>
                    <span class="legend-item"><span class="legend-dot" style="background:#ffc107"></span>경고 (10~30%)</span>
                    <span class="legend-item"><span class="legend-dot" style="background:#dc3545"></span>에러 (≥30%)</span>
                </div>
            </div>
            <div class="traffic-hint">💡 노드 또는 엣지를 클릭하면 오른쪽에 상세 정보가 표시됩니다. 엣지 위의 숫자는 요청 수입니다.</div>
            <div id="cy" class="cy-container"></div>
        </div>
        <div id="traffic-panel" class="traffic-panel card">
            <div class="card-title" id="panel-title">상세 정보</div>
            <div id="panel-content">
                <div class="panel-placeholder">
                    <div class="panel-hint-icon">👆</div>
                    <div>노드 또는 엣지를<br>클릭하세요</div>
                </div>
            </div>
        </div>
    `;
    app.appendChild(layout);

    async function loadGraph() {
        const cyEl = document.getElementById('cy');
        cyEl.innerHTML = '<div class="loading">Loading...</div>';
        cyEl.style.height = '';

        resetSummary();
        resetPanel();

        try {
            const [graphRes, throughputRes] = await Promise.all([
                fetch(`/api/traffic/graph?from=${encodeURIComponent(currentFrom)}&to=${encodeURIComponent(currentTo)}`),
                fetch(`/api/traffic/throughput?from=${encodeURIComponent(currentFrom)}&to=${encodeURIComponent(currentTo)}`),
            ]);
            const [graphData, throughputData] = await Promise.all([graphRes.json(), throughputRes.json()]);

            renderSummary(throughputData);
            renderGraph(graphData);
        } catch (e) {
            cyEl.innerHTML = '<div class="empty">Failed to load graph</div>';
        }
    }

    function resetSummary() {
        ['sum-total', 'sum-avg', 'sum-max'].forEach(id => {
            document.getElementById(id).textContent = '-';
        });
    }

    function resetPanel() {
        document.getElementById('panel-title').textContent = '상세 정보';
        document.getElementById('panel-content').innerHTML = `
            <div class="panel-placeholder">
                <div class="panel-hint-icon">👆</div>
                <div>노드 또는 엣지를<br>클릭하세요</div>
            </div>
        `;
    }

    function renderSummary(t) {
        document.getElementById('sum-total').textContent = t.totalRequests.toLocaleString() + '건';
        document.getElementById('sum-avg').textContent = t.avgPerMinute.toFixed(1) + ' req/min';
        document.getElementById('sum-max').textContent = t.maxPerMinute + ' req/min';
    }

    function edgeColor(errorRate) {
        if (errorRate >= 30) return '#dc3545';
        if (errorRate >= 10) return '#ffc107';
        return '#28a745';
    }

    function renderGraph(data) {
        const container = document.getElementById('cy');
        container.innerHTML = '';

        if (!data.nodes || data.nodes.length === 0) {
            container.innerHTML = '<div class="empty">해당 기간에 트래픽 데이터가 없습니다</div>';
            return;
        }

        container.style.height = '500px';

        if (cyInstance) {
            cyInstance.destroy();
            cyInstance = null;
        }

        const elements = [];

        data.nodes.forEach(n => {
            elements.push({ data: { id: n.name, label: n.name, nodeData: n } });
        });

        data.edges.forEach(e => {
            const errSuffix = e.errorRate > 0 ? `\n(err ${e.errorRate.toFixed(1)}%)` : '';
            elements.push({
                data: {
                    id: `${e.source}->${e.target}`,
                    source: e.source,
                    target: e.target,
                    color: edgeColor(e.errorRate),
                    label: `${e.requestCount}건${errSuffix}`,
                    edgeData: e,
                }
            });
        });

        cyInstance = cytoscape({
            container,
            elements,
            style: [
                {
                    selector: 'node',
                    style: {
                        'background-color': '#4361ee',
                        'label': 'data(label)',
                        'color': '#fff',
                        'text-valign': 'center',
                        'text-halign': 'center',
                        'font-size': '13px',
                        'font-weight': '600',
                        'width': '80px',
                        'height': '80px',
                        'border-width': '2px',
                        'border-color': '#1a1a2e',
                    }
                },
                {
                    selector: 'edge',
                    style: {
                        'width': 3,
                        'line-color': 'data(color)',
                        'target-arrow-color': 'data(color)',
                        'target-arrow-shape': 'triangle',
                        'curve-style': 'bezier',
                        'label': 'data(label)',
                        'font-size': '11px',
                        'color': '#333',
                        'text-background-color': '#fff',
                        'text-background-opacity': 0.85,
                        'text-background-padding': '3px',
                        'text-wrap': 'wrap',
                    }
                },
                {
                    selector: ':selected',
                    style: {
                        'border-color': '#ffc107',
                        'border-width': '3px',
                        'line-color': '#ffc107',
                        'target-arrow-color': '#ffc107',
                    }
                }
            ],
            layout: {
                name: 'breadthfirst',
                directed: true,
                padding: 30,
            }
        });

        cyInstance.on('tap', 'node', function(evt) {
            showNodePanel(evt.target.data('nodeData'));
        });

        cyInstance.on('tap', 'edge', function(evt) {
            showEdgePanel(evt.target.data('edgeData'));
        });

        cyInstance.on('tap', function(evt) {
            if (evt.target === cyInstance) {
                resetPanel();
            }
        });
    }

    function showNodePanel(n) {
        const errClass = n.errorRate >= 30 ? 'text-error' : n.errorRate >= 10 ? 'text-warn' : 'text-ok';
        document.getElementById('panel-title').textContent = `🔵 ${n.name}`;
        document.getElementById('panel-content').innerHTML = `
            <div class="panel-row"><span class="panel-label">서비스</span><span>${n.name}</span></div>
            <div class="panel-row"><span class="panel-label">요청 수 (inbound)</span><span>${n.requestCount.toLocaleString()}건</span></div>
            <div class="panel-row"><span class="panel-label">에러율</span><span class="${errClass}">${formatRate(n.errorRate)}</span></div>
            <div class="panel-row"><span class="panel-label">평균 응답시간</span><span>${formatMs(n.avgDuration)}</span></div>
        `;
    }

    function showEdgePanel(e) {
        const errClass = e.errorRate >= 30 ? 'text-error' : e.errorRate >= 10 ? 'text-warn' : 'text-ok';
        document.getElementById('panel-title').textContent = `${e.source} → ${e.target}`;
        document.getElementById('panel-content').innerHTML = `
            <div class="panel-row"><span class="panel-label">출발</span><span>${e.source}</span></div>
            <div class="panel-row"><span class="panel-label">도착</span><span>${e.target}</span></div>
            <div class="panel-row"><span class="panel-label">요청 수</span><span>${e.requestCount.toLocaleString()}건</span></div>
            <div class="panel-row"><span class="panel-label">에러율</span><span class="${errClass}">${formatRate(e.errorRate)}</span></div>
            <div class="panel-row"><span class="panel-label">P50 응답시간</span><span>${formatMs(e.p50)}</span></div>
            <div class="panel-row"><span class="panel-label">P99 응답시간</span><span>${formatMs(e.p99)}</span></div>
        `;
    }

    loadGraph();
});
