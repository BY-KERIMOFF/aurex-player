const CONFIG = {
    authUrl: 'http://kanal65.xyz/api.php?mac=',
    m3uDefault: 'http://kanal65.xyz/by-kerimoff-player/playlist.m3u',
    weatherApi: 'https://api.open-meteo.com/v1/forecast?latitude=40.4093&longitude=49.8671&current_weather=true',
    osdTimeout: 5000,
    volumeTimeout: 3000,
    numericTimeout: 1500
};

let state = {
    screen: 'splash',
    mac: localStorage.getItem('aurex_mac') || '',
    m3uUrl: '',
    allChannels: [],
    categories: [],
    currentCategory: 'Hamısı',
    filteredChannels: [],
    focusedArea: 'mac-input',
    focusedIndex: 0,
    currentChannelIndex: -1,
    volume: 50,
    numericInput: '',
    numericTimer: null,
    aspectModes: ['FILL', 'FIT', 'ZOOM'],
    currentAspect: 0,
    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
    hls: new Hls()
};

// --- App Lifecycle ---
window.onload = () => {
    initClock();
    startApp();
};

function initClock() {
    const update = () => {
        const now = new Date();
        const timeStr = now.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit' });
        document.getElementById('clock').innerText = timeStr;
        document.getElementById('date').innerText = now.toLocaleDateString('az-AZ', { weekday: 'long', day: 'numeric', month: 'long' });
        if(document.getElementById('osd-clock')) document.getElementById('osd-clock').innerText = timeStr;
    };
    update();
    setInterval(update, 1000);
}

async function startApp() {
    showScreen('splash-screen');
    setTimeout(async () => {
        if (state.mac) await checkAuth(state.mac);
        else showLogin();
    }, 3000);
}

// --- Auth & Data Fetching ---
async function checkAuth(mac) {
    try {
        const res = await fetch(CONFIG.authUrl + mac);
        const data = await res.json();
        if (data.status === 'success') {
            state.mac = mac;
            localStorage.setItem('aurex_mac', mac);
            state.m3uUrl = data.m3uUrl || CONFIG.m3uDefault;
            await loadPlaylist();
            showDashboard();
        } else {
            showLogin(data.message || 'MAC ünvanı aktiv edilməyib.');
        }
    } catch (e) { showLogin('Serverə qoşulmaq mümkün olmadı.'); }
}

async function loadPlaylist() {
    try {
        const res = await fetch(state.m3uUrl);
        const text = await res.text();
        const lines = text.split('\n');
        let chans = [];
        let cur = null;
        lines.forEach(line => {
            line = line.trim();
            if (line.startsWith('#EXTINF')) {
                const name = line.split(',').pop().trim();
                const logo = line.match(/tvg-logo="([^"]*)"/)?.[1] || '';
                const group = line.match(/group-title="([^"]*)"/)?.[1] || 'Digər';
                cur = { name, logo, group, id: btoa(name).substring(0,8) };
            } else if (line.startsWith('http')) {
                if (cur) { cur.url = line; chans.push(cur); cur = null; }
            }
        });
        state.allChannels = chans;
        state.categories = ["Hamısı", "Sevimlilər", ...new Set(chans.map(c => c.group))].sort();
        fetchWeather();
    } catch (e) {}
}

async function fetchWeather() {
    try {
        const res = await fetch(CONFIG.weatherApi);
        const data = await res.json();
        document.getElementById('weather-temp').innerText = Math.round(data.current_weather.temperature) + "°C";
        document.getElementById('weather-box').classList.remove('hidden');
    } catch (e) {}
}

// --- Screen Management ---
function showScreen(id) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(id).classList.add('active');
    state.screen = id;
}

function showLogin(err = '') {
    showScreen('login-screen');
    state.focusedArea = 'mac-input';
    if(err) {
        const el = document.getElementById('login-error');
        el.innerText = err;
        el.classList.remove('hidden');
    }
    updateFocus();
}

function showDashboard() {
    showScreen('dashboard');
    state.focusedArea = 'cards';
    state.focusedIndex = 0;
    updateFocus();
}

function showTV(category = 'Hamısı') {
    state.currentCategory = category;
    showScreen('tv-panel');
    renderCategories();
    renderChannels();
    state.focusedArea = 'channels';
    state.focusedIndex = 0;
    updateFocus();
}

// --- Rendering ---
function renderCategories() {
    document.getElementById('category-list').innerHTML = state.categories.map((c, i) => `
        <div class="item-channel focusable-cat" data-index="${i}">${c}</div>
    `).join('');
}

function renderChannels() {
    const query = document.getElementById('etSearch').value.toLowerCase();
    let list = state.allChannels;
    if (state.currentCategory === 'Sevimlilər') list = list.filter(c => state.favorites.includes(c.id));
    else if (state.currentCategory !== 'Hamısı') list = list.filter(c => c.group === state.currentCategory);

    if (query) list = list.filter(c => c.name.toLowerCase().includes(query));

    state.filteredChannels = list;
    document.getElementById('channel-list').innerHTML = list.map((c, i) => `
        <div class="item-channel focusable-chan" data-index="${i}">
            <img src="${c.logo}" class="chan-logo" onerror="this.src='placeholder.png'">
            <div class="chan-name">${c.name}</div>
            ${state.favorites.includes(c.id) ? '⭐' : ''}
        </div>
    `).join('');
}

function updateFocus() {
    document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));
    let sel = '';
    if (state.focusedArea === 'mac-input') sel = '#mac-input';
    else if (state.focusedArea === 'btn-login') sel = '#btn-login';
    else if (state.focusedArea === 'cards') sel = '.main-card';
    else if (state.focusedArea === 'radio') sel = '#cardRadio';
    else if (state.focusedArea === 'categories') sel = '.focusable-cat';
    else if (state.focusedArea === 'channels') sel = '.focusable-chan';
    else if (state.focusedArea === 'search') sel = '#etSearch';

    const el = document.querySelectorAll(sel)[state.focusedIndex] || document.querySelector(sel);
    if (el) {
        el.classList.add('focused');
        el.scrollIntoView({ block: 'center', behavior: 'smooth' });
        if (state.focusedArea === 'channels') updatePreview(state.filteredChannels[state.focusedIndex]);
    }
}

function updatePreview(chan) {
    if(!chan) return;
    document.getElementById('current-name').innerText = chan.name;
    document.getElementById('current-logo').src = chan.logo;
    const vid = document.getElementById('mini-player');
    if (Hls.isSupported()) { state.hls.loadSource(chan.url); state.hls.attachMedia(vid); }
    else vid.src = chan.url;
}

// --- Interaction ---
window.onkeydown = (e) => {
    const key = e.key;
    if (state.screen === 'login-screen') handleLoginInput(key);
    else if (state.screen === 'dashboard') handleDashboardInput(key);
    else if (state.screen === 'tv-panel') handleTVInput(key);
    else if (state.screen === 'player-view') handlePlayerInput(key);

    // Numeric & Aspect Ratio
    if (state.screen === 'player-view') {
        if (key >= '0' && key <= '9') handleNumeric(key);
        if (key === 'y' || key === 'Yellow') toggleAspect();
    }

    // Volume Control
    if (key === 'AudioVolumeUp') changeVolume(5);
    if (key === 'AudioVolumeDown') changeVolume(-5);

    updateFocus();
};

function handleLoginInput(key) {
    if (key === 'ArrowDown') state.focusedArea = 'btn-login';
    if (key === 'ArrowUp') state.focusedArea = 'mac-input';
    if (key === 'Enter' && state.focusedArea === 'btn-login') checkAuth(document.getElementById('mac-input').value);
}

function handleDashboardInput(key) {
    if (state.focusedArea === 'cards') {
        const cards = document.querySelectorAll('.main-card');
        if (key === 'ArrowRight') state.focusedIndex = (state.focusedIndex + 1) % cards.length;
        if (key === 'ArrowLeft') state.focusedIndex = (state.focusedIndex - 1 + cards.length) % cards.length;
        if (key === 'ArrowDown') { state.focusedArea = 'radio'; state.focusedIndex = 0; }
        if (key === 'Enter') {
            const action = cards[state.focusedIndex].dataset.action;
            if (action === 'live-tv') showTV();
        }
    } else if (state.focusedArea === 'radio') {
        if (key === 'ArrowUp') { state.focusedArea = 'cards'; state.focusedIndex = 0; }
        if (key === 'Enter') console.log("Radio screen coming soon");
    }
}

function handleTVInput(key) {
    if (state.focusedArea === 'channels') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.filteredChannels.length;
        if (key === 'ArrowUp') {
            if (state.focusedIndex === 0) { state.focusedArea = 'search'; state.focusedIndex = 0; }
            else state.focusedIndex--;
        }
        if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
        if (key === 'Enter') startPlayer(state.filteredChannels[state.focusedIndex]);
    } else if (state.focusedArea === 'categories') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.categories.length;
        if (key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + state.categories.length) % state.categories.length;
        if (key === 'ArrowRight') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if (key === 'Enter') showTV(state.categories[state.focusedIndex]);
    } else if (state.focusedArea === 'search') {
        if (key === 'ArrowDown') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
    }
    if (key === 'Backspace' || key === 'Escape') showDashboard();
}

function startPlayer(chan) {
    showScreen('player-view');
    document.getElementById('mini-player').pause();
    const main = document.getElementById('main-player');
    if (Hls.isSupported()) { state.hls.loadSource(chan.url); state.hls.attachMedia(main); }
    else main.src = chan.url;

    document.getElementById('tvChannelName').innerText = chan.name;
    document.getElementById('ivChannelLogo').src = chan.logo;
    showOSD();
}

function handlePlayerInput(key) {
    if (key === 'Backspace' || key === 'Escape') {
        const osd = document.getElementById('osdLayout');
        if (osd.classList.contains('osd-hidden')) {
            document.getElementById('main-player').pause();
            showTV(state.currentCategory);
        } else osd.classList.add('osd-hidden');
    }
    if (key === 'Enter' || key === 'ArrowUp' || key === 'ArrowDown') showOSD();
}

function showOSD() {
    const osd = document.getElementById('osdLayout');
    osd.classList.remove('osd-hidden');
    clearTimeout(window.osdTimer);
    window.osdTimer = setTimeout(() => osd.classList.add('osd-hidden'), CONFIG.osdTimeout);
}

function changeVolume(delta) {
    state.volume = Math.max(0, Math.min(100, state.volume + delta));
    const main = document.getElementById('main-player');
    if(main) main.volume = state.volume / 100;

    document.getElementById('vol-fill').style.width = state.volume + '%';
    document.getElementById('tvVolumePercent').innerText = state.volume + '%';
    document.getElementById('volumeLayout').classList.remove('vol-hidden');

    clearTimeout(window.volTimer);
    window.volTimer = setTimeout(() => document.getElementById('volumeLayout').classList.add('vol-hidden'), CONFIG.volumeTimeout);
}

function handleNumeric(digit) {
    state.numericInput += digit;
    const el = document.getElementById('numeric-input');
    el.innerText = state.numericInput;
    el.classList.remove('hidden');
    clearTimeout(state.numericTimer);
    state.numericTimer = setTimeout(() => {
        const idx = parseInt(state.numericInput) - 1;
        if (idx >= 0 && idx < state.allChannels.length) startPlayer(state.allChannels[idx]);
        state.numericInput = '';
        el.classList.add('hidden');
    }, CONFIG.numericTimeout);
}

function toggleAspect() {
    state.currentAspect = (state.currentAspect + 1) % state.aspectModes.length;
    const mode = state.aspectModes[state.currentAspect];
    document.getElementById('main-player').style.objectFit = mode === 'FILL' ? 'fill' : (mode === 'FIT' ? 'contain' : 'cover');
    const toast = document.getElementById('aspect-toast');
    toast.innerText = "Format: " + mode;
    toast.classList.remove('hidden');
    setTimeout(() => toast.classList.add('hidden'), 2000);
}
