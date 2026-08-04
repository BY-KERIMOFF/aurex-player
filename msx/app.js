const CONFIG = {
    authUrl: 'http://kanal65.xyz/api.php?mac=',
    m3uDefault: 'http://kanal65.xyz/by-kerimoff-player/playlist.m3u',
    weatherApi: 'https://api.open-meteo.com/v1/forecast?latitude=40.4093&longitude=49.8671&current_weather=true',
    epgProxy: 'https://epg.pw/xmltv/feed/az.xml',
    osdTimeout: 5000,
    volumeTimeout: 3000,
    numericTimeout: 1500
};

let state = {
    screen: 'splash',
    mac: localStorage.getItem('aurex_mac') || '',
    m3uUrl: '',
    isAdultEnabled: true,
    allChannels: [],
    movies: [],
    series: [],
    liveTv: [],
    categories: [],
    currentCategory: 'Hamısı',
    filteredChannels: [],
    epgData: {},
    focusedArea: 'mac-input',
    focusedIndex: 0,
    currentChannelIndex: -1,
    volume: 50,
    numericInput: '',
    numericTimer: null,
    lastChannelUrl: localStorage.getItem('aurex_last_url') || '',
    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
    recentTv: JSON.parse(localStorage.getItem('aurex_recent_tv') || '[]'),
    hls: new Hls(),
    currentMode: 'live' // 'live', 'movie', 'series'
};

const radioAudio = new Audio();

// --- Initialization ---
window.onload = () => {
    setupClock();
    startLaunchSequence();
};

function setupClock() {
    const update = () => {
        const now = new Date();
        const time = now.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit' });
        const date = now.toLocaleDateString('az-AZ', { weekday: 'long', day: 'numeric', month: 'long' });
        if(document.getElementById('clock')) document.getElementById('clock').innerText = time;
        if(document.getElementById('osd-clock')) document.getElementById('osd-clock').innerText = time;
        if(document.getElementById('date')) document.getElementById('date').innerText = date;
    };
    update();
    setInterval(update, 1000);
}

async function startLaunchSequence() {
    showScreen('splash-screen');
    setTimeout(async () => {
        if (state.mac) await performAuth(state.mac);
        else showLogin();
    }, 3000);
}

// --- Auth & Data ---
async function performAuth(mac) {
    try {
        const res = await fetch(CONFIG.authUrl + mac);
        const data = await res.json();
        if (data.status === 'success') {
            state.mac = mac;
            localStorage.setItem('aurex_mac', mac);
            state.m3uUrl = data.m3uUrl || CONFIG.m3uDefault;
            state.isAdultEnabled = data.is_adult === 1;

            await loadPlaylistAndEPG();

            if (state.lastChannelUrl) {
                const idx = state.allChannels.findIndex(c => c.url === state.lastChannelUrl);
                if (idx !== -1) {
                    state.currentChannelIndex = idx;
                    state.currentMode = isVod(state.allChannels[idx].url) ? 'movie' : 'live';
                    startFullscreen(state.allChannels[idx]);
                    return;
                }
            }
            showDashboard();
        } else showLogin(data.message || 'MAC ünvanı aktiv edilməyib.');
    } catch (e) { showLogin('Server bağlantısı kəsildi.'); }
}

async function loadPlaylistAndEPG() {
    await fetchM3U();
    fetchEPG();
    fetchWeather();
    fetchCurrency();
}

function isVod(url) {
    if(!url) return false;
    const low = url.toLowerCase();
    return low.includes('.mp4') || low.includes('.mkv') || low.includes('/movie/') || low.includes('/series/') || low.includes('type=vod');
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
                cur = { name, logo, group, tvgId, id: btoa(name).substring(0,8) };
            } else if (line.startsWith('http')) {
                if (cur) {
                    cur.url = line;
                    chans.push(cur);
                    cur = null;
                }
            }
        });
        state.allChannels = chans;

        // Smart Separation
        state.movies = chans.filter(c => c.url.toLowerCase().includes('.mp4') || c.url.toLowerCase().includes('.mkv') || c.url.toLowerCase().includes('/movie/'));
        state.series = chans.filter(c => c.url.toLowerCase().includes('/series/'));
        state.liveTv = chans.filter(c => !isVod(c.url));

        renderRecentTv();
    } catch (e) {}
}

function renderRecentTv() {
    if (state.recentTv.length > 0) {
        const container = document.getElementById('recent-tv-list');
        container.innerHTML = state.recentTv.slice(0, 10).map(c => `
            <div class="resume-card">
                <img src="${c.logo}" class="resume-img" onerror="this.src='placeholder.png'">
                <div class="resume-overlay"><span class="resume-name">${c.name}</span></div>
            </div>
        `).join('');
        document.getElementById('recentTvSection').classList.remove('hidden');
    }
}

function addToRecent(chan) {
    if (isVod(chan.url)) return; // Only Live TV
    state.recentTv = state.recentTv.filter(c => c.url !== chan.url);
    state.recentTv.unshift(chan);
    if (state.recentTv.length > 15) state.recentTv.pop();
    localStorage.setItem('aurex_recent_tv', JSON.stringify(state.recentTv));
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
    if(err) { const el = document.getElementById('login-error'); el.innerText = err; el.classList.remove('hidden'); }
    updateFocus();
}

function showDashboard() {
    showScreen('dashboard');
    state.focusedArea = 'cards';
    state.focusedIndex = 0;
    renderRecentTv();
    updateFocus();
}

function showTV(mode = 'live', cat = 'Hamısı') {
    state.currentMode = mode;
    state.currentCategory = cat;
    showScreen('tv-panel');

    let source = mode === 'movie' ? state.movies : (mode === 'series' ? state.series : state.liveTv);
    const groups = [...new Set(source.map(c => c.group))];
    state.categories = ["Hamısı", "Sevimlilər", ...groups].sort();

    renderCategories();
    renderChannels();
    state.focusedArea = 'channels';
    state.focusedIndex = 0;
    updateFocus();
}

function renderCategories() {
    document.getElementById('category-list').innerHTML = state.categories.map((c, i) => `
        <div class="item-channel focusable-cat ${c===state.currentCategory?'active-cat':''}" data-index="${i}">${c}</div>
    `).join('');
}

function renderChannels() {
    const query = document.getElementById('etSearch').value.toLowerCase();
    let source = state.currentMode === 'movie' ? state.movies : (state.currentMode === 'series' ? state.series : state.liveTv);

    let list = source;
    if(state.currentCategory === 'Sevimlilər') list = state.allChannels.filter(c => state.favorites.includes(c.id));
    else if(state.currentCategory !== 'Hamısı') list = source.filter(c => c.group === state.currentCategory);
    if(query) list = list.filter(c => c.name.toLowerCase().includes(query));

    state.filteredChannels = list;
    document.getElementById('channel-list').innerHTML = list.map((c, i) => `
        <div class="item-channel focusable-chan" data-index="${i}">
            <img src="${c.logo}" class="chan-logo" onerror="this.src='placeholder.png'">
            <div class="chan-name">${c.name}</div>
            ${state.favorites.includes(c.id)?'<span class="text-gold">⭐</span>':''}
        </div>
    `).join('');
}

function updateFocus() {
    document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));
    let sel = '';
    if(state.focusedArea === 'mac-input') sel = '#mac-input';
    else if(state.focusedArea === 'btn-login') sel = '#btn-login';
    else if(state.focusedArea === 'cards') sel = '.card';
    else if(state.focusedArea === 'radio') sel = '#cardRadio';
    else if(state.focusedArea === 'categories') sel = '.focusable-cat';
    else if(state.focusedArea === 'channels') sel = '.focusable-chan';
    else if(state.focusedArea === 'search') sel = '#etSearch';

    const el = document.querySelectorAll(sel)[state.focusedIndex] || document.querySelector(sel);
    if(el) {
        el.classList.add('focused');
        el.scrollIntoView({ block: 'center', behavior: 'smooth' });
        if(state.focusedArea === 'channels') updatePreview(state.filteredChannels[state.focusedIndex]);
    }
}

function updatePreview(chan) {
    if(!chan) return;
    document.getElementById('current-name').innerText = chan.name;
    document.getElementById('current-logo').src = chan.logo;
    const vid = document.getElementById('mini-player');
    if(Hls.isSupported()) { state.hls.loadSource(chan.url); state.hls.attachMedia(vid); }
    else vid.src = chan.url;
}

// --- Key Event System ---
window.onkeydown = (e) => {
    const key = e.key;
    if(state.screen === 'login-screen') handleLoginKey(key);
    else if(state.screen === 'dashboard') handleDashboardKey(key);
    else if(state.screen === 'tv-panel') handleTVKey(key);
    else if(state.screen === 'player-view') handlePlayerKey(key);

    if(key === 'AudioVolumeUp') changeVolume(5);
    if(key === 'AudioVolumeDown') changeVolume(-5);
    updateFocus();
};

function handleLoginKey(key) {
    if(key === 'ArrowDown') state.focusedArea = 'btn-login';
    if(key === 'ArrowUp') state.focusedArea = 'mac-input';
    if(key === 'Enter' && state.focusedArea === 'btn-login') performAuth(document.getElementById('mac-input').value);
}

function handleDashboardKey(key) {
    if(state.focusedArea === 'cards') {
        const cards = document.querySelectorAll('.card');
        if(key === 'ArrowRight') state.focusedIndex = (state.focusedIndex + 1) % cards.length;
        if(key === 'ArrowLeft') state.focusedIndex = (state.focusedIndex - 1 + cards.length) % cards.length;
        if(key === 'ArrowDown') { state.focusedArea = 'radio'; state.focusedIndex = 0; }
        if(key === 'Enter') {
            const act = cards[state.focusedIndex].dataset.action;
            if(act==='live-tv') showTV('live');
            if(act==='movies') showTV('movie');
            if(act==='series') showTV('series');
        }
    } else if(state.focusedArea === 'radio') {
        if(key === 'ArrowUp') { state.focusedArea = 'cards'; state.focusedIndex = 0; }
    }
}

function handleTVKey(key) {
    if(state.focusedArea === 'channels') {
        if(key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.filteredChannels.length;
        if(key === 'ArrowUp') { if(state.focusedIndex === 0) { state.focusedArea = 'search'; state.focusedIndex = 0; } else state.focusedIndex--; }
        if(key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
        if(key === 'Enter') startFullscreen(state.filteredChannels[state.focusedIndex]);
    } else if(state.focusedArea === 'categories') {
        if(key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.categories.length;
        if(key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + state.categories.length) % state.categories.length;
        if(key === 'ArrowRight') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if(key === 'Enter') showTV(state.currentMode, state.categories[state.focusedIndex]);
    } else if(state.focusedArea === 'search') {
        if(key === 'ArrowDown') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if(key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
    }
    if(key === 'Backspace') showDashboard();
}

function startFullscreen(chan) {
    showScreen('player-view');
    document.getElementById('mini-player').pause();
    const vid = document.getElementById('main-player');
    if(Hls.isSupported()) { state.hls.loadSource(chan.url); state.hls.attachMedia(vid); }
    else vid.src = chan.url;

    state.lastChannelUrl = chan.url;
    localStorage.setItem('aurex_last_url', chan.url);
    addToRecent(chan);

    document.getElementById('tvChannelName').innerText = chan.name;
    document.getElementById('ivChannelLogo').src = chan.logo;
    triggerOSD();
}

function handlePlayerKey(key) {
    if(key === 'Backspace' || key === 'Escape') {
        const osd = document.getElementById('osdLayout');
        if(osd.classList.contains('osd-hidden')) { document.getElementById('main-player').pause(); showTV(state.currentMode, state.currentCategory); }
        else osd.classList.add('osd-hidden');
    }
    if(key === 'Enter' || key === 'ArrowUp' || key === 'ArrowDown') triggerOSD();
}

function triggerOSD() {
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
    document.getElementById('vol-val').innerText = state.volume + '%';
    const volUI = document.getElementById('volume-ui');
    volUI.classList.remove('vol-hidden');
    clearTimeout(window.volTimer);
    window.volTimer = setTimeout(() => document.getElementById('volume-ui').classList.add('vol-hidden'), CONFIG.volumeTimeout);
}

function handleNumeric(digit) {
    state.numericInput += digit;
    const el = document.getElementById('numeric-input');
    el.innerText = state.numericInput; el.classList.remove('hidden');
    clearTimeout(state.numericTimer);
    state.numericTimer = setTimeout(() => {
        const idx = parseInt(state.numericInput) - 1;
        if(idx >= 0 && idx < state.allChannels.length) startFullscreen(state.allChannels[idx]);
        state.numericInput = ''; el.classList.add('hidden');
    }, CONFIG.numericTimeout);
}

function toggleAspect() {
    state.currentAspect = (state.currentAspect + 1) % state.aspectModes.length;
    const mode = state.aspectModes[state.currentAspect];
    document.getElementById('main-player').style.objectFit = mode==='FILL'?'fill':(mode==='FIT'?'contain':'cover');
    const toast = document.getElementById('aspect-toast');
    toast.innerText = "Format: " + mode; toast.classList.remove('hidden');
    setTimeout(() => toast.classList.add('hidden'), 2000);
}

function fetchEPG() {}
function fetchWeather() {}
