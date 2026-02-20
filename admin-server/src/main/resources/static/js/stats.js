registerPage('stats', async function(app) {
    let statsData = [];
    let sortCol = 'count';
    let sortAsc = false;
    let chart = null;

    const now = new Date();
    const from24 = new Date(now.getTime() - 86400000);
    let currentFrom = toLocalISOString(from24);
    let currentTo = toLocalISOString(now);

    app.innerHTML = '';

    const picker = createPeriodPicker((from, to) => {
        currentFrom = from;
        currentTo = to;
        loadStats();
    });
    app.appendChild(picker);

    const tableCard = document.createElement('div');
    tableCard.className = 'card';
    tableCard.innerHTML = '<div class="card-title">API Statistics</div><div id="stats-table-wrap"></div>';
    app.appendChild(tableCard);

    const chartCard = document.createElement('div');
    chartCard.className = 'card';
    chartCard.innerHTML = '<div class="card-title">Response Time Percentiles</div><div class="chart-container"><canvas id="stats-chart"></canvas></div>';
    app.appendChild(chartCard);

    async function loadStats() {
        const wrap = document.getElementById('stats-table-wrap');
        wrap.innerHTML = '<div class="loading">Loading...</div>';
        try {
            const res = await fetch(`/api/stats?from=${encodeURIComponent(currentFrom)}&to=${encodeURIComponent(currentTo)}`);
            statsData = await res.json();
            renderTable();
        } catch (e) {
            wrap.innerHTML = '<div class="empty">Failed to load stats</div>';
        }
    }

    function renderTable() {
        const wrap = document.getElementById('stats-table-wrap');
        if (!statsData.length) {
            wrap.innerHTML = '<div class="empty">No data</div>';
            return;
        }

        const cols = [
            { key: 'path', label: 'Path' },
            { key: 'count', label: 'Count' },
            { key: 'errorRate', label: 'Error Rate' },
            { key: 'avg', label: 'Avg' },
            { key: 'min', label: 'Min' },
            { key: 'max', label: 'Max' },
            { key: 'p30', label: 'P30' },
            { key: 'p50', label: 'P50' },
            { key: 'p75', label: 'P75' },
            { key: 'p90', label: 'P90' },
            { key: 'p95', label: 'P95' },
            { key: 'p99', label: 'P99' },
        ];

        const sorted = [...statsData].sort((a, b) => {
            const va = a[sortCol] ?? 0, vb = b[sortCol] ?? 0;
            return sortAsc ? (va > vb ? 1 : -1) : (va < vb ? 1 : -1);
        });

        let html = '<table><thead><tr>';
        cols.forEach(c => {
            const icon = sortCol === c.key ? (sortAsc ? '\u25B2' : '\u25BC') : '';
            html += `<th data-col="${c.key}">${c.label} <span class="sort-icon">${icon}</span></th>`;
        });
        html += '</tr></thead><tbody>';

        sorted.forEach((s, i) => {
            html += `<tr class="stats-row" data-idx="${i}" style="cursor:pointer">
                <td>${s.path}</td>
                <td>${s.count}</td>
                <td>${formatRate(s.errorRate)}</td>
                <td>${formatMs(s.avg)}</td>
                <td>${formatMs(s.min)}</td>
                <td>${formatMs(s.max)}</td>
                <td>${formatMs(s.p30)}</td>
                <td>${formatMs(s.p50)}</td>
                <td>${formatMs(s.p75)}</td>
                <td>${formatMs(s.p90)}</td>
                <td>${formatMs(s.p95)}</td>
                <td>${formatMs(s.p99)}</td>
            </tr>`;
        });
        html += '</tbody></table>';
        wrap.innerHTML = html;

        // Sort handlers
        wrap.querySelectorAll('th').forEach(th => {
            th.addEventListener('click', () => {
                const col = th.dataset.col;
                if (sortCol === col) sortAsc = !sortAsc;
                else { sortCol = col; sortAsc = true; }
                renderTable();
            });
        });

        // Row click -> chart
        wrap.querySelectorAll('.stats-row').forEach(row => {
            row.addEventListener('click', () => {
                const idx = parseInt(row.dataset.idx);
                renderChart(sorted[idx]);
            });
        });

        if (sorted.length > 0) renderChart(sorted[0]);
    }

    function renderChart(stat) {
        const canvas = document.getElementById('stats-chart');
        if (!canvas) return;
        if (chart) chart.destroy();

        chart = new Chart(canvas, {
            type: 'bar',
            data: {
                labels: ['P30', 'P50', 'P75', 'P90', 'P95', 'P99'],
                datasets: [{
                    label: stat.path,
                    data: [stat.p30, stat.p50, stat.p75, stat.p90, stat.p95, stat.p99],
                    backgroundColor: ['#7b8cde', '#4361ee', '#3a56d4', '#2f4bc0', '#2541ad', '#1b3699'],
                    borderRadius: 4,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: true } },
                scales: { y: { beginAtZero: true, title: { display: true, text: 'ms' } } }
            }
        });
    }

    loadStats();
});
