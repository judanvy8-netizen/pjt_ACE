-- Workbench에서 검사할 회차의 UUID로 바꾼다.
SET @round_id = '회차 UUID';
SELECT i.filename, a.variant, a.cat_count, a.dog_count
FROM analysis_run a JOIN input_media i ON i.id = a.input_id
WHERE a.round_id = @round_id ORDER BY i.filename, a.variant;

SELECT i.filename, a.variant, d.object_no, d.species, d.x1, d.y1, d.x2, d.y2,
       d.det_score, d.breed, d.breed_score, d.class_status, a.image_width, a.image_height
FROM analysis_run a JOIN input_media i ON i.id = a.input_id
JOIN detected_pet d ON d.run_id = a.run_id
WHERE a.round_id = @round_id ORDER BY i.filename, a.variant, d.object_no;
