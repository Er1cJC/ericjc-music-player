<script setup lang="ts">
/**
 * 歌词舞台：负责歌词的渲染、居中滚动与「流光」逐字高亮。
 *
 * 性能约定（重要）：
 *  - 行级切换走 Vue 响应式（activeIndex 由父组件在 timeupdate 里更新，频率很低）；
 *  - 逐字进度由组件内部的 requestAnimationFrame 循环直接写 DOM 的 CSS 变量（--progress），
 *    不经过响应式系统，因此每帧不会触发组件重渲染。
 */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { LyricLine } from '../lyrics/types';

const props = defineProps<{
  lines: LyricLine[];
  /** 当前行下标；-1 表示还没唱到第一句（待命状态） */
  activeIndex: number;
  /** 播放器 audio 元素：逐字进度直接从它的 currentTime 读取 */
  audioEl: HTMLAudioElement | null;
  playing: boolean;
}>();

const containerRef = ref<HTMLElement | null>(null);
const lineEls: HTMLElement[] = [];

let rafId = 0;
let wordEls: HTMLElement[] = [];
let cachedWordLine = -1;
let lastWordIndex = -2;
let reducedMotion = false;
let resizeObserver: ResizeObserver | null = null;

function setLineRef(el: unknown, index: number) {
  if (el instanceof HTMLElement) lineEls[index] = el;
}

function setProgress(el: HTMLElement | null | undefined, value: number) {
  if (!el) return;
  const rounded = Math.round(value * 1000) / 1000;
  if (el.style.getPropertyValue('--progress') === String(rounded)) return;
  el.style.setProperty('--progress', String(rounded));
}

function progressTargets(): HTMLElement[] {
  const container = containerRef.value;
  if (!container) return [];
  return Array.from(container.querySelectorAll<HTMLElement>('.lyric-text, .lyric-word'));
}

function resetProgress() {
  for (const el of progressTargets()) el.style.removeProperty('--progress');
  wordEls = [];
  cachedWordLine = -1;
  lastWordIndex = -2;
}

/** 逐字（或整行）流光的进度应用：时间 → CSS 变量 */
function applyProgress(time: number) {
  const index = props.activeIndex;
  if (index < 0) return;
  const line = props.lines[index];
  const lineEl = lineEls[index];
  if (!line || !lineEl) return;

  const words = line.words;
  if (words && words.length) {
    if (cachedWordLine !== index) {
      wordEls = Array.from(lineEl.querySelectorAll<HTMLElement>('.lyric-word'));
      cachedWordLine = index;
      lastWordIndex = -2;
    }
    let current = -1;
    for (let i = 0; i < words.length; i += 1) {
      if (time >= words[i].startTime) current = i;
      else break;
    }
    if (current !== lastWordIndex) {
      for (let i = 0; i < wordEls.length; i += 1) setProgress(wordEls[i], i < current ? 1 : 0);
      lastWordIndex = current;
    }
    if (current < 0 || current >= wordEls.length) return;
    const word = words[current];
    const span = Math.max(0.05, word.endTime - word.startTime);
    setProgress(wordEls[current], Math.min(1, Math.max(0, (time - word.startTime) / span)));
    return;
  }

  const textEl = lineEl.querySelector<HTMLElement>('.lyric-text');
  if (!textEl) return;
  if (!line.text) {
    setProgress(textEl, 0);
    return;
  }
  const span = Math.max(0.1, line.endTime - line.startTime);
  setProgress(textEl, Math.min(1, Math.max(0, (time - line.startTime) / span)));
}

/** 关闭动效偏好时：不做逐帧推进，直接把当前行点亮 */
function applyStaticHighlight() {
  resetProgress();
  const index = props.activeIndex;
  const lineEl = index >= 0 ? lineEls[index] : undefined;
  if (!lineEl) return;
  const wordNodes = lineEl.querySelectorAll<HTMLElement>('.lyric-word');
  if (wordNodes.length) {
    wordNodes.forEach((el) => setProgress(el, 1));
    return;
  }
  setProgress(lineEl.querySelector<HTMLElement>('.lyric-text'), 1);
}

function tick() {
  rafId = 0;
  if (!props.playing) return;
  const audio = props.audioEl;
  if (audio) applyProgress(audio.currentTime);
  rafId = window.requestAnimationFrame(tick);
}

function startLoop() {
  if (reducedMotion || rafId || !props.playing) return;
  rafId = window.requestAnimationFrame(tick);
}

function stopLoop() {
  if (rafId) {
    window.cancelAnimationFrame(rafId);
    rafId = 0;
  }
}

/** 维护"最后一行半高"变量，让最后一句也能精确滚到中心线 */
function refreshPlaceholders() {
  const container = containerRef.value;
  if (!container) return;
  const last = lineEls[props.lines.length - 1];
  const half = last && last.offsetHeight > 0 ? last.offsetHeight / 2 : 22;
  container.style.setProperty('--half-last-line', `${half}px`);
}

/** 让当前行（或待命位置）对齐歌词区中心线 */
function centerActive(smooth: boolean) {
  const container = containerRef.value;
  if (!container) return;
  refreshPlaceholders();
  const index = props.activeIndex;
  if (index < 0) {
    container.scrollTo({ top: 0, behavior: smooth ? 'smooth' : 'auto' });
    return;
  }
  const el = lineEls[index];
  if (!el) return;
  const target = Math.max(0, el.offsetTop - (container.clientHeight - el.offsetHeight) / 2);
  container.scrollTo({ top: target, behavior: smooth ? 'smooth' : 'auto' });
}

watch(
  () => props.activeIndex,
  () => {
    cachedWordLine = -1;
    lastWordIndex = -2;
    nextTick(() => {
      centerActive(true);
      if (reducedMotion) {
        applyStaticHighlight();
      } else if (props.audioEl) {
        // 暂停时拖动进度条：行级切换后补一次填充，避免停留旧进度
        applyProgress(props.audioEl.currentTime);
      }
    });
  },
);

watch(
  () => props.lines,
  () => {
    lineEls.length = 0;
    resetProgress();
    nextTick(() => {
      refreshPlaceholders();
      centerActive(false);
      if (reducedMotion) applyStaticHighlight();
    });
  },
);

watch(
  () => props.playing,
  (playing) => {
    if (playing) {
      lastWordIndex = -2;
      startLoop();
    } else {
      stopLoop();
    }
  },
);

onMounted(() => {
  reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const container = containerRef.value;
  if (container && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      refreshPlaceholders();
      centerActive(false);
    });
    resizeObserver.observe(container);
  }

  nextTick(() => {
    if (containerRef.value) containerRef.value.scrollTop = 0;
    refreshPlaceholders();
    if (reducedMotion) applyStaticHighlight();
  });
  startLoop();
});

onBeforeUnmount(() => {
  stopLoop();
  resizeObserver?.disconnect();
  resizeObserver = null;
});
</script>

<template>
  <div class="detail-lyrics" ref="containerRef">
    <p
      v-for="(line, index) in lines"
      :key="index"
      class="lyric-line"
      :class="{ active: index === activeIndex, 'has-translation': Boolean(line.translation) }"
      :ref="(el) => setLineRef(el, index)"
    >
      <span v-if="line.words && line.words.length" class="lyric-words">
        <span v-for="(word, wi) in line.words" :key="wi" class="lyric-word">{{ word.text }}</span>
      </span>
      <span v-else class="lyric-text">{{ line.text || '' }}</span>
      <span v-if="line.translation" class="lyric-translation">{{ line.translation }}</span>
    </p>
  </div>
</template>
