/**
 * AUREX PLAYER WEB (MSX) - 1:1 ANDROID MIRROR
 * Developed by BY-KERIMOFF
 */

const CONFIG = {
    authUrl: 'http://kanal65.xyz/api.php?mac=',
    m3uDefault: 'http://kanal65.xyz/by-kerimoff-player/playlist.m3u',
    weatherApi: 'https://api.open-meteo.com/v1/forecast?latitude=40.4093&longitude=49.8671&current_weather=true',
    currencyApi: 'https://www.cbar.az/currencies/', // Using proxy or static for web if needed
    epgProxy: 'https://epg.pw/xmltv/feed/az.xml',
    osdTimeout: 5000,
    volumeTimeout: 3000,
    numericTimeout: 2000,
    kidsModePin: '2266'
};

const KIDS_KEYWORDS = [
    "cizgi", "kids", "детские", "uşaq", "cartoon", "animation",
    "trt çocuk", "minika", "disney", "boing", "nick", "baby",
    "junior", "family", "uşaqlar"
];

let state = {
    screen: 'splash',
    mac: localStorage.getItem('aurex_mac') || '',
    m3uUrl: '',
    isAdultEnabled: true,
    kidsModeActive: localStorage.getItem('aurex_kids_mode') === 'true',

    allChannels: [],
    categories: [],
    liveChannels: [],
    movieChannels: [],
    seriesChannels: [],
    favoriteChannels: [],

    currentList: [], // The list currently being viewed
    currentCategory: null,
    currentMode: 'live', // 'live', 'movie', 'series', 'fav'

    focusedArea: 'mac-input',
    focusedIndex: 0,

    volume: parseInt(localStorage.getItem('aurex_volume')) || 50,
    currentChannel: null,
    lastUrl: localStorage.getItem('aurex_last_url') || '',

    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
    recent: JSON.parse(localStorage.getItem('aurex_recent') || '[]'),

    hls: null,
    osdTimer: null,
    volTimer: null,
    numericInput: '',
    numericTimer: null,

    speedTest: {
        isTesting: false,
        maxMbps: 0,
        currentMbps: 0,
        ping: 0
    }
};

// --- System Core ---

window.onload = () => {
    if (initSecurity()) return;
    initFocusSystem();
    setupClock();
    startLaunchSequence();
};

function setupClock() {
    const days = ["Bazar", "Bazar ertəsi", "Çərşənbə axşamı", "Çərşənbə", "Cümə axşamı", "Cümə", "Şənbə"];
    const months = ["Yanvar", "Fevral", "Mart", "Aprel", "May", "İyun", "İyul", "Avqust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr"];

    const update = () => {
        const now = new Date();
        const h = now.getHours().toString().padStart(2, '0');
        const m = now.getMinutes().toString().padStart(2, '0');
        const day = days[now.getDay()];
        const dateNum = now.getDate();
        const month = months[now.getMonth()];

        document.getElementById('clock').innerText = `${h}:${m}`;
        if(document.getElementById('osd-clock')) document.getElementById('osd-clock').innerText = `${h}:${m}`;
        document.getElementById('date').innerText = `${day}, ${dateNum} ${month}`;
    };
    update();
    setInterval(update, 1000);
}

async function startLaunchSequence() {
    showScreen('splash-screen');

    // URL-dən və ya yaddaşdan MAC-ı götür (Avtomatik giriş üçün)
    const urlParams = new URLSearchParams(window.location.search);
    const autoMac = urlParams.get('mac');

    if (autoMac) {
        state.mac = autoMac.toUpperCase();
        localStorage.setItem('aurex_mac', state.mac);
    }

    setTimeout(async () => {
        if (state.mac) {
            await performAuth(state.mac);
        } else {
            showLogin();
        }
    }, 3500);
}

// --- Auth & API ---

async function performAuth(mac) {
    try {
        // In real app, we would add the dynamic token here if we didn't revert it
        const res = await fetch(CONFIG.authUrl + mac.toUpperCase());
        const data = await res.json();

        if (data.status === 'success') {
            state.mac = mac.toUpperCase();
            localStorage.setItem('aurex_mac', state.mac);
            state.m3uUrl = data.m3u_url || CONFIG.m3uDefault;
            state.isAdultEnabled = data.is_adult === 1;

            if (data.expire_date) {
                const el = document.getElementById('expiry-info');
                el.innerText = `Abunəlik bitir: ${data.expire_date}`;
                el.classList.remove('hidden');
            }

            await loadData();
            showDashboard();

            // Auto-start last channel if enabled
            if (state.lastUrl && state.allChannels.length > 0) {
                const last = state.allChannels.find(c => c.url === state.lastUrl);
                if (last) {
                    playChannel(last);
                }
            }
        } else {
            showLogin(data.message || 'Giriş uğursuz oldu.');
        }
    } catch (e) {
        showLogin('Bağlantı xətası.');
    }
}

async function loadData() {
    await fetchM3U();
    fetchWeather();
    fetchCurrency();
}

async function fetchM3U() {
    try {
        const res = await fetch(state.m3uUrl);
        const text = await res.text();
        const lines = text.split('\n');

        let channels = [];
        let cur = null;

        lines.forEach(line => {
            line = line.trim();
            if (line.startsWith('#EXTINF')) {
                const name = line.split(',').pop().trim();
                const logo = line.match(/tvg-logo="([^"]*)"/)?.[1] || '';
                const group = line.match(/group-title="([^"]*)"/)?.[1] || 'Digər';
                const tvgId = line.match(/tvg-id="([^"]*)"/)?.[1] || '';
                cur = { id: btoa(name).substring(0, 12), name, logo, group, tvgId };
            } else if (line.startsWith('http')) {
                if (cur) {
                    cur.url = line;
                    channels.push(cur);
                    cur = null;
                }
            }
        });

        state.allChannels = channels;

        // Categorize
        state.liveChannels = channels.filter(c => !isVod(c.url));
        state.movieChannels = channels.filter(c => c.url.toLowerCase().includes('/movie/') || c.url.toLowerCase().endsWith('.mp4') || c.url.toLowerCase().endsWith('.mkv'));
        state.seriesChannels = channels.filter(c => c.url.toLowerCase().includes('/series/'));

        updateCategories();
    } catch (e) {
        console.error("M3U Load Error", e);
    }
}

function isVod(url) {
    const low = url.toLowerCase();
    return low.includes('.mp4') || low.includes('.mkv') || low.includes('/movie/') || low.includes('/series/') || low.includes('type=vod');
}

function updateCategories() {
    const source = (state.currentMode === 'movie') ? state.movieChannels :
                   (state.currentMode === 'series') ? state.seriesChannels : state.liveChannels;

    let groups = [...new Set(source.map(c => c.group))];

    if (state.kidsModeActive) {
        groups = groups.filter(g => KIDS_KEYWORDS.some(k => g.toLowerCase().includes(k)));
    }

    state.categories = ["Hamısı", "Sevimlilər", ...groups].sort((a, b) => {
        if (a === "Hamısı") return -1;
        if (b === "Hamısı") return 1;
        if (a === "Sevimlilər") return -1;
        if (b === "Sevimlilər") return 1;
        return a.localeCompare(b);
    });
}

// --- Navigation & UI ---

function showScreen(id) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(id).classList.add('active');
    state.screen = id;
}

function showLogin(err = '') {
    showScreen('login-screen');
    state.focusedArea = 'mac-input';
    document.getElementById('device-mac').innerText = "Veb Pleyer"; // Web version limitation
    if (err) {
        const el = document.getElementById('login-error');
        el.innerText = err;
        el.classList.remove('hidden');
    }
    updateFocus();
}

function showDashboard() {
    showScreen('dashboard');
    updateDashboardUI();
    state.focusedArea = 'cards';
    state.focusedIndex = 0;
    updateFocus();
}

function showError(title, msg) {
    const overlay = document.getElementById('error-overlay');
    document.getElementById('error-title').innerText = title;
    document.getElementById('error-message').innerText = msg;
    overlay.classList.remove('hidden');
    state.focusedArea = 'error-retry';
    updateFocus();
}

function updateDashboardUI() {
    const isKids = state.kidsModeActive;
    document.getElementById('card-movies').style.display = isKids ? 'none' : 'flex';
    document.getElementById('card-series').style.display = isKids ? 'none' : 'flex';
    document.getElementById('card-radio').style.display = isKids ? 'none' : 'flex';
    document.getElementById('btn-settings').style.display = isKids ? 'none' : 'flex';
    document.getElementById('btn-search').style.display = isKids ? 'none' : 'flex';
    document.getElementById('kids-mode-section-label').style.display = isKids ? 'none' : 'block';

    document.getElementById('kids-mode-title').innerText = isKids ? "REJİMDƏN ÇIX" : "UŞAQ REJİMİ";
    document.getElementById('kids-mode-subtitle').innerText = isKids ? "⚠️ Təhlükəsiz Rejim Aktivdir" : "Yalnız uşaqlar üçün kontent";
    document.getElementById('kids-mode-action').innerText = isKids ? "ÇIXIŞ" : "AKTİV ET";
    document.getElementById('kids-mode-icon').innerText = isKids ? "🔒" : "👶";

    document.getElementById('cards-container').style.justifyContent = isKids ? 'center' : 'flex-start';
}

function showTV(mode = 'live') {
    state.currentMode = mode;
    state.currentCategory = "Hamısı";
    updateCategories();
    renderCategories();
    renderChannels();
    showScreen('tv-panel');

    document.getElementById('tv-panel-title').innerText = (mode === 'movie' || mode === 'series') ? "FİLMLƏR / SERİALLAR" : "KANALLAR";
    document.getElementById('panel-preview').style.display = (mode === 'live') ? 'flex' : 'none';
    document.getElementById('panel-channels').style.flex = (mode === 'live') ? '2.8' : '6.2';

    state.focusedArea = 'categories';
    state.focusedIndex = 0;
    updateFocus();
}

function renderCategories() {
    const container = document.getElementById('category-list');
    container.innerHTML = state.categories.map((cat, i) => `
        <div class="item-channel focusable-cat ${cat === state.currentCategory ? 'active-cat' : ''}" data-index="${i}">
            ${cat}
        </div>
    `).join('');
}

function renderChannels() {
    const query = document.getElementById('et-search').value.toLowerCase();
    let list = [];
    const source = (state.currentMode === 'movie') ? state.movieChannels :
                   (state.currentMode === 'series') ? state.seriesChannels : state.liveChannels;

    if (state.currentCategory === 'Hamısı') list = source;
    else if (state.currentCategory === 'Sevimlilər') list = state.allChannels.filter(c => state.favorites.includes(c.id));
    else list = source.filter(c => c.group === state.currentCategory);

    if (query) list = list.filter(c => c.name.toLowerCase().includes(query));

    state.currentList = list;
    const container = document.getElementById('channel-list');
    container.innerHTML = list.map((chan, i) => `
        <div class="item-channel focusable-chan" data-index="${i}">
            <img src="${chan.logo}" class="chan-logo" onerror="this.src='placeholder.png'">
            <div class="chan-name">${chan.name}</div>
            ${state.favorites.includes(chan.id) ? '<span class="text-gold">⭐</span>' : ''}
        </div>
    `).join('');
}

// --- Focus System ---

function initFocusSystem() {
    window.addEventListener('keydown', (e) => {
        const key = e.key;

        // Global Volume
        if (key === 'AudioVolumeUp' || key === '+') changeVolume(5);
        if (key === 'AudioVolumeDown' || key === '-') changeVolume(-5);

        if (state.screen === 'login-screen') handleLoginNav(key);
        else if (state.screen === 'dashboard') handleDashboardNav(key);
        else if (state.screen === 'tv-panel') handleTVNav(key);
        else if (state.screen === 'player-view') handlePlayerNav(key);
        else if (state.screen === 'speed-test-panel') handleSpeedTestNav(key);

        updateFocus();
    });
}

function updateFocus() {
    document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));

    let selector = '';
    if (state.focusedArea === 'mac-input') selector = '#mac-input';
    else if (state.focusedArea === 'btn-login') selector = '#btn-login';
    else if (state.focusedArea === 'cards') selector = '.card';
    else if (state.focusedArea === 'radio') selector = '#card-radio';
    else if (state.focusedArea === 'speed-test') selector = '#card-speed-test';
    else if (state.focusedArea === 'kids-mode') selector = '#card-kids-mode';
    else if (state.focusedArea === 'footer-btns') selector = '.footer-row .focusable';
    else if (state.focusedArea === 'categories') selector = '.focusable-cat';
    else if (state.focusedArea === 'channels') selector = '.focusable-chan';
    else if (state.focusedArea === 'search-input') selector = '#et-search';
    else if (state.focusedArea === 'tv-back') selector = '#btn-tv-back';
    else if (state.focusedArea === 'speed-back') selector = '#btn-speed-back';
    else if (state.focusedArea === 'speed-start') selector = '#btn-start-test';
    else if (state.focusedArea === 'error-retry') selector = '#btn-error-retry';

    const elements = document.querySelectorAll(selector);
    const el = elements[state.focusedIndex] || elements[0];

    if (el) {
        el.classList.add('focused');
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });

        // Preview handling for TV list
        if (state.focusedArea === 'channels' && state.currentMode === 'live') {
            updatePreview(state.currentList[state.focusedIndex]);
        }
    }
}

function handleLoginNav(key) {
    if (key === 'ArrowDown') state.focusedArea = 'btn-login';
    if (key === 'ArrowUp') state.focusedArea = 'mac-input';
    if (key === 'Enter') {
        if (state.focusedArea === 'btn-login') performAuth(document.getElementById('mac-input').value);
        else document.getElementById('mac-input').focus();
    }
}

function handleDashboardNav(key) {
    if (state.focusedArea === 'cards') {
        const count = document.querySelectorAll('.card:not([style*="display: none"])').length;
        if (key === 'ArrowRight') state.focusedIndex = (state.focusedIndex + 1) % count;
        if (key === 'ArrowLeft') state.focusedIndex = (state.focusedIndex - 1 + count) % count;
        if (key === 'ArrowDown') { state.focusedArea = 'radio'; state.focusedIndex = 0; }
        if (key === 'Enter') {
            const act = document.querySelectorAll('.card:not([style*="display: none"])')[state.focusedIndex].dataset.action;
            if (act === 'live-tv') showTV('live');
            if (act === 'movies') showTV('movie');
            if (act === 'series') showTV('series');
        }
    } else if (state.focusedArea === 'radio') {
        if (key === 'ArrowUp') { state.focusedArea = 'cards'; state.focusedIndex = 0; }
        if (key === 'ArrowDown') { state.focusedArea = 'speed-test'; state.focusedIndex = 0; }
    } else if (state.focusedArea === 'speed-test') {
        if (key === 'ArrowUp') { state.focusedArea = 'radio'; state.focusedIndex = 0; }
        if (key === 'ArrowDown') { state.focusedArea = 'kids-mode'; state.focusedIndex = 0; }
        if (key === 'Enter') showSpeedTest();
    } else if (state.focusedArea === 'kids-mode') {
        if (key === 'ArrowUp') { state.focusedArea = 'speed-test'; state.focusedIndex = 0; }
        if (key === 'ArrowDown') { state.focusedArea = 'footer-btns'; state.focusedIndex = 0; }
        if (key === 'Enter') toggleKidsMode();
    } else if (state.focusedArea === 'footer-btns') {
        if (key === 'ArrowUp') { state.focusedArea = 'kids-mode'; state.focusedIndex = 0; }
        if (key === 'ArrowRight') state.focusedIndex = 1;
        if (key === 'ArrowLeft') state.focusedIndex = 0;
    }
}

function handleTVNav(key) {
    if (state.focusedArea === 'categories') {
        const count = state.categories.length;
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % count;
        if (key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + count) % count;
        if (key === 'ArrowRight') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if (key === 'Enter') {
            state.currentCategory = state.categories[state.focusedIndex];
            renderCategories();
            renderChannels();
        }
    } else if (state.focusedArea === 'channels') {
        const count = state.currentList.length;
        if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % count;
        if (key === 'ArrowUp') {
            if (state.focusedIndex === 0) { state.focusedArea = 'search-input'; state.focusedIndex = 0; }
            else state.focusedIndex--;
        }
        if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = state.categories.indexOf(state.currentCategory); }
        if (key === 'ArrowRight' && state.currentMode === 'live') { state.focusedArea = 'tv-back'; state.focusedIndex = 0; }
        if (key === 'Enter') playChannel(state.currentList[state.focusedIndex]);
    } else if (state.focusedArea === 'search-input') {
        if (key === 'ArrowDown') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
        if (key === 'Enter') document.getElementById('et-search').focus();
    } else if (state.focusedArea === 'tv-back') {
        if (key === 'ArrowLeft') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
        if (key === 'Enter') showDashboard();
    }
    if (key === 'Backspace' || key === 'Escape') showDashboard();
}

// --- Player Logic ---

function playChannel(chan) {
    state.currentChannel = chan;
    state.lastUrl = chan.url;
    localStorage.setItem('aurex_last_url', chan.url);

    showScreen('player-view');
    initPlayer(chan.url);

    document.getElementById('osd-name').innerText = chan.name;
    document.getElementById('osd-logo').src = chan.logo;
    document.getElementById('osd-quality').innerText = isVod(chan.url) ? 'VOD' : 'LIVE';

    triggerOSD();
}

function initPlayer(url) {
    const video = document.getElementById('main-player');
    if (state.hls) state.hls.destroy();

    if (url.includes('.m3u8')) {
        if (Hls.isSupported()) {
            state.hls = new Hls();
            state.hls.loadSource(url);
            state.hls.attachMedia(video);
            state.hls.on(Hls.Events.MANIFEST_PARSED, () => video.play());
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            video.src = url;
            video.addEventListener('loadedmetadata', () => video.play());
        }
    } else {
        video.src = url;
        video.play();
    }

    video.volume = state.volume / 100;
}

function handlePlayerNav(key) {
    if (key === 'Backspace' || key === 'Escape') {
        const osd = document.getElementById('osd-layout');
        if (!osd.classList.contains('osd-hidden')) {
            osd.classList.add('osd-hidden');
        } else {
            document.getElementById('main-player').pause();
            showTV(state.currentMode);
        }
    }
    if (key === 'Enter' || key === 'ArrowUp' || key === 'ArrowDown' || key === 'ArrowLeft' || key === 'ArrowRight') {
        triggerOSD();
    }
}

function triggerOSD() {
    const osd = document.getElementById('osd-layout');
    osd.classList.remove('osd-hidden');
    clearTimeout(state.osdTimer);
    state.osdTimer = setTimeout(() => osd.classList.add('osd-hidden'), CONFIG.osdTimeout);
}

function changeVolume(delta) {
    state.volume = Math.max(0, Math.min(100, state.volume + delta));
    localStorage.setItem('aurex_volume', state.volume);
    const video = document.getElementById('main-player');
    if (video) video.volume = state.volume / 100;

    document.getElementById('vol-fill').style.width = state.volume + '%';
    document.getElementById('vol-val').innerText = state.volume + '%';

    const ui = document.getElementById('volume-ui');
    ui.classList.remove('vol-hidden');
    clearTimeout(state.volTimer);
    state.volTimer = setTimeout(() => ui.classList.add('vol-hidden'), CONFIG.volumeTimeout);
}

// --- Kids Mode ---

function toggleKidsMode() {
    if (state.kidsModeActive) {
        showPinPad(() => {
            state.kidsModeActive = false;
            localStorage.setItem('aurex_kids_mode', 'false');
            showDashboard();
        });
    } else {
        state.kidsModeActive = true;
        localStorage.setItem('aurex_kids_mode', 'true');
        showDashboard();
    }
}

function showPinPad(callback) {
    const overlay = document.getElementById('pin-pad');
    overlay.classList.remove('hidden');
    let input = '';

    const handler = (e) => {
        if (e.key >= '0' && e.key <= '9') {
            input += e.key;
            document.getElementById('pin-stars').innerText = '*'.repeat(input.length).padEnd(4, '-');
            if (input.length === 4) {
                if (input === CONFIG.kidsModePin) {
                    overlay.classList.add('hidden');
                    window.removeEventListener('keydown', handler);
                    callback();
                } else {
                    input = '';
                    document.getElementById('pin-stars').innerText = '----';
                    alert("Səhv PİN!");
                }
            }
        } else if (e.key === 'Backspace') {
            overlay.classList.add('hidden');
            window.removeEventListener('keydown', handler);
        }
    };
    window.addEventListener('keydown', handler);
}

// --- Speed Test ---

function showSpeedTest() {
    showScreen('speed-test-panel');
    state.focusedArea = 'speed-start';
    state.focusedIndex = 0;
    updateFocus();
}

function handleSpeedTestNav(key) {
    if (key === 'ArrowUp') state.focusedArea = 'speed-back';
    if (key === 'ArrowDown') state.focusedArea = 'speed-start';
    if (key === 'Enter') {
        if (state.focusedArea === 'speed-start') runSpeedTest();
        if (state.focusedArea === 'speed-back') showDashboard();
    }
    if (key === 'Backspace') showDashboard();
}

async function runSpeedTest() {
    if (state.speedTest.isTesting) return;
    state.speedTest.isTesting = true;
    const btn = document.getElementById('btn-start-test');
    btn.innerText = "TEST GEDİR...";
    btn.disabled = true;

    // Reset UI
    document.getElementById('speed-max').innerText = "-- Mbps";
    document.getElementById('speed-ping').innerText = "-- ms";
    document.querySelectorAll('.q-item').forEach(el => el.classList.remove('q-active'));

    // 1. Fake Ping (for web simplicity)
    const startPing = Date.now();
    try { await fetch('https://1.1.1.1', { mode: 'no-cors' }); } catch(e){}
    const ping = Date.now() - startPing;
    document.getElementById('speed-ping').innerText = ping + " ms";

    // 2. Download Test (Chunked)
    const testFile = "https://speed.cloudflare.com/__down?bytes=5000000"; // 5MB
    const startTime = Date.now();
    let downloaded = 0;

    try {
        const response = await fetch(testFile);
        const reader = response.body.getReader();
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            downloaded += value.length;
            const elapsed = (Date.now() - startTime) / 1000;
            const mbps = (downloaded * 8) / (elapsed * 1000000);
            updateSpeedUI(mbps);
        }
    } catch (e) {}

    state.speedTest.isTesting = false;
    btn.innerText = "YENİDƏN BAŞLAT";
    btn.disabled = false;
}

function updateSpeedUI(mbps) {
    document.getElementById('current-speed').innerText = mbps.toFixed(1);
    const dash = 283 - (Math.min(mbps, 100) / 100 * 283);
    document.getElementById('gauge-fill').style.strokeDashoffset = dash;

    if (mbps > state.speedTest.maxMbps) {
        state.speedTest.maxMbps = mbps;
        document.getElementById('speed-max').innerText = mbps.toFixed(1) + " Mbps";
    }

    // Quality Indicators
    document.getElementById('q-sd').classList.toggle('q-active', mbps >= 2);
    document.getElementById('q-hd').classList.toggle('q-active', mbps >= 5);
    document.getElementById('q-fhd').classList.toggle('q-active', mbps >= 10);
    document.getElementById('q-4k').classList.toggle('q-active', mbps >= 25);

    const rec = mbps >= 25 ? "Mükəmməl! 4K yayım üçün tam uyğundur. ✅" :
                mbps >= 10 ? "Çox yaxşı! FHD kanalları rahat izləyə bilərsiniz. ✅" :
                mbps >= 5  ? "Yaxşı. HD yayım üçün kifayətdir. ⚠️" :
                mbps >= 2  ? "Zəif. Yalnız SD kanallarda stabil ola bilər. ⚠️" :
                             "Çox zəif internet! Donmalar qaçılmazdır. ❌";
    document.getElementById('speed-recommendation').innerText = rec;
}

// --- Data Fetching ---

function fetchWeather() {
    fetch(CONFIG.weatherApi).then(r => r.json()).then(data => {
        const temp = Math.round(data.current_weather.temperature);
        const code = data.current_weather.weathercode;
        document.getElementById('weather-temp').innerText = `${temp}°C`;
        document.getElementById('weather-emoji').innerText = getWeatherEmoji(code);
    }).catch(() => {});
}

function getWeatherEmoji(code) {
    // 1:1 Mirror of WeatherManager.kt
    if (code === 0) return "☀️";
    if ([1, 2, 3].includes(code)) return "🌤️";
    if ([45, 48].includes(code)) return "🌫️";
    if ([51, 53, 55, 61, 63, 65].includes(code)) return "🌧️";
    if ([71, 73, 75, 77, 85, 86].includes(code)) return "❄️";
    if ([95, 96, 99].includes(code)) return "⛈️";
    return "☁️";
}

function fetchCurrency() {
    // CBAR logic: USD, EUR, RUB, TRY, GBP, GEL
    const mockData = [
        { code: 'USD', value: '1.7000' },
        { code: 'EUR', value: '1.8450' },
        { code: 'RUB', value: '0.0185' },
        { code: 'TRY', value: '0.0510' },
        { code: 'GBP', value: '2.1600' },
        { code: 'GEL', value: '0.6300' }
    ];

    document.getElementById('currency-section').classList.remove('hidden');
    document.getElementById('currency-list').innerHTML = mockData.map(item => `
        <div class="speed-card glass-bg" style="min-width: 130px; padding: 15px;">
            <small style="color: var(--gray)">${item.code}</small>
            <div style="font-weight: 900; color: var(--gold); margin-top: 5px;">${item.value}</div>
        </div>
    `).join('');
}

// --- Security: Self-Defense (Android Mirror) ---

function initSecurity() {
    let violations = parseInt(localStorage.getItem('aurex_violations')) || 0;

    if (localStorage.getItem('aurex_perm_block') === 'true') {
        showError('BLOKLANDI', 'Cihaz təhlükəsizlik səbəbi ilə bloklanıb.');
        return true;
    }

    // Basic DevTools Detection (Browser equivalent of Sniffer/Debugger)
    const detect = () => {
        const start = Date.now();
        debugger;
        if (Date.now() - start > 100) {
            violations++;
            localStorage.setItem('aurex_violations', violations);
            if (violations >= 3) {
                localStorage.setItem('aurex_perm_block', 'true');
                location.reload();
            } else {
                alert(`Təhlükəsizlik xəbərdarlığı! (${violations}/3)`);
            }
        }
    };

    setInterval(detect, 5000);
    return false;
}

function updatePreview(chan) {
    if (!chan) return;
    document.getElementById('current-name').innerText = chan.name;
    document.getElementById('current-logo').src = chan.logo;
    const miniVideo = document.getElementById('mini-player');
    const loader = document.getElementById('mini-loader');

    loader.classList.remove('hidden');
    // Simplified mini player for web
    miniVideo.src = chan.url;
    miniVideo.onloadeddata = () => loader.classList.add('hidden');
}
