/**
 * 歌词解析器（自研，不依赖任何第三方歌词库）。
 *
 * 支持：
 *  - 普通 LRC：        [mm:ss.xx] 文本（兼容 [mm:ss]、[mm:ss:xx]、[mm:ss.xxx]、一行多时间戳、[offset:]）
 *  - 增强 LRC：        [mm:ss.xx] <mm:ss.xx>字 <mm:ss.xx>字（逐字）
 *  - 网易云 YRC：      [行起,行时长](字起,字时长,0)字(字起,字时长,0)字（逐字）
 *  - QQ 音乐 QRC：     [行起,行时长]字(字起,字时长,0)字(字起,字时长,0)（逐字）
 *  - 酷狗 KRC：        [行起,行时长]<相对偏移,字时长,0>字<相对偏移,字时长,0>字（逐字）
 *  - Apple TTML：      XML，按 <p>/<span> 提取行与字
 *  - 纯文本：          无时间戳时原样作为无时间轴的歌词行
 *
 * 实际数据里常见的"脏"形态都必须容错：
 *  - 网易云的 JSON 元数据行：{"t":0,"c":[{"tx":"作词: "},...]}
 *  - 标签行：[ti:] [ar:] [al:] [by:] [offset:] 等
 *  - 行尾不带空格、空文本行（间奏）、时间戳乱序、同一时间戳重复（翻译歌词）
 */

import type {
  LyricFormat,
  LyricLine,
  LyricParseOptions,
  LyricsDoc,
  LyricWord,
} from './types';

/** 最后一行没有下一行可参考时的默认时长（秒） */
const DEFAULT_LAST_LINE_DURATION = 5;
/** 末字没有显式时长时，最多向后延伸多久（秒），避免长间奏把高亮拖得极慢 */
const MAX_INFERRED_LAST_WORD_DURATION = 4;
/** 判定"这是翻译歌词"所需的最低重复时间戳占比 */
const TRANSLATION_PAIR_RATIO = 0.6;

// ---------------------------------------------------------------------------
// 正则工厂（每次新建，避免 /g 正则的 lastIndex 状态互相污染）
// ---------------------------------------------------------------------------

/** LRC 时间戳：mm:ss / mm:ss.xx / mm:ss.xxx / mm:ss:xx */
function timeTagRegex(): RegExp {
  return /\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g;
}

/** 逐字格式的行头：[start,duration]（毫秒） */
function rangeHeaderRegex(): RegExp {
  return /\[(\d+),(\d+)\]/;
}

/** 行内逐字标签：<mm:ss.xx> 或 <毫秒,时长[,flag]> 或 (毫秒,时长[,flag]) */
function wordTagRegex(): RegExp {
  return /<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>|<(\d+),(\d+)(?:,\d+)?>|\((\d+),(\d+)(?:,\d+)?\)/g;
}

/** 元数据标签行 */
function metaTagRegex(): RegExp {
  return /^\[(ti|ar|al|by|offset|re|ve|length|kana|language|tool|qrc|encoding)\s*:([^\]]*)\]/i;
}

// ---------------------------------------------------------------------------
// 基础工具
// ---------------------------------------------------------------------------

function stripBom(text: string): string {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

/** 分:秒.小数 → 秒（小数 1/2/3 位分别按分秒/厘秒/毫秒换算） */
function toSeconds(minutes: string, seconds: string, fraction?: string): number {
  const m = Number.parseInt(minutes, 10) || 0;
  const s = Number.parseInt(seconds, 10) || 0;
  let ms = 0;
  if (fraction) {
    const value = Number.parseInt(fraction, 10) || 0;
    if (fraction.length === 1) ms = value * 100;
    else if (fraction.length === 2) ms = value * 10;
    else ms = value;
  }
  return m * 60 + s + ms / 1000;
}

function decodeEntities(text: string): string {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&apos;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&');
}

function collapseSpaces(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}

// ---------------------------------------------------------------------------
// 行内逐字标签
// ---------------------------------------------------------------------------

type TagOrder = 'none' | 'before' | 'after';

interface WordParseResult {
  /** 去掉所有标签后的整行文本 */
  text: string;
  words: LyricWord[] | null;
  /** 'after' = 时间标签在字之前（YRC/增强LRC/KRC）；'before' = 在字之后（QRC） */
  order: TagOrder;
}

/**
 * 解析一行里的逐字时间标签。
 * 通过「第一个非空文本片段出现在第几个位置」判断标签在字前还是字后，
 * 从而同时兼容 YRC（标签在前）、QRC（标签在后）与增强 LRC。
 */
function parseWordTags(content: string, lineStart: number): WordParseResult {
  const regex = wordTagRegex();
  const segments: string[] = [];
  const tags: { start: number; duration: number }[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(content)) !== null) {
    segments.push(content.slice(lastIndex, match.index));
    lastIndex = match.index + match[0].length;
    if (match[1] !== undefined) {
      // <mm:ss.xx> 形式（增强 LRC）
      tags.push({ start: toSeconds(match[1], match[2] ?? '0', match[3]), duration: 0 });
    } else if (match[4] !== undefined) {
      // <毫秒,时长,flag> 形式（KRC）
      tags.push({
        start: (Number.parseInt(match[4], 10) || 0) / 1000,
        duration: (Number.parseInt(match[5] ?? '0', 10) || 0) / 1000,
      });
    } else {
      // (毫秒,时长,flag) 形式（YRC / QRC）
      tags.push({
        start: (Number.parseInt(match[6] ?? '0', 10) || 0) / 1000,
        duration: (Number.parseInt(match[7] ?? '0', 10) || 0) / 1000,
      });
    }
  }

  if (!tags.length) {
    return { text: collapseSpaces(content), words: null, order: 'none' };
  }
  segments.push(content.slice(lastIndex));

  const firstTextIndex = segments.findIndex((segment) => segment.trim() !== '');
  const order: TagOrder = firstTextIndex <= 0 ? 'before' : 'after';

  const words: LyricWord[] = [];
  for (let i = 0; i < tags.length; i += 1) {
    const raw = segments[i + (order === 'after' ? 1 : 0)] ?? '';
    const text = raw.trim();
    if (!text) continue;
    let start = tags[i].start;
    // 相对时间兜底：明显早于行首的数值按"相对行首的毫秒"处理（KRC 约定）
    if (start < lineStart - 0.5) start = lineStart + tags[i].start;
    // 标签给了时长就用它；没给则先留 0（后续按"下一个字的起点/行尾"补全）
    words.push({
      startTime: start,
      endTime: start + (tags[i].duration > 0 ? tags[i].duration : 0),
      text,
    });
  }

  const text = collapseSpaces(content.replace(wordTagRegex(), ''));
  if (!words.length) return { text, words: null, order: 'none' };
  return { text, words, order };
}

// ---------------------------------------------------------------------------
// LRC / 增强 LRC / YRC / QRC / KRC
// ---------------------------------------------------------------------------

interface RawLine {
  startTime: number;
  /** 格式自带的行结束时间；没有则为 null（后续按下一行起点补全） */
  endTime: number | null;
  text: string;
  words: LyricWord[] | null;
  order: number;
}

interface DetectFlags {
  timeTag: boolean;
  rangeHeader: boolean;
  parenTag: boolean;
  angleRangeTag: boolean;
  angleTimeTag: boolean;
  qrcStyle: boolean;
  yrcStyle: boolean;
}

function parseTimedLines(raw: string, options: LyricParseOptions): LyricsDoc {
  const text = stripBom(raw).replace(/\r\n?/g, '\n');
  const rawLines = text.split('\n');
  const collected: RawLine[] = [];
  const plainLines: string[] = [];
  const flags: DetectFlags = {
    timeTag: false,
    rangeHeader: false,
    parenTag: false,
    angleRangeTag: false,
    angleTimeTag: false,
    qrcStyle: false,
    yrcStyle: false,
  };

  let offset = 0;
  let order = 0;

  for (const rawLine of rawLines) {
    const line = rawLine.trim();
    if (!line) continue;

    // 网易云 / QQ 音乐的 JSON 元数据行（作词、作曲、编曲等）
    if (line.startsWith('{')) continue;

    // 标签行
    const meta = line.match(metaTagRegex());
    if (meta) {
      if (meta[1].toLowerCase() === 'offset') {
        const value = Number.parseInt(meta[2].trim(), 10);
        if (Number.isFinite(value)) offset = value / 1000;
      }
      continue;
    }

    // 逐字格式（YRC / QRC / KRC）：[start,duration] 开头
    const rangeHeader = line.match(rangeHeaderRegex());
    if (rangeHeader) {
      flags.rangeHeader = true;
      const lineStart = (Number.parseInt(rangeHeader[1], 10) || 0) / 1000 - offset;
      const lineDuration = (Number.parseInt(rangeHeader[2], 10) || 0) / 1000;
      const parsed = parseWordTags(line.slice(rangeHeader[0].length), lineStart);
      if (parsed.words) {
        if (parsed.order === 'after') flags.yrcStyle = true;
        else if (parsed.order === 'before') flags.qrcStyle = true;
        if (/<(\d+),(\d+)(?:,\d+)?>/.test(line)) flags.angleRangeTag = true;
        else flags.parenTag = true;
      }
      collected.push({
        startTime: lineStart,
        endTime: lineDuration > 0 ? lineStart + lineDuration : null,
        text: parsed.text,
        words: parsed.words,
        order: order++,
      });
      continue;
    }

    // 普通 LRC / 增强 LRC：可能一行带多个时间戳
    const stamps = [...line.matchAll(timeTagRegex())];
    if (!stamps.length) {
      plainLines.push(line);
      continue;
    }
    flags.timeTag = true;

    const body = line.replace(timeTagRegex(), '');
    const firstStart = toSeconds(stamps[0][1], stamps[0][2], stamps[0][3]) - offset;
    const parsed = parseWordTags(body, firstStart);
    if (parsed.words) {
      if (parsed.order === 'after') flags.yrcStyle = true;
      if (/<\d{1,3}:\d{1,2}/.test(body)) flags.angleTimeTag = true;
    }

    for (const stamp of stamps) {
      if (stamp[1] === undefined || stamp[2] === undefined) continue;
      collected.push({
        startTime: toSeconds(stamp[1], stamp[2], stamp[3]) - offset,
        endTime: null,
        text: parsed.text,
        words: parsed.words,
        order: order++,
      });
    }
  }

  if (!collected.length) {
    const lines = plainLines.map<LyricLine>((value) => ({
      startTime: 0,
      endTime: 0,
      text: value,
      words: null,
      translation: null,
    }));
    return { lines, format: 'plain', hasWordTiming: false };
  }

  collected.sort((a, b) => a.startTime - b.startTime || a.order - b.order);

  let lines = collected.map<LyricLine>((item) => ({
    startTime: item.startTime,
    endTime: item.endTime ?? item.startTime,
    text: item.text,
    words: item.words,
    translation: null,
  }));

  lines = mergeTranslationPairs(lines);
  fillEndTimes(lines, options.duration);

  return {
    lines,
    format: detectFormat(flags),
    hasWordTiming: lines.some((line) => Boolean(line.words && line.words.length)),
  };
}

function detectFormat(flags: DetectFlags): LyricFormat {
  if (flags.angleTimeTag) return 'lrc-enhanced';
  if (flags.angleRangeTag) return 'krc';
  if (flags.parenTag) return flags.qrcStyle && !flags.yrcStyle ? 'qrc' : 'yrc';
  if (flags.timeTag) return 'lrc';
  if (flags.rangeHeader) return 'yrc';
  return 'plain';
}

/**
 * 补全结束时间：
 *  - 只补全"没有自带结束时间"的行，尊重 YRC/QRC/KRC 的行时长与 TTML 的 end
 *  - 逐字行：中间字的结束时间 = 下一个字的起点；末字按行尾补全（并设上限）
 */
function fillEndTimes(lines: LyricLine[], duration?: number): void {
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const next = lines[i + 1];

    if (line.endTime <= line.startTime) {
      if (next) {
        line.endTime = Math.max(line.startTime + 0.05, next.startTime);
      } else {
        const fallback = duration && duration > line.startTime
          ? duration
          : line.startTime + DEFAULT_LAST_LINE_DURATION;
        line.endTime = Math.max(line.startTime + 0.1, fallback);
      }
    }

    const words = line.words;
    if (!words || !words.length) continue;
    for (let w = 0; w < words.length - 1; w += 1) {
      const current = words[w];
      const following = words[w + 1];
      if (following.startTime > current.startTime) current.endTime = following.startTime;
      if (current.endTime <= current.startTime) current.endTime = current.startTime + 0.08;
    }
    const last = words[words.length - 1];
    if (last.endTime <= last.startTime) {
      const inferred = Math.min(line.endTime, last.startTime + MAX_INFERRED_LAST_WORD_DURATION);
      last.endTime = Math.max(inferred, last.startTime + 0.2);
    }
  }
}

/**
 * 翻译合并（保守启发式）：
 * 网易云的"翻译歌词"常见形态是同一时间戳紧跟重复一次。只有当重复时间戳占比
 * 达到 60% 才认为是翻译，否则保持原样（避免把合法的重复行错并成翻译）。
 */
function mergeTranslationPairs(lines: LyricLine[]): LyricLine[] {
  if (lines.length < 4) return lines;

  let pairs = 0;
  for (let i = 0; i + 1 < lines.length; i += 1) {
    const a = lines[i];
    const b = lines[i + 1];
    if (Math.abs(a.startTime - b.startTime) < 0.001 && !a.words && !b.words) pairs += 1;
  }
  // 若每一行都成对，则 pairs = 行数 / 2，占比应为 100%
  const candidateCount = lines.length / 2;
  if (candidateCount <= 0 || pairs / candidateCount < TRANSLATION_PAIR_RATIO) return lines;

  const result: LyricLine[] = [];
  for (let i = 0; i < lines.length; i += 1) {
    const current = lines[i];
    const next = lines[i + 1];
    if (next && Math.abs(current.startTime - next.startTime) < 0.001 && !current.words && !next.words) {
      result.push({
        ...current,
        translation: next.text || null,
        endTime: Math.max(current.endTime, next.endTime),
      });
      i += 1;
      continue;
    }
    result.push(current);
  }
  return result;
}

// ---------------------------------------------------------------------------
// TTML（Apple Music）
// ---------------------------------------------------------------------------

function looksLikeTtml(text: string): boolean {
  return /^\s*(<\?xml|<tt[\s>])/i.test(text);
}

function parseTtmlTimeValue(value: string): number | null {
  const raw = value.trim();
  if (!raw) return null;
  const clock = raw.match(/^(\d+):(\d{2}):(\d{2})(?:[.,:](\d+))?$/);
  if (clock) {
    const fraction = clock[4] ? Number.parseInt(clock[4], 10) / 10 ** clock[4].length : 0;
    return Number(clock[1]) * 3600 + Number(clock[2]) * 60 + Number(clock[3]) + fraction;
  }
  const milliseconds = raw.match(/^(\d+(?:\.\d+)?)ms$/);
  if (milliseconds) return Number(milliseconds[1]) / 1000;
  const seconds = raw.match(/^(\d+(?:\.\d+)?)(s)?$/);
  if (seconds) return Number(seconds[1]);
  return null;
}

function readTtmlTime(attributes: string, name: string): number | null {
  const match = attributes.match(new RegExp(`${name}\\s*=\\s*"([^"]*)"`, 'i'));
  if (!match) return null;
  return parseTtmlTimeValue(match[1]);
}

function parseTtml(raw: string, options: LyricParseOptions): LyricsDoc {
  const body = raw.replace(/<\?[\s\S]*?\?>/g, '');
  const lines: LyricLine[] = [];

  for (const paragraph of body.matchAll(/<p\b([^>]*)>([\s\S]*?)<\/p>/gi)) {
    const attributes = paragraph[1] ?? '';
    const inner = paragraph[2] ?? '';
    const begin = readTtmlTime(attributes, 'begin');
    if (begin === null) continue;

    let words: LyricWord[] | null = null;
    const spans = [...inner.matchAll(/<span\b([^>]*)>([\s\S]*?)<\/span>/gi)];
    if (spans.length) {
      const collected: LyricWord[] = [];
      for (const span of spans) {
        const spanBegin = readTtmlTime(span[1] ?? '', 'begin');
        if (spanBegin === null) continue;
        const spanEnd = readTtmlTime(span[1] ?? '', 'end');
        const spanText = decodeEntities((span[2] ?? '').replace(/<[^>]+>/g, '')).trim();
        if (!spanText) continue;
        collected.push({ startTime: spanBegin, endTime: spanEnd ?? spanBegin, text: spanText });
      }
      if (collected.length) words = collected;
    }

    const text = collapseSpaces(
      decodeEntities(inner.replace(/<br\s*\/?>/gi, ' ').replace(/<[^>]+>/g, '')),
    );
    lines.push({
      startTime: begin,
      endTime: readTtmlTime(attributes, 'end') ?? begin,
      text,
      words,
      translation: null,
    });
  }

  lines.sort((a, b) => a.startTime - b.startTime);
  fillEndTimes(lines, options.duration);

  return {
    lines,
    format: 'ttml',
    hasWordTiming: lines.some((line) => Boolean(line.words && line.words.length)),
  };
}

// ---------------------------------------------------------------------------
// 对外入口 & 辅助查询
// ---------------------------------------------------------------------------

/**
 * 解析歌词文本。格式按内容特征自动识别，不依赖文件扩展名。
 */
export function parseLyrics(raw: string, options: LyricParseOptions = {}): LyricsDoc {
  const text = stripBom(raw ?? '');
  if (!text.trim()) return { lines: [], format: 'plain', hasWordTiming: false };
  if (looksLikeTtml(text)) return parseTtml(text, options);
  return parseTimedLines(text, options);
}

/**
 * 二分查找当前时间对应的歌词行下标；还没唱到第一句时返回 -1。
 */
export function findActiveIndex(lines: LyricLine[], time: number): number {
  if (!lines.length) return -1;
  let low = 0;
  let high = lines.length - 1;
  let result = -1;
  while (low <= high) {
    const mid = (low + high) >> 1;
    if (time >= lines[mid].startTime) {
      result = mid;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }
  return result;
}

/** 查找当前时间在行内对应的字下标；无逐字信息或未开始时返回 -1 */
export function findActiveWordIndex(line: LyricLine | null | undefined, time: number): number {
  const words = line?.words;
  if (!words || !words.length) return -1;
  let result = -1;
  for (let i = 0; i < words.length; i += 1) {
    if (time >= words[i].startTime) result = i;
    else break;
  }
  return result;
}
