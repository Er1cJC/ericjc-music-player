<script setup lang="ts">
/**
 * 歌单详情：头部操作 + 歌曲表（收藏/移除）。
 */
import { ArrowLeft, Pencil, Trash2 } from '@lucide/vue';
import SongTable from './SongTable.vue';
import type { PlaylistDetail, Song } from '../types';

defineProps<{
  playlist: PlaylistDetail | null;
  loading: boolean;
  busy: boolean;
  likedIds: Set<number>;
  currentSongId: number | null;
}>();

const emit = defineEmits<{
  back: [];
  rename: [];
  delete: [];
  select: [song: Song];
  'toggle-like': [song: Song];
  remove: [song: Song];
  'search-field': [field: 'title' | 'artist' | 'album', value: string];
}>();
</script>

<template>
  <section class="playlist-detail" aria-label="歌单详情">
    <div class="playlist-heading">
      <button class="ghost-button" type="button" @click="emit('back')"><ArrowLeft :size="18" />返回我的音乐</button>
      <div v-if="playlist" class="playlist-detail-actions">
        <button class="icon-button" type="button" title="重命名歌单" aria-label="重命名歌单" @click="emit('rename')"><Pencil :size="18" /></button>
        <button class="ghost-button" type="button" :disabled="busy" @click="emit('delete')"><Trash2 :size="18" />删除歌单</button>
      </div>
    </div>
    <p v-if="loading" class="status-text">正在加载歌单...</p>
    <template v-else-if="playlist">
      <div class="playlist-description">
        <p>{{ playlist.description || '暂无描述' }}</p>
        <span>{{ playlist.songs.length }} 首</span>
      </div>
      <SongTable
        mode="playlist"
        title=""
        empty-text="歌单还是空的，去歌曲列表添加歌曲吧。"
        :songs="playlist.songs"
        :current-song-id="currentSongId"
        :liked-ids="likedIds"
        :busy="busy"
        @select="emit('select', $event)"
        @toggle-like="emit('toggle-like', $event)"
        @remove="emit('remove', $event)"
        @search-field="(field, value) => emit('search-field', field, value)"
      />
    </template>
  </section>
</template>
