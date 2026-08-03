const CONFIG = {
    authUrl: 'http://kanal65.xyz/api.php?mac=',
    m3uDefault: 'http://kanal65.xyz/by-kerimoff-player/playlist.m3u',
    osdTimeout: 6000,
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
    hls: new Hls(),
    numericInput: '',
    numericTimer: null,
    aspectModes: ['FILL', 'FIT', 'ZOOM'],
    currentAspect: 0,
    radioCountry: 'AZ',
    radios: [],
    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
};

// --- Initialization ---
window.onload = () => {
    startClock();
    launchApp();
};

function startClock() {
    const update = () => {
        const now = new Date();
        const opts = { hour: '2-digit', minute: '2-digit' };
        document.getElementById('clock').innerText = now.toLocaleTimeString('az-AZ', opts);
        if(document.getElementById('osd-time')) document.getElementById('osd-time').innerText = now.toLocaleTimeString('az-AZ', opts);
        document.getElementById('date').innerText = now.toLocaleDateString('az-AZ', { weekday: 'long', day: 'numeric', month: 'long' });
    };
    update();
    setInterval(update, 1000);
}

async function launchApp() {
    showScreen('splash-screen');
    setTimeout(async () => {
        if (state.mac) await checkAuth(state.mac);
        else showLogin();
    }, 3000);
}

// --- Server & Data ---
async function checkAuth(mac) {
    try {
        const res = await fetch(CONFIG.authUrl + mac);
        const data = await res.json();
        if (data.status === 'success') {
            state.mac = mac;
            localStorage.setItem('aurex_mac', mac);
            state.m3uUrl = data.m3uUrl || CONFIG.m3uDefault;
            if (data.announcement) {
                const elan = document.getElementById('announcement-text');
                elan.innerText = data.announcement;
                if(data.announcementColor) elan.style.color = data.announcementColor;
                document.getElementById('announcement-container').classList.remove('hidden');
            }
            await loadPlaylist();
            showDashboard();
        } else showLogin(data.message);
    } catch (e) { showLogin("Bağlantı xətası"); }
}

async function loadPlaylist() {
    try {
        const res = await fetch(state.m3uUrl);
        const text = await res.text();
        parseM3U(text);
        fetchWeather();
    } catch (e) {}
}

function parseM3U(text) {
    const lines = text.split('\n');
    let chans = [];
    let cur = null;
    lines.forEach(line => {
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
}

async function fetchWeather() {
    try {
        const res = await fetch('https://api.open-meteo.com/v1/forecast?latitude=40.4093&longitude=49.8671&current_weather=true');
        const data = await res.json();
        document.getElementById('weather-temp').innerText = Math.round(data.current_weather.temperature) + "°C";
        document.getElementById('weather-widget').classList.remove('hidden');
    } catch (e) {}
}

// --- Navigation ---
function showScreen(id) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(id).classList.add('active');
    state.screen = id;
}

function showLogin(err = '') {
    showScreen('login-screen');
    state.focusedArea = 'mac-input';
    if(err) document.getElementById('login-error').innerText = err;
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
    showScreen('tv-view');
    renderCategories();
    renderChannels();
    state.focusedArea = 'channels';
    state.focusedIndex = 0;
    updateFocus();
}

function renderCategories() {
    document.getElementById('category-list').innerHTML = state.categories.map((c, i) =>
        `<div class="list-item cat-item" data-index="${i}">${c}</div>`
    ).join('');
}

function renderChannels() {
    if (state.currentCategory === 'Hamısı') state.filteredChannels = state.allChannels;
    else if (state.currentCategory === 'Sevimlilər') state.filteredChannels = state.allChannels.filter(c => state.favorites.includes(c.id));
    else state.filteredChannels = state.allChannels.filter(c => c.group === state.currentCategory);

    document.getElementById('channel-list').innerHTML = state.filteredChannels.map((c, i) => `
        <div class="list-item chan-item" data-index="${i}">
            <img src="${c.logo}" onerror="this.src='placeholder.png'">
            <div class="chan-info">
                <div class="chan-name">${c.name}</div>
                <div class="chan-epg">Canlı Yayım</div>
            </div>
        </div>
    `).join('');
}

function updateFocus() {
    document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));
    let sel = '';
    if (state.focusedArea === 'mac-input') sel = '#mac-input';
    else if (state.focusedArea === 'btn-login') sel = '#btn-login';
    else if (state.focusedArea === 'cards') sel = '.card';
    else if (state.focusedArea === 'categories') sel = '.cat-item';
    else if (state.focusedArea === 'channels') sel = '.chan-item';
    else if (state.focusedArea === 'tv-search') sel = '#tv-search';

    const el = document.querySelectorAll(sel)[state.focusedIndex];
    if (el) {
        el.classList.add('focused');
        el.scrollIntoView({ block: 'center', behavior: 'smooth' });
        if (state.focusedArea === 'channels') updatePreview(state.filteredChannels[state.focusedIndex]);
    }
}

function updatePreview(chan) {
    if(!chan) return;
    document.getElementById('preview-name').innerText = chan.name;
    document.getElementById('preview-logo').src = chan.logo;
    const vid = document.getElementById('mini-player');
    if (Hls.isSupported()) { state.hls.loadSource(chan.url); state.hls.attachMedia(vid); }
    else vid.src = chan.url;
}

// --- Key Handlers ---
window.onkeydown = (e) => {
    const key = e.key;
    if (state.screen === 'login-screen') handleLoginInput(key);
    else if (state.screen === 'dashboard') handleDashboardInput(key);
    else if (state.screen === 'tv-view') handleTVInput(key);
    else if (state.screen === 'player-view') handlePlayerInput(key);

    // Numeric Input (0-9)
    if (state.screen === 'player-view' && key >= '0' && key <= '9') {
        handleNumeric(key);
    }

    // Aspect Ratio (Yellow button or 'y')
    if (state.screen === 'player-view' && (key === 'y' || key === 'Yellow')) {
        toggleAspect();
    }

    updateFocus();
};

function handleLoginInput(key) {
    if (key === 'ArrowDown') state.focusedArea = 'btn-login';
    if (key === 'ArrowUp') state.focusedArea = 'mac-input';
    if (key === 'Enter' && state.focusedArea === 'btn-login') checkAuth(document.getElementById('mac-input').value);
}

function handleDashboardInput(key) {
    const cards = document.querySelectorAll('.card');
    if (key === 'ArrowRight') state.focusedIndex = (state.focusedIndex + 1) % cards.length;
    if (key === 'ArrowLeft') state.focusedIndex = (state.focusedIndex - 1 + cards.length) % cards.length;
    if (key === 'Enter') {
        const action = cards[state.focusedIndex].getAttribute('data-action');
        if (action === 'live-tv') showTV();
    }
}

function handleTVInput(key) {
    if (state.focusedArea === 'channels') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.filteredChannels.length;
        if (key === 'ArrowUp') {
            if (state.focusedIndex === 0) { state.focusedArea = 'tv-search'; state.focusedIndex = 0; }
            else state.focusedIndex--;
        }
        if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
        if (key === 'Enter') {
            state.currentChannelIndex = state.allChannels.indexOf(state.filteredChannels[state.focusedIndex]);
            startPlayer(state.filteredChannels[state.focusedIndex]);
        }
    } else if (state.focusedArea === 'categories') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.categories.length;
        if (key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + state.categories.length) % state.categories.length;
        if (key === 'ArrowRight') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if (key === 'Enter') showTV(state.categories[state.focusedIndex]);
    } else if (state.focusedArea === 'tv-search') {
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
    document.getElementById('osd-name').innerText = chan.name;
    document.getElementById('osd-logo').src = chan.logo;
    showOSD();
}

function handlePlayerInput(key) {
    if (key === 'Backspace' || key === 'Escape') {
        const osd = document.getElementById('player-osd');
        if (osd.classList.contains('osd-hidden')) {
            document.getElementById('main-player').pause();
            showTV(state.currentCategory);
        } else {
            osd.classList.add('osd-hidden');
        }
    }
    if (key === 'Enter' || key === 'ArrowUp' || key === 'ArrowDown') showOSD();

    // Channel Switch with Up/Down when OSD is hidden
    if (document.getElementById('player-osd').classList.contains('osd-hidden')) {
        if (key === 'ArrowUp') switchChannel(1);
        if (key === 'ArrowDown') switchChannel(-1);
    }
}

function switchChannel(dir) {
    state.currentChannelIndex = (state.currentChannelIndex + dir + state.allChannels.length) % state.allChannels.length;
    startPlayer(state.allChannels[state.currentChannelIndex]);
}

function showOSD() {
    const osd = document.getElementById('player-osd');
    osd.classList.remove('osd-hidden');
    clearTimeout(window.osdTimer);
    window.osdTimer = setTimeout(() => osd.classList.add('osd-hidden'), CONFIG.osdTimeout);
}

// --- Final Gold Features ---
function handleNumeric(digit) {
    state.numericInput += digit;
    const overlay = document.getElementById('numeric-overlay');
    overlay.innerText = state.numericInput;
    overlay.classList.remove('hidden');

    clearTimeout(state.numericTimer);
    state.numericTimer = setTimeout(() => {
        const idx = parseInt(state.numericInput) - 1;
        if (idx >= 0 && idx < state.allChannels.length) {
            state.currentChannelIndex = idx;
            startPlayer(state.allChannels[idx]);
        }
        state.numericInput = '';
        overlay.classList.add('hidden');
    }, CONFIG.numericTimeout);
}

function toggleAspect() {
    state.currentAspect = (state.currentAspect + 1) % state.aspectModes.length;
    const mode = state.aspectModes[state.currentAspect];
    const video = document.getElementById('main-player');
    video.className = 'video-' + mode.toLowerCase();

    const status = document.getElementById('aspect-status');
    status.innerText = "Format: " + mode;
    status.classList.remove('hidden');
    clearTimeout(window.aspectTimer);
    window.aspectTimer = setTimeout(() => status.classList.add('hidden'), 2000);
}
