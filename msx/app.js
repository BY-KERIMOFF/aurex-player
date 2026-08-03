const CONFIG = {
    authUrl: 'http://kanal65.xyz/api.php?mac=',
    m3uDefault: 'http://kanal65.xyz/by-kerimoff-player/playlist.m3u',
    epgProxyUrl: 'https://epg.pw/xmltv/feed/az.xml',
    osdTimeout: 6000,
    numericTimeout: 1500,
    volumeTimeout: 3000
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
    currentChannelIndex: -1,
    hls: new Hls(),
    volume: 50,
    numericInput: '',
    numericTimer: null,
    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
};

// --- Initialization ---
window.onload = () => {
    initClock();
    launchApp();
};

function initClock() {
    const update = () => {
        const now = new Date();
        const time = now.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit' });
        if(document.getElementById('clock')) document.getElementById('clock').innerText = time;
        if(document.getElementById('osd-clock')) document.getElementById('osd-clock').innerText = time;
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

// --- Server & Auth ---
async function checkAuth(mac) {
    try {
        const res = await fetch(CONFIG.authUrl + mac);
        const data = await res.json();
        if (data.status === 'success') {
            state.mac = mac;
            localStorage.setItem('aurex_mac', mac);
            state.m3uUrl = data.m3uUrl || CONFIG.m3uDefault;
            await loadPlaylistAndEPG();
            showDashboard();
        } else showLogin(data.message);
    } catch (e) { showLogin("Bağlantı xətası"); }
}

async function loadPlaylistAndEPG() {
    await fetchM3U();
    fetchEPG(); // Background EPG fetch
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
                if (cur) { cur.url = line; chans.push(cur); cur = null; }
            }
        });
        state.allChannels = chans;
        state.categories = ["Hamısı", "Sevimlilər", ...new Set(chans.map(c => c.group))].sort();
    } catch (e) {}
}

async function fetchEPG() {
    try {
        const res = await fetch(CONFIG.epgProxyUrl);
        const text = await res.text();
        const parser = new DOMParser();
        const xml = parser.parseFromString(text, "application/xml");
        const programmes = xml.getElementsByTagName("programme");

        let epg = {};
        for (let p of programmes) {
            const channelId = p.getAttribute("channel");
            const title = p.getElementsByTagName("title")[0]?.textContent;
            const start = p.getAttribute("start");
            const stop = p.getAttribute("stop");

            if(!epg[channelId]) epg[channelId] = [];
            epg[channelId].push({ title, start, stop });
        }
        state.epgData = epg;
        renderChannels(); // Refresh channel list with EPG
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

    document.getElementById('channel-list').innerHTML = state.filteredChannels.map((c, i) => {
        const epg = getCurrentEPG(c.tvgId);
        return `
            <div class="list-item chan-item" data-index="${i}">
                <img src="${c.logo}" onerror="this.src='placeholder.png'">
                <div class="chan-info">
                    <div class="chan-name">${c.name}</div>
                    <div class="chan-epg">${epg ? epg.title : 'Canlı Yayım'}</div>
                </div>
            </div>
        `;
    }).join('');
}

function getCurrentEPG(tvgId) {
    if(!state.epgData[tvgId]) return null;
    const now = new Date().getTime();
    return state.epgData[tvgId].find(p => {
        const start = parseEPGTime(p.start);
        const stop = parseEPGTime(p.stop);
        return now >= start && now < stop;
    });
}

function parseEPGTime(timeStr) {
    // Format: 20230724230000 +0000
    const y = timeStr.substring(0,4), m = timeStr.substring(4,6)-1, d = timeStr.substring(6,8),
          h = timeStr.substring(8,10), min = timeStr.substring(10,12);
    return new Date(Date.UTC(y, m, d, h, min)).getTime();
}

function updateFocus() {
    document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));
    let sel = '';
    if (state.focusedArea === 'mac-input') sel = '#mac-input';
    else if (state.focusedArea === 'btn-login') sel = '#btn-login';
    else if (state.focusedArea === 'cards') sel = '.card';
    else if (state.focusedArea === 'categories') sel = '.cat-item';
    else if (state.focusedArea === 'channels') sel = '.chan-item';

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
    const epg = getCurrentEPG(chan.tvgId);
    document.getElementById('preview-epg-now').innerText = epg ? epg.title : 'Canlı Yayım';
    playVideo(document.getElementById('mini-player'), chan.url);
}

function playVideo(vid, url) {
    if (Hls.isSupported()) { state.hls.loadSource(url); state.hls.attachMedia(vid); }
    else vid.src = url;
}

// --- Key Handlers ---
window.onkeydown = (e) => {
    const key = e.key;
    if (state.screen === 'login-screen') handleLoginInput(key);
    else if (state.screen === 'dashboard') handleDashboardInput(key);
    else if (state.screen === 'tv-panel') handleTVInput(key);
    else if (state.screen === 'player-view') handlePlayerInput(key);

    // Volume Control
    if (key === 'AudioVolumeUp') changeVolume(5);
    if (key === 'AudioVolumeDown') changeVolume(-5);

    updateFocus();
};

function changeVolume(delta) {
    state.volume = Math.max(0, Math.min(100, state.volume + delta));
    const main = document.getElementById('main-player');
    if(main) main.volume = state.volume / 100;

    const volUI = document.getElementById('volume-overlay');
    document.getElementById('vol-bar-fill').style.width = state.volume + '%';
    document.getElementById('vol-percent').innerText = state.volume + '%';
    volUI.classList.remove('volume-hidden');

    clearTimeout(window.volTimer);
    window.volTimer = setTimeout(() => volUI.classList.add('volume-hidden'), CONFIG.volumeTimeout);
}

function handleTVInput(key) {
    if (state.focusedArea === 'channels') {
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % state.filteredChannels.length;
        if (key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + state.filteredChannels.length) % state.filteredChannels.length;
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
    }
    if (key === 'Backspace') showDashboard();
}

function startPlayer(chan) {
    showScreen('player-view');
    const main = document.getElementById('main-player');
    playVideo(main, chan.url);
    document.getElementById('osd-ch-name').innerText = chan.name;
    document.getElementById('osd-logo').src = chan.logo;
    const epg = getCurrentEPG(chan.tvgId);
    document.getElementById('osd-epg-info').innerText = epg ? epg.title : 'Canlı Yayım';
    showOSD();
}

function showOSD() {
    const osd = document.getElementById('player-osd');
    osd.classList.remove('osd-hidden');
    clearTimeout(window.osdTimer);
    window.osdTimer = setTimeout(() => osd.classList.add('osd-hidden'), CONFIG.osdTimeout);
}

function handlePlayerInput(key) {
    if (key === 'Backspace' || key === 'Escape') {
        const osd = document.getElementById('player-osd');
        if (osd.classList.contains('osd-hidden')) {
            document.getElementById('main-player').pause();
            showTV(state.currentCategory);
        } else osd.classList.add('osd-hidden');
    }
    if (key === 'Enter' || key === 'ArrowUp' || key === 'ArrowDown') showOSD();
}
