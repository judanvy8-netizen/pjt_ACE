package kr.ace.web.media.repository;

import kr.ace.web.media.entity.InputMedia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MediaRepository {
    private final JdbcTemplate jdbc;

    public MediaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(InputMedia input) {
        return insert(input, null);
    }

    public long insert(InputMedia input, String datasetId) {
        jdbc.update("INSERT INTO input_media(run_id, filename, source_path, dataset_id) VALUES (?,?,?,?)",
                input.runId(), input.filename(), input.relativePath(), datasetId);
        return jdbc.queryForObject("SELECT id FROM input_media WHERE run_id=?", Long.class, input.runId());
    }
}
