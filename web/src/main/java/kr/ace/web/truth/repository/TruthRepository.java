package kr.ace.web.truth.repository;

import java.util.List;
import java.util.Set;
import kr.ace.web.truth.entity.Evaluation;
import kr.ace.web.truth.entity.GroundTruth;
import kr.ace.web.truth.entity.GroundTruthPet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TruthRepository {
    private final JdbcTemplate jdbc;

    public TruthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int nextVersion(long inputId) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM ground_truth WHERE input_id=?",
                Integer.class, inputId);
    }

    public void lockInput(long inputId) {
        jdbc.queryForObject("SELECT id FROM input_media WHERE id=? FOR UPDATE", Long.class, inputId);
    }

    public java.util.Optional<GroundTruth> latest(long inputId) {
        return jdbc.query("""
                SELECT input_id,version,cat_count,dog_count FROM ground_truth
                WHERE input_id=? ORDER BY version DESC LIMIT 1
                """, (rs, n) -> new GroundTruth(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)),
                inputId).stream().findFirst();
    }

    public Set<Long> reviewedInputs(List<Long> inputIds) {
        if (inputIds.isEmpty()) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(inputIds.size(), "?"));
        String sql = "SELECT DISTINCT input_id FROM ground_truth WHERE input_id IN (" + placeholders + ")";
        return Set.copyOf(jdbc.queryForList(sql, Long.class, inputIds.toArray()));
    }

    public void save(GroundTruth truth) {
        jdbc.update("INSERT INTO ground_truth(input_id,version,cat_count,dog_count) VALUES (?,?,?,?)",
                truth.inputId(), truth.version(), truth.cats(), truth.dogs());
    }

    public void savePets(GroundTruth truth, List<GroundTruthPet> pets) {
        for (var pet : pets) {
            jdbc.update("""
                    INSERT INTO ground_truth_pet(input_id,version,object_no,species,breed,position_note)
                    VALUES (?,?,?,?,?,?)
                    """, truth.inputId(), truth.version(), pet.number(), pet.species(), pet.breed(), pet.position());
        }
    }

    public void evaluate(String runId, GroundTruth truth, kr.ace.web.evaluation.Verdict verdict) {
        jdbc.update("""
                INSERT INTO evaluation(run_id,input_id,version,cat_error,dog_error,is_match,
                    breed_correct,breed_total,cat_diff,dog_diff,breed_unsupported)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, runId, truth.inputId(), truth.version(), Math.abs(verdict.catDiff()), Math.abs(verdict.dogDiff()),
                verdict.catDiff() == 0 && verdict.dogDiff() == 0, verdict.correct(), verdict.total(),
                verdict.catDiff(), verdict.dogDiff(), verdict.unsupported());
    }

    private static Evaluation row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Evaluation(rs.getInt("version"), rs.getInt("cat_count"), rs.getInt("dog_count"),
                rs.getInt("cat_error"), rs.getInt("dog_error"), rs.getBoolean("is_match"),
                rs.getInt("breed_correct"), rs.getInt("breed_total"), rs.getInt("cat_diff"),
                rs.getInt("dog_diff"), rs.getInt("breed_unsupported"));
    }

    public java.util.Map<String, Evaluation> latestByRuns(List<String> ids) {
        if (ids.isEmpty()) return java.util.Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = """
                SELECT e.*, g.cat_count, g.dog_count FROM evaluation e
                JOIN ground_truth g ON g.input_id=e.input_id AND g.version=e.version
                WHERE e.version=(SELECT MAX(m.version) FROM evaluation m WHERE m.run_id=e.run_id)
                AND e.run_id IN (
                """ + placeholders + ")";
        return jdbc.query(sql, rs -> {
            var map = new java.util.HashMap<String, Evaluation>();
            while (rs.next()) map.put(rs.getString("run_id"), row(rs));
            return map;
        }, ids.toArray());
    }

    public List<Evaluation> history(String runId) {
        return jdbc.query("""
                SELECT e.*,g.cat_count,g.dog_count FROM evaluation e
                JOIN ground_truth g ON g.input_id=e.input_id AND g.version=e.version
                WHERE e.run_id=? ORDER BY e.version DESC
                """, (rs, n) -> row(rs), runId);
    }

    public List<GroundTruthPet> pets(long inputId, int version) {
        return jdbc.query("""
                SELECT * FROM ground_truth_pet WHERE input_id=? AND version=? ORDER BY object_no
                """, (rs, n) -> new GroundTruthPet(rs.getInt("object_no"), rs.getString("species"),
                rs.getString("breed"), rs.getString("position_note")), inputId, version);
    }
}
