package kr.ace.web.dataset.repository;

import java.util.List;
import java.util.Optional;
import kr.ace.web.dataset.entity.Dataset;
import kr.ace.web.dataset.entity.AnalysisRound;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DatasetRepository {
    private static final String DATASETS = """
            SELECT d.id,d.name,(SELECT COUNT(*) FROM input_media i WHERE i.dataset_id=d.id) AS photos
            FROM dataset d
            """;
    private static final RowMapper<Dataset> DATASET = (rs, i) ->
            new Dataset(rs.getString("id"), rs.getString("name"), rs.getInt("photos"));
    private static final String ROUNDS = """
            SELECT r.*,d.name FROM analysis_round r JOIN dataset d ON d.id=r.dataset_id
            """;
    private static final RowMapper<AnalysisRound> ROUND = (rs, i) -> new AnalysisRound(rs.getString("id"),
            rs.getString("dataset_id"), rs.getString("name"), rs.getTimestamp("created_at").toLocalDateTime());
    private final JdbcTemplate jdbc;

    public DatasetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(String id, String name) {
        jdbc.update("INSERT INTO dataset(id,name) VALUES (?,?)", id, name);
    }

    public List<Dataset> datasets() {
        return jdbc.query(DATASETS + " ORDER BY d.created_at DESC", DATASET);
    }

    public Optional<Dataset> dataset(String id) {
        return jdbc.query(DATASETS + " WHERE d.id=?", DATASET, id).stream().findFirst();
    }

    public void lock(String id) {
        jdbc.queryForObject("SELECT id FROM dataset WHERE id=? FOR UPDATE", String.class, id);
    }

    public List<Long> inputIds(String datasetId) {
        return jdbc.queryForList("SELECT id FROM input_media WHERE dataset_id=? ORDER BY id", Long.class, datasetId);
    }

    public List<String> filenames(String datasetId) {
        return jdbc.queryForList("SELECT filename FROM input_media WHERE dataset_id=? ORDER BY id", String.class, datasetId);
    }

    public List<AnalysisRound> rounds(String datasetId) {
        return jdbc.query(ROUNDS + " WHERE r.dataset_id=? ORDER BY r.created_at DESC", ROUND, datasetId);
    }

    public Optional<AnalysisRound> round(String id) {
        return jdbc.query(ROUNDS + " WHERE r.id=?", ROUND, id).stream().findFirst();
    }

    public void createRound(String id, String datasetId) {
        jdbc.update("INSERT INTO analysis_round(id,dataset_id) VALUES (?,?)", id, datasetId);
    }

    public String roundForRun(String runId) {
        return jdbc.query("SELECT round_id FROM analysis_run WHERE run_id=?", (rs, n) -> rs.getString(1), runId)
                .stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
}
