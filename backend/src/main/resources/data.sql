-- 内置示例歌：只在「全新数据库」（songs 表完全为空）时一次性插入。
-- 用单条 INSERT 整体判空：一旦表里已有任何歌曲（包括管理员之后导入的），
-- 就绝不再补插，从而避免「删掉某首内置歌后重启又被补回」的复活问题。
INSERT INTO songs (id, title, artist, album, duration, cover_url, audio_url, lyric_url)
SELECT * FROM (
    SELECT 1 AS id, 'Echoes' AS title, 'Pink Floyd' AS artist, 'Meddle' AS album, 1412 AS duration,
           '/uploads/covers/Pink%20Floyd%20-%20Echoes.jpg' AS cover_url,
           '/uploads/audio/Pink%20Floyd%20-%20Echoes.flac' AS audio_url,
           NULL AS lyric_url
    UNION ALL
    SELECT 2, 'Goldener Schnitt', 'Sacco', 'Single', 52,
           '/uploads/covers/Sacco%20-%20Goldener%20Schnitt.jpg',
           '/uploads/audio/Sacco%20-%20Goldener%20Schnitt.wav',
           NULL
    UNION ALL
    SELECT 3, 'Highscore', '哈基米', 'Single', 156,
           '/uploads/covers/%E3%80%90%E5%93%88%E5%9F%BA%E7%B1%B3%E3%80%91Highscore.jpg',
           '/uploads/audio/%E3%80%90%E5%93%88%E5%9F%BA%E7%B1%B3%E3%80%91Highscore.wav',
           NULL
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM songs);
