/**
 * 全局共享工具函数（与 UI 无关）。
 */
import type { Song, SongResponse } from './types';

/** 由后端歌曲数据生成前端展示用 Song（补封面占位与配色） */
export function toDisplaySong(s: SongResponse): Song {
  return { ...s, cover: getCoverText(s.title), color: getSongColor(s.id) };
}

/** 取标题首字母作为封面占位（最多 2 个字符） */
export function getCoverText(t: string) {
  return t.split(/\s+/).map((w) => w[0]).join('').slice(0, 2).toUpperCase();
}

/** 按歌曲 id 派生一个稳定的封面底色 */
export function getSongColor(id: number) {
  return ['#3b82f6', '#111827', '#d97706', '#0f766e', '#7c3aed', '#be123c'][(id - 1) % 6];
}

/** 秒数 → mm:ss */
export function formatTime(s: number) {
  return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
}

/** 文件大小 → 可读文本 */
export function formatFileSize(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

// —— 管理后台排序：中文拼音 → 英文 → 符号 ——

const adminPinyinCollator = new Intl.Collator('zh-Hans-u-co-pinyin', { sensitivity: 'base', numeric: true });
const adminEnglishCollator = new Intl.Collator('en', { sensitivity: 'base', numeric: true });

function adminTextGroup(value: string) {
  const first = value.trim().charAt(0);
  if (/^[\u3400-\u9fff]$/.test(first)) return 0;
  if (/^[A-Za-z]$/.test(first)) return 1;
  return 2;
}

export function compareAdminText(a: string, b: string, direction = 1) {
  const groupDiff = adminTextGroup(a) - adminTextGroup(b);
  if (groupDiff) return groupDiff;
  const group = adminTextGroup(a);
  return (group === 0 ? adminPinyinCollator : group === 1 ? adminEnglishCollator : adminPinyinCollator).compare(a, b) * direction;
}
