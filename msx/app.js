const CONFIG = {
    authUrl: 'http://kanal65.xyz/api.php?mac=',
    osdTimeout: 5000,
    m3uDefault: 'http://kanal65.xyz/by-kerimoff-player/playlist.m3u'
};

let state = {
    screen: 'splash',
    mac: localStorage.getItem('aurex_mac') || '',
    m3uUrl: '',
    allChannels: [],
    categories: [],
    currentCategory: '',
    filteredChannels: [],
    focusedArea: 'mac-input',
    focusedIndex: 0,
    player: null,
    isLive: true,
    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
};

const hls = new Hls();

// --- Lifecycle ---
window.onload = () => {
    initClock();
    startApp();
};

async function initClock() {
    const update = () => {
        const now = new Date();
        const time = now.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit' });
        const date = now.toLocaleDateString('az-AZ', { weekday: 'long', day: 'numeric', month: 'long' });
        if(document.getElementById('clock')) document.getElementById('clock').innerText = time;
        if(document.getElementById('osd-time')) document.getElementById('osd-time').innerText = time;
        if(document.getElementById('date')) document.getElementById('date').innerText = date;
    };
    update();
    setInterval(update, 1000);
}

async function startApp() {
    showScreen('splash-screen');
    setTimeout(async () => {
        if (state.mac) {
            await checkAuth(state.mac);
        } else {
            showLogin();
        }
    }, 3000);
}

// --- Auth ---
async function checkAuth(mac) {
    try {
        const response = await fetch(CONFIG.authUrl + mac);
        const data = await response.json();

        if (data.status === 'success') {
            state.mac = mac;
            localStorage.setItem('aurex_mac', mac);
            state.m3uUrl = data.m3uUrl || CONFIG.m3uDefault;
            await loadContent();
            showDashboard();
        } else {
            showLogin(data.message || 'MAC ünvanı aktiv edilməyib.');
        }
    } catch (e) {
        showLogin('Bağlantı xətası baş verdi.');
    }
}

function showLogin(error = '') {
    showScreen('login-screen');
    state.focusedArea = 'mac-input';
    state.focusedIndex = 0;
    if (error) {
        const errEl = document.getElementById('login-error');
        errEl.innerText = error;
        errEl.classList.remove('hidden');
    }
    updateFocus();
}

// --- Data ---
async function loadContent() {
    try {
        const response = await fetch(state.m3uUrl);
        const text = await response.text();
        parseM3U(text);
    } catch (e) { console.error("M3U error", e); }
}

function parseM3U(content) {
    const lines = content.split('\n');
    let channels = [];
    let cur = null;
    lines.forEach(line => {
        line = line.trim();
        if (line.startsWith('#EXTINF')) {
            const name = line.split(',').pop();
            const logo = line.match(/tvg-logo="([^"]*)"/)?.[1] || '';
            const group = line.match(/group-title="([^"]*)"/)?.[1] || 'Digər';
            cur = { name, logo, group, id: btoa(name).substring(0, 8) };
        } else if (line.startsWith('http')) {
            if (cur) { cur.url = line; channels.push(cur); cur = null; }
        }
    });
    state.allChannels = channels;
    state.categories = ["Hamısı", "Sevimlilər", ...new Set(channels.map(c => c.group))].sort();
}

// --- UI / Navigation ---
function showScreen(id) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(id).classList.add('active');
    state.screen = id;
}

function showDashboard() {
    showScreen('dashboard');
    state.focusedArea = 'cards';
    state.focusedIndex = 0;
    updateFocus();
}

function showTVView(category = 'Hamısı') {
    showScreen('tv-view');
    state.currentCategory = category;
    renderCategories();
    filterAndRenderChannels();
    state.focusedArea = 'channels';
    state.focusedIndex = 0;
    updateFocus();
}

function renderCategories() {
    const container = document.getElementById('category-list');
    container.innerHTML = state.categories.map((c, i) => `
        <div class="list-item cat-item" data-index="${i}">${c}</div>
    `).join('');
}

function filterAndRenderChannels() {
    if (state.currentCategory === 'Hamısı') state.filteredChannels = state.allChannels;
    else if (state.currentCategory === 'Sevimlilər') state.filteredChannels = state.allChannels.filter(c => state.favorites.includes(c.id));
    else state.filteredChannels = state.allChannels.filter(c => c.group === state.currentCategory);

    const container = document.getElementById('channel-list');
    container.innerHTML = state.filteredChannels.map((c, i) => `
        <div class="list-item chan-item" data-index="${i}">
            <img src="${c.logo}" onerror="this.src='placeholder.png'">
            <span>${c.name}</span>
        </div>
    `).join('');
}

function updateFocus() {
    document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));
    let selector = '';
    if (state.focusedArea === 'mac-input') selector = '#mac-input';
    else if (state.focusedArea === 'btn-login') selector = '#btn-login';
    else if (state.focusedArea === 'cards') selector = '.card';
    else if (state.focusedArea === 'categories') selector = '.cat-item';
    else if (state.focusedArea === 'channels') selector = '.chan-item';
    else if (state.focusedArea === 'tv-search') selector = '#tv-search';

    const elements = document.querySelectorAll(selector);
    const target = elements[state.focusedIndex];
    if (target) {
        target.classList.add('focused');
        target.scrollIntoView({ block: 'center', behavior: 'smooth' });
        if (state.focusedArea === 'channels') updatePreview(state.filteredChannels[state.focusedIndex]);
    }
}

function updatePreview(channel) {
    if (!channel) return;
    document.getElementById('preview-name').innerText = channel.name;
    document.getElementById('preview-logo').src = channel.logo;
    playStream(document.getElementById('mini-player'), channel.url);
}

function playStream(videoEl, url) {
    if (Hls.isSupported()) {
        hls.loadSource(url);
        hls.attachMedia(videoEl);
    } else {
        videoEl.src = url;
    }
}

// --- Key Handlers ---
window.onkeydown = (e) => {
    const key = e.key;
    if (state.screen === 'login-screen') handleLoginKeys(key);
    else if (state.screen === 'dashboard') handleDashboardKeys(key);
    else if (state.screen === 'tv-view') handleTVKeys(key);
    else if (state.screen === 'player-view') handlePlayerKeys(key);
    updateFocus();
};

function handleLoginKeys(key) {
    if (key === 'ArrowDown') state.focusedArea = 'btn-login';
    if (key === 'ArrowUp') state.focusedArea = 'mac-input';
    if (key === 'Enter') {
        if (state.focusedArea === 'btn-login') checkAuth(document.getElementById('mac-input').value);
    }
}

function handleDashboardKeys(key) {
    const cards = document.querySelectorAll('.card');
    if (key === 'ArrowRight') state.focusedIndex = (state.focusedIndex + 1) % cards.length;
    if (key === 'ArrowLeft') state.focusedIndex = (state.focusedIndex - 1 + cards.length) % cards.length;
    if (key === 'Enter') {
        const action = cards[state.focusedIndex].dataset.action;
        if (action === 'live-tv') showTVView();
    }
}

function handleTVKeys(key) {
    const chans = state.filteredChannels.length;
    const cats = state.categories.length;

    if (state.focusedArea === 'channels') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % chans;
        if (key === 'ArrowUp') {
            if (state.focusedIndex === 0) { state.focusedArea = 'tv-search'; state.focusedIndex = 0; }
            else state.focusedIndex--;
        }
        if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
        if (key === 'Enter') startPlayer(state.filteredChannels[state.focusedIndex]);
    } else if (state.focusedArea === 'categories') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % cats;
        if (key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + cats) % cats;
        if (key === 'ArrowRight') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if (key === 'Enter') {
            state.currentCategory = state.categories[state.focusedIndex];
            filterAndRenderChannels();
            state.focusedArea = 'channels';
            state.focusedIndex = 0;
        }
    } else if (state.focusedArea === 'tv-search') {
        if (key === 'ArrowDown') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
    }

    if (key === 'Backspace' || key === 'Escape') showDashboard();
}

function startPlayer(channel) {
    showScreen('player-view');
    document.getElementById('mini-player').pause();
    playStream(document.getElementById('main-player'), channel.url);
    document.getElementById('osd-channel-name').innerText = channel.name;
    document.getElementById('osd-logo').src = channel.logo;
    showOSD();
}

function handlePlayerKeys(key) {
    if (key === 'Backspace' || key === 'Escape') {
        document.getElementById('main-player').pause();
        showTVView(state.currentCategory);
    }
    if (key === 'Enter') showOSD();
}

function showOSD() {
    const osd = document.getElementById('player-osd');
    osd.classList.remove('osd-hidden');
    clearTimeout(window.osdTimer);
    osdTimer = setTimeout(() => osd.classList.add('osd-hidden'), CONFIG.osdTimeout);
}
