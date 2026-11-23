package kr.ace.web.analysis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Python HTTP 응답을 받는 데이터 객체. JSON 이름을 명시해 DB 엔티티와 독립시킨다. */
public record InferenceDO(
        @JsonProperty("run_id") String runId,
        @JsonProperty("cat_count") int catCount,
        @JsonProperty("dog_count") int dogCount,
        @JsonProperty("result_path") String resultPath,
        int width, int height, List<Pet> objects,
        @JsonProperty("elapsed_ms") long elapsedMs,
        @JsonProperty("engine_version") String engineVersion,
        Map<String, String> models, Map<String, Object> settings) {

    public record Pet(
            @JsonProperty("object_no") int number,
            @JsonProperty("detected_species") String species,
            List<Double> box,
            @JsonProperty("det_confidence") double detectionScore,
            @JsonProperty("class_label") String breed,
            @JsonProperty("class_confidence") Double breedScore,
            @JsonProperty("species_match") String classificationStatus) {}
}
