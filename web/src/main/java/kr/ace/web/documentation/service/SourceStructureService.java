package kr.ace.web.documentation.service;

import java.util.List;
import kr.ace.web.documentation.model.SourceStructureDO.Entry;
import kr.ace.web.documentation.model.SourceStructureDO.Group;
import org.springframework.stereotype.Service;

@Service
public class SourceStructureService {
    // Describe selected project files explicitly. The browser never receives directory access.
    private static final String JAVA_ROOT = "web/src/main/java/kr/ace/web/";
    private static final List<Group> JAVA = List.of(
            new Group("dataset", "데이터셋·분석 회차", JAVA_ROOT + "dataset/", List.of(
                    new Entry("controller", "DatasetController.java", "데이터셋, 회차, 사진 비교 요청 처리"),
                    new Entry("service", "DatasetService.java", "사진 등록, AI 실행 예약, 회차 진행 관리"),
                    new Entry("repository", "DatasetRepository.java", "데이터셋과 회차 조회·저장"),
                    new Entry("entity", "Dataset.java, AnalysisRound.java", "데이터셋과 분석 회차 정보"),
                    new Entry("model", "RoundDO.java", "회차와 사진 결과 전달")
            ), "datasets.html · dataset.html · round.html · round-photo.html"),
            new Group("analysis", "AI 호출·분석 이력", JAVA_ROOT + "analysis/", List.of(
                    new Entry("controller", "AnalysisController.java", "분석 이력, 상세, 실패 기록 삭제 요청"),
                    new Entry("service", "AnalysisService.java", "AI 추론 요청 및 분석 결과·평가 저장"),
                    new Entry("repository", "AnalysisRepository.java", "실행과 검출 결과 및 이력 집계"),
                    new Entry("entity", "AnalysisRun.java, DetectedPet.java", "실행과 검출 개체 정보"),
                    new Entry("model", "InferenceDO.java, HistorySummaryDO.java", "AI 응답과 이력 요약 데이터"),
                    new Entry("client", "AiClient.java", "Python 상태와 추론 REST API 호출")
            ), "history.html · detail.html"),
            new Group("truth", "정답 저장·조회", JAVA_ROOT + "truth/", List.of(
                    new Entry("service", "ReferenceTruthService.java", "파일명과 사진 내용으로 CSV 정답 연결"),
                    new Entry("repository", "TruthRepository.java", "검수 정답과 평가 조회·저장"),
                    new Entry("entity", "GroundTruth.java, GroundTruthPet.java, Evaluation.java", "정답·개체·평가 정보"),
                    new Entry("model", "ReferenceTruthDO.java", "CSV 정답과 확인 상태 전달")
            ), ""),
            new Group("review", "검수 저장", JAVA_ROOT + "review/", List.of(
                    new Entry("controller", "ReviewController.java", "단일 실행의 검수 요청 처리"),
                    new Entry("service", "ReviewService.java", "정답 버전과 성공 결과의 평가 저장")
            ), "detail.html · round-photo.html"),
            new Group("media", "원본·결과 이미지", JAVA_ROOT + "media/", List.of(
                    new Entry("controller", "MediaController.java", "원본과 결과 이미지 제공"),
                    new Entry("service", "MediaService.java", "사진 저장 및 저장 경로 확인"),
                    new Entry("repository", "MediaRepository.java", "입력 사진 정보 저장"),
                    new Entry("entity", "InputMedia.java", "입력 사진 데이터"),
                    new Entry("model", "UploadDO.java, PhotoNavigationDO.java", "업로드와 사진 이동 데이터")
            ), ""),
            new Group("benchmark", "기준 자료·성능 검증", JAVA_ROOT + "benchmark/", List.of(
                    new Entry("controller", "BenchmarkController.java", "홈, 버전 비교, 사진 지도와 검증표"),
                    new Entry("service", "BenchmarkService.java", "기준 CSV 결과와 버전별 지표 계산"),
                    new Entry("repository", "BenchmarkRepository.java, CsvTable.java", "기준 CSV와 이미지 읽기"),
                    new Entry("model", "BenchmarkDO.java", "성능 검증과 비교 화면 데이터")
            ), "home.html · versions.html · version-photo.html · benchmark.html · performance.html"),
            new Group("evaluation", "판정 규칙", JAVA_ROOT + "evaluation/", List.of(
                    new Entry("평가 규칙", "BreedRules.java", "품종 번역, 별칭, 지원 범위 확인"),
                    new Entry("정답·판정", "GroundTruthParser.java, Verdict.java", "개체 정답 해석과 개수·품종 비교")
            ), ""),
            new Group("design", "DB 설계 자료", JAVA_ROOT + "design/", List.of(
                    new Entry("controller", "DatabaseDesignController.java", "테이블 정의·ERD 화면 제공")
            ), "database-design.html"),
            new Group("environment", "서버 상태 조회", JAVA_ROOT + "environment/", List.of(
                    new Entry("controller", "ServerStatusController.java", "웹·AI·DB 상태 응답"),
                    new Entry("service", "ServerStatusService.java", "AI 상태 API와 MySQL 조회"),
                    new Entry("model", "ServerStatusDO.java", "서버별 상태와 안내 메시지")
            ), "layout.html"),
            new Group("documentation", "소스 구조·기술 안내", JAVA_ROOT + "documentation/", List.of(
                    new Entry("controller", "DocumentationController.java", "소스 구조와 기술 안내 화면 제공"),
                    new Entry("service", "SourceStructureService.java", "폴더별 파일 설명 목록 관리"),
                    new Entry("model", "SourceStructureDO.java", "파일 목록과 역할 전달")
            ), "source-structure.html · technology.html"));

    private static final List<Group> PYTHON = List.of(
            new Group("app", "AI 서버·분석 엔진", "app/", List.of(
                    new Entry("서버", "ai_server.py", "FastAPI 상태 확인과 추론 요청 처리"),
                    new Entry("main", "detection_engine.py", "개·고양이 검출과 마릿수 계산"),
                    new Entry("main-1", "imagenet_engine.py", "검출 이미지의 ImageNet 품종 후보 분류"),
                    new Entry("main-2", "pet_engine.py", "고양이 전용 품종 분류와 검출 보정"),
                    new Entry("main-3", "vit_engine.py", "ViT-B/16 견종 분류와 종류 보정")
            ), ""),
            new Group("source", "버전별 실행 파일", "source/", List.of(
                    new Entry("CLI", "main.py, main-1.py, main-2.py, main-3.py", "터미널에서 버전별 사진 분석 실행")
            ), ""),
            new Group("shared", "공통 이미지 처리", "shared/", List.of(
                    new Entry("공통 코드", "common.py", "이미지 읽기·표준화·저장과 모델 관리")
            ), ""));

    public List<Group> groups(boolean python) { return python ? PYTHON : JAVA; }
}
