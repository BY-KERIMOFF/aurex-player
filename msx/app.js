const CONFIG = {
    authUrl: 'http://kanal65.xyz/api.php?mac=',
    weatherUrl: 'https://api.open-meteo.com/v1/forecast?latitude=40.4093&longitude=49.8671&current_weather=true',
    currencyUrl: 'https://www.cbar.az/currencies/', // Proxied or processed on server usually
    radioApi: 'https://all.api.radio-browser.info/json/stations/bycountrycodeexact/',
    osdTimeout: 5000
};

let state = {
    screen: 'splash',
    mac: localStorage.getItem('aurex_mac') || '',
    m3uUrl: '',
    allChannels: [],
    categories: [],
    currentCategory: '',
    filteredChannels: [],
    radios: [],
    focusedArea: 'mac-input',
    focusedIndex: 0,
    pin: '',
    isAdultLocked: false,
    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
};

const hls = new Hls();

// --- Initialization ---
window.onload = () => {
    initClock();
    startApp();
};

function initClock() {
    const update = () => {
        const now = new Date();
        document.getElementById('clock').innerText = now.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit' });
        document.getElementById('date').innerText = now.toLocaleDateString('az-AZ', { weekday: 'long', day: 'numeric', month: 'long' });
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

// --- Auth & API ---
async function checkAuth(mac) {
    try {
        const response = await fetch(CONFIG.authUrl + mac);
        const data = await response.json();
        if (data.status === 'success') {
            state.mac = mac;
            localStorage.setItem('aurex_mac', mac);
            state.m3uUrl = data.m3uUrl;
            if (data.announcement) {
                document.getElementById('announcement-text').innerText = data.announcement;
                document.getElementById('announcement-container').classList.remove('hidden');
            }
            await loadData();
            showDashboard();
        } else showLogin(data.message);
    } catch (e) { showLogin("Bağlantı xətası"); }
}

async function loadData() {
    await fetchM3U();
    fetchWeather();
    fetchRadios();
}

async function fetchM3U() {
    try {
        const response = await fetch(state.m3uUrl);
        const text = await response.text();
        const lines = text.split('\n');
        let channels = [];
        let cur = null;
        lines.forEach(line => {
            if (line.startsWith('#EXTINF')) {
                const name = line.split(',').pop().trim();
                const logo = line.match(/tvg-logo="([^"]*)"/)?.[1] || '';
                const group = line.match(/group-title="([^"]*)"/)?.[1] || 'Digər';
                cur = { name, logo, group, id: btoa(name).substring(0,8) };
            } else if (line.startsWith('http')) {
                if (cur) { cur.url = line; channels.push(cur); cur = null; }
            }
        });
        state.allChannels = channels;
        state.categories = ["Hamısı", "Sevimlilər", ...new Set(channels.map(c => c.group))].sort();
    } catch (e) {}
}

async function fetchWeather() {
    try {
        const res = await fetch(CONFIG.weatherUrl);
        const data = await res.json();
        document.getElementById('weather-temp').innerText = Math.round(data.current_weather.temperature) + "°C";
        document.getElementById('weather-widget').classList.remove('hidden');
    } catch (e) {}
}

async function fetchRadios() {
    try {
        const res = await fetch(CONFIG.radioApi + 'AZ');
        state.radios = await res.json();
    } catch (e) {}
}

// --- Screens ---
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

function showTV(cat = 'Hamısı') {
    state.currentCategory = cat;
    showScreen('tv-view');
    renderCategories();
    renderChannels();
    state.focusedArea = 'channels';
    state.focusedIndex = 0;
    updateFocus();
}

function renderCategories() {
    document.getElementById('category-list').innerHTML = state.categories.map((c, i) =>
        `<div class="list-item focusable-cat ${c === state.currentCategory ? 'active-cat' : ''}" data-index="${i}">${c}</div>`
    ).join('');
}

function renderChannels() {
    if (state.currentCategory === 'Hamısı') state.filteredChannels = state.allChannels;
    else if (state.currentCategory === 'Sevimlilər') state.filteredChannels = state.allChannels.filter(c => state.favorites.includes(c.id));
    else state.filteredChannels = state.allChannels.filter(c => c.group === state.currentCategory);

    document.getElementById('channel-list').innerHTML = state.filteredChannels.map((c, i) =>
        `<div class="list-item focusable-chan" data-index="${i}"><img src="${c.logo}"><span>${c.name}</span></div>`
    ).join('');
}

// --- Navigation & Keys ---
function updateFocus() {
    document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));
    let selector = '';
    if (state.focusedArea === 'mac-input') selector = '#mac-input';
    else if (state.focusedArea === 'btn-login') selector = '#btn-login';
    else if (state.focusedArea === 'cards') selector = '.card';
    else if (state.focusedArea === 'categories') selector = '.focusable-cat';
    else if (state.focusedArea === 'channels') selector = '.focusable-chan';

    const el = document.querySelectorAll(selector)[state.focusedIndex];
    if (el) {
        el.classList.add('focused');
        el.scrollIntoView({ block: 'center' });
        if (state.focusedArea === 'channels') updatePreview(state.filteredChannels[state.focusedIndex]);
    }
}

function updatePreview(chan) {
    if(!chan) return;
    document.getElementById('preview-name').innerText = chan.name;
    document.getElementById('preview-logo').src = chan.logo;
    playStream(document.getElementById('mini-player'), chan.url);
}

function playStream(video, url) {
    if (Hls.isSupported()) { hls.loadSource(url); hls.attachMedia(video); }
    else video.src = url;
}

window.onkeydown = (e) => {
    const key = e.key;
    const cardsCount = document.querySelectorAll('.card').length;

    if (state.screen === 'dashboard') {
        if (key === 'ArrowRight') state.focusedIndex = (state.focusedIndex + 1) % cardsCount;
        if (key === 'ArrowLeft') state.focusedIndex = (state.focusedIndex - 1 + cardsCount) % cardsCount;
        if (key === 'Enter') {
            const action = document.querySelectorAll('.card')[state.focusedIndex].dataset.action;
            if (action === 'live-tv') showTV();
            if (action === 'radio') showRadio();
        }
    } else if (state.screen === 'tv-view') {
        if (state.focusedArea === 'channels') {
            if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.filteredChannels.length;
            if (key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + state.filteredChannels.length) % state.filteredChannels.length;
            if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
            if (key === 'Enter') startPlayer(state.filteredChannels[state.focusedIndex]);
        } else if (state.focusedArea === 'categories') {
            if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.categories.length;
            if (key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + state.categories.length) % state.categories.length;
            if (key === 'ArrowRight') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
            if (key === 'Enter') showTV(state.categories[state.focusedIndex]);
        }
        if (key === 'Backspace') showDashboard();
    }
    updateFocus();
};

function startPlayer(chan) {
    showScreen('player-view');
    playStream(document.getElementById('main-player'), chan.url);
    document.getElementById('osd-name').innerText = chan.name;
    document.getElementById('osd-logo').src = chan.logo;
    showOSD();
}

function showOSD() {
    const osd = document.getElementById('player-osd');
    osd.classList.remove('osd-hidden');
    clearTimeout(window.osdTimer);
    window.osdTimer = setTimeout(() => osd.classList.add('osd-hidden'), CONFIG.osdTimeout);
}

function showRadio() {
    showScreen('radio-view');
    document.getElementById('radio-list').innerHTML = state.radios.map((r, i) =>
        `<div class="list-item focusable-radio" data-index="${i}">${r.name}</div>`
    ).join('');
    // Radio logic...
}
