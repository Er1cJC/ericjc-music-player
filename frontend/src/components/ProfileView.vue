<script setup lang="ts">
/**
 * 个人中心视图。
 *
 * 交互设计要点：
 *  - 头像：悬停浮现相机徽标，点击后进入可拖动、可缩放的方形裁剪器；
 *  - 编辑资料：昵称 / 签名即时预览（草稿直接反映在头部），带字数统计；
 *  - 统计卡片：数字滚动动画，进场错峰上浮；
 *  - 全部动效尊重 prefers-reduced-motion。
 */
import { computed, onMounted, ref } from 'vue';
import { Camera, Check, Clock3, Heart, ListMusic, Pencil, X } from '@lucide/vue';
import ImageCropper from './ImageCropper.vue';

interface ProfileUser {
  userId: number;
  username: string;
  role: string;
  nickname: string | null;
  avatarUrl: string | null;
  bio: string | null;
}

interface ProfileStats {
  favorites: number;
  playlists: number;
  history: number;
}

const props = defineProps<{
  user: ProfileUser;
  token: string;
}>();

const emit = defineEmits<{
  'update-user': [user: ProfileUser];
}>();

const stats = ref<ProfileStats | null>(null);
const statsLoading = ref(true);
const editing = ref(false);
const nicknameDraft = ref('');
const bioDraft = ref('');
const saving = ref(false);
const uploading = ref(false);
const message = ref('');
const messageError = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);
const avatarCropFile = ref<File | null>(null);
const showAvatarCropper = ref(false);
const avatarPopped = ref(false);
const joinedText = ref('');
let messageTimer: ReturnType<typeof setTimeout> | undefined;

const favDisplay = ref(0);
const playlistDisplay = ref(0);
const historyDisplay = ref(0);

const prefersReducedMotion = () =>
  typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function notify(text: string, error = false) {
  message.value = text;
  messageError.value = error;
  clearTimeout(messageTimer);
  messageTimer = setTimeout(() => { message.value = ''; }, 3600);
}

function authHeaders(): Record<string, string> {
  return { Authorization: `Bearer ${props.token}` };
}

function apiHeaders(): Record<string, string> {
  return { 'Content-Type': 'application/json', ...authHeaders() };
}

const displayName = computed(() => {
  const source = editing.value ? nicknameDraft.value : (props.user.nickname || props.user.username);
  return source.trim() || props.user.username;
});

const previewBio = computed(() => {
  const source = editing.value ? bioDraft.value : props.user.bio;
  return source?.trim() || '';
});

const avatarInitial = computed(() => displayName.value.charAt(0).toUpperCase());

const AVATAR_GRADIENTS = [
  'linear-gradient(135deg, #f43f5e, #d946ef)',
  'linear-gradient(135deg, #0ea5e9, #6366f1)',
  'linear-gradient(135deg, #f59e0b, #ef4444)',
  'linear-gradient(135deg, #10b981, #0ea5e9)',
  'linear-gradient(135deg, #8b5cf6, #ec4899)',
  'linear-gradient(135deg, #14b8a6, #84cc16)',
];
const avatarGradient = computed(() => AVATAR_GRADIENTS[props.user.userId % AVATAR_GRADIENTS.length]);

// ---------------------------------------------------------------------------
// 数据加载
// ---------------------------------------------------------------------------

async function loadProfile() {
  statsLoading.value = true;
  try {
    const res = await fetch('/api/profile', { headers: authHeaders() });
    if (res.ok) {
      const data = await res.json();
      stats.value = { favorites: data.stats.favorites, playlists: data.stats.playlists, history: data.stats.history };
      animateCount(stats.value.favorites, (v) => { favDisplay.value = v; });
      animateCount(stats.value.playlists, (v) => { playlistDisplay.value = v; });
      animateCount(stats.value.history, (v) => { historyDisplay.value = v; });
      if (data.createdAt) {
        const date = new Date(data.createdAt);
        if (!Number.isNaN(date.getTime())) {
          joinedText.value = `${date.getFullYear()} 年 ${date.getMonth() + 1} 月`;
        }
      }
      // 以服务端为准同步资料
      emit('update-user', {
        userId: data.userId,
        username: data.username,
        role: data.role,
        nickname: data.nickname ?? null,
        avatarUrl: data.avatarUrl ?? null,
        bio: data.bio ?? null,
      });
    } else {
      stats.value = null;
    }
  } catch {
    stats.value = null;
  }
  statsLoading.value = false;
}

function animateCount(target: number, setter: (value: number) => void) {
  if (prefersReducedMotion()) {
    setter(target);
    return;
  }
  const duration = 650;
  const start = performance.now();
  const step = (now: number) => {
    const t = Math.min(1, (now - start) / duration);
    const eased = 1 - (1 - t) ** 3;
    setter(Math.round(target * eased));
    if (t < 1) window.requestAnimationFrame(step);
  };
  window.requestAnimationFrame(step);
}

// ---------------------------------------------------------------------------
// 编辑资料
// ---------------------------------------------------------------------------

function startEdit() {
  nicknameDraft.value = props.user.nickname || '';
  bioDraft.value = props.user.bio || '';
  editing.value = true;
  message.value = '';
}

function cancelEdit() {
  editing.value = false;
  message.value = '';
}

async function saveProfile() {
  saving.value = true;
  message.value = '';
  try {
    const res = await fetch('/api/profile', {
      method: 'PUT',
      headers: apiHeaders(),
      body: JSON.stringify({ nickname: nicknameDraft.value, bio: bioDraft.value }),
    });
    if (!res.ok) {
      const e = await res.json();
      notify(e.error || '保存失败', true);
      return;
    }
    const data = await res.json();
    emit('update-user', {
      ...props.user,
      nickname: data.nickname ?? null,
      bio: data.bio ?? null,
    });
    editing.value = false;
    notify('资料已更新');
  } catch {
    notify('网络错误，请稍后重试', true);
  } finally {
    saving.value = false;
  }
}

// ---------------------------------------------------------------------------
// 头像
// ---------------------------------------------------------------------------

function openAvatarPicker() {
  if (uploading.value) return;
  avatarInput.value?.click();
}

async function handleAvatarSelect(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (file.type && !file.type.startsWith('image/')) {
    notify('请选择图片文件', true);
    return;
  }

  avatarCropFile.value = file;
  showAvatarCropper.value = true;
}

function cancelAvatarCrop() {
  showAvatarCropper.value = false;
  avatarCropFile.value = null;
}

async function uploadCroppedAvatar(blob: Blob) {
  showAvatarCropper.value = false;
  avatarCropFile.value = null;
  uploading.value = true;
  message.value = '';
  try {
    const form = new FormData();
    form.append('file', blob, 'avatar.png');
    const res = await fetch('/api/profile/avatar', { method: 'POST', headers: authHeaders(), body: form });
    if (!res.ok) {
      const e = await res.json();
      notify(e.error || '头像上传失败', true);
      return;
    }
    const data = await res.json();
    emit('update-user', { ...props.user, avatarUrl: data.avatarUrl });
    avatarPopped.value = true;
    window.setTimeout(() => { avatarPopped.value = false; }, 450);
    notify('头像已更新');
  } catch {
    notify('头像上传失败', true);
  } finally {
    uploading.value = false;
  }
}

onMounted(loadProfile);
</script>

<template>
  <div class="profile-view">
    <div class="profile-hero">
      <div class="profile-avatar-wrap">
        <button
          class="profile-avatar"
          :class="{ popped: avatarPopped, uploading }"
          type="button"
          :disabled="uploading"
          title="点击更换头像"
          aria-label="更换头像"
          @click="openAvatarPicker"
        >
          <img v-if="user.avatarUrl" :src="user.avatarUrl" :alt="`${displayName} 的头像`" />
          <span v-else class="profile-avatar-initial" :style="{ background: avatarGradient }">{{ avatarInitial }}</span>
          <span class="profile-avatar-camera" aria-hidden="true">
            <Camera :size="16" />
          </span>
        </button>
        <span v-if="uploading" class="profile-avatar-hint">处理中…</span>
        <span v-else class="profile-avatar-hint">点击更换头像</span>
        <input ref="avatarInput" type="file" accept="image/*" hidden @change="handleAvatarSelect" />
      </div>

      <ImageCropper
        v-if="showAvatarCropper && avatarCropFile"
        :file="avatarCropFile"
        title="调整头像"
        @confirm="uploadCroppedAvatar"
        @cancel="cancelAvatarCrop"
      />

      <div class="profile-id">
        <div class="profile-name-row">
          <h2 class="profile-display-name">{{ displayName }}</h2>
          <span v-if="user.role === 'ADMIN'" class="profile-badge">管理员</span>
        </div>
        <p class="profile-username">@{{ user.username }}</p>
        <p class="profile-bio">
          <template v-if="previewBio">{{ previewBio }}</template>
          <template v-else-if="editing">写一句话介绍自己吧（比如最近在循环的歌）</template>
          <template v-else>还没有个性签名，点「编辑资料」写一句吧</template>
        </p>
        <p v-if="joinedText" class="profile-joined">加入于 {{ joinedText }}</p>
      </div>
    </div>

    <div class="profile-actions">
      <button v-if="!editing" class="primary-action" type="button" @click="startEdit">
        <Pencil :size="16" />编辑资料
      </button>
    </div>

    <Transition name="profile-edit">
      <form v-if="editing" class="profile-edit-form" @submit.prevent="saveProfile">
        <div class="profile-field">
          <label for="profile-nickname">昵称</label>
          <input
            id="profile-nickname"
            v-model="nicknameDraft"
            type="text"
            maxlength="50"
            placeholder="给自己起个好听的名字"
          />
          <small>{{ nicknameDraft.length }}/50</small>
        </div>
        <div class="profile-field">
          <label for="profile-bio">个性签名</label>
          <textarea
            id="profile-bio"
            v-model="bioDraft"
            rows="3"
            maxlength="200"
            placeholder="一句话介绍自己"
          ></textarea>
          <small>{{ bioDraft.length }}/200</small>
        </div>
        <div class="profile-form-actions">
          <button class="primary-action" type="submit" :disabled="saving">
            <Check :size="16" />{{ saving ? '保存中…' : '保存' }}
          </button>
          <button class="ghost-button" type="button" :disabled="saving" @click="cancelEdit">
            <X :size="16" />取消
          </button>
        </div>
      </form>
    </Transition>

    <div class="profile-stats" aria-label="统计">
      <div class="profile-stat-card">
        <Heart :size="20" class="stat-icon heart" />
        <strong>{{ statsLoading ? '—' : favDisplay }}</strong>
        <span>收藏</span>
      </div>
      <div class="profile-stat-card">
        <ListMusic :size="20" class="stat-icon playlist" />
        <strong>{{ statsLoading ? '—' : playlistDisplay }}</strong>
        <span>歌单</span>
      </div>
      <div class="profile-stat-card">
        <Clock3 :size="20" class="stat-icon history" />
        <strong>{{ statsLoading ? '—' : historyDisplay }}</strong>
        <span>最近播放</span>
      </div>
    </div>

    <p v-if="message" class="status-text profile-notice" :class="{ warning: messageError }" role="status">{{ message }}</p>
  </div>
</template>
