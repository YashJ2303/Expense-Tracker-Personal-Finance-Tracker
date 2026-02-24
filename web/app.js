/* ═══════════════════════════════════════════════════════
   Expense Tracker — Premium App Logic
   ═══════════════════════════════════════════════════════ */

const API = '';
let token = localStorage.getItem('et_token');
let username = localStorage.getItem('et_user');
let currentTheme = localStorage.getItem('et_theme') || 'dark';
let trendChartInstance = null;
let allocChartInstance = null;
let dashTrendInstance = null;
let dashAllocInstance = null;

// ─── Session Timeout (30 min) ────────────────────────
let lastActivity = Date.now();
const SESSION_TIMEOUT = 30 * 60 * 1000; // 30 minutes

function resetActivity() { lastActivity = Date.now(); }
document.addEventListener('click', resetActivity);
document.addEventListener('keydown', resetActivity);

setInterval(() => {
    if (!token) return;
    const elapsed = Date.now() - lastActivity;
    if (elapsed > SESSION_TIMEOUT - 5 * 60 * 1000 && elapsed < SESSION_TIMEOUT) {
        // Warning at 25 min
    }
    if (elapsed >= SESSION_TIMEOUT) {
        logout();
        toast('Session timed out due to inactivity', 'error');
    }
}, 60000);

// ─── API Helper ──────────────────────────────────────

async function api(path, opts = {}) {
    const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const res = await fetch(API + path, { ...opts, headers });

    if (res.status === 401 && !path.includes('/api/login') && !path.includes('/api/signup')) {
        token = null;
        username = null;
        localStorage.removeItem('et_token');
        localStorage.removeItem('et_user');
        showScreen('auth-screen');
        toast('Session expired. Please log in again.', 'error');
        throw new Error('Session expired');
    }

    if (res.headers.get('Content-Type')?.includes('text/csv')) {
        return res.blob();
    }

    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Request failed');
    return data;
}

// ─── Toast Notifications ─────────────────────────────

function toast(msg, type = 'info') {
    const container = document.getElementById('toast-container');
    const el = document.createElement('div');
    el.className = `toast ${type}`;

    let icon = 'info';
    if (type === 'success') icon = 'check';
    if (type === 'error') icon = 'error';

    el.innerHTML = `
        <svg class="icon-sm"><use href="#i-${icon}"/></svg>
        <span>${msg}</span>
    `;
    container.appendChild(el);
    setTimeout(() => {
        el.classList.add('fade-out');
        setTimeout(() => el.remove(), 350);
    }, 4000);
}

// ─── Screen & Page Navigation ────────────────────────

function showScreen(id) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(id).classList.add('active');
}

function showPage(name) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    const page = document.getElementById('page-' + name);
    if (page) page.classList.add('active');

    document.querySelectorAll('.nav-link').forEach(l => {
        l.classList.remove('active');
        if (l.dataset.page === name) l.classList.add('active');
    });

    closeMobileSidebar();

    if (name === 'dashboard') loadDashboard();
    else if (name === 'expenses') loadExpenses();
    else if (name === 'budgets') loadBudgets();
    else if (name === 'trends') loadTrends();
    else if (name === 'report') loadReport();
    else if (name === 'categories') loadCategories();
    else if (name === 'recurring') loadRecurring();
    else if (name === 'reminders') loadReminders();
}

// ─── Auth ────────────────────────────────────────────

document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById(btn.dataset.tab + '-form').classList.add('active');
    });
});

document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('login-submit-btn');
    setLoading(btn, true);
    try {
        const data = await api('/api/login', {
            method: 'POST',
            body: JSON.stringify({
                username: document.getElementById('login-user').value,
                password: document.getElementById('login-pass').value
            })
        });
        saveSession(data.token, data.username);
        enterApp();
        toast('Welcome back, ' + username + '!', 'success');
    } catch (err) {
        toast(err.message, 'error');
    } finally {
        setLoading(btn, false);
    }
});

document.getElementById('signup-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('signup-submit-btn');
    setLoading(btn, true);
    try {
        const data = await api('/api/signup', {
            method: 'POST',
            body: JSON.stringify({
                username: document.getElementById('signup-user').value,
                password: document.getElementById('signup-pass').value
            })
        });
        saveSession(data.token, data.username);
        enterApp();
        toast('Account created! Welcome, ' + username + '!', 'success');
    } catch (err) {
        toast(err.message, 'error');
    } finally {
        setLoading(btn, false);
    }
});

function saveSession(t, u) {
    token = t;
    username = u;
    localStorage.setItem('et_token', t);
    localStorage.setItem('et_user', u);
}

async function enterApp() {
    try {
        await api('/api/categories');
    } catch (err) {
        return;
    }
    document.getElementById('user-name').textContent = username;
    document.getElementById('user-avatar').textContent = username.charAt(0).toUpperCase();
    applyTheme(currentTheme);
    showScreen('app-screen');
    initDateSelectors();
    showPage('dashboard');
}

function logout() {
    token = null;
    username = null;
    localStorage.removeItem('et_token');
    localStorage.removeItem('et_user');
    showScreen('auth-screen');
    toast('Logged out successfully', 'info');
}

document.getElementById('logout-btn').addEventListener('click', logout);

// ─── Theme Toggle ────────────────────────────────────

function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    const icon = document.getElementById('theme-icon');
    const label = document.getElementById('theme-label');
    if (icon) icon.innerHTML = theme === 'dark' ? '<svg class="icon"><use href="#i-moon"/></svg>' : '<svg class="icon"><use href="#i-sun"/></svg>';
    if (label) label.textContent = theme === 'dark' ? 'Dark Mode' : 'Light Mode';
    localStorage.setItem('et_theme', theme);
}

document.getElementById('theme-toggle').addEventListener('click', (e) => {
    e.preventDefault();
    currentTheme = currentTheme === 'dark' ? 'light' : 'dark';
    applyTheme(currentTheme);
});

// ─── Dashboard ───────────────────────────────────────

async function loadDashboard() {
    renderSkeleton('recent-body', 3, 3);
    try {
        const d = await api('/api/dashboard');

        const safeSetText = (id, text) => {
            const el = document.getElementById(id);
            if (el) el.textContent = text;
        };

        safeSetText('stat-total', '₹ ' + (d.monthlyTotal || 0).toFixed(2));
        safeSetText('stat-count', d.expenseCount || 0);
        safeSetText('stat-top-cat', d.topCategory || '—');

        // Note: stat-budget was removed in the Tallygraphs layout update.
        // If it's needed again, it should be added to the HTML first.

        // Alerts
        const alertsDiv = document.getElementById('budget-alerts');
        if (alertsDiv) {
            if (d.budgetAlerts && d.budgetAlerts.length > 0) {
                alertsDiv.innerHTML = d.budgetAlerts.map(a => `
                    <div class="budget-alert-item">
                        <span>${a.percent >= 100 ? '🚨' : '⚠️'}</span>
                        <span>${a.percent >= 100 ? 'Over budget' : 'Nearing limit'} in <strong>${esc(a.category)}</strong> (${a.percent.toFixed(0)}%)</span>
                    </div>
                `).join('');
            } else {
                alertsDiv.innerHTML = '<div class="empty-state"><div class="empty-state-icon">✅</div>All budgets on track!</div>';
            }
        }

        // Recent table
        const tbody = document.getElementById('recent-body');
        if (tbody) {
            if (!d.recent || d.recent.length === 0) {
                tbody.innerHTML = '<tr><td colspan="3"><div class="empty-state"><div class="empty-state-icon">📝</div>No expenses yet</div></td></tr>';
            } else {
                tbody.innerHTML = d.recent.map(e => `
                    <tr>
                        <td><span class="cat-chip">${esc(e.category)}</span></td>
                        <td class="amount-cell">₹ ${e.amount.toFixed(2)}</td>
                        <td>${formatDate(e.date)}</td>
                    </tr>
                `).join('');
            }
        }

        loadPredictions();
        renderDashboardCharts();
    } catch (err) {
        if (err.message !== 'Session expired') toast(err.message, 'error');
    }
}

async function loadPredictions() {
    const list = document.getElementById('predictions-list');
    try {
        const p = await api('/api/predictions');
        if (p.categories && p.categories.length > 0) {
            list.innerHTML = p.categories.map(c => `
                <div class="prediction-item">
                    <span>${esc(c.category)}</span>
                    <span style="font-weight:700;color:var(--accent-light)">~ ₹ ${c.predicted.toFixed(2)}</span>
                </div>
            `).join('') + `
                <div class="prediction-item" style="font-weight:700;border-top:2px solid var(--border-light);margin-top:4px;padding-top:12px">
                    <span>Expected Total</span>
                    <span style="color:var(--accent-light)">₹ ${p.totalPredicted.toFixed(2)}</span>
                </div>
            `;
        } else {
            list.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔮</div>Add more data for predictions</div>';
        }
    } catch (e) { console.error(e); }
}

// ─── Expenses ────────────────────────────────────────

async function loadExpenses(filters = {}) {
    const tbody = document.getElementById('expenses-body');
    if (!tbody) return;
    renderSkeleton('expenses-body', 5, 7);

    try {
        const cats = await api('/api/categories');
        const sel = document.getElementById('filter-cat');
        if (sel) {
            const currentVal = sel.value;
            sel.innerHTML = '<option value="">All</option>' +
                cats.map(c => `<option value="${esc(c)}">${esc(c)}</option>`).join('');
            sel.value = currentVal;
        }

        const params = new URLSearchParams();
        Object.entries(filters).forEach(([k, v]) => { if (v) params.set(k, v); });

        const expenses = await api('/api/expenses' + (params.toString() ? '?' + params.toString() : ''));

        const countBadge = document.getElementById('expense-count-badge');
        if (countBadge) countBadge.textContent = expenses.length;

        if (expenses.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7"><div class="empty-state"><div class="empty-state-icon">💳</div>No expenses found</div></td></tr>';
        } else {
            tbody.innerHTML = expenses.map(e => `
                <tr>
                    <td>${e.id}</td>
                    <td><span class="cat-chip">${esc(e.category)}</span></td>
                    <td class="amount-cell">${e.amount.toFixed(2)}</td>
                    <td><span class="currency-tag">${e.currency || 'INR'}</span></td>
                    <td>${formatDate(e.date)}</td>
                    <td>${e.receiptPath ? '<a href="' + esc(e.receiptPath) + '" target="_blank">📎</a>' : '—'}</td>
                    <td><button class="btn-icon" onclick="deleteExpense(${e.id})">🗑️</button></td>
                </tr>
            `).join('');
        }
    } catch (err) {
        if (err.message !== 'Session expired') toast(err.message, 'error');
        tbody.innerHTML = '<tr><td colspan="7"><div class="empty-state">Failed to load expenses</div></td></tr>';
    }
}

async function deleteExpense(id) {
    if (!confirm('Delete this expense?')) return;
    try {
        await api(`/api/expenses?id=${id}`, { method: 'DELETE' });
        toast('Expense deleted', 'info');
        loadExpenses();
    } catch (err) { toast(err.message, 'error'); }
}

// Filter & Search
document.getElementById('search-btn')?.addEventListener('click', () => {
    loadExpenses({
        category: document.getElementById('filter-cat').value,
        keyword: document.getElementById('filter-keyword').value,
        minAmount: document.getElementById('filter-min').value,
        maxAmount: document.getElementById('filter-max').value,
        startDate: document.getElementById('filter-start').value,
        endDate: document.getElementById('filter-end').value
    });
});

// CSV Export
document.getElementById('export-csv-btn')?.addEventListener('click', async () => {
    try {
        const blob = await api('/api/export');
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'expenses.csv';
        a.click();
        URL.revokeObjectURL(url);
        toast('CSV exported!', 'success');
    } catch (err) { toast(err.message, 'error'); }
});

// ─── Budgets ─────────────────────────────────────────

async function loadBudgets() {
    const grid = document.getElementById('budgets-grid');
    grid.innerHTML = '<p class="placeholder-text">Loading...</p>';
    try {
        const [budgets, cats] = await Promise.all([
            api('/api/budgets'),
            api('/api/categories')
        ]);

        const budgetCat = document.getElementById('budget-cat');
        budgetCat.innerHTML = cats.map(c => `<option value="${esc(c)}">${esc(c)}</option>`).join('');

        if (budgets.length === 0) {
            grid.innerHTML = '<div class="empty-state" style="grid-column:1/-1"><div class="empty-state-icon">🎯</div>No budgets set yet</div>';
        } else {
            grid.innerHTML = budgets.map(b => {
                const pct = b.limit > 0 ? Math.min(b.spent / b.limit * 100, 150) : 0;
                const over = pct >= 100;
                return `
                    <div class="budget-card">
                        <div class="budget-card-header">
                            <h4>${esc(b.category)}</h4>
                            <button class="btn-icon" onclick="removeBudget('${esc(b.category)}')">🗑️</button>
                        </div>
                        <div class="progress-bar">
                            <div class="progress-fill ${over ? 'over' : ''}" style="width: ${Math.min(pct, 100)}%"></div>
                        </div>
                        <div class="budget-stats">
                            <span>₹${b.spent.toFixed(0)} / ₹${b.limit.toFixed(0)}</span>
                            <span style="color:${over ? 'var(--danger)' : 'var(--success)'}">${pct.toFixed(0)}%</span>
                        </div>
                    </div>
                `;
            }).join('');
        }
    } catch (err) { toast(err.message, 'error'); }
}

document.getElementById('set-budget-btn')?.addEventListener('click', async () => {
    const cat = document.getElementById('budget-cat').value;
    const limit = parseFloat(document.getElementById('budget-limit').value);
    if (!cat || isNaN(limit) || limit <= 0) { toast('Select a category and enter a valid limit', 'error'); return; }
    try {
        await api('/api/budgets', {
            method: 'POST',
            body: JSON.stringify({ category: cat, limit: limit })
        });
        document.getElementById('budget-limit').value = '';
        toast('Budget set!', 'success');
        loadBudgets();
    } catch (err) { toast(err.message, 'error'); }
});

async function removeBudget(cat) {
    if (!confirm(`Remove budget for ${cat}?`)) return;
    try {
        await api(`/api/budgets?category=${encodeURIComponent(cat)}`, { method: 'DELETE' });
        toast('Budget removed', 'info');
        loadBudgets();
    } catch (err) { toast(err.message, 'error'); }
}

// ─── Recurring ───────────────────────────────────────

async function loadRecurring() {
    const list = document.getElementById('recurring-list');
    list.innerHTML = '<p class="placeholder-text">Loading...</p>';
    try {
        const [schedules, cats] = await Promise.all([
            api('/api/recurring'),
            api('/api/categories')
        ]);

        const recCat = document.getElementById('rec-cat');
        recCat.innerHTML = cats.map(c => `<option value="${esc(c)}">${esc(c)}</option>`).join('');

        if (schedules.length === 0) {
            list.innerHTML = '<div class="empty-state" style="grid-column:1/-1"><div class="empty-state-icon">🔄</div>No recurring expenses</div>';
        } else {
            list.innerHTML = schedules.map(s => `
                <div class="recurring-card">
                    <h4>${esc(s.description)} <button class="btn-icon" onclick="deleteRecurring(${s.id})" style="float:right">🗑️</button></h4>
                    <div class="meta"><strong>₹ ${s.amount.toFixed(2)}</strong> • ${esc(s.category)}</div>
                    <div class="meta">Interval: <strong>${s.interval}</strong></div>
                    <div class="meta">Start: <strong>${s.startDate}</strong></div>
                </div>
            `).join('');
        }
    } catch (err) { toast(err.message, 'error'); }
}

async function deleteRecurring(id) {
    if (!confirm('Cancel this recurring schedule?')) return;
    try {
        await api(`/api/recurring?id=${id}`, { method: 'DELETE' });
        toast('Schedule cancelled', 'info');
        loadRecurring();
    } catch (err) { toast(err.message, 'error'); }
}

document.getElementById('add-recurring-btn')?.addEventListener('click', () => {
    document.getElementById('rec-start').value = new Date().toISOString().split('T')[0];
    document.getElementById('add-recurring-modal').classList.add('show');
});

document.getElementById('submit-recurring-btn')?.addEventListener('click', async () => {
    try {
        await api('/api/recurring', {
            method: 'POST',
            body: JSON.stringify({
                description: document.getElementById('rec-desc').value,
                amount: document.getElementById('rec-amount').value,
                category: document.getElementById('rec-cat').value,
                interval: document.getElementById('rec-interval').value,
                startDate: document.getElementById('rec-start').value
            })
        });
        toast('Recurring expense added!', 'success');
        document.getElementById('add-recurring-modal').classList.remove('show');
        loadRecurring();
    } catch (err) { toast(err.message, 'error'); }
});

// ─── Reminders ───────────────────────────────────────

async function loadReminders() {
    const list = document.getElementById('reminders-list');
    if (!list) return;
    list.innerHTML = '<p class="placeholder-text">Loading...</p>';
    try {
        const reminders = await api('/api/reminders');
        if (reminders.length === 0) {
            list.innerHTML = '<div class="empty-state" style="grid-column:1/-1"><div class="empty-state-icon">🔔</div>No reminders yet</div>';
        } else {
            list.innerHTML = reminders.map(r => `
                <div class="reminder-card">
                    <h4>${esc(r.title)} <button class="btn-icon" onclick="deleteReminder(${r.id})" style="float:right">🗑️</button></h4>
                    <div class="due-date">📅 Due: ${r.dueDate}</div>
                    <div class="notes">${r.notes ? esc(r.notes) : ''}</div>
                </div>
            `).join('');
        }
    } catch (err) {
        console.error('Reminders error:', err);
        list.innerHTML = '<div class="empty-state">Failed to load reminders</div>';
    }
}

async function deleteReminder(id) {
    if (!confirm('Delete this reminder?')) return;
    try {
        await api(`/api/reminders?id=${id}`, { method: 'DELETE' });
        toast('Reminder deleted', 'info');
        loadReminders();
    } catch (err) { toast(err.message, 'error'); }
}

document.getElementById('add-reminder-btn')?.addEventListener('click', () => {
    document.getElementById('reminder-due').value = new Date().toISOString().split('T')[0];
    document.getElementById('add-reminder-modal').classList.add('show');
});

document.getElementById('submit-reminder-btn')?.addEventListener('click', async () => {
    const title = document.getElementById('reminder-title').value.trim();
    const due = document.getElementById('reminder-due').value;
    if (!title || !due) { toast('Title and due date are required', 'error'); return; }
    try {
        await api('/api/reminders', {
            method: 'POST',
            body: JSON.stringify({
                title: title,
                dueDate: due,
                notes: document.getElementById('reminder-notes').value
            })
        });
        toast('Reminder added!', 'success');
        document.getElementById('add-reminder-modal').classList.remove('show');
        document.getElementById('reminder-title').value = '';
        document.getElementById('reminder-notes').value = '';
        loadReminders();
    } catch (err) { toast(err.message, 'error'); }
});

// ─── Trends & Heatmap ────────────────────────────────

trendChartInstance = null;

async function loadTrends() {
    renderTrendChart();
    renderHeatmap();
}

async function renderTrendChart() {
    try {
        const trend = await api('/api/trends?months=6');
        const canvas = document.getElementById('trend-chart');
        if (!canvas) return; // Silent return if element not found
        const ctx = canvas.getContext('2d');

        if (trendChartInstance) trendChartInstance.destroy();

        const monthNames = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const labels = trend.map(t => monthNames[t.month] + ' ' + t.year);
        const values = trend.map(t => t.total);

        const gradient = ctx.createLinearGradient(0, 0, 0, 400);
        gradient.addColorStop(0, 'rgba(188, 19, 254, 0.4)');
        gradient.addColorStop(1, 'rgba(188, 19, 254, 0)');

        trendChartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Monthly Spending',
                    data: values,
                    borderColor: '#bc13fe',
                    backgroundColor: gradient,
                    borderWidth: 3,
                    fill: true,
                    tension: 0.4,
                    pointBackgroundColor: '#bc13fe',
                    pointBorderColor: '#fff',
                    pointBorderWidth: 2,
                    pointRadius: 4,
                    pointHoverRadius: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: 'rgba(18, 20, 29, 0.95)',
                        borderColor: 'rgba(188, 19, 254, 0.3)',
                        borderWidth: 1,
                        titleFont: { family: 'Outfit, Inter', weight: 'bold' },
                        bodyFont: { family: 'Outfit, Inter' },
                        padding: 12,
                        displayColors: false,
                        callbacks: {
                            label: ctx => '₹ ' + ctx.parsed.y.toLocaleString()
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false },
                        ticks: { color: '#6b7280', font: { family: 'Outfit, Inter', size: 12 } }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: 'rgba(255, 255, 255, 0.03)' },
                        ticks: {
                            color: '#6b7280',
                            font: { family: 'Outfit, Inter', size: 12 },
                            callback: v => '₹' + v.toLocaleString()
                        }
                    }
                }
            }
        });
    } catch (e) {
        console.error('Trend chart error:', e);
        const canvas = document.getElementById('trend-chart');
        if (canvas && canvas.parentElement) {
            canvas.parentElement.innerHTML = '<div class="empty-state">Failed to load trend data</div>';
        }
    }
}

async function renderHeatmap() {
    const grid = document.getElementById('heatmap-grid');
    if (!grid) return;
    const mEl = document.getElementById('heatmap-month');
    const yEl = document.getElementById('heatmap-year');
    if (!mEl || !yEl) return;
    const m = mEl.value;
    const y = yEl.value;
    grid.innerHTML = '';

    try {
        const daily = await api(`/api/daily-spending?month=${m}&year=${y}`);
        const daysInMonth = new Date(y, m, 0).getDate();
        const startDay = new Date(y, m - 1, 1).getDay();

        const dayLabels = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];
        dayLabels.forEach(l => {
            const el = document.createElement('div');
            el.className = 'heatmap-cell';
            el.style.fontWeight = '700';
            el.style.color = 'var(--text-muted)';
            el.textContent = l;
            grid.appendChild(el);
        });

        for (let i = 0; i < startDay; i++) {
            const empty = document.createElement('div');
            empty.className = 'heatmap-cell';
            grid.appendChild(empty);
        }

        const max = Math.max(...Object.values(daily), 1);

        for (let d = 1; d <= daysInMonth; d++) {
            const amt = daily[d] || 0;
            const el = document.createElement('div');
            el.className = 'heatmap-cell';
            el.textContent = d;
            el.title = `Day ${d}: ₹${amt.toFixed(2)}`;

            if (amt > 0) {
                const pct = amt / max;
                const opacity = 0.1 + pct * 0.8;
                el.style.background = `rgba(124,108,240,${opacity})`;
                el.style.color = pct > 0.5 ? '#fff' : 'var(--text-primary)';
            } else {
                el.style.background = 'var(--bg-input)';
            }
            grid.appendChild(el);
        }
    } catch (e) { console.error(e); }
}

document.getElementById('heatmap-btn')?.addEventListener('click', () => renderHeatmap());

// ─── Reports (Auto-load) ────────────────────────────

const CHART_COLORS = ['#7c6cf0', '#34d399', '#fbbf24', '#f87171', '#3b82f6', '#c084fc', '#22d3ee', '#f97316', '#a78bfa', '#fb923c'];
let pieChartInstance = null;

async function loadReport() {
    const month = document.getElementById('report-month').value;
    const year = document.getElementById('report-year').value;
    try {
        const data = await api(`/api/report?month=${month}&year=${year}`);

        // Total
        document.getElementById('report-total').innerHTML = `Total: <strong>₹ ${data.total.toFixed(2)}</strong>`;

        // Bars
        const bars = document.getElementById('report-bars');
        if (data.breakdown.length === 0) {
            bars.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No data for this period</div>';
        } else {
            const maxAmt = Math.max(...data.breakdown.map(b => b.amount));
            bars.innerHTML = data.breakdown.map((b, i) => `
                <div class="bar-item">
                    <div class="bar-header">
                        <span class="bar-label">${esc(b.category)}</span>
                        <span class="bar-amount">₹ ${b.amount.toFixed(2)}</span>
                    </div>
                    <div class="bar-track">
                        <div class="bar-fill" style="width: ${(b.amount / maxAmt * 100).toFixed(1)}%; background: ${CHART_COLORS[i % CHART_COLORS.length]}"></div>
                    </div>
                </div>
            `).join('');
        }

        // Pie Chart using Chart.js
        const canvas = document.getElementById('report-pie');
        const ctx = canvas.getContext('2d');

        if (pieChartInstance) pieChartInstance.destroy();

        if (data.breakdown.length > 0 && data.total > 0) {
            pieChartInstance = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: data.breakdown.map(b => b.category),
                    datasets: [{
                        data: data.breakdown.map(b => b.amount),
                        backgroundColor: CHART_COLORS.slice(0, data.breakdown.length),
                        borderWidth: 0,
                        hoverOffset: 8
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    cutout: '60%',
                    plugins: {
                        legend: {
                            position: 'bottom',
                            labels: {
                                color: '#8b95a5',
                                font: { family: 'Inter', size: 12 },
                                padding: 16,
                                usePointStyle: true
                            }
                        },
                        tooltip: {
                            backgroundColor: 'rgba(17,25,40,0.9)',
                            titleFont: { family: 'Inter' },
                            bodyFont: { family: 'Inter' },
                            callbacks: {
                                label: ctx => {
                                    const pct = (ctx.parsed / data.total * 100).toFixed(1);
                                    return `${ctx.label}: ₹${ctx.parsed.toFixed(2)} (${pct}%)`;
                                }
                            }
                        }
                    }
                }
            });
        }
    } catch (err) {
        if (err.message !== 'Session expired') toast(err.message, 'error');
    }
}

// Auto-reload report when month/year changes
document.getElementById('report-month')?.addEventListener('change', loadReport);
document.getElementById('report-year')?.addEventListener('change', loadReport);

// ─── Categories ──────────────────────────────────────

async function loadCategories() {
    const list = document.getElementById('cat-list');
    if (!list) return;
    list.innerHTML = '<p class="placeholder-text">Loading...</p>';
    try {
        const cats = await api('/api/categories');
        if (cats.length === 0) {
            list.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🏷️</div>No categories yet</div>';
        } else {
            list.innerHTML = cats.map(c => `
                <div class="cat-card">
                    <span class="cat-name">${esc(c)}</span>
                    <button class="cat-del" onclick="deleteCategory('${esc(c)}')">&times;</button>
                </div>
            `).join('');
        }
    } catch (err) {
        console.error('Categories error:', err);
        list.innerHTML = '<div class="empty-state">Failed to load categories</div>';
    }
}

async function deleteCategory(name) {
    if (!confirm(`Delete category "${name}"?`)) return;
    try {
        await api(`/api/categories?name=${encodeURIComponent(name)}`, { method: 'DELETE' });
        toast('Category deleted', 'info');
        loadCategories();
    } catch (err) { toast(err.message, 'error'); }
}

document.getElementById('add-cat-btn')?.addEventListener('click', async () => {
    const input = document.getElementById('new-cat-name');
    const name = input.value.trim();
    if (!name) { toast('Enter a category name', 'error'); return; }
    try {
        await api('/api/categories', {
            method: 'POST',
            body: JSON.stringify({ name })
        });
        input.value = '';
        toast('Category added!', 'success');
        loadCategories();
    } catch (err) { toast(err.message, 'error'); }
});

// ─── Profile ─────────────────────────────────────────

document.getElementById('profile-link')?.addEventListener('click', (e) => {
    e.preventDefault();
    document.getElementById('profile-modal').classList.add('show');
});

document.getElementById('change-pass-btn')?.addEventListener('click', async () => {
    const btn = document.getElementById('change-pass-btn');
    setLoading(btn, true);
    try {
        await api('/api/profile', {
            method: 'POST',
            body: JSON.stringify({
                currentPassword: document.getElementById('current-pass').value,
                newPassword: document.getElementById('new-pass').value
            })
        });
        toast('Password updated!', 'success');
        document.getElementById('profile-modal').classList.remove('show');
        document.getElementById('current-pass').value = '';
        document.getElementById('new-pass').value = '';
    } catch (err) {
        toast(err.message, 'error');
    } finally {
        setLoading(btn, false);
    }
});

// ─── Add Expense Modal ───────────────────────────────

function openAddExpenseModal() {
    api('/api/categories').then(cats => {
        const sel = document.getElementById('expense-cat');
        sel.innerHTML = '<option value="" disabled selected>Select category</option>' +
            cats.map(c => `<option value="${esc(c)}">${esc(c)}</option>`).join('');
        document.getElementById('add-expense-modal').classList.add('show');
    });
}

document.getElementById('submit-expense-btn')?.addEventListener('click', async () => {
    const btn = document.getElementById('submit-expense-btn');
    setLoading(btn, true);
    try {
        await api('/api/expenses', {
            method: 'POST',
            body: JSON.stringify({
                category: document.getElementById('expense-cat').value,
                amount: document.getElementById('expense-amount').value,
                currency: document.getElementById('expense-currency').value,
                receiptPath: document.getElementById('expense-receipt').value
            })
        });
        toast('Expense added!', 'success');
        document.getElementById('add-expense-modal').classList.remove('show');
        document.getElementById('expense-amount').value = '';
        document.getElementById('expense-receipt').value = '';
        if (document.getElementById('page-dashboard').classList.contains('active')) loadDashboard();
        if (document.getElementById('page-expenses').classList.contains('active')) loadExpenses();
    } catch (err) {
        toast(err.message, 'error');
    } finally {
        setLoading(btn, false);
    }
});

// ─── Helpers ─────────────────────────────────────────

function setLoading(btn, loading) {
    if (loading) btn.classList.add('loading');
    else btn.classList.remove('loading');
    btn.disabled = loading;
}

function formatDate(dateStr) {
    if (dateStr && dateStr.includes('-') && dateStr.length >= 10) {
        const parts = dateStr.split(/[\s-:]/);
        if (parts.length >= 3 && parts[0].length === 2) {
            const day = parseInt(parts[0]);
            const month = parseInt(parts[1]) - 1;
            const year = parseInt(parts[2]);
            const d = new Date(year, month, day);
            return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
        }
    }
    const d = new Date(dateStr);
    if (isNaN(d)) return dateStr || 'N/A';
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

function esc(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function renderSkeleton(id, rows, cols) {
    const tbody = document.getElementById(id);
    if (!tbody) return;
    tbody.innerHTML = Array(rows).fill(0).map(() => `
        <tr>${Array(cols).fill(0).map(() => '<td><div class="skeleton" style="height:16px;width:80%"></div></td>').join('')}</tr>
    `).join('');
}

function initDateSelectors() {
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const now = new Date();
    const m = now.getMonth() + 1;
    const y = now.getFullYear();

    const selectors = ['report', 'heatmap'];
    selectors.forEach(prefix => {
        const mSel = document.getElementById(prefix + '-month');
        const ySel = document.getElementById(prefix + '-year');
        if (!mSel) return;
        mSel.innerHTML = months.map((name, i) => `<option value="${i + 1}" ${i + 1 === m ? 'selected' : ''}>${name}</option>`).join('');
        ySel.innerHTML = '';
        for (let yr = y; yr >= y - 3; yr--) ySel.innerHTML += `<option value="${yr}">${yr}</option>`;
    });
}

function closeMobileSidebar() {
    document.getElementById('sidebar')?.classList.remove('open');
    document.getElementById('sidebar-overlay')?.classList.remove('show');
}

// ─── Initialization ──────────────────────────────────

// Keyboard Shortcuts
document.addEventListener('keydown', (e) => {
    if (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA' || document.activeElement.tagName === 'SELECT') return;
    const key = e.key.toLowerCase();
    if (key === 'n') { e.preventDefault(); openAddExpenseModal(); }
    if (key === '1') showPage('dashboard');
    if (key === '2') showPage('expenses');
    if (key === '3') showPage('trends');
    if (key === '4') showPage('report');
    if (key === '5') showPage('budgets');
    if (key === 't') { e.preventDefault(); currentTheme = currentTheme === 'dark' ? 'light' : 'dark'; applyTheme(currentTheme); }
    if (key === '?') document.getElementById('shortcuts-overlay').classList.add('show');
    if (e.key === 'Escape') {
        document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('show'));
        document.getElementById('shortcuts-overlay')?.classList.remove('show');
    }
});

document.getElementById('shortcuts-close')?.addEventListener('click', () => {
    document.getElementById('shortcuts-overlay').classList.remove('show');
});

async function renderDashboardCharts() {
    try {
        const [trendData, cats] = await Promise.all([
            api('/api/trends?months=7'),
            api('/api/categories')
        ]);

        // 1. Portfolio Growth Chart (Line)
        const trendCanvas = document.getElementById('dash-trend-canvas');
        if (trendCanvas) {
            const ctx = trendCanvas.getContext('2d');
            if (dashTrendInstance) dashTrendInstance.destroy();

            const labels = trendData.map(t => {
                const monthNames = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
                return monthNames[t.month];
            });
            const values = trendData.map(t => t.total);

            const gradient = ctx.createLinearGradient(0, 0, 0, 300);
            gradient.addColorStop(0, 'rgba(188, 19, 254, 0.4)');
            gradient.addColorStop(1, 'rgba(188, 19, 254, 0)');

            dashTrendInstance = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        data: values,
                        borderColor: '#bc13fe',
                        backgroundColor: gradient,
                        borderWidth: 3,
                        fill: true,
                        tension: 0.4,
                        pointRadius: 0,
                        pointHoverRadius: 6
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false }, tooltip: { enabled: true } },
                    scales: {
                        x: { grid: { display: false }, ticks: { color: '#6b7280', font: { size: 11 } } },
                        y: {
                            beginAtZero: true,
                            grid: { color: 'rgba(255,255,255,0.03)' },
                            ticks: { color: '#6b7280', font: { size: 11 } }
                        }
                    }
                }
            });
        }

        // 2. Allocation Chart (Donut)
        const allocCanvas = document.getElementById('dash-alloc-canvas');
        if (allocCanvas) {
            const ctx = allocCanvas.getContext('2d');
            if (dashAllocInstance) dashAllocInstance.destroy();

            // Mock allocation for now or fetch if API exists
            const colors = ['#bc13fe', '#00f5ff', '#14f195', '#ffcc00', '#ff3366'];

            dashAllocInstance = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: cats.slice(0, 5),
                    datasets: [{
                        data: [40, 25, 15, 10, 10],
                        backgroundColor: colors,
                        borderColor: 'transparent',
                        hoverOffset: 4
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    cutout: '75%',
                    plugins: {
                        legend: {
                            position: 'right',
                            labels: {
                                color: '#9ea3b0',
                                font: { size: 11 },
                                usePointStyle: true,
                                padding: 15
                            }
                        }
                    }
                }
            });
        }
    } catch (e) { console.error('Chart error:', e); }
}

// Sidebar nav
document.querySelectorAll('.nav-link').forEach(link => {
    if (link.dataset.page) {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            showPage(link.dataset.page);
        });
    }
});

// Mobile menu
document.getElementById('menu-toggle')?.addEventListener('click', () => {
    document.getElementById('sidebar').classList.add('open');
    document.getElementById('sidebar-overlay').classList.add('show');
});
document.getElementById('sidebar-overlay')?.addEventListener('click', closeMobileSidebar);

// Initial state
if (token && username) enterApp();
else showScreen('auth-screen');

// Expose to window
window.deleteExpense = deleteExpense;
window.removeBudget = removeBudget;
window.deleteRecurring = deleteRecurring;
window.deleteCategory = deleteCategory;
window.deleteReminder = deleteReminder;
window.openAddExpenseModal = openAddExpenseModal;
