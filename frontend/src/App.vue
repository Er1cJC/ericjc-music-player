<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  Check,
  Home,
  Library,
  LogIn,
  LogOut,
  Plus,
  Save,
  Search,
  Shield,
  User,
  X
} from '@lucide/vue';
import ProfileView from './components/ProfileView.vue';
import AdminPanel from './components/AdminPanel.vue';
import DetailOverlay from './components/DetailOverlay.vue';
import HomeView from './components/HomeView.vue';
import LibraryView from './components/LibraryView.vue';
import PlayerBar from './components/PlayerBar.vue';
import PlaylistDetailView from './components/PlaylistDetailView.vue';
import SongTable from './components/SongTable.vue';
import { findActiveIndex, parseLyrics } from './lyrics/parser';
import type { LyricFormat, LyricLine } from './lyrics/types';
import { formatTime, toDisplaySong } from './utils';
import type {
  AuthUser,
  PlaylistDetail,
  PlaylistDetailResponse,
  PlaylistSummary,
  Song,
  SongResponse,
  ViewKey
} from './types';

const BATCH_MAX_FILES = 100;
const BATCH_MAX_BYTES = 2 * 1024 * 1024 * 1024;

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
const searchField = ref<'all' | 'title' | 'artist' | 'album'>('all');
const isLoading = ref(false);
const errorMessage = ref('');
// ——————————————————————————————————————————————————————————————
// 设计说明：视图导航不使用 Vue Router
// 本应用同时支持 Web（Vite 代理）和 Electron（file:// 协议）两种运行模式。
// 在 Electron 中，hash 路由容易与 file:// 路径冲突，history 路由则需要额外的
// 本地服务器配置。由于页面结构固定、无需 SEO、也无需分享 URL，用 activeView
// ref 做状态机切换是更简单且完全等效的方案；若日后迁移为纯 Web 应用，
// 替换为 Vue Router 的成本也很低。
// ——————————————————————————————————————————————————————————————
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

const brandingLogoUrl = ref<string | null>(null);
const activeAdminTab = ref<'songs' | 'users' | 'upload' | 'batch' | 'branding'>('songs');

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
  return songs.value.filter((s) => {
    if (searchField.value === 'title') return s.title.toLowerCase().includes(k);
    if (searchField.value === 'artist') return s.artist.toLowerCase().includes(k);
    if (searchField.value === 'album') return s.album.toLowerCase().includes(k);
    return [s.title, s.artist, s.album].some(t => t.toLowerCase().includes(k));
  });
});
const visibleSongs = computed(() => (activeView.value === 'search' ? filteredSongs.value : songs.value));
const currentTime = computed(() => formatTime(Math.round(currentSeconds.value)));
const durationTime = computed(() => formatTime(Math.round(activeDuration.value)));
const activeDuration = computed(() => audioDuration.value || currentSong.value?.duration || 0);

const viewTitle = computed(() => {
  if (activeView.value === 'playlist') return { eyebrow: '我的音乐', title: activePlaylist.value?.name || '歌单' };
  if (activeView.value === 'profile') return { eyebrow: '个人中心', title: '我的主页' };
  return { eyebrow: activeView.value === 'search' ? '搜索' : '个人音乐', title: activeView.value === 'search' ? '搜索音乐' : '我的音乐' };
});

onMounted(async () => {
  document.addEventListener('keydown', handlePlaylistEscape);
  loadBranding();
  await loadSongs();
  // 恢复上次播放的歌曲与进度；首次打开（无记录）则保持空状态
  restorePlayback();
});
onUnmounted(() => { document.removeEventListener('keydown', handlePlaylistEscape); clearTimeout(playlistMessageTimer); });
watch(currentSong, (s) => { if (s) { loadLyrics(s); persistPlayerState(true); } });

watch(volume, v => { if (audioElement.value) audioElement.value.volume = v / 100; });

function apiHeaders(): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  if (authToken.value) h['Authorization'] = `Bearer ${authToken.value}`;
  return h;
}

async function loadBranding() {
  try {
    const response = await fetch('/api/branding');
    if (response.ok) {
      const data = await response.json();
      brandingLogoUrl.value = data.logoUrl || null;
    }
  } catch {}
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

function searchByField(field: 'title' | 'artist' | 'album', value: string) {
  searchKeyword.value = value;
  searchField.value = field;
  showDetail.value = false;
  activeView.value = 'search';
}
function handleMetadataKeydown(event: KeyboardEvent, field: 'title' | 'artist' | 'album', value: string) {
  if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); event.stopPropagation(); searchByField(field, value); }
}

function selectSong(song: Song) { currentSong.value = song; addToRecent(song); resetPlaybackPosition(); playCurrentSong(); }

async function toggleLike(song: Song) {
  if (!authToken.value) { showAuthModal.value = true; return; }
  try { const r = await fetch(`/api/favorites/${song.id}`, { method: likedSongIds.value.has(song.id) ? 'DELETE' : 'POST', headers: authHeaders() }); if (r.ok) { const n = new Set(likedSongIds.value); likedSongIds.value.has(song.id) ? n.delete(song.id) : n.add(song.id); likedSongIds.value = n; } } catch {}
}

function authHeaders(): Record<string, string> { const h: Record<string, string> = {}; if (authToken.value) h['Authorization'] = `Bearer ${authToken.value}`; return h; }

function switchView(v: ViewKey) { activeView.value = v; }

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
/** 视图组件传入的随机列表可能为空（表示全库随机） */
function playRandomSongs(list: Song[]) { playRandomSong(list.length ? list : songs.value); }
function renameActivePlaylist() { if (activePlaylist.value) openPlaylistModal(activePlaylist.value); }
function deleteActivePlaylist() { if (activePlaylist.value) deletePlaylist(activePlaylist.value); }
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
function seekToProgress(v?: number) { const a = audioElement.value; if (a && activeDuration.value > 0) { const t = (activeDuration.value * (v ?? progress.value)) / 100; a.currentTime = t; currentSeconds.value = t; } }
function handlePlaybackEnded() { isPlaying.value = false; nextSong(); }
function handleAudioError() { isPlaying.value = false; }
function hideBrokenImage(e: Event) { const img = e.target; if (img instanceof HTMLImageElement) { img.style.display = 'none'; img.parentElement?.classList.remove('has-image'); } }
function markCoverLoaded(e: Event) { const img = e.target; if (img instanceof HTMLImageElement) { img.style.display = ''; img.parentElement?.classList.add('has-image'); } }

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
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark" :class="{ 'has-logo': brandingLogoUrl }">
          <img v-if="brandingLogoUrl" :src="brandingLogoUrl" alt="网站图标" />
          <span v-else>M</span>
        </div>
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
          <input v-model="searchKeyword" type="search" placeholder="搜索您喜欢的歌喵" @focus="switchView('search')" @input="searchField = 'all'" />
        </label>
      </header>

      <p v-if="playlistMsg" class="status-text playlist-notice" :class="{ warning: playlistMsgError }" role="status">{{ playlistMsg }}</p>

      <HomeView
        v-if="activeView === 'discover'"
        :greeting="greeting"
        :display-name="authUser ? (authUser.nickname || authUser.username) : null"
        :current-song="currentSong"
        :is-playing="isPlaying"
        :recent-songs="recentSongs"
        :recommended-songs="recommendedSongs"
        :playlists="playlists"
        :has-token="!!authToken"
        @navigate="switchView"
        @play-random="playRandomSongs"
        @select="selectSong"
        @open-detail="openDetail"
        @toggle-playback="togglePlayback"
        @open-playlist="openPlaylist"
        @search-field="searchByField"
      />

      <LibraryView
        v-if="activeView === 'library'"
        :playlists="playlists"
        :liked-songs="likedSongList"
        :recent-songs="recentSongs"
        :liked-ids="likedSongIds"
        :current-song-id="currentSong ? currentSong.id : null"
        :has-token="!!authToken"
        :busy="playlistBusy"
        :is-loading="isLoading"
        :error-message="errorMessage"
        @select="selectSong"
        @toggle-like="toggleLike"
        @add-to-playlist="openAddToPlaylist"
        @search-field="searchByField"
        @open-playlist="openPlaylist"
        @create-playlist="openPlaylistModal()"
        @rename-playlist="openPlaylistModal"
        @delete-playlist="deletePlaylist"
        @login-required="showAuthModal = true"
        @play-random="playRandomSongs"
      />

      <PlaylistDetailView
        v-if="activeView === 'playlist'"
        :playlist="activePlaylist"
        :loading="playlistLoading"
        :busy="playlistBusy"
        :liked-ids="likedSongIds"
        :current-song-id="currentSong ? currentSong.id : null"
        @back="switchView('library')"
        @rename="renameActivePlaylist"
        @delete="deleteActivePlaylist"
        @select="selectSong"
        @toggle-like="toggleLike"
        @remove="removePlaylistSong"
        @search-field="searchByField"
      />

      <section v-if="activeView === 'profile' && authUser" class="profile-section">
        <ProfileView :user="authUser" :token="authToken || ''" @update-user="(u) => (authUser = u)" />
      </section>

      <SongTable
        v-if="activeView === 'search'"
        mode="main"
        title="搜索结果"
        empty-text="没有找到匹配的歌曲。"
        shuffle-label="随机播放"
        :songs="visibleSongs"
        :current-song-id="currentSong ? currentSong.id : null"
        :liked-ids="likedSongIds"
        :is-loading="isLoading"
        :error-message="errorMessage"
        @select="selectSong"
        @toggle-like="toggleLike"
        @add-to-playlist="openAddToPlaylist"
        @search-field="searchByField"
        @play-random="playRandomSongs"
      />
    </section>

    <AdminPanel
      v-else
      v-model:tab="activeAdminTab"
      :songs="songs"
      :token="authToken"
      :branding-logo-url="brandingLogoUrl"
      :current-username="authUser ? authUser.username : null"
      @songs-changed="loadSongs"
      @song-deleted="cleanupLocalSongCache"
      @logo-changed="(url) => (brandingLogoUrl = url)"
      @search-field="searchByField"
    />

    <audio v-if="currentSong" ref="audioElement" :src="currentSong.audioUrl" preload="metadata" @loadedmetadata="updateMetadata" @timeupdate="updateProgress" @ended="handlePlaybackEnded" @play="isPlaying = true" @pause="isPlaying = false" @error="handleAudioError" />
    <PlayerBar
      v-if="currentSong"
      :song="currentSong"
      :is-playing="isPlaying"
      :current-time="currentTime"
      :duration-time="durationTime"
      v-model:progress="progress"
      v-model:volume="volume"
      @seek="seekToProgress"
      @toggle-playback="togglePlayback"
      @previous="previousSong"
      @next="nextSong"
      @open-detail="openDetail"
      @search-field="searchByField"
    />

    <DetailOverlay
      :visible="showDetail && !!currentSong"
      :song="currentSong"
      :is-playing="isPlaying"
      :lyric-lines="lyricLines"
      :current-lyric-index="currentLyricIndex"
      :audio-el="audioElement"
      :current-time="currentTime"
      :duration-time="durationTime"
      v-model:progress="progress"
      v-model:volume="volume"
      @close="closeDetail"
      @toggle-playback="togglePlayback"
      @previous="previousSong"
      @next="nextSong"
      @seek="seekToProgress"
      @search-field="searchByField"
      @resync-lyrics="resyncLyrics"
    />

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
