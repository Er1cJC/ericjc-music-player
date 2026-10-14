<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { Check, Move, X, ZoomIn, ZoomOut } from '@lucide/vue';

const props = withDefaults(defineProps<{
  file: File;
  title?: string;
}>(), { title: '调整图片' });

const emit = defineEmits<{
  confirm: [blob: Blob];
  cancel: [];
}>();

const canvas = ref<HTMLCanvasElement | null>(null);
const image = ref<HTMLImageElement | null>(null);
const imageUrl = ref('');
const zoom = ref(1);
const offsetX = ref(0);
const offsetY = ref(0);
const dragging = ref(false);
const busy = ref(false);
let pointerId: number | null = null;
let lastPointerX = 0;
let lastPointerY = 0;

const OUTPUT_SIZE = 640;

function revokeImageUrl() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value);
  imageUrl.value = '';
}

function loadImage() {
  revokeImageUrl();
  imageUrl.value = URL.createObjectURL(props.file);
  const next = new Image();
  next.onload = () => {
    image.value = next;
    zoom.value = 1;
    offsetX.value = 0;
    offsetY.value = 0;
    draw();
  };
  next.onerror = () => emit('cancel');
  next.src = imageUrl.value;
}

function draw() {
  const target = canvas.value;
  const source = image.value;
  if (!target || !source) return;
  const ctx = target.getContext('2d');
  if (!ctx) return;
  const side = Math.min(source.naturalWidth, source.naturalHeight);
  const baseScale = OUTPUT_SIZE / side;
  const scale = baseScale * zoom.value;
  const width = source.naturalWidth * scale;
  const height = source.naturalHeight * scale;
  const maxOffsetX = Math.max(0, (width - OUTPUT_SIZE) / 2);
  const maxOffsetY = Math.max(0, (height - OUTPUT_SIZE) / 2);
  offsetX.value = Math.min(maxOffsetX, Math.max(-maxOffsetX, offsetX.value));
  offsetY.value = Math.min(maxOffsetY, Math.max(-maxOffsetY, offsetY.value));
  ctx.clearRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
  ctx.save();
  const radius = 56;
  ctx.beginPath();
  ctx.roundRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE, radius);
  ctx.clip();
  ctx.fillStyle = '#f4f1ec';
  ctx.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
  ctx.drawImage(source, (OUTPUT_SIZE - width) / 2 + offsetX.value, (OUTPUT_SIZE - height) / 2 + offsetY.value, width, height);
  ctx.restore();
}

function startDrag(event: PointerEvent) {
  if (!image.value || busy.value) return;
  dragging.value = true;
  pointerId = event.pointerId;
  lastPointerX = event.clientX;
  lastPointerY = event.clientY;
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
}

function moveDrag(event: PointerEvent) {
  if (!dragging.value || event.pointerId !== pointerId) return;
  offsetX.value += event.clientX - lastPointerX;
  offsetY.value += event.clientY - lastPointerY;
  lastPointerX = event.clientX;
  lastPointerY = event.clientY;
  draw();
}

function endDrag(event: PointerEvent) {
  if (event.pointerId === pointerId) {
    dragging.value = false;
    pointerId = null;
  }
}

function clampZoom(value: number) {
  zoom.value = Math.min(3, Math.max(1, value));
  draw();
}

function confirm() {
  const target = canvas.value;
  if (!target || busy.value) return;
  busy.value = true;
  target.toBlob((blob) => {
    busy.value = false;
    if (blob) emit('confirm', blob);
  }, 'image/png');
}

watch(() => props.file, async () => { await nextTick(); loadImage(); }, { immediate: true });
watch([zoom, offsetX, offsetY], draw);
onBeforeUnmount(revokeImageUrl);
</script>

<template>
  <Teleport to="body">
    <div class="image-crop-backdrop" @mousedown.self="emit('cancel')">
      <section class="image-crop-dialog" role="dialog" aria-modal="true" :aria-label="title">
        <header class="image-crop-header">
          <div>
            <p class="eyebrow">图片调整</p>
            <h2>{{ title }}</h2>
          </div>
          <button class="icon-button" type="button" aria-label="取消" @click="emit('cancel')"><X :size="20" /></button>
        </header>
        <div
          class="image-crop-stage"
          :class="{ dragging }"
          @pointerdown="startDrag"
          @pointermove="moveDrag"
          @pointerup="endDrag"
          @pointercancel="endDrag"
        >
          <canvas ref="canvas" :width="OUTPUT_SIZE" :height="OUTPUT_SIZE" aria-label="图片裁剪预览" />
          <span class="image-crop-hint"><Move :size="15" />拖动图片调整位置</span>
        </div>
        <div class="image-crop-controls">
          <ZoomOut :size="17" />
          <input :value="zoom" type="range" min="1" max="3" step="0.01" aria-label="缩放图片" @input="clampZoom(Number(($event.target as HTMLInputElement).value))" />
          <ZoomIn :size="17" />
        </div>
        <footer class="image-crop-actions">
          <button class="ghost-button" type="button" :disabled="busy" @click="emit('cancel')">取消</button>
          <button class="primary-action" type="button" :disabled="busy || !image" @click="confirm"><Check :size="17" />{{ busy ? '处理中…' : '使用此图片' }}</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
