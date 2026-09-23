<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { parseBlob } from 'music-metadata';
import {
  Heart,
  Home,
  Library,
  LogIn,
  LogOut,
  Pause,
  Play,
  Plus,
  Save,
  Search,
  Shield,
  Shuffle,
  SkipBack,
  SkipForward,
  ChevronDown,
  Clock3,
  Trash2,
  Upload,
  User,
  Volume2,
  X
} from '@lucide/vue';

type Song = {
  id: number;
  title: string;
  artist: string;
  album: string;
  duration: number;
  coverUrl: string;
  audioUrl: string;
  lyricUrl: string | null;
  cover: string;
  color: string;
};

type SongResponse = {
  id: number;
  title: string;
  artist: string;
  album: string;
  duration: number;
  coverUrl: string;
  audioUrl: string;
  lyricUrl: string | null;
  liked: boolean;
};

type ViewKey = 'discover' | 'search' | 'library' | 'admin';

type AuthUser = { userId: number; username: string; role: string };

type AdminUser = { id: number; username: string; role: string; createdAt: string };

const fallbackSongs: Song[] = [
  toDisplaySong({ id: 1, title: 'Echoes', artist: 'Pink Floyd', album: 'Meddle', duration: 1412, coverUrl: '/uploads/covers/Pink%20Floyd%20-%20Echoes.jpg', audioUrl: '/uploads/audio/Pink%20Floyd%20-%20Echoes.flac', lyricUrl: null, liked: false }),
  toDisplaySong({ id: 2, title: 'Goldener Schnitt', artist: 'Sacco', album: 'Single', duration: 52, coverUrl: '/uploads/covers/Sacco%20-%20Goldener%20Schnitt.jpg', audioUrl: '/uploads/audio/Sacco%20-%20Goldener%20Schnitt.wav', lyricUrl: null, liked: false }),
  toDisplaySong({ id: 3, title: 'Highscore', artist: '哈基米', album: 'Single', duration: 156, coverUrl: '/uploads/covers/%E3%80%90%E5%93%88%E5%9F%BA%E7%B1%B3%E3%80%91Highscore.jpg', audioUrl: '/uploads/audio/%E3%80%90%E5%93%88%E5%9F%BA%E7%B1%B3%E3%80%91Highscore.wav', lyricUrl: null, liked: false })
];

const songs = ref<Song[]>([]);
const currentSong = ref<Song | null>(null);
const isPlaying = ref(false);
const audioElement = ref<HTMLAudioElement | null>(null);
const currentSeconds = ref(0);
const audioDuration = ref(0);
const progress = ref(0);
const volume = ref(72);
const likedSongIds = ref<Set<number>>(new Set());
const recentSongIds = ref<number[]>([]);
const searchKeyword = ref('');
const isLoading = ref(false);
const errorMessage = ref('');
const activeView = ref<ViewKey>('discover');

const authToken = ref<string | null>(localStorage.getItem('musicPlayerToken'));
const authUser = ref<AuthUser | null>(null);
const showAuthModal = ref(false);
const authMode = ref<'login' | 'register'>('login');
const authUsername = ref('');
const authPassword = ref('');
const authError = ref('');

const isAdmin = computed(() => authUser.value?.role === 'ADMIN');

const activeAdminTab = ref<'songs' | 'users' | 'upload'>('songs');
const adminUsers = ref<AdminUser[]>([]);
const adminSaving = ref(false);
const adminMsg = ref('');

const editSong = ref<Song | null>(null);
const editedTitle = ref('');
const editedArtist = ref('');
const editedAlbum = ref('');
const editedDuration = ref(0);
const editedCoverUrl = ref('');
const editedAudioUrl = ref('');
const editedLyricUrl = ref('');

const uploadCoverFile = ref<File | null>(null);
const uploadAudioFile = ref<File | null>(null);
const uploadLyricFile = ref<File | null>(null);
const uploadTitle = ref('');
const uploadArtist = ref('');
const uploadAlbum = ref('');
const uploadDuration = ref(180);
const uploadCoverPreview = ref('');
const uploadAudioName = ref('');
const uploadLyricName = ref('');
const uploadMetadataHint = ref('');

const showDetail = ref(false);
const lyricLines = ref<{ time: number; text: string }[]>([]);
const currentLyricIndex = ref(-1);
const lyricContainerRef = ref<HTMLElement | null>(null);

if (authToken.value) {
  fetchAuthMe();
}

const likedSongList = computed(() => songs.value.filter(s => likedSongIds.value.has(s.id)));
const recentSongs = computed(() => recentSongIds.value.map(id => songs.value.find(s => s.id === id)).filter((s): s is Song => Boolean(s)));
const filteredSongs = computed(() => {
  const k = searchKeyword.value.trim().toLowerCase();
  if (!k) return songs.value;
  return songs.value.filter(s => [s.title, s.artist, s.album].some(t => t.toLowerCase().includes(k)));
});
const visibleSongs = computed(() => {
  if (activeView.value === 'search') return filteredSongs.value;
  if (activeView.value === 'library') return likedSongList.value;
  return songs.value;
});
const songSectionTitle = computed(() => activeView.value === 'search' ? '搜索结果' : activeView.value === 'library' ? '收藏歌曲' : '歌曲列表');
const emptyMessage = computed(() => activeView.value === 'search' ? '没有找到匹配的歌曲。' : activeView.value === 'library' ? '还没有收藏歌曲。' : '暂无歌曲。');
const currentTime = computed(() => formatTime(Math.round(currentSeconds.value)));
const durationTime = computed(() => formatTime(Math.round(activeDuration.value)));
const activeDuration = computed(() => audioDuration.value || currentSong.value?.duration || 0);

const viewTitle = computed(() => {
  if (activeView.value === 'admin') return { eyebrow: '管理后台', title: activeAdminTab.value === 'songs' ? '歌曲管理' : activeAdminTab.value === 'users' ? '用户管理' : '上传歌曲' };
  return { eyebrow: activeView.value === 'discover' ? '推荐' : activeView.value === 'search' ? '搜索' : '个人音乐', title: activeView.value === 'discover' ? '今天想听什么？' : activeView.value === 'search' ? '搜索音乐' : '我的音乐' };
});

onMounted(() => { loadSongs(); });
watch(currentSong, (s) => { if (s) loadLyrics(s); });

watch(volume, v => { if (audioElement.value) audioElement.value.volume = v / 100; });

function apiHeaders(): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  if (authToken.value) h['Authorization'] = `Bearer ${authToken.value}`;
  return h;
}

async function fetchAuthMe() {
  try {
    const res = await fetch('/api/auth/me', { headers: apiHeaders() });
    if (res.ok) authUser.value = await res.json();
    else { localStorage.removeItem('musicPlayerToken'); authToken.value = null; }
  } catch {}
}

async function loadSongs() {
  isLoading.value = true; errorMessage.value = '';
  try {
    const r = await fetch('/api/songs', { headers: apiHeaders() });
    if (!r.ok) throw new Error(`歌曲接口返回 ${r.status}`);
    const data = (await r.json()) as SongResponse[];
    songs.value = data.map(toDisplaySong);
    likedSongIds.value = new Set(data.filter(s => s.liked).map(s => s.id));
    errorMessage.value = '';
  } catch {
    songs.value = fallbackSongs;
    errorMessage.value = '后端暂时不可用，已显示本地示例数据。';
  } finally { currentSong.value = songs.value[0] ?? null; isLoading.value = false; }
  if (authToken.value) { loadFavorites(); loadHistory(); }
}

async function loadFavorites() {
  try { const r = await fetch('/api/favorites', { headers: apiHeaders() }); if (r.ok) { const d = await r.json(); likedSongIds.value = new Set(d.songIds as number[]); } } catch {}
}
async function loadHistory() {
  try { const r = await fetch('/api/history', { headers: apiHeaders() }); if (r.ok) { const d = await r.json(); recentSongIds.value = d.songIds as number[]; } } catch {}
}

async function handleAuth() {
  authError.value = '';
  try {
    const r = await fetch(authMode.value === 'login' ? '/api/auth/login' : '/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: authUsername.value, password: authPassword.value }) });
    if (!r.ok) { const e = await r.json(); authError.value = e.error || '操作失败'; return; }
    const d = await r.json();
    authToken.value = d.token; authUser.value = { userId: d.userId, username: d.username, role: d.role };
    localStorage.setItem('musicPlayerToken', d.token);
    showAuthModal.value = false; authUsername.value = ''; authPassword.value = '';
    loadSongs();
  } catch { authError.value = '网络错误'; }
}

function logout() { authToken.value = null; authUser.value = null; localStorage.removeItem('musicPlayerToken'); likedSongIds.value = new Set(); recentSongIds.value = []; activeView.value = 'discover'; }

function toDisplaySong(s: SongResponse): Song { return { ...s, cover: getCoverText(s.title), color: getSongColor(s.id) }; }
function getCoverText(t: string) { return t.split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase(); }
function getSongColor(id: number) { return ['#3b82f6','#111827','#d97706','#0f766e','#7c3aed','#be123c'][(id-1)%6]; }
function formatTime(s: number) { return `${Math.floor(s/60)}:${(s%60).toString().padStart(2,'0')}`; }

function selectSong(song: Song) { currentSong.value = song; addToRecent(song); resetPlaybackPosition(); playCurrentSong(); }

async function toggleLike(song: Song) {
  if (!authToken.value) { showAuthModal.value = true; return; }
  try { const r = await fetch(`/api/favorites/${song.id}`, { method: likedSongIds.value.has(song.id) ? 'DELETE' : 'POST', headers: authHeaders() }); if (r.ok) { const n = new Set(likedSongIds.value); likedSongIds.value.has(song.id) ? n.delete(song.id) : n.add(song.id); likedSongIds.value = n; } } catch {}
}

function authHeaders(): Record<string, string> { const h: Record<string, string> = {}; if (authToken.value) h['Authorization'] = `Bearer ${authToken.value}`; return h; }

function switchView(v: ViewKey) { activeView.value = v; if (v === 'admin') { adminMsg.value = ''; loadAdminData(); } }

function addToRecent(song: Song) {
  recentSongIds.value = [song.id, ...recentSongIds.value.filter(id => id !== song.id)].slice(0, 12);
  if (authToken.value) { fetch('/api/history', { method: 'POST', headers: apiHeaders(), body: JSON.stringify({ songId: song.id }) }).catch(() => {}); }
}

function playRandomSong(list: Song[] = songs.value) { if (list.length) selectSong(list[Math.floor(Math.random() * list.length)]); }
function nextSong() { if (!currentSong.value || !songs.value.length) return; const i = songs.value.findIndex(s => s.id === currentSong.value?.id); currentSong.value = songs.value[(i+1)%songs.value.length]; resetPlaybackPosition(); playCurrentSong(); }
function previousSong() { if (!currentSong.value || !songs.value.length) return; const i = songs.value.findIndex(s => s.id === currentSong.value?.id); currentSong.value = songs.value[(i-1+songs.value.length)%songs.value.length]; resetPlaybackPosition(); playCurrentSong(); }
function resetPlaybackPosition() { progress.value = 0; currentSeconds.value = 0; audioDuration.value = 0; }
async function togglePlayback() { if (!currentSong.value) return; if (isPlaying.value) { audioElement.value?.pause(); isPlaying.value = false; } else await playCurrentSong(); }
async function playCurrentSong() { await nextTick(); const a = audioElement.value; if (!a) return; a.volume = volume.value/100; try { await a.play(); isPlaying.value = true; } catch { isPlaying.value = false; } }
function updateMetadata() { const a = audioElement.value; if (a) audioDuration.value = Number.isFinite(a.duration) ? a.duration : currentSong.value?.duration || 0; }
function updateProgress() { const a = audioElement.value; if (a) { currentSeconds.value = a.currentTime; progress.value = activeDuration.value > 0 ? Math.round((a.currentTime/activeDuration.value)*1000)/10 : 0; updateLyricIndex(a.currentTime); } }
function updateLyricIndex(time: number) {
  const lines = lyricLines.value;
  if (!lines.length) return;
  let idx = -1;
  for (let i = 0; i < lines.length; i++) {
    if (time >= lines[i].time) idx = i; else break;
  }
  if (idx !== currentLyricIndex.value) {
    currentLyricIndex.value = idx;
    nextTick(() => {
      const active = lyricContainerRef.value?.querySelector('.lyric-line.active') as HTMLElement;
      if (active) {
        active.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    });
  }
}
async function loadLyrics(song: Song) {
  lyricLines.value = [];
  currentLyricIndex.value = -1;
  if (!song.lyricUrl) return;
  try {
    const res = await fetch(song.lyricUrl);
    if (!res.ok) return;
    const raw = await res.text();
    lyricLines.value = parseLrc(raw);
  } catch {}
}
function parseLrc(raw: string): { time: number; text: string }[] {
  const lines: { time: number; text: string }[] = [];
  const regex = /\[(\d{2}):(\d{2})(?:[.:](\d{2,3}))?\]\s*(.*)/;
  for (const line of raw.split('\n')) {
    const m = line.match(regex);
    if (!m) continue;
    const min = parseInt(m[1]), sec = parseInt(m[2]);
    let ms = 0;
    if (m[3]) ms = m[3].length === 2 ? parseInt(m[3]) * 10 : parseInt(m[3]);
    lines.push({ time: min * 60 + sec + ms / 1000, text: m[4].trim() || '' });
  }
  return lines;
}
function openDetail() { if (currentSong.value) { showDetail.value = true; loadLyrics(currentSong.value); } }
function closeDetail() { showDetail.value = false; }
function seekToProgress() { const a = audioElement.value; if (a && activeDuration.value > 0) { const t = (activeDuration.value * progress.value)/100; a.currentTime = t; currentSeconds.value = t; } }
function handlePlaybackEnded() { isPlaying.value = false; nextSong(); }
function handleAudioError() { isPlaying.value = false; }
function hideBrokenImage(e: Event) { const img = e.target; if (img instanceof HTMLImageElement) { img.style.display = 'none'; img.parentElement?.classList.remove('has-image'); } }
function markCoverLoaded(e: Event) { const img = e.target; if (img instanceof HTMLImageElement) { img.style.display = ''; img.parentElement?.classList.add('has-image'); } }

async function loadAdminData() {
  if (!authToken.value) return;
  try {
    const r = await fetch('/api/admin/users', { headers: apiHeaders() });
    if (r.ok) adminUsers.value = await r.json();
  } catch {}
}

async function toggleUserRole(u: AdminUser) {
  const newRole = u.role === 'ADMIN' ? 'USER' : 'ADMIN';
  try {
    const r = await fetch(`/api/admin/users/${u.id}/role`, { method: 'PUT', headers: apiHeaders(), body: JSON.stringify({ role: newRole }) });
    if (r.ok) { u.role = newRole; adminMsg.value = `已将 ${u.username} 的角色改为 ${newRole}`; }
  } catch { adminMsg.value = '操作失败'; }
}

function startEditSong(s: Song) {
  editSong.value = s;
  editedTitle.value = s.title; editedArtist.value = s.artist; editedAlbum.value = s.album;
  editedDuration.value = s.duration; editedCoverUrl.value = s.coverUrl; editedAudioUrl.value = s.audioUrl; editedLyricUrl.value = s.lyricUrl || '';
}

function cancelEdit() { editSong.value = null; }

async function saveEdit() {
  if (!editSong.value) return;
  adminSaving.value = true; adminMsg.value = '';
  try {
    const r = await fetch(`/api/admin/songs/${editSong.value.id}`, {
      method: 'PUT', headers: apiHeaders(),
      body: JSON.stringify({ title: editedTitle.value, artist: editedArtist.value, album: editedAlbum.value, duration: editedDuration.value, coverUrl: editedCoverUrl.value, audioUrl: editedAudioUrl.value, lyricUrl: editedLyricUrl.value || null })
    });
    if (r.ok) { adminMsg.value = '保存成功'; editSong.value = null; loadSongs(); }
    else { const e = await r.json(); adminMsg.value = e.error || '保存失败'; }
  } catch { adminMsg.value = '保存失败'; }
  adminSaving.value = false;
}

async function deleteSongItem(s: Song) {
  if (!confirm(`确定删除「${s.title}」吗？`)) return;
  try {
    const r = await fetch(`/api/admin/songs/${s.id}`, { method: 'DELETE', headers: apiHeaders() });
    if (r.ok) { adminMsg.value = '删除成功'; loadSongs(); }
    else { const e = await r.json(); adminMsg.value = e.error || '删除失败'; }
  } catch { adminMsg.value = '删除失败'; }
}

async function uploadFileToServer(file: File, type: string): Promise<string> {
  const fd = new FormData();
  fd.append('file', file);
  fd.append('type', type);
  const r = await fetch('/api/admin/upload', { method: 'POST', headers: authHeaders(), body: fd });
  if (!r.ok) throw new Error('上传失败');
  const d = await r.json();
  return d.url as string;
}

function handleCoverSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files?.[0]) {
    uploadCoverFile.value = input.files[0];
    uploadCoverPreview.value = URL.createObjectURL(input.files[0]);
  }
}

async function handleAudioSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  uploadAudioFile.value = file;
  uploadAudioName.value = file.name;
  uploadMetadataHint.value = '';

  let matched = 0;

  try {
    const metadata = await parseBlob(file);
    const { common, format } = metadata;

    if (!uploadTitle.value && common.title) { uploadTitle.value = common.title; matched++; }
    if (!uploadArtist.value && common.artist) { uploadArtist.value = common.artist; matched++; }
    if (!uploadAlbum.value && common.album) { uploadAlbum.value = common.album; matched++; }
    if (format.duration && format.duration > 0) { uploadDuration.value = Math.round(format.duration); matched++; }

    if (!uploadCoverFile.value && common.picture && common.picture.length > 0) {
      const pic = common.picture[0];
      const blob = new Blob([pic.data as unknown as BlobPart], { type: pic.type || 'image/jpeg' });
      uploadCoverFile.value = new File([blob], 'cover.' + (pic.type?.split('/')[1] || 'jpg'), { type: pic.type });
      uploadCoverPreview.value = URL.createObjectURL(blob);
      matched++;
    }
  } catch (err) {
    console.error('music-metadata 解析失败:', err);
  }

  if (uploadDuration.value <= 0) {
    const audio = new Audio(URL.createObjectURL(file));
    audio.addEventListener('loadedmetadata', () => {
      if (uploadDuration.value <= 0) uploadDuration.value = Math.round(audio.duration || 0);
    });
  }

  if (matched > 0) {
    uploadMetadataHint.value = `已从文件中读取 ${matched} 项信息`;
  } else {
    uploadMetadataHint.value = '文件中未包含元数据，请手动填写';
  }
}
function handleLyricSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files?.[0]) { uploadLyricFile.value = input.files[0]; uploadLyricName.value = input.files[0].name; }
}

async function handleUpload() {
  if (!uploadTitle.value || !uploadArtist.value || !uploadAlbum.value || !uploadAudioFile.value) {
    adminMsg.value = '请填写标题、歌手、专辑并选择音频文件';
    return;
  }
  adminSaving.value = true; adminMsg.value = '';
  try {
    let coverUrl = '';
    let lyricUrl = '';
    if (uploadCoverFile.value) coverUrl = await uploadFileToServer(uploadCoverFile.value, 'cover');
    const audioUrl = await uploadFileToServer(uploadAudioFile.value, 'audio');
    if (uploadLyricFile.value) lyricUrl = await uploadFileToServer(uploadLyricFile.value, 'lyric');

    const r = await fetch('/api/admin/songs', {
      method: 'POST', headers: apiHeaders(),
      body: JSON.stringify({ title: uploadTitle.value, artist: uploadArtist.value, album: uploadAlbum.value, duration: uploadDuration.value, coverUrl: coverUrl || null, audioUrl, lyricUrl: lyricUrl || null })
    });
    if (r.ok) {
      adminMsg.value = '上传成功！';
      uploadTitle.value = ''; uploadArtist.value = ''; uploadAlbum.value = ''; uploadDuration.value = 180;
      uploadCoverFile.value = null; uploadAudioFile.value = null; uploadLyricFile.value = null;
      uploadCoverPreview.value = ''; uploadAudioName.value = ''; uploadLyricName.value = ''; uploadMetadataHint.value = '';
      loadSongs();
    } else { const e = await r.json(); adminMsg.value = e.error || '上传失败'; }
  } catch { adminMsg.value = '上传失败'; }
  adminSaving.value = false;
}
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">M</div>
        <span>EricJC Music</span>
      </div>

      <nav class="nav-list" aria-label="主导航">
        <button class="nav-item" :class="{ active: activeView === 'discover' }" type="button" @click="switchView('discover')">
          <Home :size="20" />发现音乐
        </button>
        <button class="nav-item" :class="{ active: activeView === 'search' }" type="button" @click="switchView('search')">
          <Search :size="20" />搜索
        </button>
        <button class="nav-item" :class="{ active: activeView === 'library' }" type="button" @click="switchView('library')">
          <Library :size="20" />我的音乐
        </button>
      </nav>

      <section class="user-section">
        <template v-if="authUser">
          <div class="user-info">
            <span class="user-avatar"><User :size="16" /></span>
            <span class="user-name">{{ authUser.username }}</span>
            <span v-if="isAdmin" class="admin-badge">管理员</span>
          </div>
          <button v-if="isAdmin" class="nav-item admin-entry" :class="{ active: activeView === 'admin' }" type="button" @click="switchView('admin')">
            <Shield :size="20" />管理后台
          </button>
          <button class="auth-button" type="button" @click="logout">
            <LogOut :size="16" />退出
          </button>
        </template>
        <button v-else class="auth-button" type="button" @click="showAuthModal = true; authMode = 'login'; authError = ''; authUsername = ''; authPassword = ''">
          <LogIn :size="16" />登录 / 注册
        </button>
      </section>
    </aside>

    <section v-if="activeView !== 'admin'" class="content">
      <header class="topbar">
        <div>
          <p class="eyebrow">{{ viewTitle.eyebrow }}</p>
          <h1>{{ viewTitle.title }}</h1>
        </div>
        <label class="search-box">
          <Search :size="18" />
          <input v-model="searchKeyword" type="search" placeholder="搜索歌曲、歌手、专辑" @focus="switchView('search')" />
        </label>
      </header>

      <section v-if="currentSong && activeView === 'discover'" class="now-playing">
        <div class="cover-large" :style="{ background: currentSong.color }">
          <img :src="currentSong.coverUrl" :alt="`${currentSong.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" />
          <span>{{ currentSong.cover }}</span>
        </div>
        <div class="track-summary">
          <p class="eyebrow">正在播放</p>
          <h2>{{ currentSong.title }}</h2>
          <p>{{ currentSong.artist }} · {{ currentSong.album }}</p>
          <button class="primary-action" type="button" @click="togglePlayback">
            <Pause v-if="isPlaying" :size="20" /><Play v-else :size="20" />
            {{ isPlaying ? '暂停' : '播放' }}
          </button>
        </div>
      </section>

      <section v-if="activeView === 'library'" class="library-grid">
        <article class="metric-card"><span>收藏歌曲</span><strong>{{ likedSongList.length }}</strong></article>
        <article class="metric-card"><span>最近播放</span><strong>{{ recentSongs.length }}</strong></article>
      </section>

      <section class="song-table" aria-label="歌曲列表">
        <div class="table-heading">
          <h2>{{ songSectionTitle }}</h2>
          <button class="ghost-button" type="button" @click="playRandomSong(visibleSongs)"><Shuffle :size="18" />随机播放</button>
        </div>
        <p v-if="isLoading" class="status-text">正在加载歌曲...</p>
        <p v-else-if="errorMessage" class="status-text warning">后端暂时不可用，已显示本地示例数据。</p>
        <div class="song-row header-row"><span>歌曲</span><span>专辑</span><span>时长</span><span></span></div>
        <button v-for="song in visibleSongs" :key="song.id" class="song-row" :class="{ selected: song.id === currentSong?.id }" type="button" @click="selectSong(song)">
          <span class="song-cell">
            <span class="cover-small" :style="{ background: song.color }"><img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ song.cover }}</span></span>
            <span><strong>{{ song.title }}</strong><small>{{ song.artist }}</small></span>
          </span>
          <span>{{ song.album }}</span>
          <span>{{ formatTime(song.duration) }}</span>
          <span class="row-actions">
            <button class="icon-button" type="button" aria-label="收藏歌曲" @click.stop="toggleLike(song)">
              <Heart :size="18" :fill="likedSongIds.has(song.id) ? 'currentColor' : 'none'" />
            </button>
          </span>
        </button>
        <p v-if="!isLoading && visibleSongs.length === 0" class="status-text">{{ emptyMessage }}</p>
      </section>

      <section v-if="activeView === 'library'" class="song-table secondary-section" aria-label="最近播放">
        <div class="table-heading"><h2>最近播放</h2><button class="ghost-button" type="button" @click="playRandomSong(recentSongs)"><Clock3 :size="18" />随机回听</button></div>
        <button v-for="song in recentSongs" :key="'r'+song.id" class="song-row" :class="{ selected: song.id === currentSong?.id }" type="button" @click="selectSong(song)">
          <span class="song-cell">
            <span class="cover-small" :style="{ background: song.color }"><img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ song.cover }}</span></span>
            <span><strong>{{ song.title }}</strong><small>{{ song.artist }}</small></span>
          </span>
          <span>{{ song.album }}</span><span>{{ formatTime(song.duration) }}</span><span></span>
        </button>
        <p v-if="recentSongs.length === 0" class="status-text">播放歌曲后会出现在这里。</p>
      </section>
    </section>

    <section v-else class="content admin-panel">
      <header class="topbar">
        <div>
          <p class="eyebrow">{{ viewTitle.eyebrow }}</p>
          <h1>{{ viewTitle.title }}</h1>
        </div>
        <div class="admin-tabs">
          <button :class="{ active: activeAdminTab === 'songs' }" @click="activeAdminTab = 'songs'">歌曲管理</button>
          <button :class="{ active: activeAdminTab === 'users' }" @click="activeAdminTab = 'users'">用户管理</button>
          <button :class="{ active: activeAdminTab === 'upload' }" @click="activeAdminTab = 'upload'"><Upload :size="16" />上传歌曲</button>
        </div>
      </header>

      <p v-if="adminMsg" class="status-text" :class="{ warning: adminMsg.includes('失败') }">{{ adminMsg }}</p>

      <section v-if="activeAdminTab === 'songs'" class="song-table">
        <div class="song-row header-row"><span>歌曲</span><span>专辑</span><span>时长</span><span>操作</span></div>
        <div v-for="song in songs" :key="song.id" class="song-row admin-song-row">
          <span class="song-cell">
            <span class="cover-small" :style="{ background: song.color }"><img :src="song.coverUrl" :alt="song.title" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ song.cover }}</span></span>
            <span><strong>{{ song.title }}</strong><small>{{ song.artist }}</small></span>
          </span>
          <span>{{ song.album }}</span>
          <span>{{ formatTime(song.duration) }}</span>
          <span class="row-actions">
            <button class="icon-button" type="button" title="编辑" @click="startEditSong(song)"><Save :size="16" /></button>
            <button class="icon-button danger" type="button" title="删除" @click="deleteSongItem(song)"><Trash2 :size="16" /></button>
          </span>
        </div>
      </section>

      <section v-if="activeAdminTab === 'users'">
        <div class="admin-table">
          <div class="song-row header-row"><span>用户</span><span>角色</span><span>注册时间</span><span>操作</span></div>
          <div v-for="u in adminUsers" :key="u.id" class="song-row admin-song-row">
            <span><strong>{{ u.username }}</strong></span>
            <span><span class="role-tag" :class="u.role === 'ADMIN' ? 'admin-role' : 'user-role'">{{ u.role === 'ADMIN' ? '管理员' : '普通用户' }}</span></span>
            <span>{{ u.createdAt ? u.createdAt.slice(0,10) : '' }}</span>
            <span class="row-actions">
              <button v-if="u.username !== authUser?.username" class="ghost-button small" type="button" @click="toggleUserRole(u)">{{ u.role === 'ADMIN' ? '取消管理员' : '设为管理员' }}</button>
              <span v-else class="self-tag">当前用户</span>
            </span>
          </div>
        </div>
      </section>

      <section v-if="activeAdminTab === 'upload'" class="upload-form">
        <div class="upload-grid">
          <div class="upload-field">
            <label>标题 *</label>
            <input v-model="uploadTitle" type="text" placeholder="歌曲标题" />
          </div>
          <div class="upload-field">
            <label>歌手 *</label>
            <input v-model="uploadArtist" type="text" placeholder="歌手名称" />
          </div>
          <div class="upload-field">
            <label>专辑 *</label>
            <input v-model="uploadAlbum" type="text" placeholder="专辑名称" />
          </div>
          <div class="upload-field">
            <label>时长</label>
            <span class="duration-display">{{ uploadDuration > 0 ? formatTime(uploadDuration) : '选择音频文件后自动识别' }}</span>
          </div>
        </div>

        <div class="upload-files">
          <label class="file-drop" :class="{ 'has-file': uploadCoverPreview }">
            <template v-if="uploadCoverPreview">
              <img :src="uploadCoverPreview" alt="封面预览" class="cover-preview" />
              <span class="file-label">封面图片（点击更换）</span>
            </template>
            <template v-else>
              <Upload :size="24" /><span>点击上传封面图片</span>
            </template>
            <input type="file" accept="image/*" hidden @change="handleCoverSelect" />
          </label>

          <label class="file-drop" :class="{ 'has-file': uploadAudioName }">
            <template v-if="uploadAudioName">
              <span class="file-name">{{ uploadAudioName }}</span>
              <span class="file-label">音频文件（点击更换）*</span>
            </template>
            <template v-else>
              <Upload :size="24" /><span>点击上传音频文件 *</span>
            </template>
            <input type="file" accept="audio/*" hidden @change="handleAudioSelect" />
          </label>

          <label class="file-drop" :class="{ 'has-file': uploadLyricName }">
            <template v-if="uploadLyricName">
              <span class="file-name">{{ uploadLyricName }}</span>
              <span class="file-label">歌词文件（点击更换）</span>
            </template>
            <template v-else>
              <Upload :size="24" /><span>点击上传歌词文件（可选）</span>
            </template>
            <input type="file" accept=".lrc,.txt" hidden @change="handleLyricSelect" />
          </label>
        </div>

        <p v-if="uploadMetadataHint" class="metadata-hint">{{ uploadMetadataHint }}</p>

        <button class="primary-action" type="button" :disabled="adminSaving" @click="handleUpload">
          <Upload :size="18" />{{ adminSaving ? '上传中...' : '上传歌曲' }}
        </button>
      </section>
    </section>

    <footer v-if="currentSong" class="player-bar">
      <audio ref="audioElement" :src="currentSong.audioUrl" preload="metadata" @loadedmetadata="updateMetadata" @timeupdate="updateProgress" @ended="handlePlaybackEnded" @play="isPlaying = true" @pause="isPlaying = false" @error="handleAudioError" />
      <div class="mini-track" @click="openDetail">
        <div class="cover-small" :style="{ background: currentSong.color }"><img :src="currentSong.coverUrl" :alt="`${currentSong.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ currentSong.cover }}</span></div>
        <div><strong>{{ currentSong.title }}</strong><small>{{ currentSong.artist }}</small></div>
      </div>
      <div class="transport">
        <div class="transport-buttons">
          <button class="icon-button" type="button" aria-label="上一首" @click="previousSong"><SkipBack :size="20" /></button>
          <button class="play-button" type="button" aria-label="播放或暂停" @click="togglePlayback"><Pause v-if="isPlaying" :size="22" /><Play v-else :size="22" /></button>
          <button class="icon-button" type="button" aria-label="下一首" @click="nextSong"><SkipForward :size="20" /></button>
        </div>
        <div class="progress-line"><span>{{ currentTime }}</span><input v-model.number="progress" type="range" min="0" max="100" step="0.1" @input="seekToProgress" /><span>{{ durationTime }}</span></div>
      </div>
      <div class="volume-box"><Volume2 :size="20" /><input v-model.number="volume" type="range" min="0" max="100" aria-label="音量" /></div>
    </footer>

    <Teleport to="body">
      <Transition name="detail-slide">
        <div v-if="showDetail && currentSong" class="detail-overlay" @click.self="closeDetail">
          <button class="detail-close" type="button" @click="closeDetail"><ChevronDown :size="28" /></button>

          <div class="detail-content">
            <div class="detail-cover-section">
              <div class="detail-vinyl" :class="{ spinning: isPlaying }">
                <div class="vinyl-disc" :style="{ background: currentSong.color }">
                  <img v-if="currentSong.coverUrl" :src="currentSong.coverUrl" :alt="currentSong.title" @error="($event.target as HTMLImageElement).style.display='none'" />
                  <span v-else class="vinyl-fallback">{{ currentSong.cover }}</span>
                </div>
//                <div class="vinyl-hole"></div>
              </div>
              <div class="detail-info">
                <h2 class="detail-title">{{ currentSong.title }}</h2>
                <p class="detail-artist">{{ currentSong.artist }}</p>
                <p class="detail-album">{{ currentSong.album }}</p>
              </div>
            </div>

            <div v-if="lyricLines.length" class="detail-lyrics" ref="lyricContainerRef">
              <p
                v-for="(line, i) in lyricLines"
                :key="i"
                class="lyric-line"
                :class="{ active: i === currentLyricIndex }"
              >{{ line.text || '&nbsp;' }}</p>
            </div>
            <div v-else class="detail-no-lyrics">
              <p>暂无歌词</p>
              <p class="sub">纯音乐，请欣赏</p>
            </div>
          </div>

          <footer class="player-bar detail-player-bar">
            <div class="mini-track">
              <div class="cover-small" :style="{ background: currentSong.color }"><img :src="currentSong.coverUrl" :alt="currentSong.title" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ currentSong.cover }}</span></div>
              <div><strong>{{ currentSong.title }}</strong><small>{{ currentSong.artist }}</small></div>
            </div>
            <div class="transport">
              <div class="transport-buttons">
                <button class="icon-button" type="button" @click="previousSong"><SkipBack :size="20" /></button>
                <button class="play-button" type="button" @click="togglePlayback"><Pause v-if="isPlaying" :size="22" /><Play v-else :size="22" /></button>
                <button class="icon-button" type="button" @click="nextSong"><SkipForward :size="20" /></button>
              </div>
              <div class="progress-line"><span>{{ currentTime }}</span><input v-model.number="progress" type="range" min="0" max="100" step="0.1" @input="seekToProgress" /><span>{{ durationTime }}</span></div>
            </div>
            <div class="volume-box"><Volume2 :size="20" /><input v-model.number="volume" type="range" min="0" max="100" aria-label="音量" /></div>
          </footer>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <div v-if="editSong" class="modal-backdrop" @click.self="cancelEdit">
        <div class="modal-card edit-modal">
          <div class="modal-header">
            <h3>编辑歌曲</h3>
            <button class="icon-button" type="button" @click="cancelEdit"><X :size="20" /></button>
          </div>
          <form class="auth-form" @submit.prevent="saveEdit">
            <label><span>标题</span><input v-model="editedTitle" required /></label>
            <label><span>歌手</span><input v-model="editedArtist" required /></label>
            <label><span>专辑</span><input v-model="editedAlbum" required /></label>
            <label><span>时长（秒）</span><input v-model.number="editedDuration" type="number" required /></label>
            <label><span>封面 URL</span><input v-model="editedCoverUrl" /></label>
            <label><span>音频 URL</span><input v-model="editedAudioUrl" required /></label>
            <label><span>歌词 URL</span><input v-model="editedLyricUrl" /></label>
            <button class="primary-action" type="submit" :disabled="adminSaving">{{ adminSaving ? '保存中...' : '保存' }}</button>
          </form>
        </div>
      </div>

      <div v-if="showAuthModal" class="modal-backdrop" @click.self="showAuthModal = false">
        <div class="modal-card">
          <div class="modal-header">
            <h3>{{ authMode === 'login' ? '登录' : '注册' }}</h3>
            <button class="icon-button" type="button" @click="showAuthModal = false"><X :size="20" /></button>
          </div>
          <form class="auth-form" @submit.prevent="handleAuth">
            <label><span>用户名</span><input v-model="authUsername" type="text" required minlength="2" maxlength="50" placeholder="输入用户名" /></label>
            <label><span>密码</span><input v-model="authPassword" type="password" required minlength="6" maxlength="100" placeholder="输入密码（至少6位）" /></label>
            <p v-if="authError" class="auth-error">{{ authError }}</p>
            <button class="primary-action" type="submit">{{ authMode === 'login' ? '登录' : '注册' }}</button>
          </form>
          <p class="auth-switch">{{ authMode === 'login' ? '还没有账号？' : '已有账号？' }}<button type="button" @click="authMode = authMode === 'login' ? 'register' : 'login'; authError = ''">{{ authMode === 'login' ? '去注册' : '去登录' }}</button></p>
        </div>
      </div>
    </Teleport>
  </main>
</template>
