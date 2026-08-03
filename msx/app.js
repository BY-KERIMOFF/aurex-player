const CONFIG = {
    authUrl: 'http://kanal65.xyz/api.php?mac=',
    m3uDefault: 'http://kanal65.xyz/by-kerimoff-player/playlist.m3u',
    weatherApi: 'https://api.open-meteo.com/v1/forecast?latitude=40.4093&longitude=49.8671&current_weather=true',
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
    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
    focusedArea: 'mac-input',
    focusedIndex: 0,
    radioCountry: 'AZ',
    radios: [],
    hls: new Hls()
};

// --- Initial Launch ---
window.onload = () => {
    setupClock();
    launchFlow();
};

function setupClock() {
    const update = () => {
        const now = new Date();
        document.getElementById('clock').innerText = now.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit' });
        document.getElementById('date').innerText = now.toLocaleDateString('az-AZ', { weekday: 'long', day: 'numeric', month: 'long' });
        if(document.getElementById('osd-clock')) document.getElementById('osd-clock').innerText = now.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit' });
    };
    update();
    setInterval(update, 1000);
}

async function launchFlow() {
    showScreen('splash-screen');
    setTimeout(async () => {
        if (state.mac) await checkServerAuth(state.mac);
        else showLoginScreen();
    }, 3000);
}

// --- Auth & Data Fetching ---
async function checkServerAuth(mac) {
    try {
        const res = await fetch(CONFIG.authUrl + mac);
        const data = await res.json();
        if (data.status === 'success') {
            state.mac = mac;
            localStorage.setItem('aurex_mac', mac);
            state.m3uUrl = data.m3uUrl || CONFIG.m3uDefault;

            if (data.announcement) {
                const elan = document.getElementById('announcement-scroll');
                elan.innerText = data.announcement;
                if(data.announcementColor) elan.style.color = data.announcementColor;
                document.getElementById('announcement-bar').classList.remove('hidden');
            }

            await initializeAppData();
            showDashboard();
        } else {
            showLoginScreen(data.message || 'MAC ünvanı tapılmadı.');
        }
    } catch (e) {
        showLoginScreen("Serverə qoşulmaq mümkün olmadı.");
    }
}

async function initializeAppData() {
    await fetchM3U();
    fetchWeather();
    fetchCurrency();
    fetchRadios(state.radioCountry);
}

async function fetchM3U() {
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
                cur = { name, logo, group, id: btoa(name).substring(0, 8) };
            } else if (line.startsWith('http')) {
                if (cur) { cur.url = line; chans.push(cur); cur = null; }
            }
        });

        state.allChannels = chans;
        state.categories = ["Hamısı", "Sevimlilər", ...new Set(chans.map(c => c.group))].sort();
    } catch (e) {}
}

async function fetchWeather() {
    try {
        const res = await fetch(CONFIG.weatherApi);
        const data = await res.json();
        document.getElementById('weather-temp').innerText = Math.round(data.current_weather.temperature) + "°C";
        document.getElementById('weather-widget').classList.remove('hidden');
    } catch (e) {}
}

function fetchCurrency() {
    // Simulated Currency (CBAR has CORS issues with direct browser fetch)
    const mock = [
        { code: 'USD', value: '1.7000' },
        { code: 'EUR', value: '1.8450' },
        { code: 'TRY', value: '0.0520' }
    ];
    document.getElementById('currency-list').innerHTML = mock.map(c => `
        <div class="list-item widget"><strong>${c.code}</strong> <span>${c.value}</span></div>
    `).join('');
    document.getElementById('currency-section').classList.remove('hidden');
}

async function fetchRadios(country) {
    try {
        const res = await fetch(CONFIG.radioApi + country);
        state.radios = await res.json();
        if(state.screen === 'radio-view') renderRadioList();
    } catch (e) {}
}

// --- Navigation Controller ---
function showScreen(id) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(id).classList.add('active');
    state.screen = id;
}

function showLoginScreen(err = '') {
    showScreen('login-screen');
    state.focusedArea = 'mac-input';
    if(err) {
        const errEl = document.getElementById('login-error');
        errEl.innerText = err;
        errEl.classList.remove('hidden');
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

function renderCategories() {
    document.getElementById('category-list').innerHTML = state.categories.map((c, i) => `
        <div class="list-item cat-item ${c === state.currentCategory ? 'active-cat' : ''}" data-index="${i}">${c}</div>
    `).join('');
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
    let selector = '';
    if (state.focusedArea === 'mac-input') selector = '#mac-input';
    else if (state.focusedArea === 'btn-login') selector = '#btn-login';
    else if (state.focusedArea === 'cards') selector = '.card:not(.mini)';
    else if (state.focusedArea === 'mini-card') selector = '.card.mini';
    else if (state.focusedArea === 'categories') selector = '.cat-item';
    else if (state.focusedArea === 'channels') selector = '.chan-item';
    else if (state.focusedArea === 'tv-search') selector = '#tv-search';
    else if (state.focusedArea === 'radio-tab') selector = '.tab';
    else if (state.focusedArea === 'radio-item') selector = '.list-item.radio-item';

    const elements = document.querySelectorAll(selector);
    const target = elements[state.focusedIndex];
    if (target) {
        target.classList.add('focused');
        target.scrollIntoView({ block: 'center', behavior: 'smooth' });
        if (state.focusedArea === 'channels') updatePreview(state.filteredChannels[state.focusedIndex]);
    }
}

function updatePreview(chan) {
    if(!chan) return;
    document.getElementById('preview-name').innerText = chan.name;
    document.getElementById('preview-logo').src = chan.logo;
    const vid = document.getElementById('mini-player');
    if (Hls.isSupported()) {
        state.hls.loadSource(chan.url);
        state.hls.attachMedia(vid);
    } else {
        vid.src = chan.url;
    }
}

// --- Input Processor ---
window.onkeydown = (e) => {
    const key = e.key;
    if (state.screen === 'login-screen') handleLoginInput(key);
    else if (state.screen === 'dashboard') handleDashboardInput(key);
    else if (state.screen === 'tv-panel') handleTVInput(key);
    else if (state.screen === 'player-view') handlePlayerInput(key);
    else if (state.screen === 'radio-view') handleRadioInput(key);
    updateFocus();
};

function handleLoginInput(key) {
    if (key === 'ArrowDown') state.focusedArea = 'btn-login';
    if (key === 'ArrowUp') state.focusedArea = 'mac-input';
    if (key === 'Enter') {
        if (state.focusedArea === 'btn-login') checkServerAuth(document.getElementById('mac-input').value);
    }
}

function handleDashboardInput(key) {
    const cards = document.querySelectorAll('.card:not(.mini)');
    if (key === 'ArrowRight') state.focusedIndex = (state.focusedIndex + 1) % cards.length;
    if (key === 'ArrowLeft') state.focusedIndex = (state.focusedIndex - 1 + cards.length) % cards.length;
    if (key === 'ArrowDown') { state.focusedArea = 'mini-card'; state.focusedIndex = 0; }
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
    if (key === 'Backspace' || key === 'Escape') showDashboard();
}

function startPlayer(chan) {
    showScreen('player-view');
    document.getElementById('mini-player').pause();
    const main = document.getElementById('main-player');
    if (Hls.isSupported()) {
        state.hls.loadSource(chan.url);
        state.hls.attachMedia(main);
    } else {
        main.src = chan.url;
    }
    document.getElementById('osd-name').innerText = chan.name;
    document.getElementById('osd-logo').src = chan.logo;
    triggerOSD();
}

function triggerOSD() {
    const osd = document.getElementById('player-osd');
    osd.classList.remove('osd-hidden');
    clearTimeout(window.osdHideTimer);
    window.osdHideTimer = setTimeout(() => osd.classList.add('osd-hidden'), CONFIG.osdTimeout);
}

function handlePlayerInput(key) {
    if (key === 'Backspace' || key === 'Escape') {
        document.getElementById('main-player').pause();
        showTV(state.currentCategory);
    }
    if (key === 'Enter' || key === 'ArrowUp' || key === 'ArrowDown') triggerOSD();
}
