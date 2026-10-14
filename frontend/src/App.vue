<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { parseBlob } from 'music-metadata';
import {
  Heart,
  ArrowLeft,
  Check,
  Files,
  Home,
  Library,
  ListMusic,
  LogIn,
  LogOut,
  Pause,
  Pencil,
  Play,
  MoreHorizontal,
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
import LyricStage from './components/LyricStage.vue';
import ProfileView from './components/ProfileView.vue';
import { findActiveIndex, parseLyrics } from './lyrics/parser';
import type { LyricFormat, LyricLine } from './lyrics/types';

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

type ViewKey = 'discover' | 'search' | 'library' | 'playlist' | 'admin' | 'profile';

type PlaylistSummary = { id: number; name: string; description: string | null; songCount: number; createdAt: string };
type PlaylistDetail = { id: number; name: string; description: string | null; songs: Song[] };
type PlaylistDetailResponse = Omit<PlaylistDetail, 'songs'> & { songs: SongResponse[] };

type AuthUser = { userId: number; username: string; role: string; nickname: string | null; avatarUrl: string | null; bio: string | null };

type AdminUser = { id: number; username: string; role: string; createdAt: string };

type BatchSongItem = {
  file: File;
  title: string;
  artist: string;
  album: string;
  duration: number;
  cover: File | null;
  coverPreview: string;
  metadataMessage: string;
  status: 'pending' | 'uploading' | 'done' | 'failed';
  error: string;
};

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
// 刷新后待恢复的播放进度（秒）；由 loadedmetadata 里 seek 到该位置后清空
const pendingSeekTime = ref<number | null>(null);
// 播放状态持久化键：记录 { songId, time }，用于刷新后还原
const PLAYER_STATE_KEY = 'musicPlayerState';
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

const playlists = ref<PlaylistSummary[]>([]);
const activePlaylist = ref<PlaylistDetail | null>(null);
const showPlaylistModal = ref(false);
const playlistModalMode = ref<'create' | 'rename'>('create');
const playlistFormName = ref('');
const playlistFormDesc = ref('');
const playlistFormError = ref('');
const playlistMsg = ref('');
const playlistMsgError = ref(false);
const playlistEditingId = ref<number | null>(null);
const playlistSaving = ref(false);
const playlistLoading = ref(false);
const playlistBusy = ref(false);
const addToPlaylistSong = ref<Song | null>(null);
const playlistContainsIds = ref<Set<number>>(new Set());
const playlistMenuLoading = ref(false);
const playlistMenuError = ref('');
const playlistMenuStyle = ref({ top: '0px', left: '0px' });
let playlistDetailRequest = 0;
let playlistMenuRequest = 0;
let playlistMessageTimer: ReturnType<typeof setTimeout> | undefined;

const isAdmin = computed(() => authUser.value?.role === 'ADMIN');

const activeAdminTab = ref<'songs' | 'users' | 'upload' | 'batch'>('songs');
const adminUsers = ref<AdminUser[]>([]);
const adminSaving = ref(false);
const adminMsg = ref('');
const batchItems = ref<BatchSongItem[]>([]);
const batchProgress = ref('');
const batchImporting = ref(false);

const editSong = ref<Song | null>(null);
const editedTitle = ref('');
const editedArtist = ref('');
const editedAlbum = ref('');
const editedDuration = ref(0);
const editedCoverUrl = ref('');
const editedAudioUrl = ref('');
const editedLyricUrl = ref('');
const editedCoverFile = ref<File | null>(null);
const editedCoverPreview = ref('');
const editedAudioFile = ref<File | null>(null);
const editedAudioName = ref('');
const editedLyricFile = ref<File | null>(null);
const editedLyricName = ref('');
const editedAudioCurrentName = computed(() => {
  const url = editedAudioUrl.value;
  if (!url) return '';
  const name = url.split('/').pop() || '';
  try { return decodeURIComponent(name); } catch { return name; }
});
const editedLyricCurrentName = computed(() => {
  const url = editedLyricUrl.value;
  if (!url) return '';
  const name = url.split('/').pop() || '';
  try { return decodeURIComponent(name); } catch { return name; }
});

const uploadCoverFile = ref<File | null>(null);
const uploadAudioFile = ref<File | null>(null);
const uploadLyricFile = ref<File | null>(null);
const uploadTitle = ref('');
const uploadArtist = ref('');
const uploadAlbum = ref('');
const uploadDuration = ref(0);
const uploadCoverPreview = ref('');
const uploadAudioName = ref('');
const uploadLyricName = ref('');
const uploadMetadataHint = ref('');

const showDetail = ref(false);
const lyricLines = ref<LyricLine[]>([]);
const lyricsFormat = ref<LyricFormat>('plain');
const currentLyricIndex = ref(-1);

if (authToken.value) {
  fetchAuthMe();
}

const likedSongList = computed(() => songs.value.filter(s => likedSongIds.value.has(s.id)));
const recentSongs = computed(() => recentSongIds.value.map(id => songs.value.find(s => s.id === id)).filter((s): s is Song => Boolean(s)));

// —— 首页推荐 ——
const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 5) return '夜深了';
  if (h < 11) return '早上好';
  if (h < 13) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

// 推荐逻辑：优先推荐「收藏 / 最近播放」歌手的其他歌，不足再按曲库顺序补足；
// 排除最近播放过与当前正在播放的歌，结果确定性稳定、不随播放跳动。
const recommendedSongs = computed<Song[]>(() => {
  const preferredArtists = new Set<string>();
  likedSongList.value.forEach((s) => preferredArtists.add(s.artist));
  recentSongs.value.forEach((s) => preferredArtists.add(s.artist));
  const excluded = new Set<number>(recentSongIds.value);
  if (currentSong.value) excluded.add(currentSong.value.id);

  const preferred: Song[] = [];
  const others: Song[] = [];
  for (const s of songs.value) {
    if (excluded.has(s.id)) continue;
    (preferredArtists.has(s.artist) ? preferred : others).push(s);
  }
  return [...preferred, ...others].slice(0, 12);
});
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
  if (activeView.value === 'playlist') return { eyebrow: '我的音乐', title: activePlaylist.value?.name || '歌单' };
  if (activeView.value === 'profile') return { eyebrow: '个人中心', title: '我的主页' };
  if (activeView.value === 'admin') return { eyebrow: '管理后台', title: activeAdminTab.value === 'songs' ? '歌曲管理' : activeAdminTab.value === 'users' ? '用户管理' : activeAdminTab.value === 'batch' ? '批量导入歌曲' : '上传歌曲' };
  return { eyebrow: activeView.value === 'discover' ? '推荐' : activeView.value === 'search' ? '搜索' : '个人音乐', title: activeView.value === 'discover' ? '今天想听什么？' : activeView.value === 'search' ? '搜索音乐' : '我的音乐' };
});

onMounted(async () => {
  document.addEventListener('keydown', handlePlaylistEscape);
  await loadSongs();
  // 恢复上次播放的歌曲与进度；首次打开（无记录）则保持空状态
  restorePlayback();
});
onUnmounted(() => { document.removeEventListener('keydown', handlePlaylistEscape); clearTimeout(playlistMessageTimer); releaseBatchCoverPreviews(); });
watch(currentSong, (s) => { if (s) { loadLyrics(s); persistPlayerState(true); } });

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
  } finally { isLoading.value = false; }
  if (authToken.value) { loadFavorites(); loadHistory(); loadPlaylists(); }
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
    authToken.value = d.token; authUser.value = { userId: d.userId, username: d.username, role: d.role, nickname: d.nickname ?? null, avatarUrl: d.avatarUrl ?? null, bio: d.bio ?? null };
    localStorage.setItem('musicPlayerToken', d.token);
    showAuthModal.value = false; authUsername.value = ''; authPassword.value = '';
    loadSongs();
  } catch { authError.value = '网络错误'; }
}

function logout() {
  authToken.value = null; authUser.value = null; localStorage.removeItem('musicPlayerToken'); likedSongIds.value = new Set(); recentSongIds.value = []; activeView.value = 'discover';
  playlists.value = []; activePlaylist.value = null; showPlaylistModal.value = false; playlistMsg.value = ''; playlistLoading.value = false;
  playlistDetailRequest++; closePlaylistMenu();
}

function notifyPlaylist(message: string, error = false) {
  playlistMsg.value = message; playlistMsgError.value = error;
  clearTimeout(playlistMessageTimer);
  playlistMessageTimer = setTimeout(() => { playlistMsg.value = ''; }, 5000);
}

async function playlistResponse<T>(response: Response): Promise<T> {
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || (response.status === 401 ? '请先登录' : '操作失败，请稍后重试。'));
  return data as T;
}

async function loadPlaylists(): Promise<boolean> {
  const token = authToken.value;
  if (!token) return false;
  try {
    const r = await fetch('/api/playlists', { headers: apiHeaders() });
    const data = await playlistResponse<{ playlists: PlaylistSummary[] }>(r);
    if (authToken.value !== token) return false;
    playlists.value = data.playlists;
    return true;
  } catch {
    if (authToken.value === token) notifyPlaylist('歌单暂时无法加载，请稍后重试。', true);
    return false;
  }
}

async function openPlaylist(p: PlaylistSummary) {
  const request = ++playlistDetailRequest;
  const token = authToken.value;
  closePlaylistMenu();
  activePlaylist.value = { id: p.id, name: p.name, description: p.description, songs: [] };
  playlistLoading.value = true;
  switchView('playlist');
  try {
    const r = await fetch(`/api/playlists/${p.id}`, { headers: apiHeaders() });
    const data = await playlistResponse<PlaylistDetailResponse>(r);
    if (request !== playlistDetailRequest || token !== authToken.value) return;
    activePlaylist.value = { ...data, songs: data.songs.map(toDisplaySong) };
  } catch (e) {
    if (request === playlistDetailRequest && token === authToken.value) {
      activePlaylist.value = null;
      switchView('library');
      notifyPlaylist(e instanceof Error ? e.message : '歌单暂时无法加载，请稍后重试。', true);
    }
  } finally { if (request === playlistDetailRequest) playlistLoading.value = false; }
}

function openPlaylistModal(p?: PlaylistSummary | PlaylistDetail) {
  closePlaylistMenu();
  if (!authToken.value) { showAuthModal.value = true; return; }
  playlistModalMode.value = p ? 'rename' : 'create';
  playlistEditingId.value = p?.id ?? null;
  playlistFormName.value = p?.name || ''; playlistFormDesc.value = p?.description || ''; playlistFormError.value = '';
  showPlaylistModal.value = true;
  nextTick(() => document.querySelector<HTMLInputElement>('#playlist-name')?.focus());
}

function closePlaylistModal() { if (!playlistSaving.value) showPlaylistModal.value = false; }

async function savePlaylist() {
  if (playlistSaving.value) return;
  const name = playlistFormName.value.trim();
  if (!name) { playlistFormError.value = '歌单名称不能为空'; return; }
  playlistSaving.value = true; playlistFormError.value = '';
  const token = authToken.value;
  try {
    const rename = playlistModalMode.value === 'rename';
    const r = await fetch(rename ? `/api/playlists/${playlistEditingId.value}` : '/api/playlists', {
      method: rename ? 'PUT' : 'POST', headers: apiHeaders(),
      body: JSON.stringify({ name, description: playlistFormDesc.value.trim() })
    });
    const data = await playlistResponse<PlaylistSummary>(r);
    if (token !== authToken.value) return;
    if (activePlaylist.value?.id === data.id) {
      activePlaylist.value.name = data.name; activePlaylist.value.description = data.description;
    }
    showPlaylistModal.value = false;
    await loadPlaylists();
    notifyPlaylist(rename ? '歌单已更新' : '歌单创建成功');
  } catch (e) {
    if (token === authToken.value) playlistFormError.value = e instanceof Error ? e.message : '保存失败，请稍后重试。';
  } finally { playlistSaving.value = false; }
}

async function deletePlaylist(p: PlaylistSummary | PlaylistDetail) {
  if (playlistBusy.value || !window.confirm(`确定删除歌单「${p.name}」吗？`)) return;
  playlistBusy.value = true;
  const token = authToken.value;
  try {
    const r = await fetch(`/api/playlists/${p.id}`, { method: 'DELETE', headers: apiHeaders() });
    await playlistResponse(r);
    if (token !== authToken.value) return;
    if (activePlaylist.value?.id === p.id) { playlistDetailRequest++; activePlaylist.value = null; switchView('library'); }
    await loadPlaylists();
    notifyPlaylist('歌单已删除');
  } catch (e) { if (token === authToken.value) notifyPlaylist(e instanceof Error ? e.message : '删除失败，请稍后重试。', true); }
  finally { playlistBusy.value = false; }
}

async function removePlaylistSong(song: Song) {
  const p = activePlaylist.value;
  if (!p || playlistBusy.value) return;
  playlistBusy.value = true;
  const token = authToken.value;
  try {
    const r = await fetch(`/api/playlists/${p.id}/songs/${song.id}`, { method: 'DELETE', headers: apiHeaders() });
    await playlistResponse(r);
    if (token !== authToken.value) return;
    if (activePlaylist.value?.id === p.id) activePlaylist.value.songs = activePlaylist.value.songs.filter(s => s.id !== song.id);
    await loadPlaylists();
    notifyPlaylist('歌曲已从歌单移除');
  } catch (e) { if (token === authToken.value) notifyPlaylist(e instanceof Error ? e.message : '移除失败，请稍后重试。', true); }
  finally { playlistBusy.value = false; }
}

function closePlaylistMenu() { addToPlaylistSong.value = null; playlistMenuRequest++; playlistMenuLoading.value = false; }
function handlePlaylistEscape(e: KeyboardEvent) { if (e.key === 'Escape') { closePlaylistMenu(); closePlaylistModal(); } }

async function openAddToPlaylist(song: Song, event: MouseEvent) {
  const anchor = (event.currentTarget as HTMLElement).getBoundingClientRect();
  const width = Math.min(300, window.innerWidth - 24);
  const height = Math.min(360, window.innerHeight - 24);
  playlistMenuStyle.value = {
    left: `${Math.max(12, Math.min(anchor.right - width, window.innerWidth - width - 12))}px`,
    top: `${Math.max(12, Math.min(anchor.bottom + 6, window.innerHeight - height - 12))}px`
  };
  addToPlaylistSong.value = song; playlistContainsIds.value = new Set(); playlistMenuError.value = '';
  const request = ++playlistMenuRequest;
  if (!authToken.value) return;
  playlistMenuLoading.value = true;
  try {
    if (!await loadPlaylists()) throw new Error('歌单暂时无法加载，请稍后重试。');
    if (request !== playlistMenuRequest) return;
    const details = await Promise.all(playlists.value.map(async p => {
      const r = await fetch(`/api/playlists/${p.id}`, { headers: apiHeaders() });
      return playlistResponse<PlaylistDetailResponse>(r);
    }));
    if (request !== playlistMenuRequest) return;
    playlistContainsIds.value = new Set(details.filter(p => p.songs.some(s => s.id === song.id)).map(p => p.id));
  } catch (e) {
    if (request === playlistMenuRequest) playlistMenuError.value = e instanceof Error ? e.message : '歌单暂时无法加载，请稍后重试。';
  } finally { if (request === playlistMenuRequest) playlistMenuLoading.value = false; }
}

async function addSongToPlaylist(p: PlaylistSummary) {
  const song = addToPlaylistSong.value;
  if (!song || playlistBusy.value || playlistMenuLoading.value || playlistContainsIds.value.has(p.id)) return;
  playlistBusy.value = true;
  const token = authToken.value;
  try {
    const r = await fetch(`/api/playlists/${p.id}/songs`, { method: 'POST', headers: apiHeaders(), body: JSON.stringify({ songId: song.id }) });
    await playlistResponse(r);
    if (token !== authToken.value) return;
    closePlaylistMenu();
    await loadPlaylists();
    notifyPlaylist(`已添加到「${p.name}」`);
  } catch (e) {
    if (token === authToken.value) { closePlaylistMenu(); notifyPlaylist(e instanceof Error ? e.message : '添加失败，请稍后重试。', true); }
  } finally { playlistBusy.value = false; }
}

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

function openProfile() {
  if (!authUser.value) {
    showAuthModal.value = true; authMode.value = 'login'; authError.value = ''; authUsername.value = ''; authPassword.value = '';
    return;
  }
  switchView('profile');
}

function addToRecent(song: Song) {
  recentSongIds.value = [song.id, ...recentSongIds.value.filter(id => id !== song.id)].slice(0, 12);
  if (authToken.value) { fetch('/api/history', { method: 'POST', headers: apiHeaders(), body: JSON.stringify({ songId: song.id }) }).catch(() => {}); }
}

function playRandomSong(list: Song[] = songs.value) { if (list.length) selectSong(list[Math.floor(Math.random() * list.length)]); }
function nextSong() { if (!currentSong.value || !songs.value.length) return; const i = songs.value.findIndex(s => s.id === currentSong.value?.id); currentSong.value = songs.value[(i+1)%songs.value.length]; resetPlaybackPosition(); playCurrentSong(); }
function previousSong() { if (!currentSong.value || !songs.value.length) return; const i = songs.value.findIndex(s => s.id === currentSong.value?.id); currentSong.value = songs.value[(i-1+songs.value.length)%songs.value.length]; resetPlaybackPosition(); playCurrentSong(); }
let lastPersistAt = 0;
/** 持久化当前播放状态（歌曲 + 进度）。resetTime=true 表示切歌，进度归零 */
function persistPlayerState(resetTime = false) {
  const song = currentSong.value;
  if (!song) return;
  const now = Date.now();
  if (!resetTime && now - lastPersistAt < 1000) return; // 节流：最多每秒写一次 localStorage
  lastPersistAt = now;
  const time = resetTime ? 0 : Math.round(audioElement.value?.currentTime ?? currentSeconds.value);
  try { localStorage.setItem(PLAYER_STATE_KEY, JSON.stringify({ songId: song.id, time })); } catch {}
}

/** 刷新后恢复上次的歌曲与进度；找不到对应歌曲则清除记录并保持空状态 */
function restorePlayback() {
  if (currentSong.value) return; // 已有选中歌曲则不覆盖
  try {
    const raw = localStorage.getItem(PLAYER_STATE_KEY);
    if (!raw) return;
    const state = JSON.parse(raw) as { songId?: unknown; time?: unknown };
    if (typeof state.songId !== 'number') return;
    const song = songs.value.find((s) => s.id === state.songId);
    if (!song) { localStorage.removeItem(PLAYER_STATE_KEY); return; }
    currentSong.value = song;
    if (typeof state.time === 'number' && state.time > 0) pendingSeekTime.value = state.time;
  } catch { localStorage.removeItem(PLAYER_STATE_KEY); }
}

function resetPlaybackPosition() { progress.value = 0; currentSeconds.value = 0; audioDuration.value = 0; }
async function togglePlayback() { if (!currentSong.value) return; if (isPlaying.value) { audioElement.value?.pause(); isPlaying.value = false; } else await playCurrentSong(); }
async function playCurrentSong() { await nextTick(); const a = audioElement.value; if (!a) return; a.volume = volume.value/100; try { await a.play(); isPlaying.value = true; } catch { isPlaying.value = false; } }
function updateMetadata() {
  const a = audioElement.value;
  if (!a) return;
  audioDuration.value = Number.isFinite(a.duration) ? a.duration : currentSong.value?.duration || 0;
  // 刷新恢复：seek 到上次保存的进度
  if (pendingSeekTime.value !== null) {
    const t = pendingSeekTime.value;
    pendingSeekTime.value = null;
    if (Number.isFinite(a.duration) && t > 0 && t < a.duration - 0.5) {
      a.currentTime = t;
      currentSeconds.value = t;
      progress.value = activeDuration.value > 0 ? Math.round((t / activeDuration.value) * 1000) / 10 : 0;
    }
  }
}
function updateProgress() {
  const a = audioElement.value;
  if (a) {
    currentSeconds.value = a.currentTime;
    progress.value = activeDuration.value > 0 ? Math.round((a.currentTime / activeDuration.value) * 1000) / 10 : 0;
    updateLyricIndex(a.currentTime);
    persistPlayerState();
  }
}
function updateLyricIndex(time: number) {
  // 行级高亮在这里更新（timeupdate 频率低）；逐字流光由 LyricStage 内部用 rAF 自行处理。
  // 纯文本歌词没有时间轴，不参与高亮。
  if (!lyricLines.value.length || lyricsFormat.value === 'plain') return;
  const idx = findActiveIndex(lyricLines.value, time);
  if (idx !== currentLyricIndex.value) currentLyricIndex.value = idx;
}
/** 页面重新可见/聚焦时，由 LyricStage 触发，重新对齐当前行 */
function resyncLyrics() {
  if (audioElement.value) updateLyricIndex(audioElement.value.currentTime);
}
async function loadLyrics(song: Song) {
  lyricLines.value = [];
  currentLyricIndex.value = -1;
  lyricsFormat.value = 'plain';
  if (!song.lyricUrl) return;
  try {
    const res = await fetch(song.lyricUrl);
    if (!res.ok) return;
    const raw = await res.text();
    const doc = parseLyrics(raw, { fileName: song.lyricUrl, duration: song.duration });
    lyricLines.value = doc.lines;
    lyricsFormat.value = doc.format;
  } catch {}
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
  editedCoverFile.value = null; editedCoverPreview.value = '';
  editedAudioFile.value = null; editedAudioName.value = '';
  editedLyricFile.value = null; editedLyricName.value = '';
}

function cancelEdit() { editSong.value = null; }

function handleEditCoverSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files?.[0]) {
    editedCoverFile.value = input.files[0];
    editedCoverPreview.value = URL.createObjectURL(input.files[0]);
  }
}

async function handleEditAudioSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  editedAudioFile.value = file;
  editedAudioName.value = file.name;
  editedDuration.value = 0;

  try {
    const metadata = await parseBlob(file);
    if (metadata.format.duration && metadata.format.duration > 0) {
      editedDuration.value = Math.round(metadata.format.duration);
    }
  } catch (err) {
    console.error('music-metadata 解析失败:', err);
  }

  if (editedDuration.value <= 0) {
    const audio = new Audio(URL.createObjectURL(file));
    audio.addEventListener('loadedmetadata', () => {
      if (editedDuration.value <= 0) editedDuration.value = Math.round(audio.duration || 0);
    });
  }
}

function handleEditLyricSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files?.[0]) { editedLyricFile.value = input.files[0]; editedLyricName.value = input.files[0].name; }
}

async function saveEdit() {
  if (!editSong.value) return;
  adminSaving.value = true; adminMsg.value = '';
  try {
    let coverUrl = editedCoverUrl.value;
    let audioUrl = editedAudioUrl.value;
    let lyricUrl = editedLyricUrl.value;
    if (editedCoverFile.value) coverUrl = await uploadFileToServer(editedCoverFile.value, 'cover');
    if (editedAudioFile.value) audioUrl = await uploadFileToServer(editedAudioFile.value, 'audio');
    if (editedLyricFile.value) lyricUrl = await uploadFileToServer(editedLyricFile.value, 'lyric');
    const duration = editedAudioFile.value && editedDuration.value > 0 ? editedDuration.value : editSong.value.duration;
    const r = await fetch(`/api/admin/songs/${editSong.value.id}`, {
      method: 'PUT', headers: apiHeaders(),
      body: JSON.stringify({ title: editedTitle.value, artist: editedArtist.value, album: editedAlbum.value, duration, coverUrl: coverUrl || null, audioUrl, lyricUrl: lyricUrl || null })
    });
    if (r.ok) { adminMsg.value = '保存成功'; editSong.value = null; loadSongs(); }
    else { const e = await r.json(); adminMsg.value = e.error || '保存失败'; }
  } catch { adminMsg.value = '保存失败'; }
  adminSaving.value = false;
}

function cleanupLocalSongCache(songId: number) {
  // 正在播放的就是被删歌曲：停止播放、清空播放器与歌词页
  if (currentSong.value?.id === songId) {
    const a = audioElement.value;
    if (a) { a.pause(); a.removeAttribute('src'); a.load(); }
    isPlaying.value = false;
    currentSong.value = null;
    showDetail.value = false;
    lyricLines.value = [];
    currentLyricIndex.value = -1;
    resetPlaybackPosition();
    // 播放状态指向已删除的歌曲，一并清除，避免刷新时尝试恢复失效引用
    try { localStorage.removeItem(PLAYER_STATE_KEY); } catch {}
  }
  // 最近播放记录
  recentSongIds.value = recentSongIds.value.filter(id => id !== songId);
  // 收藏集合
  if (likedSongIds.value.has(songId)) {
    const n = new Set(likedSongIds.value);
    n.delete(songId);
    likedSongIds.value = n;
  }
  // 歌单中的引用（当前打开的歌单 + 歌单菜单缓存）
  if (activePlaylist.value) {
    activePlaylist.value.songs = activePlaylist.value.songs.filter(s => s.id !== songId);
  }
  if (playlistContainsIds.value.has(songId)) {
    const n = new Set(playlistContainsIds.value);
    n.delete(songId);
    playlistContainsIds.value = n;
  }
}

async function deleteSongItem(s: Song) {
  if (!confirm(`确定删除「${s.title}」吗？删除后歌曲文件与相关记录（收藏、播放历史、歌单引用）将一并清除。`)) return;
  adminSaving.value = true; adminMsg.value = '';
  try {
    const r = await fetch(`/api/admin/songs/${s.id}`, { method: 'DELETE', headers: apiHeaders() });
    if (r.ok) {
      adminMsg.value = '删除成功';
      cleanupLocalSongCache(s.id);
      loadSongs();
    } else {
      const e = await r.json();
      adminMsg.value = e.error || '删除失败';
    }
  } catch { adminMsg.value = '删除失败'; }
  adminSaving.value = false;
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
      uploadTitle.value = ''; uploadArtist.value = ''; uploadAlbum.value = ''; uploadDuration.value = 0;
      uploadCoverFile.value = null; uploadAudioFile.value = null; uploadLyricFile.value = null;
      uploadCoverPreview.value = ''; uploadAudioName.value = ''; uploadLyricName.value = ''; uploadMetadataHint.value = '';
      loadSongs();
    } else { const e = await r.json(); adminMsg.value = e.error || '上传失败'; }
  } catch { adminMsg.value = '上传失败'; }
  adminSaving.value = false;
}

async function extractBatchAudioMetadata(file: File): Promise<Pick<BatchSongItem, 'title' | 'artist' | 'album' | 'duration' | 'cover' | 'coverPreview' | 'metadataMessage'>> {
  let title = '';
  let artist = '';
  let album = '';
  let duration = 0;
  let cover: File | null = null;
  let coverPreview = '';
  let matched = 0;

  try {
    const metadata = await parseBlob(file);
    const { common, format } = metadata;
    if (common.title) { title = common.title; matched++; }
    if (common.artist) { artist = common.artist; matched++; }
    if (common.album) { album = common.album; matched++; }
    if (format.duration && format.duration > 0) { duration = Math.round(format.duration); matched++; }
    if (common.picture && common.picture.length > 0) {
      const picture = common.picture[0];
      const blob = new Blob([picture.data as unknown as BlobPart], { type: picture.type || 'image/jpeg' });
      cover = new File([blob], `cover.${picture.type?.split('/')[1] || 'jpg'}`, { type: picture.type || 'image/jpeg' });
      coverPreview = URL.createObjectURL(blob);
      matched++;
    }
  } catch (error) {
    console.warn('批量导入元数据解析失败:', error);
  }

  return {
    title,
    artist,
    album,
    duration,
    cover,
    coverPreview,
    metadataMessage: matched > 0 ? `已从文件中读取 ${matched} 项信息` : '文件中未包含元数据，请手动填写'
  };
}

function releaseBatchCoverPreview(item: BatchSongItem) {
  if (item.coverPreview) {
    URL.revokeObjectURL(item.coverPreview);
    item.coverPreview = '';
  }
}

function releaseBatchCoverPreviews() { batchItems.value.forEach(releaseBatchCoverPreview); }

function batchItemHasMissing(item: BatchSongItem) {
  return !item.title.trim() || !item.artist.trim() || !item.album.trim();
}

async function handleBatchAudioSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  const selectedFiles = Array.from(input.files || []);
  input.value = '';
  if (!selectedFiles.length) return;
  if (selectedFiles.length > 50) {
    adminMsg.value = '一次最多选择 50 个音频文件';
  }

  const existingKeys = new Set(batchItems.value.map(item => `${item.file.name}:${item.file.size}`));
  const newFiles: File[] = [];
  let duplicateCount = 0;
  for (const file of selectedFiles.slice(0, 50)) {
    const key = `${file.name}:${file.size}`;
    if (existingKeys.has(key)) { duplicateCount++; continue; }
    existingKeys.add(key);
    newFiles.push(file);
  }
  if (duplicateCount > 0) {
    adminMsg.value = `${adminMsg.value ? `${adminMsg.value}；` : ''}已跳过 ${duplicateCount} 个重复文件`;
  }

  for (const file of newFiles) {
    const item: BatchSongItem = {
      file,
      title: '',
      artist: '',
      album: '',
      duration: 0,
      cover: null,
      coverPreview: '',
      metadataMessage: '正在读取文件信息...',
      status: 'pending',
      error: ''
    };
    batchItems.value.push(item);
    const metadata = await extractBatchAudioMetadata(file);
    const current = batchItems.value.find(candidate => candidate.file === file);
    if (current) Object.assign(current, metadata);
  }
}

function removeBatchItem(index: number) {
  if (batchImporting.value) return;
  const item = batchItems.value[index];
  if (item) releaseBatchCoverPreview(item);
  batchItems.value.splice(index, 1);
}

function clearBatchItems() {
  if (batchImporting.value) return;
  releaseBatchCoverPreviews();
  batchItems.value = [];
  batchProgress.value = '';
}

async function uploadBatchFiles(files: File[], type: 'audio' | 'cover'): Promise<{ url: string | null; error: string | null }[]> {
  const formData = new FormData();
  files.forEach(file => formData.append('files', file));
  formData.append('type', type);
  const response = await fetch('/api/admin/upload/batch', { method: 'POST', headers: authHeaders(), body: formData });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || '批量上传失败');
  return data.results || [];
}

async function createBatchSongs(songs: Record<string, unknown>[]) {
  const response = await fetch('/api/admin/songs/batch', {
    method: 'POST',
    headers: apiHeaders(),
    body: JSON.stringify({ songs })
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || '批量创建歌曲失败');
  return data as { created: number; results: { index: number; success: boolean; id?: number; error?: string }[] };
}

async function importBatchSongs() {
  if (batchImporting.value || !batchItems.value.length) {
    if (!batchItems.value.length) adminMsg.value = '没有可导入的歌曲';
    return;
  }

  adminMsg.value = '';
  batchProgress.value = '';
  const rows = batchItems.value.filter(item => item.status !== 'done');
  const validRows: BatchSongItem[] = [];
  rows.forEach(item => {
    item.error = '';
    if (batchItemHasMissing(item)) {
      item.status = 'failed';
      item.error = '缺少必填信息';
    } else {
      item.status = 'uploading';
      validRows.push(item);
    }
  });
  if (!validRows.length) {
    adminMsg.value = '没有可导入的歌曲';
    return;
  }

  batchImporting.value = true;
  const audioUrls = new Map<BatchSongItem, string>();
  const coverUrls = new Map<BatchSongItem, string>();
  try {
    for (let start = 0; start < validRows.length; start += 10) {
      const chunk = validRows.slice(start, start + 10);
      batchProgress.value = `正在上传音频文件 ${Math.min(start + chunk.length, validRows.length)}/${validRows.length}…`;
      try {
        const results = await uploadBatchFiles(chunk.map(item => item.file), 'audio');
        chunk.forEach((item, index) => {
          const result = results[index];
          if (result?.url && !result.error) audioUrls.set(item, result.url);
          else { item.status = 'failed'; item.error = result?.error || '音频上传失败'; }
        });
      } catch (error) {
        chunk.forEach(item => { item.status = 'failed'; item.error = error instanceof Error ? error.message : '音频上传失败'; });
      }
    }

    const coverRows = validRows.filter(item => item.status === 'uploading' && item.cover);
    for (let start = 0; start < coverRows.length; start += 10) {
      const chunk = coverRows.slice(start, start + 10);
      batchProgress.value = `正在上传封面文件 ${Math.min(start + chunk.length, coverRows.length)}/${coverRows.length}…`;
      try {
        const results = await uploadBatchFiles(chunk.map(item => item.cover as File), 'cover');
        chunk.forEach((item, index) => {
          const result = results[index];
          if (result?.url && !result.error) coverUrls.set(item, result.url);
          else { item.status = 'failed'; item.error = result?.error || '封面上传失败'; }
        });
      } catch (error) {
        chunk.forEach(item => { item.status = 'failed'; item.error = error instanceof Error ? error.message : '封面上传失败'; });
      }
    }

    const rowsToCreate = validRows.filter(item => item.status === 'uploading' && audioUrls.has(item) && (!item.cover || coverUrls.has(item)));
    if (rowsToCreate.length) {
      batchProgress.value = `正在创建 ${rowsToCreate.length} 条歌曲记录…`;
      try {
        const data = await createBatchSongs(rowsToCreate.map(item => ({
          title: item.title.trim(),
          artist: item.artist.trim(),
          album: item.album.trim(),
          duration: item.duration,
          audioUrl: audioUrls.get(item),
          coverUrl: item.cover ? coverUrls.get(item) : null,
          lyricUrl: null
        })));
        rowsToCreate.forEach((item, index) => {
          const result = data.results?.[index];
          if (result?.success) { item.status = 'done'; item.error = ''; }
          else { item.status = 'failed'; item.error = result?.error || '歌曲创建失败'; }
        });
      } catch (error) {
        rowsToCreate.forEach(item => { item.status = 'failed'; item.error = error instanceof Error ? error.message : '歌曲创建失败'; });
      }
    }

    const successCount = validRows.filter(item => item.status === 'done').length;
    const failedCount = rows.filter(item => item.status === 'failed').length;
    adminMsg.value = `批量导入完成：成功 ${successCount} 首，失败 ${failedCount} 首`;
    await loadSongs();
  } finally {
    batchProgress.value = '';
    batchImporting.value = false;
  }
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
        <button class="nav-item" :class="{ active: activeView === 'library' || activeView === 'playlist' }" type="button" @click="switchView('library')">
          <Library :size="20" />我的音乐
        </button>
        <button v-if="authUser" class="nav-item" :class="{ active: activeView === 'profile' }" type="button" @click="openProfile">
          <User :size="20" />个人中心
        </button>
      </nav>

      <section class="user-section">
        <template v-if="authUser">
          <div class="user-info" role="button" tabindex="0" title="打开个人中心" @click="openProfile" @keydown.enter.self="openProfile">
            <span class="user-avatar" :class="{ 'has-avatar': !!authUser.avatarUrl }">
              <img v-if="authUser.avatarUrl" :src="authUser.avatarUrl" alt="" />
              <template v-else>{{ (authUser.nickname || authUser.username).charAt(0).toUpperCase() }}</template>
            </span>
            <span class="user-name">{{ authUser.nickname || authUser.username }}</span>
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
        <div v-if="activeView !== 'discover'">
          <p class="eyebrow">{{ viewTitle.eyebrow }}</p>
          <h1>{{ viewTitle.title }}</h1>
        </div>
        <label class="search-box">
          <Search :size="18" />
          <input v-model="searchKeyword" type="search" placeholder="搜索歌曲、歌手、专辑" @focus="switchView('search')" />
        </label>
      </header>

      <p v-if="playlistMsg" class="status-text playlist-notice" :class="{ warning: playlistMsgError }" role="status">{{ playlistMsg }}</p>

      <!-- ============ 首页推荐 ============ -->
      <template v-if="activeView === 'discover'">
        <section class="home-greeting">
          <div>
            <h2 class="home-greeting-title">{{ greeting }}<template v-if="authUser">，{{ authUser.nickname || authUser.username }}</template></h2>
            <p class="home-greeting-sub">为你精选的音乐，随时开启。</p>
          </div>
        </section>

        <section class="home-shortcuts" aria-label="快捷入口">
          <button class="home-shortcut" type="button" @click="switchView('library')">
            <span class="home-shortcut-icon recent"><Clock3 :size="22" /></span>
            <span class="home-shortcut-label">最近播放</span>
          </button>
          <button class="home-shortcut" type="button" @click="switchView('library')">
            <span class="home-shortcut-icon liked"><Heart :size="22" /></span>
            <span class="home-shortcut-label">收藏的歌</span>
          </button>
          <button class="home-shortcut" type="button" @click="switchView('library')">
            <span class="home-shortcut-icon playlist"><ListMusic :size="22" /></span>
            <span class="home-shortcut-label">我的歌单</span>
          </button>
          <button class="home-shortcut" type="button" @click="playRandomSong(songs)">
            <span class="home-shortcut-icon shuffle"><Shuffle :size="22" /></span>
            <span class="home-shortcut-label">随机播放</span>
          </button>
        </section>

        <section v-if="currentSong" class="home-now" role="button" tabindex="0" aria-label="打开正在播放" @click="openDetail" @keydown.enter.self="openDetail">
          <div class="home-now-cover" :style="{ background: currentSong.color }">
            <img :src="currentSong.coverUrl" :alt="`${currentSong.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" />
            <span>{{ currentSong.cover }}</span>
          </div>
          <div class="home-now-info">
            <p class="home-now-eyebrow">正在播放</p>
            <h2>{{ currentSong.title }}</h2>
            <p class="home-now-meta">{{ currentSong.artist }} · {{ currentSong.album }}</p>
          </div>
          <button class="home-now-play" type="button" :aria-label="isPlaying ? '暂停' : '播放'" @click.stop="togglePlayback">
            <Pause v-if="isPlaying" :size="22" fill="currentColor" /><Play v-else :size="22" fill="currentColor" />
          </button>
        </section>

        <section class="home-section">
          <div class="home-section-head">
            <h3>最近播放</h3>
            <button v-if="recentSongs.length" class="home-see-all" type="button" @click="switchView('library')">查看全部</button>
          </div>
          <div v-if="recentSongs.length" class="home-row">
            <button v-for="song in recentSongs.slice(0, 12)" :key="'r' + song.id" class="home-song-card" type="button" @click="selectSong(song)">
              <div class="home-card-cover" :style="{ background: song.color }">
                <img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" />
                <span class="home-card-fallback">{{ song.cover }}</span>
                <span class="home-card-play"><Play :size="16" fill="currentColor" /></span>
              </div>
              <div class="home-card-meta">
                <strong>{{ song.title }}</strong>
                <small>{{ song.artist }}</small>
              </div>
            </button>
          </div>
          <p v-else class="home-empty">播放歌曲后会出现在这里。</p>
        </section>

        <section class="home-section">
          <div class="home-section-head">
            <h3>为你推荐</h3>
          </div>
          <div v-if="recommendedSongs.length" class="home-row">
            <button v-for="song in recommendedSongs" :key="'rc' + song.id" class="home-song-card" type="button" @click="selectSong(song)">
              <div class="home-card-cover" :style="{ background: song.color }">
                <img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" />
                <span class="home-card-fallback">{{ song.cover }}</span>
                <span class="home-card-play"><Play :size="16" fill="currentColor" /></span>
              </div>
              <div class="home-card-meta">
                <strong>{{ song.title }}</strong>
                <small>{{ song.artist }}</small>
              </div>
            </button>
          </div>
          <p v-else class="home-empty">暂无推荐，去导入一些歌曲吧。</p>
        </section>

        <section class="home-section">
          <div class="home-section-head">
            <h3>我的歌单</h3>
            <button class="home-see-all" type="button" @click="switchView('library')">查看全部</button>
          </div>
          <div v-if="playlists.length" class="home-row">
            <button v-for="p in playlists.slice(0, 12)" :key="'p' + p.id" class="home-song-card" type="button" @click="openPlaylist(p)">
              <div class="home-card-cover home-playlist-cover"><ListMusic :size="28" /></div>
              <div class="home-card-meta">
                <strong>{{ p.name }}</strong>
                <small>{{ p.songCount }} 首{{ p.description ? ' · ' + p.description : '' }}</small>
              </div>
            </button>
          </div>
          <p v-else class="home-empty">
            <template v-if="authToken">还没有歌单，去「我的音乐」新建一个吧。</template>
            <template v-else>登录后即可创建歌单。</template>
          </p>
        </section>
      </template>

      <section v-if="activeView === 'library'" class="library-grid">
        <article class="metric-card"><span>收藏歌曲</span><strong>{{ likedSongList.length }}</strong></article>
        <article class="metric-card"><span>最近播放</span><strong>{{ recentSongs.length }}</strong></article>
      </section>

      <section v-if="activeView === 'library'" class="playlist-section" aria-label="我的歌单">
        <div class="table-heading playlist-heading">
          <h2>我的歌单</h2>
          <button class="ghost-button" type="button" @click="openPlaylistModal()"><Plus :size="18" />新建歌单</button>
        </div>
        <div v-if="!authToken" class="playlist-login">
          <p class="status-text">登录后即可创建歌单。</p>
          <button class="ghost-button" type="button" @click="showAuthModal = true"><LogIn :size="18" />登录</button>
        </div>
        <template v-else>
          <div v-if="playlists.length" class="playlist-grid">
            <article v-for="p in playlists" :key="p.id" class="playlist-card saved-playlist-card">
              <button class="playlist-open" type="button" @click="openPlaylist(p)">
                <ListMusic :size="24" />
                <strong>{{ p.name }}</strong>
                <span>{{ p.songCount }} 首</span>
                <small>{{ p.description || '暂无描述' }}</small>
              </button>
              <div class="playlist-card-actions">
                <button class="icon-button" type="button" title="重命名歌单" aria-label="重命名歌单" @click.stop="openPlaylistModal(p)"><Pencil :size="18" /></button>
                <button class="icon-button danger" type="button" title="删除歌单" aria-label="删除歌单" :disabled="playlistBusy" @click.stop="deletePlaylist(p)"><Trash2 :size="18" /></button>
              </div>
            </article>
          </div>
          <p v-else class="status-text">还没有歌单，点击右上角新建一个吧。</p>
        </template>
      </section>

      <section v-if="activeView === 'playlist'" class="playlist-detail" aria-label="歌单详情">
        <div class="playlist-heading">
          <button class="ghost-button" type="button" @click="switchView('library')"><ArrowLeft :size="18" />返回我的音乐</button>
          <div v-if="activePlaylist" class="playlist-detail-actions">
            <button class="icon-button" type="button" title="重命名歌单" aria-label="重命名歌单" @click="openPlaylistModal(activePlaylist)"><Pencil :size="18" /></button>
            <button class="ghost-button" type="button" :disabled="playlistBusy" @click="deletePlaylist(activePlaylist)"><Trash2 :size="18" />删除歌单</button>
          </div>
        </div>
        <p v-if="playlistLoading" class="status-text">正在加载歌单...</p>
        <template v-else-if="activePlaylist">
          <div class="playlist-description">
            <p>{{ activePlaylist.description || '暂无描述' }}</p>
            <span>{{ activePlaylist.songs.length }} 首</span>
          </div>
          <div class="song-table playlist-song-table">
            <div class="song-row header-row"><span>歌曲</span><span class="song-album">专辑</span><span>时长</span><span>操作</span></div>
            <div v-for="song in activePlaylist.songs" :key="song.id" class="song-row" :class="{ selected: song.id === currentSong?.id }" role="button" tabindex="0" @click="selectSong(song)" @keydown.enter.self="selectSong(song)" @keydown.space.self.prevent="selectSong(song)">
              <span class="song-cell">
                <span class="cover-small" :style="{ background: song.color }"><img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ song.cover }}</span></span>
                <span><strong>{{ song.title }}</strong><small>{{ song.artist }}</small></span>
              </span>
              <span class="song-album">{{ song.album }}</span><span>{{ formatTime(song.duration) }}</span>
              <span class="row-actions">
                <button class="icon-button" type="button" title="收藏歌曲" aria-label="收藏歌曲" @click.stop="toggleLike(song)"><Heart :size="18" :fill="likedSongIds.has(song.id) ? 'currentColor' : 'none'" /></button>
                <button class="icon-button danger" type="button" title="从歌单移除" aria-label="从歌单移除" :disabled="playlistBusy" @click.stop="removePlaylistSong(song)"><Trash2 :size="18" /></button>
              </span>
            </div>
            <p v-if="!activePlaylist.songs.length" class="status-text">歌单还是空的，去歌曲列表添加歌曲吧。</p>
          </div>
        </template>
      </section>

      <section v-if="activeView === 'profile' && authUser" class="profile-section">
        <ProfileView :user="authUser" :token="authToken || ''" @update-user="(u) => (authUser = u)" />
      </section>

      <section v-if="activeView === 'search' || activeView === 'library'" class="song-table main-song-table" aria-label="歌曲列表">
        <div class="table-heading">
          <h2>{{ songSectionTitle }}</h2>
          <button class="ghost-button" type="button" @click="playRandomSong(visibleSongs)"><Shuffle :size="18" />随机播放</button>
        </div>
        <p v-if="isLoading" class="status-text">正在加载歌曲...</p>
        <p v-else-if="errorMessage" class="status-text warning">后端暂时不可用，已显示本地示例数据。</p>
        <div class="song-row header-row"><span>歌曲</span><span class="song-album">专辑</span><span>时长</span><span></span></div>
        <div v-for="song in visibleSongs" :key="song.id" class="song-row" :class="{ selected: song.id === currentSong?.id }" role="button" tabindex="0" @click="selectSong(song)" @keydown.enter.self="selectSong(song)" @keydown.space.self.prevent="selectSong(song)">
          <span class="song-cell">
            <span class="cover-small" :style="{ background: song.color }"><img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ song.cover }}</span></span>
            <span><strong>{{ song.title }}</strong><small>{{ song.artist }}</small></span>
          </span>
          <span class="song-album">{{ song.album }}</span>
          <span>{{ formatTime(song.duration) }}</span>
          <span class="row-actions">
            <button class="icon-button" type="button" aria-label="收藏歌曲" @click.stop="toggleLike(song)">
              <Heart :size="18" :fill="likedSongIds.has(song.id) ? 'currentColor' : 'none'" />
            </button>
            <button class="icon-button" type="button" title="添加到歌单" aria-label="添加到歌单" :aria-expanded="addToPlaylistSong?.id === song.id" @click.stop="openAddToPlaylist(song, $event)"><MoreHorizontal :size="18" /></button>
          </span>
        </div>
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
          <button :class="{ active: activeAdminTab === 'batch' }" @click="activeAdminTab = 'batch'"><Files :size="16" />批量导入</button>
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

      <section v-if="activeAdminTab === 'batch'" class="batch-import-panel">
        <div class="batch-toolbar">
          <div>
            <h2>批量导入歌曲</h2>
            <p class="batch-count">共 {{ batchItems.length }} 首</p>
          </div>
          <div class="batch-toolbar-actions">
            <button class="ghost-button" type="button" :disabled="batchImporting || !batchItems.length" @click="clearBatchItems">清空</button>
            <button class="primary-action" type="button" :disabled="batchImporting || !batchItems.length" @click="importBatchSongs"><Upload :size="18" />{{ batchImporting ? '导入中...' : '开始导入' }}</button>
          </div>
        </div>

        <label class="file-drop batch-file-drop" :class="{ 'has-file': batchItems.length }">
          <Files :size="26" />
          <strong>选择多个音频文件</strong>
          <span class="file-label">可重复选择，系统会自动读取歌曲信息和内嵌封面</span>
          <input type="file" accept="audio/*" multiple hidden :disabled="batchImporting" @change="handleBatchAudioSelect" />
        </label>

        <p v-if="batchProgress" class="status-text batch-progress">{{ batchProgress }}</p>
        <p v-if="!batchItems.length" class="status-text">选择多个音频文件，自动读取歌曲信息并批量导入。</p>

        <div v-else class="batch-list">
          <article v-for="(item, index) in batchItems" :key="`${item.file.name}-${item.file.size}-${index}`" class="batch-item" :class="`batch-item-${item.status}`">
            <div class="batch-item-header">
              <span class="batch-index">{{ index + 1 }}</span>
              <strong class="batch-file-name" :title="item.file.name">{{ item.file.name }}</strong>
              <span v-if="item.status === 'done'" class="batch-status success">已导入</span>
              <span v-else-if="item.status === 'uploading'" class="batch-status">处理中...</span>
              <button class="icon-button danger" type="button" title="移除文件" aria-label="移除文件" :disabled="batchImporting" @click="removeBatchItem(index)"><X :size="18" /></button>
            </div>
            <div class="batch-item-content">
              <div class="batch-cover" :style="{ background: getSongColor(index + 1) }">
                <img v-if="item.coverPreview" :src="item.coverPreview" alt="内嵌封面预览" />
                <Files v-else :size="24" />
              </div>
              <div class="batch-fields">
                <label><span>标题 *</span><input v-model="item.title" :class="{ 'batch-input-error': !item.title.trim() }" :disabled="batchImporting || item.status === 'done'" maxlength="200" placeholder="歌曲标题" /></label>
                <label><span>歌手 *</span><input v-model="item.artist" :class="{ 'batch-input-error': !item.artist.trim() }" :disabled="batchImporting || item.status === 'done'" maxlength="200" placeholder="歌手名称" /></label>
                <label><span>专辑 *</span><input v-model="item.album" :class="{ 'batch-input-error': !item.album.trim() }" :disabled="batchImporting || item.status === 'done'" maxlength="200" placeholder="专辑名称" /></label>
              </div>
              <div class="batch-item-meta">
                <span>时长：{{ item.duration > 0 ? formatTime(item.duration) : '未识别' }}</span>
                <span>{{ item.cover ? '已读取内嵌封面' : '未读取到封面' }}</span>
                <small>{{ item.metadataMessage }}</small>
                <strong v-if="item.error" class="batch-item-error">{{ item.error }}</strong>
                <span v-else-if="item.status === 'pending' && batchItemHasMissing(item)" class="batch-item-error">缺少必填信息</span>
              </div>
            </div>
          </article>
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
        <div v-if="showDetail && currentSong" class="detail-overlay">
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

            <LyricStage
              v-if="lyricLines.length"
              :lines="lyricLines"
              :active-index="currentLyricIndex"
              :audio-el="audioElement"
              :playing="isPlaying"
              @resync="resyncLyrics"
            />
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
      <div v-if="addToPlaylistSong" class="playlist-menu-backdrop" @click.self="closePlaylistMenu">
        <div class="playlist-menu" :style="playlistMenuStyle" role="dialog" aria-label="添加到歌单">
          <div class="playlist-menu-heading"><strong>添加到歌单</strong><button class="icon-button" type="button" title="关闭菜单" aria-label="关闭菜单" @click="closePlaylistMenu"><X :size="18" /></button></div>
          <p class="playlist-menu-song">{{ addToPlaylistSong.title }}</p>
          <template v-if="!authToken">
            <p class="status-text">登录后可添加</p>
            <button class="ghost-button" type="button" @click="closePlaylistMenu(); showAuthModal = true"><LogIn :size="18" />登录</button>
          </template>
          <template v-else>
            <p v-if="playlistMenuLoading" class="status-text">正在加载歌单...</p>
            <p v-else-if="playlistMenuError" class="status-text warning">{{ playlistMenuError }}</p>
            <div v-else class="playlist-menu-list">
              <button v-for="p in playlists" :key="p.id" class="playlist-menu-item" type="button" :disabled="playlistBusy || playlistContainsIds.has(p.id)" @click="addSongToPlaylist(p)">
                <Check v-if="playlistContainsIds.has(p.id)" :size="18" /><span v-else class="playlist-menu-count">{{ p.songCount }} 首</span>
                <span>{{ p.name }}</span><small v-if="playlistContainsIds.has(p.id)">已添加</small>
              </button>
              <p v-if="!playlists.length" class="status-text">还没有歌单。</p>
            </div>
            <button class="playlist-menu-item playlist-menu-create" type="button" @click="openPlaylistModal()"><Plus :size="18" />新建歌单</button>
          </template>
        </div>
      </div>

      <div v-if="showPlaylistModal" class="modal-backdrop" @click.self="closePlaylistModal">
        <div class="modal-card playlist-modal" role="dialog" aria-modal="true" aria-labelledby="playlist-modal-title">
          <div class="modal-header">
            <h3 id="playlist-modal-title">{{ playlistModalMode === 'create' ? '新建歌单' : '重命名歌单' }}</h3>
            <button class="icon-button" type="button" title="关闭" aria-label="关闭歌单弹窗" :disabled="playlistSaving" @click="closePlaylistModal"><X :size="20" /></button>
          </div>
          <form class="auth-form" @submit.prevent="savePlaylist">
            <label><span>名称（必填）</span><input id="playlist-name" v-model="playlistFormName" required maxlength="100" placeholder="歌单名称" /></label>
            <label><span>描述（选填）</span><textarea v-model="playlistFormDesc" maxlength="500" rows="3" placeholder="歌单描述" /></label>
            <p v-if="playlistFormError" class="auth-error" role="alert">{{ playlistFormError }}</p>
            <button class="primary-action" type="submit" :disabled="playlistSaving"><Save :size="18" />{{ playlistSaving ? '保存中...' : '保存' }}</button>
          </form>
        </div>
      </div>

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

            <div class="upload-files">
              <label class="file-drop" :class="{ 'has-file': editedCoverPreview || editedCoverUrl }">
                <template v-if="editedCoverPreview">
                  <img :src="editedCoverPreview" alt="新封面预览" class="cover-preview" />
                  <span class="file-label">新封面图片（点击更换）</span>
                </template>
                <template v-else-if="editedCoverUrl">
                  <img :src="editedCoverUrl" alt="当前封面" class="cover-preview" />
                  <span class="file-label">当前封面（点击更换，可选）</span>
                </template>
                <template v-else>
                  <Upload :size="24" /><span>点击上传封面图片（可选）</span>
                </template>
                <input type="file" accept="image/*" hidden @change="handleEditCoverSelect" />
              </label>

              <label class="file-drop" :class="{ 'has-file': editedAudioName }">
                <template v-if="editedAudioName">
                  <span class="file-name">{{ editedAudioName }}</span>
                  <span class="file-label">新音频文件（点击更换）*</span>
                </template>
                <template v-else>
                  <span class="file-name">{{ editedAudioCurrentName }}</span>
                  <span class="file-label">当前音频（点击更换）*</span>
                </template>
                <input type="file" accept="audio/*" hidden @change="handleEditAudioSelect" />
              </label>

              <label class="file-drop" :class="{ 'has-file': editedLyricName }">
                <template v-if="editedLyricName">
                  <span class="file-name">{{ editedLyricName }}</span>
                  <span class="file-label">新歌词文件（点击更换）</span>
                </template>
                <template v-else-if="editedLyricCurrentName">
                  <span class="file-name">{{ editedLyricCurrentName }}</span>
                  <span class="file-label">当前歌词（点击更换，可选）</span>
                </template>
                <template v-else>
                  <Upload :size="24" /><span>点击上传歌词文件（可选）</span>
                </template>
                <input type="file" accept=".lrc,.txt" hidden @change="handleEditLyricSelect" />
              </label>
            </div>

            <p class="edit-duration-hint">时长：<strong>{{ editedDuration > 0 ? formatTime(editedDuration) : '未识别' }}</strong><span>{{ editedAudioFile ? '（更换音频后自动识别）' : '（自动识别，无需填写）' }}</span></p>
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
