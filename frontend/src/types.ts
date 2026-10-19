/**
 * 全局共享类型。
 */

export type Song = {
  id: number;
  title: string;
  artist: string;
  album: string;
  duration: number;
  coverUrl: string;
  audioUrl: string;
  lyricUrl: string | null;
  /** 封面缺失时的首字母占位文本 */
  cover: string;
  /** 封面底色（按歌曲 id 派生） */
  color: string;
};

export type SongResponse = {
  id: number;
  title: string;
  artist: string;
  album: string;
  duration: number;
  coverUrl: string;
  audioUrl: string;
  lyricUrl: string | null;
  liked: boolean;
};

export type ViewKey = 'discover' | 'search' | 'library' | 'playlist' | 'admin' | 'profile';

export type AdminSortKey = 'imported' | 'title' | 'artist' | 'album';

export type PlaylistSummary = { id: number; name: string; description: string | null; songCount: number; createdAt: string };
export type PlaylistDetail = { id: number; name: string; description: string | null; songs: Song[] };
export type PlaylistDetailResponse = Omit<PlaylistDetail, 'songs'> & { songs: SongResponse[] };

export type AuthUser = { userId: number; username: string; role: string; nickname: string | null; avatarUrl: string | null; bio: string | null };

export type AdminUser = { id: number; username: string; role: string; createdAt: string };

export type BatchSongItem = {
  file: File;
  title: string;
  artist: string;
  album: string;
  duration: number;
  cover: File | null;
  coverPreview: string;
  metadataMessage: string;
  status: 'pending' | 'uploading' | 'done' | 'failed';
  error: string;
};
