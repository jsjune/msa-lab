registerPage('policies', async function(app) {
    app.innerHTML = '';

    const card = document.createElement('div');
    card.className = 'card';
    card.innerHTML = `
        <div class="card-title">Body Collection Policies</div>
        <div class="form-row">
            <input type="text" id="new-pattern" class="form-input" placeholder="Path pattern (e.g. /server-a/**)">
            <button id="add-policy-btn" class="btn btn-primary">Add Policy</button>
        </div>
        <div id="policy-list"></div>
    `;
    app.appendChild(card);

    document.getElementById('add-policy-btn').addEventListener('click', addPolicy);
    document.getElementById('new-pattern').addEventListener('keydown', e => {
        if (e.key === 'Enter') addPolicy();
    });

    async function loadPolicies() {
        const wrap = document.getElementById('policy-list');
        wrap.innerHTML = '<div class="loading">Loading...</div>';
        try {
            const res = await fetch('/api/policies');
            const policies = await res.json();
            renderPolicies(policies);
        } catch (e) {
            wrap.innerHTML = '<div class="empty">Failed to load policies</div>';
        }
    }

    function renderPolicies(policies) {
        const wrap = document.getElementById('policy-list');
        if (!policies.length) {
            wrap.innerHTML = '<div class="empty">No policies configured</div>';
            return;
        }

        let html = '<table><thead><tr><th>Path Pattern</th><th>Enabled</th><th>Created</th><th>Action</th></tr></thead><tbody>';
        policies.forEach(p => {
            const createdAt = p.createdAt ? new Date(p.createdAt).toLocaleString('ko-KR') : '-';
            html += `<tr>
                <td>${p.pathPattern}</td>
                <td>
                    <label class="toggle">
                        <input type="checkbox" ${p.enabled ? 'checked' : ''} data-id="${p.id}" class="toggle-input">
                        <span class="toggle-slider"></span>
                    </label>
                </td>
                <td>${createdAt}</td>
                <td><button class="btn btn-danger btn-sm delete-btn" data-id="${p.id}">Delete</button></td>
            </tr>`;
        });
        html += '</tbody></table>';
        wrap.innerHTML = html;

        wrap.querySelectorAll('.toggle-input').forEach(input => {
            input.addEventListener('change', async () => {
                const id = input.dataset.id;
                await fetch(`/api/policies/${id}/toggle`, { method: 'PATCH' });
                loadPolicies();
            });
        });

        wrap.querySelectorAll('.delete-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                const id = btn.dataset.id;
                if (!confirm('Delete this policy?')) return;
                await fetch(`/api/policies/${id}`, { method: 'DELETE' });
                loadPolicies();
            });
        });
    }

    async function addPolicy() {
        const input = document.getElementById('new-pattern');
        const pattern = input.value.trim();
        if (!pattern) return;

        try {
            const res = await fetch('/api/policies', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pathPattern: pattern })
            });
            if (res.status === 409) {
                alert('Pattern already exists');
                return;
            }
            if (!res.ok) {
                const err = await res.json();
                alert(err.detail || 'Failed to add policy');
                return;
            }
            input.value = '';
            loadPolicies();
        } catch (e) {
            alert('Failed to add policy');
        }
    }

    loadPolicies();
});
