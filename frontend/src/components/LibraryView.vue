<script setup lang="ts">
/**
 * 我的音乐：统计卡 + 歌单区 + 收藏歌曲表 + 最近播放表。
 */
import { ListMusic, LogIn, Pencil, Plus, Trash2 } from '@lucide/vue';
import SongTable from './SongTable.vue';
import type { PlaylistSummary, Song } from '../types';

defineProps<{
  playlists: PlaylistSummary[];
  likedSongs: Song[];
  recentSongs: Song[];
  likedIds: Set<number>;
  currentSongId: number | null;
  hasToken: boolean;
  busy: boolean;
  isLoading: boolean;
  errorMessage: string;
}>();

const emit = defineEmits<{
  select: [song: Song];
  'toggle-like': [song: Song];
  'add-to-playlist': [song: Song, event: MouseEvent];
  'search-field': [field: 'title' | 'artist' | 'album', value: string];
  'open-playlist': [playlist: PlaylistSummary];
  'create-playlist': [];
  'rename-playlist': [playlist: PlaylistSummary];
  'delete-playlist': [playlist: PlaylistSummary];
  'login-required': [];
  'play-random': [songs: Song[]];
}>();
</script>

<template>
  <section class="library-grid">
    <article class="metric-card"><span>收藏歌曲</span><strong>{{ likedSongs.length }}</strong></article>
    <article class="metric-card"><span>最近播放</span><strong>{{ recentSongs.length }}</strong></article>
  </section>

  <section class="playlist-section" aria-label="我的歌单">
    <div class="table-heading playlist-heading">
      <h2>我的歌单</h2>
      <button class="ghost-button" type="button" @click="emit('create-playlist')"><Plus :size="18" />新建歌单</button>
    </div>
    <div v-if="!hasToken" class="playlist-login">
      <p class="status-text">登录后即可创建歌单。</p>
      <button class="ghost-button" type="button" @click="emit('login-required')"><LogIn :size="18" />登录</button>
    </div>
    <template v-else>
      <div v-if="playlists.length" class="playlist-grid">
        <article v-for="p in playlists" :key="p.id" class="playlist-card saved-playlist-card">
          <button class="playlist-open" type="button" @click="emit('open-playlist', p)">
            <ListMusic :size="24" />
            <strong>{{ p.name }}</strong>
            <span>{{ p.songCount }} 首</span>
            <small>{{ p.description || '暂无描述' }}</small>
          </button>
          <div class="playlist-card-actions">
            <button class="icon-button" type="button" title="重命名歌单" aria-label="重命名歌单" @click.stop="emit('rename-playlist', p)"><Pencil :size="18" /></button>
            <button class="icon-button danger" type="button" title="删除歌单" aria-label="删除歌单" :disabled="busy" @click.stop="emit('delete-playlist', p)"><Trash2 :size="18" /></button>
          </div>
        </article>
      </div>
      <p v-else class="status-text">还没有歌单，点击右上角新建一个吧。</p>
    </template>
  </section>

  <SongTable
    mode="main"
    title="收藏歌曲"
    empty-text="还没有收藏歌曲。"
    :songs="likedSongs"
    :current-song-id="currentSongId"
    :liked-ids="likedIds"
    :is-loading="isLoading"
    :error-message="errorMessage"
    shuffle-label="随机播放"
    @select="emit('select', $event)"
    @toggle-like="emit('toggle-like', $event)"
    @add-to-playlist="(song, event) => emit('add-to-playlist', song, event)"
    @search-field="(field, value) => emit('search-field', field, value)"
    @play-random="(songs) => emit('play-random', songs)"
  />

  <SongTable
    mode="recent"
    title="最近播放"
    empty-text="播放歌曲后会出现在这里。"
    :songs="recentSongs"
    :current-song-id="currentSongId"
    :liked-ids="likedIds"
    shuffle-label="随机回听"
    @select="emit('select', $event)"
    @search-field="(field, value) => emit('search-field', field, value)"
    @play-random="(songs) => emit('play-random', songs)"
  />
</template>
