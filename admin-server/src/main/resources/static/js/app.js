// --- Router ---
const pages = {};
let currentPage = null;

function registerPage(name, renderFn) {
    pages[name] = renderFn;
}

function navigate(page) {
    if (currentPage === page) return;
    currentPage = page;
    document.querySelectorAll('.nav-link').forEach(el => {
        el.classList.toggle('active', el.dataset.page === page);
    });
    const app = document.getElementById('app');
    app.innerHTML = '<div class="loading">Loading...</div>';
    if (pages[page]) pages[page](app);
}

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', e => {
            e.preventDefault();
            navigate(link.dataset.page);
        });
    });
    navigate('stats');
});

// --- Period Picker (shared component) ---
function createPeriodPicker(onApply) {
    const presets = [
        { label: '1h', hours: 1 },
        { label: '6h', hours: 6 },
        { label: '24h', hours: 24 },
        { label: '7d', hours: 168 },
        { label: '30d', hours: 720 },
    ];

    const div = document.createElement('div');
    div.className = 'period-picker';

    presets.forEach(p => {
        const btn = document.createElement('button');
        btn.className = 'period-btn';
        btn.textContent = p.label;
        if (p.hours === 24) btn.classList.add('active');
        btn.addEventListener('click', () => {
            div.querySelectorAll('.period-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const to = new Date();
            const from = new Date(to.getTime() - p.hours * 3600000);
            onApply(toLocalISOString(from), toLocalISOString(to));
        });
        div.appendChild(btn);
    });

    // Custom range
    const custom = document.createElement('div');
    custom.className = 'period-custom';
    custom.innerHTML = `
        <input type="datetime-local" id="period-from">
        <span>~</span>
        <input type="datetime-local" id="period-to">
        <button class="search-btn" id="period-apply">Apply</button>
    `;
    div.appendChild(custom);

    // Set defaults (last 24h)
    setTimeout(() => {
        const now = new Date();
        const from24 = new Date(now.getTime() - 86400000);
        const fromInput = div.querySelector('#period-from');
        const toInput = div.querySelector('#period-to');
        if (fromInput) fromInput.value = toLocalDatetimeString(from24);
        if (toInput) toInput.value = toLocalDatetimeString(now);

        div.querySelector('#period-apply').addEventListener('click', () => {
            div.querySelectorAll('.period-btn').forEach(b => b.classList.remove('active'));
            const f = toLocalISOString(new Date(fromInput.value));
            const t = toLocalISOString(new Date(toInput.value));
            onApply(f, t);
        });
    }, 0);

    return div;
}

function toLocalDatetimeString(date) {
    const pad = n => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// --- Time Utility ---
// DB에 KST 시각이 UTC offset으로 저장되므로, 로컬(KST) 시각을 UTC처럼 포맷하여 전송
function toLocalISOString(date) {
    const pad = (n, d = 2) => String(n).padStart(d, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}Z`;
}

// --- Utility ---
function statusBadge(status) {
    if (status >= 500) return `<span class="badge badge-error">${status}</span>`;
    if (status >= 400) return `<span class="badge badge-warn">${status}</span>`;
    return `<span class="badge badge-success">${status}</span>`;
}

function formatMs(ms) {
    return ms != null ? `${ms}ms` : '-';
}

function formatRate(rate) {
    return rate != null ? `${rate.toFixed(2)}%` : '-';
}
