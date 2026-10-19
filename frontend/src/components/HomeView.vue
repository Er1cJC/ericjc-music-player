<script setup lang="ts">
/**
 * 首页推荐流：问候区 + 快捷入口 + 当前播放 Hero + 三个横向滚动区块。
 */
import { Clock3, Heart, ListMusic, Pause, Play, Shuffle } from '@lucide/vue';
import type { PlaylistSummary, Song, ViewKey } from '../types';

defineProps<{
  greeting: string;
  displayName: string | null;
  currentSong: Song | null;
  isPlaying: boolean;
  recentSongs: Song[];
  recommendedSongs: Song[];
  playlists: PlaylistSummary[];
  hasToken: boolean;
}>();

const emit = defineEmits<{
  navigate: [view: ViewKey];
  'play-random': [songs: Song[]];
  select: [song: Song];
  'open-detail': [];
  'toggle-playback': [];
  'open-playlist': [playlist: PlaylistSummary];
  'search-field': [field: 'title' | 'artist' | 'album', value: string];
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
  <section class="home-greeting">
    <div>
      <h2 class="home-greeting-title">{{ greeting }}<template v-if="displayName">，{{ displayName }}</template></h2>
      <p class="home-greeting-sub">为你精选的音乐，随时开启。</p>
    </div>
  </section>

  <section class="home-shortcuts" aria-label="快捷入口">
    <button class="home-shortcut" type="button" @click="emit('navigate', 'library')">
      <span class="home-shortcut-icon recent"><Clock3 :size="22" /></span>
      <span class="home-shortcut-label">最近播放</span>
    </button>
    <button class="home-shortcut" type="button" @click="emit('navigate', 'library')">
      <span class="home-shortcut-icon liked"><Heart :size="22" /></span>
      <span class="home-shortcut-label">收藏的歌</span>
    </button>
    <button class="home-shortcut" type="button" @click="emit('navigate', 'library')">
      <span class="home-shortcut-icon playlist"><ListMusic :size="22" /></span>
      <span class="home-shortcut-label">我的歌单</span>
    </button>
    <button class="home-shortcut" type="button" @click="emit('play-random', [])">
      <span class="home-shortcut-icon shuffle"><Shuffle :size="22" /></span>
      <span class="home-shortcut-label">随机播放</span>
    </button>
  </section>

  <section v-if="currentSong" class="home-now" role="button" tabindex="0" aria-label="打开正在播放" @click="emit('open-detail')" @keydown.enter.self="emit('open-detail')">
    <div class="home-now-cover" :style="{ background: currentSong.color }">
      <img :src="currentSong.coverUrl" :alt="`${currentSong.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" />
      <span>{{ currentSong.cover }}</span>
    </div>
    <div class="home-now-info">
      <p class="home-now-eyebrow">正在播放</p>
      <h2>{{ currentSong.title }}</h2>
      <p class="home-now-meta">
        <span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'artist', currentSong.artist)" @keydown="handleMetadataKeydown($event, 'artist', currentSong.artist)">{{ currentSong.artist }}</span>
        <span aria-hidden="true"> · </span>
        <span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'album', currentSong.album)" @keydown="handleMetadataKeydown($event, 'album', currentSong.album)">{{ currentSong.album }}</span>
      </p>
    </div>
    <button class="home-now-play" type="button" :aria-label="isPlaying ? '暂停' : '播放'" @click.stop="emit('toggle-playback')">
      <Pause v-if="isPlaying" :size="22" fill="currentColor" /><Play v-else :size="22" fill="currentColor" />
    </button>
  </section>

  <section class="home-section">
    <div class="home-section-head">
      <h3>最近播放</h3>
      <button v-if="recentSongs.length" class="home-see-all" type="button" @click="emit('navigate', 'library')">查看全部</button>
    </div>
    <div v-if="recentSongs.length" class="home-row">
      <button v-for="song in recentSongs.slice(0, 12)" :key="'r' + song.id" class="home-song-card" type="button" @click="emit('select', song)">
        <div class="home-card-cover" :style="{ background: song.color }">
          <img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" />
          <span class="home-card-fallback">{{ song.cover }}</span>
          <span class="home-card-play"><Play :size="16" fill="currentColor" /></span>
        </div>
        <div class="home-card-meta">
          <strong>{{ song.title }}</strong>
          <small>
            <span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'artist', song.artist)" @keydown="handleMetadataKeydown($event, 'artist', song.artist)">{{ song.artist }}</span>
            <span aria-hidden="true"> · </span>
            <span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'album', song.album)" @keydown="handleMetadataKeydown($event, 'album', song.album)">{{ song.album }}</span>
          </small>
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
      <button v-for="song in recommendedSongs" :key="'rc' + song.id" class="home-song-card" type="button" @click="emit('select', song)">
        <div class="home-card-cover" :style="{ background: song.color }">
          <img :src="song.coverUrl" :alt="`${song.title} 封面`" @load="markCoverLoaded" @error="hideBrokenImage" />
          <span class="home-card-fallback">{{ song.cover }}</span>
          <span class="home-card-play"><Play :size="16" fill="currentColor" /></span>
        </div>
        <div class="home-card-meta">
          <strong>{{ song.title }}</strong>
          <small>
            <span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'artist', song.artist)" @keydown="handleMetadataKeydown($event, 'artist', song.artist)">{{ song.artist }}</span>
            <span aria-hidden="true"> · </span>
            <span class="metadata-link" role="button" tabindex="0" @click.stop="emit('search-field', 'album', song.album)" @keydown="handleMetadataKeydown($event, 'album', song.album)">{{ song.album }}</span>
          </small>
        </div>
      </button>
    </div>
    <p v-else class="home-empty">暂无推荐，去导入一些歌曲吧。</p>
  </section>

  <section class="home-section">
    <div class="home-section-head">
      <h3>我的歌单</h3>
      <button class="home-see-all" type="button" @click="emit('navigate', 'library')">查看全部</button>
    </div>
    <div v-if="playlists.length" class="home-row">
      <button v-for="p in playlists.slice(0, 12)" :key="'p' + p.id" class="home-song-card" type="button" @click="emit('open-playlist', p)">
        <div class="home-card-cover home-playlist-cover"><ListMusic :size="28" /></div>
        <div class="home-card-meta">
          <strong>{{ p.name }}</strong>
          <small>{{ p.songCount }} 首{{ p.description ? ' · ' + p.description : '' }}</small>
        </div>
      </button>
    </div>
    <p v-else class="home-empty">
      <template v-if="hasToken">还没有歌单，去「我的音乐」新建一个吧。</template>
      <template v-else>登录后即可创建歌单。</template>
    </p>
  </section>
</template>
