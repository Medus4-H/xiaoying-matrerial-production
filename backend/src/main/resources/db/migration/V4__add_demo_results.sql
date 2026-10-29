-- Demo results reuse the licensed local cover photos; prior migrations stay unchanged.
UPDATE templates SET preset_result_key = 'results/template-1.jpg' WHERE id = 'demo-1';
UPDATE templates SET preset_result_key = 'results/template-2.jpg' WHERE id = 'demo-2';
UPDATE templates SET preset_result_key = 'results/template-3.jpg' WHERE id = 'demo-3';
UPDATE templates SET preset_result_key = 'results/template-4.jpg' WHERE id = 'demo-4';
UPDATE templates SET preset_result_key = 'results/template-5.jpg' WHERE id = 'demo-5';
UPDATE templates SET preset_result_key = 'results/template-6.jpg' WHERE id = 'demo-6';
