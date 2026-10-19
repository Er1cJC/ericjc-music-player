<script setup lang="ts">
/**
 * 歌词详情页：旋转唱片 + 流光歌词 + 底部播放条。
 */
import { ChevronDown } from '@lucide/vue';
import LyricStage from './LyricStage.vue';
import PlayerBar from './PlayerBar.vue';
import type { LyricLine } from '../lyrics/types';
import type { Song } from '../types';

defineProps<{
  visible: boolean;
  song: Song | null;
  isPlaying: boolean;
  lyricLines: LyricLine[];
  currentLyricIndex: number;
  audioEl: HTMLAudioElement | null;
  currentTime: string;
  durationTime: string;
  progress: number;
  volume: number;
}>();

const emit = defineEmits<{
  close: [];
  'toggle-playback': [];
  previous: [];
  next: [];
  seek: [value: number];
  'update-progress': [value: number];
  'update-volume': [value: number];
  'search-field': [field: 'title' | 'artist' | 'album', value: string];
  'resync-lyrics': [];
}>();

function handleMetadataKeydown(event: KeyboardEvent, field: 'title' | 'artist' | 'album', value: string) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    event.stopPropagation();
    emit('search-field', field, value);
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="detail-slide">
      <div v-if="visible && song" class="detail-overlay">
        <button class="detail-close" type="button" @click="emit('close')"><ChevronDown :size="28" /></button>

        <div class="detail-content">
          <div class="detail-cover-section">
            <div class="detail-vinyl" :class="{ spinning: isPlaying }">
              <div class="vinyl-disc" :style="{ background: song.color }">
                <img v-if="song.coverUrl" :src="song.coverUrl" :alt="song.title" @error="($event.target as HTMLImageElement).style.display='none'" />
                <span v-else class="vinyl-fallback">{{ song.cover }}</span>
              </div>
            </div>
            <div class="detail-info">
              <h2 class="detail-title">{{ song.title }}</h2>
              <p class="detail-artist metadata-link" role="button" tabindex="0" @click="emit('search-field', 'artist', song.artist)" @keydown="handleMetadataKeydown($event, 'artist', song.artist)">{{ song.artist }}</p>
              <p class="detail-album metadata-link" role="button" tabindex="0" @click="emit('search-field', 'album', song.album)" @keydown="handleMetadataKeydown($event, 'album', song.album)">{{ song.album }}</p>
            </div>
          </div>

          <LyricStage
            v-if="lyricLines.length"
            :lines="lyricLines"
            :active-index="currentLyricIndex"
            :audio-el="audioEl"
            :playing="isPlaying"
            @resync="emit('resync-lyrics')"
          />
          <div v-else class="detail-no-lyrics">
            <p>暂无歌词</p>
            <p class="sub">纯音乐，请欣赏</p>
          </div>
        </div>

        <PlayerBar
          variant="detail"
          :song="song"
          :is-playing="isPlaying"
          :current-time="currentTime"
          :duration-time="durationTime"
          :progress="progress"
          :volume="volume"
          @update:progress="emit('update-progress', $event)"
          @update:volume="emit('update-volume', $event)"
          @seek="emit('seek', $event)"
          @toggle-playback="emit('toggle-playback')"
          @previous="emit('previous')"
          @next="emit('next')"
          @search-field="(field, value) => emit('search-field', field, value)"
        />
      </div>
    </Transition>
  </Teleport>
</template>
