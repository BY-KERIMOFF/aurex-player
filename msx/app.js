const CONFIG = {
    m3uUrl: 'http://kanal65.xyz/by-kerimoff-player/playlist.m3u',
    osdTimeout: 5000
};

let state = {
    screen: 'dashboard',
    allChannels: [],
    categories: [],
    currentCategory: null,
    filteredChannels: [],
    focusedIndex: 0,
    focusedArea: 'cards', // 'cards', 'categories', 'channels', 'search'
    favorites: JSON.parse(localStorage.getItem('aurex_favorites') || '[]'),
    resumeList: JSON.parse(localStorage.getItem('aurex_resume') || '[]')
};

const hls = new Hls();
const mainVideo = document.getElementById('main-player');
const miniVideo = document.getElementById('mini-player');

// --- Initialization ---
async function init() {
    updateClock();
    setInterval(updateClock, 1000);

    await loadM3U();
    setupNavigation();
    renderDashboard();
}

async function loadM3U() {
    try {
        const response = await fetch(CONFIG.m3uUrl);
        const text = await response.text();
        parseM3U(text);
    } catch (e) {
        console.error("M3U yüklənmədi", e);
    }
}

function parseM3U(content) {
    const lines = content.split('\n');
    let channels = [];
    let currentChannel = null;

    lines.forEach(line => {
        line = line.trim();
        if (line.startsWith('#EXTINF')) {
            const name = line.split(',').pop();
            const logo = line.match(/tvg-logo="([^"]*)"/)?.[1] || '';
            const group = line.match(/group-title="([^"]*)"/)?.[1] || 'Digər';
            currentChannel = { name, logo, group };
        } else if (line.startsWith('http')) {
            if (currentChannel) {
                currentChannel.url = line;
                channels.push(currentChannel);
                currentChannel = null;
            }
        }
    });

    state.allChannels = channels;
    state.categories = [...new Set(channels.map(c => c.group))].sort();
}

// --- Rendering ---
function renderDashboard() {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById('dashboard').classList.add('active');
    state.screen = 'dashboard';
    state.focusedArea = 'cards';
    state.focusedIndex = 0;
    updateFocus();
}

function renderTVPanel(categoryName) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById('tv-panel').classList.add('active');
    state.screen = 'tv-panel';
    state.currentCategory = categoryName;

    // Categories
    const catList = document.getElementById('category-list');
    catList.innerHTML = state.categories.map((cat, i) => `
        <div class="list-item focusable-cat ${cat === categoryName ? 'selected' : ''}" data-index="${i}">
            ${cat}
        </div>
    `).join('');

    // Channels
    filterChannels(categoryName);
    renderChannels();

    state.focusedArea = 'channels';
    state.focusedIndex = 0;
    updateFocus();
}

function renderChannels() {
    const chanList = document.getElementById('channel-list');
    chanList.innerHTML = state.filteredChannels.map((chan, i) => `
        <div class="list-item focusable-chan" data-index="${i}">
            <img src="${chan.logo || 'placeholder.png'}" onerror="this.src='placeholder.png'">
            <span>${chan.name}</span>
        </div>
    `).join('');
}

function filterChannels(category) {
    if (!category) state.filteredChannels = state.allChannels;
    else state.filteredChannels = state.allChannels.filter(c => c.group === category);
}

// --- Navigation Engine ---
function updateFocus() {
    document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));

    let selector = '';
    if (state.focusedArea === 'cards') selector = '.card';
    else if (state.focusedArea === 'categories') selector = '.focusable-cat';
    else if (state.focusedArea === 'channels') selector = '.focusable-chan';
    else if (state.focusedArea === 'search') selector = '#search-input';

    const elements = document.querySelectorAll(selector);
    if (elements[state.focusedIndex]) {
        elements[state.focusedIndex].classList.add('focused');
        elements[state.focusedIndex].scrollIntoView({ block: 'center', behavior: 'smooth' });

        // Preview logic
        if (state.focusedArea === 'channels') {
            const channel = state.filteredChannels[state.focusedIndex];
            if (channel) updatePreview(channel);
        }
    }
}

function updatePreview(channel) {
    document.getElementById('current-name').innerText = channel.name;
    document.getElementById('current-logo').src = channel.logo;
    playVideo(miniVideo, channel.url);
}

function playVideo(videoEl, url) {
    if (Hls.isSupported()) {
        hls.loadSource(url);
        hls.attachMedia(videoEl);
    } else if (videoEl.canPlayType('application/vnd.apple.mpegurl')) {
        videoEl.src = url;
    }
}

function handleKey(e) {
    const key = e.key;
    const cardsCount = document.querySelectorAll('.card').length;
    const catsCount = state.categories.length;
    const chansCount = state.filteredChannels.length;

    if (state.screen === 'dashboard') {
        if (key === 'ArrowRight') state.focusedIndex = (state.focusedIndex + 1) % cardsCount;
        if (key === 'ArrowLeft') state.focusedIndex = (state.focusedIndex - 1 + cardsCount) % cardsCount;
        if (key === 'Enter') {
            const action = document.querySelectorAll('.card')[state.focusedIndex].dataset.action;
            if (action === 'live-tv') renderTVPanel(state.categories[0]);
        }
    } else if (state.screen === 'tv-panel') {
        if (state.focusedArea === 'channels') {
            if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % chansCount;
            if (key === 'ArrowUp') {
                if (state.focusedIndex === 0) { state.focusedArea = 'search'; state.focusedIndex = 0; }
                else state.focusedIndex--;
            }
            if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
            if (key === 'Enter') startFullscreen(state.filteredChannels[state.focusedIndex]);
        } else if (state.focusedArea === 'categories') {
            if (key === 'ArrowDown') state.focusedIndex = (state.focusedIndex + 1) % catsCount;
            if (key === 'ArrowUp') state.focusedIndex = (state.focusedIndex - 1 + catsCount) % catsCount;
            if (key === 'ArrowRight') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
            if (key === 'Enter') renderTVPanel(state.categories[state.focusedIndex]);
        } else if (state.focusedArea === 'search') {
            if (key === 'ArrowDown') { state.focusedArea = 'channels'; state.focusedIndex = 0; }
            if (key === 'ArrowLeft') { state.focusedArea = 'categories'; state.focusedIndex = 0; }
        }

        if (key === 'Backspace' || key === 'Escape') renderDashboard();
    } else if (state.screen === 'fullscreen-player') {
        if (key === 'Backspace' || key === 'Escape') closeFullscreen();
        if (key === 'Enter' || key === 'ArrowUp' || key === 'ArrowDown') showOSD();
        if (key === 'ArrowLeft') mainVideo.currentTime -= 15;
        if (key === 'ArrowRight') mainVideo.currentTime += 15;
    }

    updateFocus();
}

// --- Player Logic ---
function startFullscreen(channel) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById('fullscreen-player').classList.add('active');
    state.screen = 'fullscreen-player';

    miniVideo.pause();
    playVideo(mainVideo, channel.url);

    document.getElementById('osd-name').innerText = channel.name;
    document.getElementById('osd-logo').src = channel.logo;
    showOSD();
}

function closeFullscreen() {
    mainVideo.pause();
    renderTVPanel(state.currentCategory);
}

let osdTimer;
function showOSD() {
    const osd = document.getElementById('osd');
    osd.classList.remove('osd-hidden');
    clearTimeout(osdTimer);
    osdTimer = setTimeout(() => osd.classList.add('osd-hidden'), CONFIG.osdTimeout);
}

// --- Utils ---
function updateClock() {
    const now = new Date();
    const time = now.getHours().toString().padStart(2, '0') + ":" + now.getMinutes().toString().padStart(2, '0');
    document.getElementById('clock').innerText = time;
    document.getElementById('osd-clock').innerText = time;
}

function setupNavigation() {
    window.addEventListener('keydown', handleKey);
}

init();
