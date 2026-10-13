/**
 * 歌词统一数据模型。
 *
 * 设计目标：把 LRC / 增强 LRC / YRC / QRC / KRC / TTML 等不同格式，
 * 归一化成同一套「行 + 字」结构，供歌词舞台组件消费。
 *
 * 注意：本模块与任何具体 UI 无关，也不依赖第三方库。
 */

/** 一个字（或一个词）及其时间轴，单位秒 */
export interface LyricWord {
  startTime: number;
  endTime: number;
  text: string;
}

/** 一行歌词 */
export interface LyricLine {
  startTime: number;
  endTime: number;
  /** 整行文本（已去掉时间标签） */
  text: string;
  /** 逐字时间轴；该行没有逐字信息时为 null */
  words: LyricWord[] | null;
  /** 翻译；无法可靠识别时为 null */
  translation?: string | null;
  /** 罗马音 / 注音；无法可靠识别时为 null */
  romaji?: string | null;
}

/** 识别出的歌词格式 */
export type LyricFormat =
  | 'lrc'          // 普通 LRC： [mm:ss.xx] 文本
  | 'lrc-enhanced' // 增强 LRC：  [mm:ss.xx] <mm:ss.xx>字 <mm:ss.xx>字
  | 'yrc'          // 网易云逐字：[start,dur](start,dur,0)字(start,dur,0)字
  | 'qrc'          // QQ 音乐：   [start,dur]字(start,dur,0)字(start,dur,0)
  | 'krc'          // 酷狗：      [start,dur]<off,dur,0>字<off,dur,0>字
  | 'ttml'         // Apple Music TTML（XML）
  | 'plain';       // 无任何时间戳的纯文本

/** 解析结果 */
export interface LyricsDoc {
  lines: LyricLine[];
  format: LyricFormat;
  /** 是否至少有一行带逐字时间轴 */
  hasWordTiming: boolean;
}

/** 解析选项 */
export interface LyricParseOptions {
  /** 文件名（仅用于日志/调试，解析本身按内容特征判断，不依赖扩展名） */
  fileName?: string;
  /** 歌曲时长（秒）：用于补全最后一行的结束时间 */
  duration?: number;
}
