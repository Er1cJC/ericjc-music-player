-- 内置示例歌：仅在对应 id 不存在时插入。
-- 条件插入（而非 INSERT IGNORE），保证管理员删除歌曲后重启不会复活。
INSERT INTO songs (id, title, artist, album, duration, cover_url, audio_url, lyric_url)
SELECT 1, 'Echoes', 'Pink Floyd', 'Meddle', 1412,
 '/uploads/covers/Pink%20Floyd%20-%20Echoes.jpg',
 '/uploads/audio/Pink%20Floyd%20-%20Echoes.flac',
 NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM songs WHERE id = 1);

INSERT INTO songs (id, title, artist, album, duration, cover_url, audio_url, lyric_url)
SELECT 2, 'Goldener Schnitt', 'Sacco', 'Single', 52,
 '/uploads/covers/Sacco%20-%20Goldener%20Schnitt.jpg',
 '/uploads/audio/Sacco%20-%20Goldener%20Schnitt.wav',
 NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM songs WHERE id = 2);

INSERT INTO songs (id, title, artist, album, duration, cover_url, audio_url, lyric_url)
SELECT 3, 'Highscore', '哈基米', 'Single', 156,
 '/uploads/covers/%E3%80%90%E5%93%88%E5%9F%BA%E7%B1%B3%E3%80%91Highscore.jpg',
 '/uploads/audio/%E3%80%90%E5%93%88%E5%9F%BA%E7%B1%B3%E3%80%91Highscore.wav',
 NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM songs WHERE id = 3);
