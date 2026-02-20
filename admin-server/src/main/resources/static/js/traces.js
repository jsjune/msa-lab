registerPage('traces', async function(app) {
    let expandedHop = null;
    let includeDetail = false;

    app.innerHTML = '';

    // Search bar
    const searchDiv = document.createElement('div');
    searchDiv.className = 'search-bar';
    searchDiv.innerHTML = `
        <input type="text" id="txid-input" class="search-input" placeholder="Enter Transaction ID (txId)">
        <label style="display:flex;align-items:center;gap:4px;font-size:13px">
            <input type="checkbox" id="detail-toggle"> Include Detail (Headers + Body)
        </label>
        <button id="txid-search-btn" class="search-btn">Search</button>
    `;
    app.appendChild(searchDiv);

    // Trace detail container
    const detailCard = document.createElement('div');
    detailCard.id = 'trace-detail';
    detailCard.className = 'card';
    detailCard.style.display = 'none';
    app.appendChild(detailCard);

    // Trace list section
    const listCard = document.createElement('div');
    listCard.className = 'card';
    listCard.innerHTML = '<div class="card-title">Trace List</div>';
    app.appendChild(listCard);

    const now = new Date();
    const from24 = new Date(now.getTime() - 86400000);
    let currentFrom = toLocalISOString(from24);
    let currentTo = toLocalISOString(now);
    let currentPage = 0;
    let currentPath = '';
    let currentStatus = '';

    const picker = createPeriodPicker((from, to) => {
        currentFrom = from;
        currentTo = to;
        currentPage = 0;
        loadTraceList();
    });
    listCard.appendChild(picker);

    // Filters
    const filterDiv = document.createElement('div');
    filterDiv.className = 'search-bar';
    filterDiv.innerHTML = `
        <input type="text" id="trace-path-filter" class="search-input" placeholder="Path filter (e.g. /server-a/chain)" style="min-width:200px">
        <select id="trace-status-filter" class="filter-select">
            <option value="">All Status</option>
            <option value="error">Errors Only</option>
        </select>
        <button id="trace-filter-btn" class="search-btn">Filter</button>
    `;
    listCard.appendChild(filterDiv);

    const listWrap = document.createElement('div');
    listWrap.id = 'trace-list-wrap';
    listCard.appendChild(listWrap);

    // --- Event handlers ---
    document.getElementById('txid-search-btn').addEventListener('click', () => {
        const txId = document.getElementById('txid-input').value.trim();
        if (txId) loadTraceDetail(txId);
    });

    document.getElementById('txid-input').addEventListener('keydown', e => {
        if (e.key === 'Enter') document.getElementById('txid-search-btn').click();
    });

    document.getElementById('detail-toggle').addEventListener('change', e => {
        includeDetail = e.target.checked;
    });

    document.getElementById('trace-filter-btn').addEventListener('click', () => {
        currentPath = document.getElementById('trace-path-filter').value.trim();
        currentStatus = document.getElementById('trace-status-filter').value;
        currentPage = 0;
        loadTraceList();
    });

    // --- Load trace detail ---
    async function loadTraceDetail(txId) {
        const card = document.getElementById('trace-detail');
        card.style.display = 'block';
        card.innerHTML = '<div class="loading">Loading...</div>';
        try {
            const res = await fetch(`/api/traces/${txId}?includeDetail=${includeDetail}`);
            if (!res.ok) {
                card.innerHTML = `<div class="empty">Trace not found: ${txId}</div>`;
                return;
            }
            const detail = await res.json();
            renderTraceDetail(card, detail);
        } catch (e) {
            card.innerHTML = '<div class="empty">Failed to load trace</div>';
        }
    }

    function renderTraceDetail(card, detail) {
        expandedHop = null;
        const hops = detail.hops || [];
        if (!hops.length) {
            card.innerHTML = '<div class="empty">No hops found</div>';
            return;
        }

        const firstReq = new Date(hops[0].reqTime).getTime();
        const lastRes = new Date(hops[hops.length - 1].resTime).getTime();
        const totalSpan = lastRes - firstReq || 1;

        let html = `<div class="card-title">Trace: ${detail.txId} (${detail.hopCount} hops, ${detail.totalDuration}ms)</div>`;
        html += '<div class="timeline">';

        hops.forEach((hop, i) => {
            const hopStart = new Date(hop.reqTime).getTime();
            const hopEnd = new Date(hop.resTime).getTime();
            let left = ((hopStart - firstReq) / totalSpan * 100);
            let width = ((hopEnd - hopStart) / totalSpan * 100);
            // Clamp: prevent overflow beyond container
            if (left + width > 100) width = 100 - left;
            if (width < 2) width = 2;
            if (left > 98) left = 98;
            const statusClass = hop.status >= 500 ? 'status-5xx' : hop.status >= 400 ? 'status-4xx' : 'status-2xx';

            html += `<div class="timeline-hop" data-hop="${i}">
                <div class="timeline-hop-label">Hop ${hop.hop}</div>
                <div class="timeline-bar-container">
                    <div class="timeline-bar ${statusClass}" style="left:${left.toFixed(1)}%;width:${width.toFixed(1)}%">${hop.durationMs}ms</div>
                </div>
                <div class="timeline-info">${statusBadge(hop.status)}</div>
            </div>
            <div class="hop-detail" id="hop-detail-${i}" style="display:none"></div>`;
        });
        html += '</div>';
        card.innerHTML = html;

        // Click handlers
        card.querySelectorAll('.timeline-hop').forEach(el => {
            el.addEventListener('click', () => {
                const idx = parseInt(el.dataset.hop);
                const detailEl = document.getElementById(`hop-detail-${idx}`);
                if (expandedHop === idx) {
                    detailEl.style.display = 'none';
                    expandedHop = null;
                } else {
                    card.querySelectorAll('.hop-detail').forEach(d => d.style.display = 'none');
                    renderHopDetail(detailEl, hops[idx]);
                    detailEl.style.display = 'block';
                    expandedHop = idx;
                }
            });
        });
    }

    function renderHopDetail(el, hop) {
        let html = `
            <div class="hop-detail-row"><span class="hop-detail-label">Path</span><span>${hop.path}</span></div>
            <div class="hop-detail-row"><span class="hop-detail-label">Target</span><span>${hop.target || '-'}</span></div>
            <div class="hop-detail-row"><span class="hop-detail-label">Status</span><span>${statusBadge(hop.status)}</span></div>
            <div class="hop-detail-row"><span class="hop-detail-label">Duration</span><span>${formatMs(hop.durationMs)}</span></div>
            <div class="hop-detail-row"><span class="hop-detail-label">Req Time</span><span>${hop.reqTime}</span></div>
            <div class="hop-detail-row"><span class="hop-detail-label">Res Time</span><span>${hop.resTime}</span></div>`;
        if (hop.error) {
            html += `<div class="hop-detail-row"><span class="hop-detail-label">Error</span><span class="badge badge-error">${hop.error}</span></div>`;
        }
        if (hop.requestHeaders) {
            html += `<div style="margin-top:8px"><strong>Request Headers:</strong><div class="hop-body">${prettyFormat(hop.requestHeaders)}</div></div>`;
        }
        if (hop.requestBody) {
            html += `<div style="margin-top:8px"><strong>Request Body:</strong><div class="hop-body">${prettyFormat(hop.requestBody)}</div></div>`;
        }
        if (hop.responseHeaders) {
            html += `<div style="margin-top:8px"><strong>Response Headers:</strong><div class="hop-body">${prettyFormat(hop.responseHeaders)}</div></div>`;
        }
        if (hop.responseBody) {
            html += `<div style="margin-top:8px"><strong>Response Body:</strong><div class="hop-body">${prettyFormat(hop.responseBody)}</div></div>`;
        }
        el.innerHTML = html;
    }

    function prettyFormat(str) {
        try {
            const parsed = JSON.parse(str);
            return escapeHtml(JSON.stringify(parsed, null, 2));
        } catch (e) {
            return escapeHtml(str);
        }
    }

    // --- Load trace list ---
    async function loadTraceList() {
        listWrap.innerHTML = '<div class="loading">Loading...</div>';
        try {
            let url = `/api/traces?from=${encodeURIComponent(currentFrom)}&to=${encodeURIComponent(currentTo)}&page=${currentPage}&size=20`;
            if (currentPath) url += `&path=${encodeURIComponent(currentPath)}`;
            if (currentStatus) url += `&status=${encodeURIComponent(currentStatus)}`;

            const res = await fetch(url);
            const page = await res.json();
            renderTraceList(page);
        } catch (e) {
            listWrap.innerHTML = '<div class="empty">Failed to load traces</div>';
        }
    }

    function formatDateTime(isoStr) {
        if (!isoStr) return '-';
        const d = new Date(isoStr);
        const pad = (n, len = 2) => String(n).padStart(len, '0');
        return `${d.getUTCFullYear()}-${pad(d.getUTCMonth()+1)}-${pad(d.getUTCDate())} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`;
    }

    function renderTraceList(pageData) {
        const items = pageData.content || [];
        if (!items.length) {
            listWrap.innerHTML = '<div class="empty">No traces found</div>';
            return;
        }

        let html = '<table><thead><tr><th>Transaction ID</th><th>Req Time</th></tr></thead><tbody>';
        items.forEach(item => {
            const txId = item.txId || item;
            const reqTime = item.reqTime || '';
            html += `<tr class="trace-row" data-txid="${txId}" style="cursor:pointer"><td>${txId}</td><td>${formatDateTime(reqTime)}</td></tr>`;
        });
        html += '</tbody></table>';

        // Pagination (VIA_DTO: page info nested under "page")
        const pageMeta = pageData.page || pageData;
        const totalPages = pageMeta.totalPages || 1;
        html += '<div class="pagination">';
        html += `<button class="page-btn" data-page="${currentPage - 1}" ${currentPage === 0 ? 'disabled' : ''}>&lt;</button>`;
        for (let i = 0; i < Math.min(totalPages, 10); i++) {
            html += `<button class="page-btn ${i === currentPage ? 'active' : ''}" data-page="${i}">${i + 1}</button>`;
        }
        html += `<button class="page-btn" data-page="${currentPage + 1}" ${currentPage >= totalPages - 1 ? 'disabled' : ''}>&gt;</button>`;
        html += '</div>';

        listWrap.innerHTML = html;

        listWrap.querySelectorAll('.trace-row').forEach(row => {
            row.addEventListener('click', () => {
                document.getElementById('txid-input').value = row.dataset.txid;
                loadTraceDetail(row.dataset.txid);
            });
        });

        listWrap.querySelectorAll('.page-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                if (btn.disabled) return;
                currentPage = parseInt(btn.dataset.page);
                loadTraceList();
            });
        });
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    loadTraceList();
});
