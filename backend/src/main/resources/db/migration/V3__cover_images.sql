-- 模板封面由占位 SVG 换成真实照片：键名不变，仅切换扩展名。
UPDATE templates SET cover_key = REPLACE(cover_key, '.svg', '.jpg') WHERE cover_key LIKE 'covers/%.svg';
