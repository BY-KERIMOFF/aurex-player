const CONFIG = {
    authUrl: 'http://kanal65.xyz/api.php?mac=',
    epgUrl: 'https://epg.pw/xmltv/feed/az.xml',
    radioApi: 'https://all.api.radio-browser.info/json/stations/bycountrycodeexact/',
    osdTimeout: 6000
};

let state = {
    screen: 'splash',
    mac: localStorage.getItem('aurex_mac') || '',
    m3uUrl: '',
    allChannels: [],
    categories: [],
    currentCategory: 'Hamısı',
    filteredChannels: [],
    epgData: {},
    focusedArea: 'mac-input',
    focusedIndex: 0,
    currentChannel: null,
    radioCountry: 'AZ',
    radios: [],
    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
};

const hls = new Hls();
const mainPlayer = document.getElementById('main-player');
const miniPlayer = document.getElementById('mini-player');

// --- Initialization ---
window.onload = () => {
    initClock();
    startApp();
};

function initClock() {
    const update = () => {
        const now = new Date();
        const time = now.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit' });
        if(document.getElementById('clock')) document.getElementById('clock').innerText = time;
        if(document.getElementById('osd-time')) document.getElementById('osd-time').innerText = time;
        if(document.getElementById('date')) document.getElementById('date').innerText = now.toLocaleDateString('az-AZ', { weekday: 'long', day: 'numeric', month: 'long' });
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

// --- Server & Auth ---
async function checkAuth(mac) {
    try {
        const res = await fetch(CONFIG.authUrl + mac);
        const data = await res.json();
        if (data.status === 'success') {
            state.mac = mac;
            localStorage.setItem('aurex_mac', mac);
            state.m3uUrl = data.m3uUrl;
            if (data.announcement) {
                const elan = document.getElementById('announcement-text');
                elan.innerText = data.announcement;
                if(data.announcementColor) elan.style.color = data.announcementColor;
                document.getElementById('announcement-container').classList.remove('hidden');
            }
            await loadAllData();
            showDashboard();
        } else showLogin(data.message);
    } catch (e) { showLogin("Bağlantı xətası"); }
}

async function loadAllData() {
    await Promise.all([fetchM3U(), fetchEPG(), fetchRadios(state.radioCountry), fetchWeather()]);
}

async function fetchM3U() {
    try {
        const res = await fetch(state.m3uUrl);
        const text = await res.text();
        const lines = text.split('\n');
        let chans = [];
        let cur = null;
        lines.forEach(line => {
            if (line.startsWith('#EXTINF')) {
                const name = line.split(',').pop().trim();
                const logo = line.match(/tvg-logo="([^"]*)"/)?.[1] || '';
                const group = line.match(/group-title="([^"]*)"/)?.[1] || 'Digər';
                const tvgId = line.match(/tvg-id="([^"]*)"/)?.[1] || '';
                const catchup = line.match(/catchup="([^"]*)"/)?.[1] || '';
                cur = { name, logo, group, tvgId, catchup, id: btoa(name).substring(0,8) };
            } else if (line.startsWith('http')) {
                if (cur) { cur.url = line; chans.push(cur); cur = null; }
            }
        });
        state.allChannels = chans;
        state.categories = ["Hamısı", "Sevimlilər", ...new Set(chans.map(c => c.group))].sort();
    } catch (e) {}
}

async function fetchEPG() {
    // EPG Parsing can be heavy, usually we fetch per channel or use a lightweight JSON if available
    // For now, we simulate basic EPG or fetch az.xml and parse simplified
}

async function fetchRadios(country) {
    try {
        const res = await fetch(CONFIG.radioApi + country);
        state.radios = await res.json();
        if(state.screen === 'radio-view') renderRadios();
    } catch (e) {}
}

async function fetchWeather() {
    try {
        const res = await fetch('https://api.open-meteo.com/v1/forecast?latitude=40.4093&longitude=49.8671&current_weather=true');
        const data = await res.json();
        document.getElementById('weather-temp').innerText = Math.round(data.current_weather.temperature) + "°C";
        document.getElementById('weather-widget').classList.remove('hidden');
    } catch (e) {}
}

// --- Rendering & Navigation ---
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
    filterAndRenderChannels();
    state.focusedArea = 'channels';
    state.focusedIndex = 0;
    updateFocus();
}

function renderCategories() {
    document.getElementById('category-list').innerHTML = state.categories.map((c, i) =>
        `<div class="list-item cat-item ${c === state.currentCategory ? 'active-cat' : ''}" data-index="${i}">${c}</div>`
    ).join('');
}

function filterAndRenderChannels() {
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
            ${c.catchup ? '<span class="text-gold">⟲</span>' : ''}
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
    else if (state.focusedArea === 'radio-item') selector = '.radio-item';
    else if (state.focusedArea === 'radio-tab') selector = '.tab';

    const el = document.querySelectorAll(selector)[state.focusedIndex];
    if (el) {
        el.classList.add('focused');
        el.scrollIntoView({ block: 'center', behavior: 'smooth' });
        if (state.focusedArea === 'channels') updatePreview(state.filteredChannels[state.focusedIndex]);
        if (state.focusedArea === 'radio-item') updateRadioPreview(state.radios[state.focusedIndex]);
    }
}

function updatePreview(chan) {
    if(!chan) return;
    document.getElementById('preview-name').innerText = chan.name;
    document.getElementById('preview-logo').src = chan.logo;
    playVideo(miniPlayer, chan.url);
}

function playVideo(vid, url) {
    if (Hls.isSupported()) { hls.loadSource(url); hls.attachMedia(vid); }
    else vid.src = url;
}

// --- Input Handling ---
window.onkeydown = (e) => {
    const key = e.key;
    if (state.screen === 'login-screen') handleLoginKeys(key);
    else if (state.screen === 'dashboard') handleDashboardKeys(key);
    else if (state.screen === 'tv-view') handleTVKeys(key);
    else if (state.screen === 'player-view') handlePlayerKeys(key);
    else if (state.screen === 'radio-view') handleRadioKeys(key);
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
        const action = cards[state.focusedIndex].getAttribute('data-action');
        if (action === 'live-tv') showTV();
        if (action === 'radio') showRadioScreen();
    }
}

function handleTVKeys(key) {
    if (state.focusedArea === 'channels') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.filteredChannels.length;
        if (key === 'ArrowUp') {
            if (state.focusedIndex === 0) { state.focusedArea = 'tv-search'; state.focusedIndex = 0; }
            else state.focusedIndex--;
        }
        if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
        if (key === 'Enter') startPlayer(state.filteredChannels[state.focusedIndex]);
    } else if (state.focusedArea === 'categories') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.categories.length;
        if (key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + state.categories.length) % state.categories.length;
        if (key === 'ArrowRight') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if (key === 'Enter') showTV(state.categories[state.focusedIndex]);
    } else if (state.focusedArea === 'tv-search') {
        if (key === 'ArrowDown') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
    }
    if (key === 'Backspace') showDashboard();
}

function startPlayer(chan) {
    showScreen('player-view');
    miniPlayer.pause();
    playVideo(mainPlayer, chan.url);
    document.getElementById('osd-name').innerText = chan.name;
    document.getElementById('osd-logo').src = chan.logo;
    showOSD();
}

function handlePlayerKeys(key) {
    if (key === 'Backspace' || key === 'Escape') { mainPlayer.pause(); showTV(state.currentCategory); }
    if (key === 'Enter') showOSD();
    if (key === 'ArrowUp' || key === 'ArrowDown') showOSD();
}

function showOSD() {
    const osd = document.getElementById('player-osd');
    osd.classList.remove('osd-hidden');
    clearTimeout(window.osdTimer);
    window.osdTimer = setTimeout(() => osd.classList.add('osd-hidden'), CONFIG.osdTimeout);
}

function showRadioScreen() {
    showScreen('radio-view');
    state.focusedArea = 'radio-tab';
    state.focusedIndex = 0;
    renderRadios();
}

function renderRadios() {
    document.getElementById('radio-list').innerHTML = state.radios.map((r, i) =>
        `<div class="list-item radio-item" data-index="${i}">${r.name}</div>`
    ).join('');
}

function handleRadioKeys(key) {
    if (state.focusedArea === 'radio-tab') {
        const tabs = document.querySelectorAll('.tab');
        if (key === 'ArrowRight') state.focusedIndex = (state.focusedIndex + 1) % tabs.length;
        if (key === 'ArrowLeft') state.focusedIndex = (state.focusedIndex - 1 + tabs.length) % tabs.length;
        if (key === 'ArrowDown') { state.focusedArea = 'radio-item'; state.focusedIndex = 0; }
        if (key === 'Enter') {
            const country = tabs[state.focusedIndex].dataset.country;
            state.radioCountry = country;
            tabs.forEach(t => t.classList.remove('active'));
            tabs[state.focusedIndex].classList.add('active');
            fetchRadios(country);
        }
    } else if (state.focusedArea === 'radio-item') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.radios.length;
        if (key === 'ArrowUp') {
            if (state.focusedIndex === 0) { state.focusedArea = 'radio-tab'; state.focusedIndex = 0; }
            else state.focusedIndex--;
        }
        if (key === 'Enter') playRadio(state.radios[state.focusedIndex]);
    }
    if (key === 'Backspace') showDashboard();
}

function updateRadioPreview(r) {
    if(!r) return;
    document.getElementById('radio-current-name').innerText = r.name;
    document.getElementById('radio-current-logo').src = r.favicon || 'radio.png';
}

function playRadio(r) {
    document.getElementById('radio-status').innerText = "Oynadılır: " + r.name;
    const audio = new Audio(r.url);
    audio.play();
}
