<script setup lang="ts">
/**
 * 管理后台（自包含）：歌曲管理 / 用户管理 / 上传歌曲 / 批量导入 / 网站图标。
 * 自身持有表单与过程状态、自行调用管理接口；只向父组件上报：
 *  - songs-changed：歌曲增删改后让 App 刷新曲库
 *  - song-deleted：删除歌曲后让 App 清理本地播放缓存
 *  - logo-changed：图标更新后同步侧栏品牌标志
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { parseBlob } from 'music-metadata';
import { Files, Image as ImageIcon, Save, Search, Settings, Trash2, Upload, X } from '@lucide/vue';
import ImageCropper from './ImageCropper.vue';
import { compareAdminText, formatFileSize, formatTime, getSongColor } from '../utils';
import type { AdminSortKey, AdminUser, BatchSongItem, Song } from '../types';

const props = defineProps<{
  songs: Song[];
  token: string | null;
  brandingLogoUrl: string | null;
  currentUsername: string | null;
  /** 受控：当前激活的管理页签（由父组件持有，切换视图后仍保留） */
  tab: 'songs' | 'users' | 'upload' | 'batch' | 'branding';
}>();

const emit = defineEmits<{
  'update:tab': [tab: 'songs' | 'users' | 'upload' | 'batch' | 'branding'];
  'songs-changed': [];
  'song-deleted': [id: number];
  'logo-changed': [url: string | null];
  'search-field': [field: 'title' | 'artist' | 'album', value: string];
}>();

const BATCH_MAX_FILES = 100;
const BATCH_MAX_BYTES = 2 * 1024 * 1024 * 1024;

const msg = ref('');
const saving = ref(false);
const users = ref<AdminUser[]>([]);

// —— 歌曲管理：搜索 + 排序 ——
const keyword = ref('');
const sortKey = ref<AdminSortKey>('title');
const sortDirection = ref<'asc' | 'desc'>('asc');

const songsFiltered = computed(() => {
  const k = keyword.value.trim().toLowerCase();
  const list = props.songs.filter((song) => !k || [song.title, song.artist, song.album].some((value) => value.toLowerCase().includes(k)));
  if (sortKey.value === 'imported') return list;
  const field = sortKey.value as Exclude<AdminSortKey, 'imported'>;
  const direction = sortDirection.value === 'asc' ? 1 : -1;
  return [...list].sort((a, b) => compareAdminText(a[field], b[field], direction) || a.id - b.id);
});

function toggleAdminSort(key: AdminSortKey) {
  if (sortKey.value === key) sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  else { sortKey.value = key; sortDirection.value = 'asc'; }
}

// —— 批量导入 ——
const batchItems = ref<BatchSongItem[]>([]);
const batchProgress = ref('');
const batchImporting = ref(false);
const batchTotalBytes = computed(() => batchItems.value.reduce((total, item) => total + item.file.size, 0));

// —— 品牌图标 ——
const brandingCropFile = ref<File | null>(null);
const showBrandingCropper = ref(false);
const brandingSaving = ref(false);
const brandingInput = ref<HTMLInputElement | null>(null);

// —— 上传表单 ——
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

// —— 编辑歌曲 ——
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

function apiHeaders(): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  if (props.token) h['Authorization'] = `Bearer ${props.token}`;
  return h;
}
function authHeaders(): Record<string, string> {
  const h: Record<string, string> = {};
  if (props.token) h['Authorization'] = `Bearer ${props.token}`;
  return h;
}

function emitSearchField(field: 'title' | 'artist' | 'album', value: string) {
  emit('search-field', field, value);
}
function handleMetadataKeydown(event: KeyboardEvent, field: 'title' | 'artist' | 'album', value: string) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    event.stopPropagation();
    emitSearchField(field, value);
  }
}

// —— 用户管理 ——
async function loadAdminData() {
  if (!props.token) return;
  try {
    const r = await fetch('/api/admin/users', { headers: apiHeaders() });
    if (r.ok) users.value = await r.json();
  } catch {}
}

async function toggleUserRole(u: AdminUser) {
  const newRole = u.role === 'ADMIN' ? 'USER' : 'ADMIN';
  try {
    const r = await fetch(`/api/admin/users/${u.id}/role`, { method: 'PUT', headers: apiHeaders(), body: JSON.stringify({ role: newRole }) });
    if (r.ok) { u.role = newRole; msg.value = `已将 ${u.username} 的角色改为 ${newRole}`; }
  } catch { msg.value = '操作失败'; }
}

// —— 编辑歌曲 ——
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
    if (metadata.format.duration && metadata.format.duration > 0) editedDuration.value = Math.round(metadata.format.duration);
  } catch (err) { console.error('music-metadata 解析失败:', err); }
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

async function uploadFileToServer(file: File, type: string): Promise<string> {
  const fd = new FormData();
  fd.append('file', file);
  fd.append('type', type);
  const r = await fetch('/api/admin/upload', { method: 'POST', headers: authHeaders(), body: fd });
  if (!r.ok) throw new Error('上传失败');
  const d = await r.json();
  return d.url as string;
}

async function saveEdit() {
  if (!editSong.value) return;
  saving.value = true; msg.value = '';
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
    if (r.ok) { msg.value = '保存成功'; editSong.value = null; emit('songs-changed'); }
    else { const e = await r.json(); msg.value = e.error || '保存失败'; }
  } catch { msg.value = '保存失败'; }
  saving.value = false;
}

async function deleteSongItem(s: Song) {
  if (!confirm(`确定删除「${s.title}」吗？删除后歌曲文件与相关记录（收藏、播放历史、歌单引用）将一并清除。`)) return;
  saving.value = true; msg.value = '';
  try {
    const r = await fetch(`/api/admin/songs/${s.id}`, { method: 'DELETE', headers: apiHeaders() });
    if (r.ok) {
      msg.value = '删除成功';
      emit('song-deleted', s.id);
      emit('songs-changed');
    } else {
      const e = await r.json();
      msg.value = e.error || '删除失败';
    }
  } catch { msg.value = '删除失败'; }
  saving.value = false;
}

// —— 上传歌曲 ——
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
  } catch (err) { console.error('music-metadata 解析失败:', err); }

  if (uploadDuration.value <= 0) {
    const audio = new Audio(URL.createObjectURL(file));
    audio.addEventListener('loadedmetadata', () => {
      if (uploadDuration.value <= 0) uploadDuration.value = Math.round(audio.duration || 0);
    });
  }

  uploadMetadataHint.value = matched > 0 ? `已从文件中读取 ${matched} 项信息` : '文件中未包含元数据，请手动填写';
}
function handleLyricSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files?.[0]) { uploadLyricFile.value = input.files[0]; uploadLyricName.value = input.files[0].name; }
}
async function handleUpload() {
  if (!uploadTitle.value || !uploadArtist.value || !uploadAlbum.value || !uploadAudioFile.value) {
    msg.value = '请填写标题、歌手、专辑并选择音频文件';
    return;
  }
  saving.value = true; msg.value = '';
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
      msg.value = '上传成功！';
      uploadTitle.value = ''; uploadArtist.value = ''; uploadAlbum.value = ''; uploadDuration.value = 0;
      uploadCoverFile.value = null; uploadAudioFile.value = null; uploadLyricFile.value = null;
      uploadCoverPreview.value = ''; uploadAudioName.value = ''; uploadLyricName.value = ''; uploadMetadataHint.value = '';
      emit('songs-changed');
    } else { const e = await r.json(); msg.value = e.error || '上传失败'; }
  } catch { msg.value = '上传失败'; }
  saving.value = false;
}

// —— 批量导入 ——
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
  } catch (error) { console.warn('批量导入元数据解析失败:', error); }

  return { title, artist, album, duration, cover, coverPreview, metadataMessage: matched > 0 ? `已从文件中读取 ${matched} 项信息` : '文件中未包含元数据，请手动填写' };
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
  const existingKeys = new Set(batchItems.value.map(item => `${item.file.name}:${item.file.size}`));
  const newFiles: File[] = [];
  let duplicateCount = 0;
  let limitCount = 0;
  let totalBytes = batchTotalBytes.value;
  for (const file of selectedFiles) {
    const key = `${file.name}:${file.size}`;
    if (existingKeys.has(key)) { duplicateCount++; continue; }
    if (batchItems.value.length + newFiles.length >= BATCH_MAX_FILES || totalBytes + file.size > BATCH_MAX_BYTES) {
      limitCount++;
      continue;
    }
    existingKeys.add(key);
    newFiles.push(file);
    totalBytes += file.size;
  }
  if (limitCount > 0) {
    msg.value = `已跳过 ${limitCount} 个文件，批量导入上限为 ${BATCH_MAX_FILES} 首且总大小不超过 ${formatFileSize(BATCH_MAX_BYTES)}`;
  }
  if (duplicateCount > 0) {
    msg.value = `${msg.value ? `${msg.value}；` : ''}已跳过 ${duplicateCount} 个重复文件`;
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
    if (!batchItems.value.length) msg.value = '没有可导入的歌曲';
    return;
  }

  msg.value = '';
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
    msg.value = '没有可导入的歌曲';
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
    msg.value = `批量导入完成：成功 ${successCount} 首，失败 ${failedCount} 首`;
    emit('songs-changed');
  } finally {
    batchProgress.value = '';
    batchImporting.value = false;
  }
}

// —— 品牌图标 ——
function openBrandingPicker() {
  if (!brandingSaving.value) brandingInput.value?.click();
}
function handleBrandingSelect(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (file.type && !file.type.startsWith('image/')) {
    msg.value = '请选择图片文件';
    return;
  }
  brandingCropFile.value = file;
  showBrandingCropper.value = true;
}
function cancelBrandingCrop() {
  showBrandingCropper.value = false;
  brandingCropFile.value = null;
}
async function saveBrandingLogo(blob: Blob) {
  showBrandingCropper.value = false;
  brandingCropFile.value = null;
  brandingSaving.value = true;
  msg.value = '';
  try {
    const form = new FormData();
    form.append('file', blob, 'site-logo.png');
    const response = await fetch('/api/admin/branding/logo', { method: 'POST', headers: authHeaders(), body: form });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || '网站图标保存失败');
    const url = data.logoUrl ? `${data.logoUrl}?v=${Date.now()}` : null;
    emit('logo-changed', url);
    msg.value = '网站图标已更新';
  } catch (error) {
    msg.value = error instanceof Error ? error.message : '网站图标保存失败';
  } finally {
    brandingSaving.value = false;
  }
}

function markCoverLoaded(e: Event) {
  const img = e.target;
  if (img instanceof HTMLImageElement) { img.style.display = ''; img.parentElement?.classList.add('has-image'); }
}
function hideBrokenImage(e: Event) {
  const img = e.target;
  if (img instanceof HTMLImageElement) { img.style.display = 'none'; img.parentElement?.classList.remove('has-image'); }
}

onMounted(loadAdminData);
onBeforeUnmount(releaseBatchCoverPreviews);
</script>

<template>
  <section class="content admin-panel">
    <header class="topbar">
      <div>
        <p class="eyebrow">管理后台</p>
        <h1>{{ tab === 'songs' ? '歌曲管理' : tab === 'users' ? '用户管理' : tab === 'batch' ? '批量导入歌曲' : tab === 'branding' ? '网站图标' : '上传歌曲' }}</h1>
      </div>
      <div class="admin-tabs">
        <button :class="{ active: tab === 'songs' }" @click="emit('update:tab', 'songs')">歌曲管理</button>
        <button :class="{ active: tab === 'users' }" @click="emit('update:tab', 'users')">用户管理</button>
        <button :class="{ active: tab === 'upload' }" @click="emit('update:tab', 'upload')"><Upload :size="16" />上传歌曲</button>
        <button :class="{ active: tab === 'batch' }" @click="emit('update:tab', 'batch')"><Files :size="16" />批量导入</button>
        <button :class="{ active: tab === 'branding' }" @click="emit('update:tab', 'branding')"><Settings :size="16" />网站图标</button>
      </div>
    </header>

    <p v-if="msg" class="status-text" :class="{ warning: msg.includes('失败') }">{{ msg }}</p>

    <section v-if="tab === 'songs'" class="song-table admin-song-table">
      <div class="admin-song-toolbar">
        <label class="admin-song-search"><Search :size="17" /><input v-model="keyword" type="search" placeholder="搜索歌曲标题、作者或专辑" /></label>
        <div class="admin-sort-summary"><span>{{ songsFiltered.length }} 首歌曲</span><span class="sort-order-hint">中文 → 英文 → 符号</span><label class="admin-sort-select"><span>排序</span><select v-model="sortKey"><option value="imported">导入顺序</option><option value="title">歌曲名</option><option value="artist">作者</option><option value="album">专辑</option></select></label><button class="sort-direction" type="button" :disabled="sortKey === 'imported'" @click="toggleAdminSort(sortKey)">{{ sortDirection === 'asc' ? '升序' : '降序' }}</button></div>
      </div>
      <div class="song-row header-row admin-song-grid">
        <button class="sort-heading" type="button" @click="toggleAdminSort('title')">歌曲 <span v-if="sortKey === 'title'">{{ sortDirection === 'asc' ? '↑' : '↓' }}</span></button>
        <button class="sort-heading" type="button" @click="toggleAdminSort('artist')">作者 <span v-if="sortKey === 'artist'">{{ sortDirection === 'asc' ? '↑' : '↓' }}</span></button>
        <button class="sort-heading admin-album" type="button" @click="toggleAdminSort('album')">专辑 <span v-if="sortKey === 'album'">{{ sortDirection === 'asc' ? '↑' : '↓' }}</span></button>
        <span>时长</span><span>操作</span>
      </div>
      <div v-for="song in songsFiltered" :key="song.id" class="song-row admin-song-row admin-song-grid">
        <span class="song-cell">
          <span class="cover-small" :style="{ background: song.color }"><img :src="song.coverUrl" :alt="song.title" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ song.cover }}</span></span>
          <span><strong>{{ song.title }}</strong></span>
        </span>
        <span><span class="metadata-link" role="button" tabindex="0" @click.stop="emitSearchField('artist', song.artist)" @keydown="handleMetadataKeydown($event, 'artist', song.artist)">{{ song.artist }}</span></span>
        <span class="admin-album"><span class="metadata-link" role="button" tabindex="0" @click.stop="emitSearchField('album', song.album)" @keydown="handleMetadataKeydown($event, 'album', song.album)">{{ song.album }}</span></span>
        <span>{{ formatTime(song.duration) }}</span>
        <span class="row-actions">
          <button class="icon-button" type="button" title="编辑" @click="startEditSong(song)"><Save :size="16" /></button>
          <button class="icon-button danger" type="button" title="删除" @click="deleteSongItem(song)"><Trash2 :size="16" /></button>
        </span>
      </div>
    </section>

    <section v-if="tab === 'branding'" class="branding-panel">
      <div class="branding-card">
        <div class="branding-preview" :class="{ 'has-logo': brandingLogoUrl }">
          <img v-if="brandingLogoUrl" :src="brandingLogoUrl" alt="当前网站图标" />
          <ImageIcon v-else :size="42" />
        </div>
        <div class="branding-copy">
          <p class="eyebrow">品牌识别</p>
          <h2>网站图标</h2>
          <p>上传图片后拖动位置并调整缩放，系统会输出圆角方形图标并应用到左上角品牌标志。</p>
          <div class="branding-actions">
            <button class="primary-action" type="button" :disabled="brandingSaving" @click="openBrandingPicker"><Upload :size="17" />{{ brandingSaving ? '保存中…' : '上传并裁剪' }}</button>
            <input ref="brandingInput" type="file" accept="image/*" hidden @change="handleBrandingSelect" />
          </div>
        </div>
      </div>
      <ImageCropper v-if="showBrandingCropper && brandingCropFile" :file="brandingCropFile" title="调整网站图标" @confirm="saveBrandingLogo" @cancel="cancelBrandingCrop" />
    </section>

    <section v-if="tab === 'users'">
      <div class="admin-table">
        <div class="song-row header-row"><span>用户</span><span>角色</span><span>注册时间</span><span>操作</span></div>
        <div v-for="u in users" :key="u.id" class="song-row admin-song-row">
          <span><strong>{{ u.username }}</strong></span>
          <span><span class="role-tag" :class="u.role === 'ADMIN' ? 'admin-role' : 'user-role'">{{ u.role === 'ADMIN' ? '管理员' : '普通用户' }}</span></span>
          <span>{{ u.createdAt ? u.createdAt.slice(0,10) : '' }}</span>
          <span class="row-actions">
            <button v-if="u.username !== currentUsername" class="ghost-button small" type="button" @click="toggleUserRole(u)">{{ u.role === 'ADMIN' ? '取消管理员' : '设为管理员' }}</button>
            <span v-else class="self-tag">当前用户</span>
          </span>
        </div>
      </div>
    </section>

    <section v-if="tab === 'batch'" class="batch-import-panel">
      <div class="batch-toolbar">
        <div>
          <h2>批量导入歌曲</h2>
          <p class="batch-count">共 {{ batchItems.length }} 首 · {{ formatFileSize(batchTotalBytes) }} / 2 GB</p>
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
      <p v-if="!batchItems.length" class="status-text">选择多个音频文件，自动读取歌曲信息并批量导入。单批最多 100 首，总大小不超过 2 GB。</p>

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

    <section v-if="tab === 'upload'" class="upload-form">
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

      <button class="primary-action" type="button" :disabled="saving" @click="handleUpload">
        <Upload :size="18" />{{ saving ? '上传中...' : '上传歌曲' }}
      </button>
    </section>

    <div v-if="editSong" class="modal-backdrop" @mousedown.self="cancelEdit">
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
          <button class="primary-action" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
        </form>
      </div>
    </div>
  </section>
</template>
