package kr.ace.web.review.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoNoteService {
    private static final Set<String> SCOPES = Set.of("main", "main-1", "main-2", "main-3", "overall");
    private final JdbcTemplate jdbc;
    public PhotoNoteService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, String> forPhoto(long inputId) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String scope : SCOPES) result.put(scope, "");
        jdbc.query("SELECT scope, note_text FROM photo_note WHERE input_id = ?",
            rs -> { result.put(rs.getString("scope"), rs.getString("note_text")); }, inputId);
        return result;
    }

    @Transactional
    public void save(long inputId, Map<String, String> notes) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM input_media WHERE id=?", Integer.class, inputId);
        if (exists == null || exists == 0) throw new IllegalArgumentException("사진을 찾을 수 없습니다.");
        for (var item : notes.entrySet()) {
            if (!SCOPES.contains(item.getKey())) throw new IllegalArgumentException("지원하지 않는 비고 항목입니다.");
            String value = item.getValue() == null ? "" : item.getValue().trim();
            if (value.length() > 1000) throw new IllegalArgumentException("비고는 항목별 1,000자 이하여야 합니다.");
            jdbc.update("INSERT INTO photo_note(input_id, scope, note_text) VALUES(?,?,?) " +
                 "ON DUPLICATE KEY UPDATE note_text=VALUES(note_text), updated_at=CURRENT_TIMESTAMP(6)",
                 inputId, item.getKey(), value);
        }
    }
}
