<script setup lang="ts">
/**
 * 通用歌曲表：覆盖三种用户侧形态
 *  - main：搜索结果 / 收藏列表（带标题、随机播放、收藏与"添加到歌单"操作）
 *  - playlist：歌单详情内的歌曲表（收藏与移除操作）
 *  - recent：最近播放列表（无行内操作）
 */
import { Clock3, Heart, MoreHorizontal, Shuffle, Trash2 } from '@lucide/vue';
import { formatTime } from '../utils';
import type { Song } from '../types';

const props = defineProps<{
  mode: 'main' | 'playlist' | 'recent';
  songs: Song[];
  currentSongId: number | null;
  likedIds: Set<number>;
  /** main / recent 模式的标题 */
  title: string;
  emptyText: string;
  isLoading?: boolean;
  errorMessage?: string;
  /** playlist 模式：移除按钮禁用态 */
  busy?: boolean;
  /** main / recent 模式的随机按钮文案 */
  shuffleLabel?: string;
}>();

const emit = defineEmits<{
  select: [song: Song];
  'toggle-like': [song: Song];
  'add-to-playlist': [song: Song, event: MouseEvent];
  remove: [song: Song];
  'search-field': [field: 'title' | 'artist' | 'album', value: string];
  'play-random': [songs: Song[]];
}>();

function handleMetadataKeydown(event: KeyboardEvent, field: 'title' | 'artist' | 'album', value: string) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    event.stopPropagation();
    emit('search-field', field, value);
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
</script>

<template>
  <section v-if="mode === 'main'" class="song-table main-song-table" aria-label="歌曲列表">
    <div class="table-heading">
      <h2>{{ title }}</h2>
      <button class="ghost-button" type="button" @click="emit('play-random', songs)"><Shuffle :size="18" />{{ shuffleLabel }}</button>
    </div>
    <p v-if="isLoading" class="status-text">正在加载歌曲...</p>
    <p v-else-if="errorMessage" class="status-text warning">后端暂时不可用，已显示本地示例数据。</p>
    <div class="song-row header-row"><span>歌曲</span><span class="song-album">专辑</span><span>时长</span><span></span></div>
    <template v-for="song in songs" :key="song.id">
      <div class="song-row" :class="{ selected: song.id === currentSongId }" role="button" tabindex="0" @click="emit('select', song)" @keydown.enter.self="emit('select', song)" @keydown.space.self.prevent="emit('select', song)">
        <span class="song-cell">
          <span class="cover-small" :style="{ background: song.color }"><img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ song.cover }}</span></span>
          <span><strong>{{ song.title }}</strong><small><span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'artist', song.artist)" @keydown="handleMetadataKeydown($event, 'artist', song.artist)">{{ song.artist }}</span></small></span>
        </span>
        <span class="song-album"><span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'album', song.album)" @keydown="handleMetadataKeydown($event, 'album', song.album)">{{ song.album }}</span></span>
        <span>{{ formatTime(song.duration) }}</span>
        <span class="row-actions">
          <button class="icon-button" type="button" aria-label="收藏歌曲" @click.stop="emit('toggle-like', song)"><Heart :size="18" :fill="likedIds.has(song.id) ? 'currentColor' : 'none'" /></button>
          <button class="icon-button" type="button" title="添加到歌单" aria-label="添加到歌单" @click.stop="emit('add-to-playlist', song, $event)"><MoreHorizontal :size="18" /></button>
        </span>
      </div>
    </template>
    <p v-if="!isLoading && songs.length === 0" class="status-text">{{ emptyText }}</p>
  </section>

  <div v-else-if="mode === 'playlist'" class="song-table playlist-song-table">
    <div class="song-row header-row"><span>歌曲</span><span class="song-album">专辑</span><span>时长</span><span>操作</span></div>
    <div v-for="song in songs" :key="song.id" class="song-row" :class="{ selected: song.id === currentSongId }" role="button" tabindex="0" @click="emit('select', song)" @keydown.enter.self="emit('select', song)" @keydown.space.self.prevent="emit('select', song)">
      <span class="song-cell">
        <span class="cover-small" :style="{ background: song.color }"><img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ song.cover }}</span></span>
        <span><strong>{{ song.title }}</strong><small><span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'artist', song.artist)" @keydown="handleMetadataKeydown($event, 'artist', song.artist)">{{ song.artist }}</span></small></span>
      </span>
      <span class="song-album"><span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'album', song.album)" @keydown="handleMetadataKeydown($event, 'album', song.album)">{{ song.album }}</span></span>
      <span>{{ formatTime(song.duration) }}</span>
      <span class="row-actions">
        <button class="icon-button" type="button" title="收藏歌曲" aria-label="收藏歌曲" @click.stop="emit('toggle-like', song)"><Heart :size="18" :fill="likedIds.has(song.id) ? 'currentColor' : 'none'" /></button>
        <button class="icon-button danger" type="button" title="从歌单移除" aria-label="从歌单移除" :disabled="busy" @click.stop="emit('remove', song)"><Trash2 :size="18" /></button>
      </span>
    </div>
    <p v-if="!songs.length" class="status-text">{{ emptyText }}</p>
  </div>

  <section v-else class="song-table secondary-section" aria-label="最近播放">
    <div class="table-heading"><h2>{{ title }}</h2><button class="ghost-button" type="button" @click="emit('play-random', songs)"><Clock3 :size="18" />{{ shuffleLabel }}</button></div>
    <div v-for="song in songs" :key="'r' + song.id" class="song-row" :class="{ selected: song.id === currentSongId }" role="button" tabindex="0" @click="emit('select', song)" @keydown.enter.self="emit('select', song)" @keydown.space.self.prevent="emit('select', song)">
      <span class="song-cell">
        <span class="cover-small" :style="{ background: song.color }"><img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" /><span>{{ song.cover }}</span></span>
        <span><strong>{{ song.title }}</strong><small><span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'artist', song.artist)" @keydown="handleMetadataKeydown($event, 'artist', song.artist)">{{ song.artist }}</span></small></span>
      </span>
      <span><span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'album', song.album)" @keydown="handleMetadataKeydown($event, 'album', song.album)">{{ song.album }}</span></span>
      <span>{{ formatTime(song.duration) }}</span><span></span>
    </div>
    <p v-if="songs.length === 0" class="status-text">{{ emptyText }}</p>
  </section>
</template>
