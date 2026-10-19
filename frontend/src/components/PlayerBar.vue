<script setup lang="ts">
/**
 * 底部播放条（主界面与歌词详情页共用）。
 * 注意：<audio> 元素由父组件 App 持有（全站共享 currentTime/播放事件），
 * 本组件只负责控件 UI，进度与音量通过 v-model + seek 事件上抛。
 */
import { Pause, Play, SkipBack, SkipForward, Volume2 } from '@lucide/vue';
import type { Song } from '../types';

const props = withDefaults(defineProps<{
  song: Song;
  isPlaying: boolean;
  currentTime: string;
  durationTime: string;
  progress: number;
  volume: number;
  variant?: 'main' | 'detail';
}>(), {
  variant: 'main'
});

const emit = defineEmits<{
  'update:progress': [value: number];
  'update:volume': [value: number];
  seek: [value: number];
  'toggle-playback': [];
  previous: [];
  next: [];
  'open-detail': [];
  'search-field': [field: 'title' | 'artist' | 'album', value: string];
}>();

function onProgressInput(event: Event) {
  const value = Number((event.target as HTMLInputElement).value);
  emit('update:progress', value);
  emit('seek', value);
}

function onVolumeInput(event: Event) {
  emit('update:volume', Number((event.target as HTMLInputElement).value));
}

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
  <footer class="player-bar" :class="{ 'detail-player-bar': variant === 'detail' }">
    <div class="mini-track" @click="variant === 'main' ? emit('open-detail') : undefined">
      <div class="cover-small" :style="{ background: song.color }">
        <img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" />
        <span>{{ song.cover }}</span>
      </div>
      <div>
        <strong>{{ song.title }}</strong>
        <small>
          <span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'artist', song.artist)" @keydown="handleMetadataKeydown($event, 'artist', song.artist)">{{ song.artist }}</span>
          <span aria-hidden="true"> · </span>
          <span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'album', song.album)" @keydown="handleMetadataKeydown($event, 'album', song.album)">{{ song.album }}</span>
        </small>
      </div>
    </div>
    <div class="transport">
      <div class="transport-buttons">
        <button class="icon-button" type="button" aria-label="上一首" @click="emit('previous')"><SkipBack :size="20" /></button>
        <button class="play-button" type="button" aria-label="播放或暂停" @click="emit('toggle-playback')"><Pause v-if="isPlaying" :size="22" /><Play v-else :size="22" /></button>
        <button class="icon-button" type="button" aria-label="下一首" @click="emit('next')"><SkipForward :size="20" /></button>
      </div>
      <div class="progress-line">
        <span>{{ currentTime }}</span>
        <input :value="progress" type="range" min="0" max="100" step="0.1" @input="onProgressInput" />
        <span>{{ durationTime }}</span>
      </div>
    </div>
    <div class="volume-box">
      <Volume2 :size="20" />
      <input :value="volume" type="range" min="0" max="100" aria-label="音量" @input="onVolumeInput" />
    </div>
  </footer>
</template>
