package kr.ace.web.analysis.repository;

import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.ace.web.analysis.model.HistorySummaryDO;
import java.util.Optional;
import kr.ace.web.analysis.entity.AnalysisRun;
import kr.ace.web.analysis.entity.DetectedPet;
import kr.ace.web.analysis.model.InferenceDO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisRepository {
    private static final String SELECT = """
            SELECT a.*, i.filename, i.source_path
            FROM analysis_run a JOIN input_media i ON i.id=a.input_id
            """;

    private static final RowMapper<AnalysisRun> RUN = (rs, n) -> new AnalysisRun(
            rs.getString("run_id"), rs.getLong("input_id"), rs.getString("filename"),
            rs.getString("source_path"), rs.getString("status"), rs.getString("error_message"),
            rs.getObject("cat_count", Integer.class), rs.getObject("dog_count", Integer.class),
            rs.getString("result_path"), rs.getString("engine"), rs.getObject("elapsed_ms", Long.class),
            rs.getObject("image_width", Integer.class), rs.getObject("image_height", Integer.class),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("variant"), rs.getString("round_id"));

    private final JdbcTemplate jdbc;

    public AnalysisRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(String runId, long inputId) {
        jdbc.update("INSERT INTO analysis_run(run_id,input_id,status) VALUES (?,?,'PENDING')", runId, inputId);
    }

    public void create(String runId, long inputId, String roundId, String variant) {
        jdbc.update("INSERT INTO analysis_run(run_id,input_id,round_id,variant,status) VALUES (?,?,?,?,'PENDING')",
                runId, inputId, roundId, variant);
    }

    public java.util.Map<String, Integer> statusCounts(String roundId) {
        return jdbc.query("SELECT status, COUNT(*) FROM analysis_run WHERE round_id=? GROUP BY status", rs -> {
            var result = new java.util.HashMap<String, Integer>();
            while (rs.next()) result.put(rs.getString(1), rs.getInt(2));
            return result;
        }, roundId);
    }

    public List<AnalysisRun> round(String roundId) {
        return jdbc.query(SELECT + " WHERE a.round_id=? ORDER BY a.input_id, a.variant", RUN, roundId);
    }

    public List<AnalysisRun> forInput(long inputId) {
        return jdbc.query(SELECT + " WHERE a.input_id=? ORDER BY a.created_at, a.variant", RUN, inputId);
    }

    public Optional<AnalysisRun> find(String runId) {
        return jdbc.query(SELECT + " WHERE a.run_id=?", RUN, runId).stream().findFirst();
    }

    private static final String LATEST_EVALUATION = """
            LEFT JOIN evaluation e ON e.run_id=a.run_id
              AND e.version=(SELECT MAX(v.version) FROM evaluation v WHERE v.run_id=a.run_id)
            """;

    public List<AnalysisRun> list(String filter, String filename, String model) {
        String condition = switch (filter) {
            case "matched" -> " AND e.is_match=TRUE";
            case "errors" -> " AND (e.is_match=FALSE OR e.breed_correct < e.breed_total)";
            case "failed" -> " AND a.status='FAILED'";
            default -> "";
        };
        var query = new StringBuilder(SELECT + LATEST_EVALUATION + " WHERE a.status<>'DISMISSED'" + condition);
        var parameters = new ArrayList<Object>();
        if (!filename.isBlank()) {
            query.append(" AND LOCATE(?, i.filename) > 0");
            parameters.add(filename);
        }
        if (!model.equals("all")) {
            query.append(" AND a.variant=?");
            parameters.add(model);
        }
        query.append(" ORDER BY a.created_at DESC, a.run_id LIMIT 200");
        return jdbc.query(query.toString(), RUN, parameters.toArray());
    }

    public HistorySummaryDO historySummary() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total, COUNT(e.run_id) AS evaluated,
                       COUNT(CASE WHEN e.is_match=TRUE THEN 1 END) AS matched,
                       AVG(e.cat_error) AS cat_mae, AVG(e.dog_error) AS dog_mae
                  FROM analysis_run a
                """ + LATEST_EVALUATION + " WHERE a.status<>'DISMISSED'",
                (rs, n) -> new HistorySummaryDO(rs.getLong("total"), rs.getLong("evaluated"),
                        rs.getLong("matched"), rs.getObject("cat_mae", Double.class),
                        rs.getObject("dog_mae", Double.class)));
    }

    public int dismissFailed(Collection<String> ids) {
        String parameters = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        // Preserve the round's three result slots. Only executions still in FAILED are eligible.
        return jdbc.update("UPDATE analysis_run SET status='DISMISSED' WHERE status='FAILED'"
                + " AND run_id IN (" + parameters + ")", ids.toArray());
    }

    public boolean lock(String runId) {
        return !jdbc.queryForList("SELECT run_id FROM analysis_run WHERE run_id=? FOR UPDATE", String.class, runId).isEmpty();
    }

    public void success(InferenceDO result, String metadata) {
        jdbc.update("""
                UPDATE analysis_run SET status='SUCCEEDED',error_message=NULL,cat_count=?,dog_count=?,
                result_path=?,engine=?,elapsed_ms=?,image_width=?,image_height=?,metadata_json=? WHERE run_id=?
                """, result.catCount(), result.dogCount(), result.resultPath(), result.engineVersion(),
                result.elapsedMs(), result.width(), result.height(), metadata, result.runId());
        for (var pet : result.objects()) {
            var box = pet.box();
            jdbc.update("""
                    INSERT INTO detected_pet(run_id,object_no,species,x1,y1,x2,y2,det_score,breed,breed_score,class_status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, result.runId(), pet.number(), pet.species(), box.get(0), box.get(1), box.get(2), box.get(3),
                    pet.detectionScore(), pet.breed(), pet.breedScore(), pet.classificationStatus());
        }
    }

    public void failure(String runId, String reason) {
        jdbc.update("""
                UPDATE analysis_run SET status='FAILED',error_message=? WHERE run_id=? AND status IN ('PENDING','FAILED')
                """, reason, runId);
    }

    public List<DetectedPet> pets(String runId) {
        return jdbc.query("""
                SELECT * FROM detected_pet WHERE run_id=? ORDER BY object_no
                """, (rs, n) -> new DetectedPet(rs.getInt("object_no"), rs.getString("species"),
                rs.getDouble("x1"), rs.getDouble("y1"), rs.getDouble("x2"), rs.getDouble("y2"),
                rs.getDouble("det_score"), rs.getString("breed"), rs.getObject("breed_score", Double.class),
                rs.getString("class_status")), runId);
    }

    /** Load the visible history page's detected animals with one query. */
    public Map<String, List<DetectedPet>> petsForRuns(Collection<String> runIds) {
        if (runIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(runIds.size(), "?"));
        Map<String, List<DetectedPet>> result = new LinkedHashMap<>();
        jdbc.query("SELECT * FROM detected_pet WHERE run_id IN (" + placeholders + ") ORDER BY run_id, object_no",
                rs -> {
                    result.computeIfAbsent(rs.getString("run_id"), ignored -> new java.util.ArrayList<>())
                            .add(new DetectedPet(rs.getInt("object_no"), rs.getString("species"),
                                    rs.getDouble("x1"), rs.getDouble("y1"), rs.getDouble("x2"), rs.getDouble("y2"),
                                    rs.getDouble("det_score"), rs.getString("breed"),
                                    rs.getObject("breed_score", Double.class), rs.getString("class_status")));
                }, runIds.toArray());
        return result;
    }
}
