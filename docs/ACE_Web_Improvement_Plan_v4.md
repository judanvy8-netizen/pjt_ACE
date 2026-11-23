# ACE 웹서비스 개선 작업계획서 v4

- 작성일: 2026년 11월 14일
- 대상: `pjt_ACE_web3` (Spring Boot 4.x · JDK 17 · Gradle · MySQL 8.4 · Python 3.11 · FastAPI, IntelliJ)
- 상태: 계획(P) · 구현 전
- 버전 메모: v2·v3는 별도 문서로 발행하지 않았다. 초판(v1) 이후 검토에서 나온 변경과 정정을 모두 이 문서에 통합했다. 변경 내역은 부록 B에 있다.
- 진행 방식: 작업 카드(T-xx) 1개 = 커밋 1개. 카드마다 변경 파일, 작업, 완료 기준(AC)을 적었다.

---

## 1. 목표·범위·불변 조건

### 목표

1. main, main-1, main-2가 **같은 입력 기준**을 쓰고 결과 이미지가 같은 크기로 저장된다.
2. 팀원이 클론해도 같은 화면이 나온다(기준 자료가 git에 포함).
3. 40장 회차 화면이 빠르게 열린다.
4. 사진별 비교 화면이 **개수 판정**과 **품종 판정**을 구분하고 오류 유형(오탐·미검출 등)을 보여준다.
5. 모든 변경이 골든(기준 결과)과 테스트로 검증된다.

### 범위

| 구분 | 내용 |
| --- | --- |
| 포함 | 표준 입력 도입, 기준 자료 분리, 쿼리 최적화, AI 서버 예외 분리, 품종 평가와 사진별 비교 문구, 평가 지표·홀드아웃, 구조 정비(레이어·의존·테스트·Flyway), 운영(장애 대응·일괄 재시도·IntelliJ·README) |
| 제외 | 모델 교체·재학습, 인증(Spring Security), 배포 환경, 화면 디자인 개편 |

### 불변 조건

1. 표준 입력 도입 후에도 **main-1·main-2의 마릿수와 품종 결과는 제출본(legacy)과 같다.** 제출본 기준 마릿수 일치는 main 28 · main-1 28 · main-2 31 / 40이다(원본 해상도 입력).
2. **main은 입력이 바뀌므로** 표준 입력 기준으로 다시 측정해 새 기준선으로 확정한다.
3. 이후 모든 카드는 새 골든과 비교해 합격 여부를 판정한다(5.3).

---

## 2. 확정된 결정 사항

| ID | 내용 | 결정 |
| --- | --- | --- |
| D1 | 품종 평가 | **구현**(이전의 "제거" 철회) |
| D2 | DB 테스트 | Docker가 있으면 Testcontainers MySQL 8.4, 없으면 DB 불필요 단위 테스트만 |
| D3 | 기준 결과 폴더 | `data/baseline/` (`legacy-original-res`, `main`, `main-1`, `main-2`) |
| D4 | 좌표 기준 | **표준 이미지 좌표**(이전의 "원본 좌표"에서 변경) |
| D5 | 홀드아웃 | 20장 이상, 파일명 `hold_001.jpg` 형식 |
| D6 | Flyway 권한 | `ace_app`에 ALTER, DROP, INDEX, DELETE 추가 허용 |
| D7 | 기존 DB 보존 | **불필요**. DROP 후 재생성 |
| D8 | 마이그레이션 전용 계정 | 분리하지 않음(로컬 개발) |
| D9 | 기존 박스 환산 | 해당 없음(DB를 초기화하므로 레거시 행이 없음) |
| D10 | 동일 해상도의 의미 | **(a) 긴 변 기준 동일**, 긴 변 640px |
| D11 | 병합(2마리→1마리) 용어 | "개수 부족"으로 묶음(미검출과 구분하려면 정답 박스 필요) |
| D12 | 미지원 품종 | "불일치 + 사유(미지원)" 병기, 정확도는 전체 기준과 지원 품종 기준 둘 다 집계 |
| D13 | 정답 박스(2단계) | 보류(T-36 선택 카드) |
| D14 | `data/images` 파일 변환 | **기본: 변환하지 않음**(원본 입력 유지, 표준화는 엔진 내부). 변환을 원하면 5.1의 게이트 G1을 통과해야 함 |

D14의 이유: main-1·main-2는 검출은 표준 이미지에서 하지만 **품종 분류용 크롭은 원본에서 자릅니다**(`crop_from_original`, `app/pet_engine.py` 100행·378행, `app/imagenet_engine.py` 35행·163행). 입력 파일을 640으로 줄여 두면 분류 크롭 해상도가 크게 떨어져 품종 결과가 달라질 수 있습니다. 표준화는 검출 입력과 결과 이미지에만 적용하면 "같은 기준의 결과 이미지"라는 목적을 모두 달성합니다.

---

## 3. 작업 일람

크기: S = 반나절 이내, M = 하루 내외, L = 이틀 이상.

| 단계 | 카드 | 이름 | 크기 | 선행 | 검토 번호 |
| --- | --- | --- | --- | --- | --- |
| P0 | T-00A | 제출본 보존 | S | - | |
| P0 | T-00B | 표준 입력 도입 | M | T-00A | 3 |
| P0 | T-00C | 새 기준선 생성(DB 초기화·CLI·웹 등록) | M | T-00B | |
| P0 | T-00D | 기준 자료 분리 | S | T-00C | 1 |
| P0 | T-00E | 골든 확정 | S | T-00C | |
| P1 | T-01 | `AiClient` 추출·레이어 정리 | M | T-00E | 구조 |
| P1 | T-02 | 의존 방향 정리 | M | T-01 | 구조 |
| P1 | T-03 | 테스트 기반 구축 | M | T-01 | 구조 |
| P2 | T-20 | Flyway 도입(V1 = 최종 스키마) | M | T-03 | 구조 |
| P2 | T-21 | 품종 평가 데이터 모델 | M | T-20 | 6 |
| P2 | T-34 | 정답 품종 파싱·저장 | M | T-21 | 5 |
| P2 | T-35 | 품종 표기 정규화·동의어·미지원 | M | T-34 | 5 |
| P2 | T-33 | `Verdict`와 사진별 비교 문구 | L | T-35 | 5 |
| P3 | T-30 | 쿼리 최적화·진행 표시 | M | T-20 | 2 |
| P3 | T-32 | AI 서버 예외 분리 | S | T-03 | 4 |
| P4 | T-40 | 홀드아웃 데이터 준비 | S(수집) | - | 5 |
| P4 | T-41 | 평가 지표 집계 | M | T-33 | 5 |
| P4 | T-42 | 홀드아웃 평가 흐름 | M | T-40, T-41 | 5 |
| P4 | T-43 | 변경 요인 분리(ablation, 선택) | M | T-32 | 5 |
| P4 | T-36 | 정답 박스·IoU 매칭(선택) | L | T-42 | 5 |
| P5 | T-50 | AI 서버 장애 대응 | S | T-01 | 운영 |
| P5 | T-51 | 실패 일괄 재시도 | S | T-50 | 운영 |
| P5 | T-52 | IntelliJ 실행 구성·README·문서 갱신 | S | 전체 | 운영 |

### 검토 번호 대응

| 번호 | 주제 | 카드 |
| --- | --- | --- |
| 1 | 비교 기준 자료가 git에서 빠짐 | T-00D |
| 2 | 라운드 화면 쿼리 과다·전체 reload | T-30 |
| 3 | 버전별 좌표계 불일치 | T-00B (표준 입력으로 근본 해결) |
| 4 | AI 서버 예외 분류 혼재 | T-32 |
| 5 | 평가 방법의 한계 | T-33~T-36, T-40~T-43 |
| 6 | 쓰이지 않는 스키마·코드 | T-21 (제거가 아니라 품종 평가로 활용) |

---

## 4. 공통 규칙

1. 단계(P0~P5)마다 브랜치를 만든다. 예: `feature/p0-baseline`.
2. 카드 하나를 끝낼 때마다 빌드와 테스트를 통과시킨 뒤 커밋한다.
3. 빌드: `web`에서 `.\gradlew.bat clean test bootJar`. Python: 프로젝트 루트에서 `.\.venv\Scripts\python.exe -m pytest`.
4. 단계가 끝날 때마다 골든 합격 판정(5.3)을 수행한다. 불합격이면 그 단계 커밋을 되돌려 원인을 찾는다.
5. 복구는 `git revert`다. 스키마 변경은 새 마이그레이션으로만 되돌린다.
6. `data/images`(원본)는 읽기 전용으로 취급한다. 어떤 스크립트도 이 폴더의 파일을 수정하지 않는다.

---

## 5. 확정 설계 규칙

### 5.1 표준 입력과 좌표

**표준 입력 정의**

| 항목 | 규칙 |
| --- | --- |
| 정의 | EXIF 방향 반영 디코딩 → 긴 변이 640 초과면 `INTER_AREA`로 비율 유지 축소 → 이하이면 그대로(확대 없음). 이미 표준이면 변하지 않는 멱등 함수 |
| 상수 | `STANDARD_LONG_SIDE = 640` (`shared/common.py` 한 곳). 바꾸면 모든 버전의 기준선을 다시 측정 |
| "동일 해상도"의 의미 | 같은 사진은 세 버전에서 같은 크기. 사진 사이에는 비율에 따라 크기가 다르다(모든 사진을 같은 가로×세로로 만들면 왜곡·잘림·패딩이 생겨 채택하지 않음) |
| 검출 입력 | main, main-1, main-2 모두 표준 이미지 |
| 분류 입력(main-1·2) | 표준 이미지에서 찾은 박스를 원본 좌표로 환산해 **원본에서 크롭**(현행 유지) |
| 결과 이미지 | 표준 이미지 위에 표시 → 같은 사진이면 세 버전의 `marked.jpg` 크기가 같다 |
| 구현 동등성 | 현행 `resize_for_detection`(`scale = min(1.0, 640 / 긴 변)`, `round` 크기, `INTER_AREA`, `scale == 1.0`이면 복사)과 동일하게 구현해 main-1·2의 결과가 변하지 않게 한다 |

**좌표 저장 규칙 (표준 이미지 좌표)**

| 항목 | 규칙 |
| --- | --- |
| 기준 이미지 | 표준 이미지 |
| 원점·형식 | 좌상단 (0,0), `x1,y1,x2,y2` 픽셀, x는 오른쪽·y는 아래로 증가 |
| 범위·검증 | `0 ≤ x ≤ w`, `0 ≤ y ≤ h`로 클램프, `x1 < x2`, `y1 < y2` 확인 |
| 정밀도 | 소수 둘째 자리까지 반올림, `DOUBLE`로 저장(정수화하지 않음) |
| `image_width/height` | 표준 이미지 크기(= `marked.jpg` 크기) |
| `metadata_json.settings` | `coord_space="standard"`, `standard_long_side=640`, `source_width`, `source_height` |
| 원본 환산 | 축별 비율(`source / standard`)로 환산 |
| 교차 비교 | 세 버전이 같은 좌표계라 정규화 없이 IoU 비교 가능 |

**게이트 G1 (D14에서 파일 변환을 선택할 때만)**

1. 변환본은 같은 함수로 생성하고, 파일명(`.jpg`)과 JPEG 품질 95를 유지하며, 원본은 `data/raw/images`로 옮겨 불변으로 둔다. `data/images/MANIFEST.csv`에 원본·변환본 SHA-256, 원본·변환본 크기를 기록한다.
2. 변환본을 입력으로 main-1·2를 실행해 원본 입력 결과와 비교한다. **품종 일치와 마릿수가 달라지면 변환본을 평가 입력으로 쓰지 않는다**(크롭 해상도 저하).
3. 통과하더라도 정답 SHA 매칭은 원본·변환본 SHA를 모두 허용한다.

### 5.2 DB 초기화·마이그레이션 정책

| 상황 | 정책 |
| --- | --- |
| 빈 DB(새 PC) | 앱 기동 시 Flyway `V1`이 자동 적용 |
| 개발 DB 초기화 | `db/reset_database.sql` 실행 + `ACE_STORAGE` 비우기를 **한 세트**로 수행. 하나만 지우지 않는다(고아 파일 또는 파일 없는 DB 행 방지) |
| 기존 DB 보존 | 해당 없음(D7). 이번 전환에서는 전원 1회 초기화 |

규칙

1. `V1`에 최종 스키마(5.5)를 담는다. `baseline-on-migrate`는 사용하지 않는다.
2. 적용된 마이그레이션 파일은 수정하지 않고 새 번호로 추가한다. 한 파일에는 한 목적만 담고, 번호는 이 계획서에서 정한 대로 쓴다.
3. MySQL DDL은 롤백되지 않는다. 보존할 데이터가 있는 DB에 마이그레이션을 적용하기 전에는 `mysqldump`로 백업한다.
4. `spring.flyway.validate-on-migrate=true`, `clean-disabled=true`를 유지한다.
5. 테스트는 별도 DB(Testcontainers)만 사용하고 `ace_web`에 접속하지 않는다.
6. 계정 권한: `GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON ace_web.* TO 'ace_app'@'localhost';`

### 5.3 회귀 기준과 합격 판정

**골든 파일** (`docs/golden/`, T-00E에서 생성)

| 파일 | 내용 |
| --- | --- |
| `round_counts_before.csv` | 사진 40장 × 3버전 = 120행의 고양이·개 마릿수 |
| `golden_detections.csv` | 검출 단위: 종, 박스, 검출 점수, 품종, 품종 점수, `class_status`, `image_width/height` |
| `README.md` | 회차 ID, 실행 일시, 모델 해시, 소요 시간, 화면 응답 시간, 쿼리 수, 노이즈 측정 결과 |
| `pip_freeze.txt` | 라이브러리 버전 |
| 화면 스냅샷 | `/rounds/{id}` 점수표와 `/versions` 요약(텍스트) |

```sql
-- golden_detections.csv
SELECT i.filename, a.variant, d.object_no, d.species,
       d.x1, d.y1, d.x2, d.y2, d.det_score, d.breed, d.breed_score, d.class_status,
       a.image_width, a.image_height
FROM detected_pet d
JOIN analysis_run a ON a.run_id = d.run_id
JOIN input_media i ON i.id = a.input_id
WHERE a.round_id = '<회차 ID>'
ORDER BY i.filename, a.variant, d.object_no;
```

**노이즈 측정:** 같은 코드로 회차를 2번 실행해 두 결과를 비교한다. 정수와 문자열은 완전히 같아야 하고, 실수는 관측된 최대 차이를 허용 오차로 쓴다(차이가 없으면 `1e-6`).

**카드 유형별 합격표**

| 유형 | 카드 | 마릿수 | 종·점수·상태 | 박스 | width/height |
| --- | --- | --- | --- | --- | --- |
| 구조·성능·운영 | T-01, 02, 03, 20, 30, 32, 50~52 | 동일 | 동일 | 동일 | 동일 |
| 데이터 모델·평가 | T-21, 33, 34, 35, 41, 42, 43 | 동일 | 동일 | 동일 | 동일(새 컬럼·표시만 추가) |

골든은 덮어쓰지 않는다. 의도된 변경이 생기면 새 골든을 `after_<단계>`로 만들고 사유를 `docs/golden/README.md`에 적는다.
골든을 만드는 시점은 표준화와 DB 초기화 **이후**(T-00E)다. "변경 전" 기준은 제출본 CSV(`legacy-original-res`)가 대신한다.

### 5.4 판정 규칙 (사진별 비교 문구)

판정은 **개수**와 **품종** 두 축으로 나눈다.

| 축 | 대상 | 내용 |
| --- | --- | --- |
| 개수 | main, main-1, main-2 | 고양이/개 마릿수 비교 |
| 품종 | main-1, main-2 (main은 "해당 없음: 개수만 집계") | 정답 품종과 예측 품종 비교 |

**개수 판정** (정답 T=(Tc, Td), 예측 P=(Pc, Pd), 차이 dc = Pc−Tc, dd = Pd−Td). 위에서부터 순서대로 판정한다.

| 문구 | 규칙 | 사용 용어 | 기존 CSV 용어 |
| --- | --- | --- | --- |
| 일치 | dc = 0 이고 dd = 0 | - | - |
| 다른 동물 오검출 | T가 0/0이고 P가 1 이상 | 오탐 | 다른 동물 오검출 |
| 종 오판(개→고양이) | dc > 0 이고 dd < 0 | 오탐 | 검출 종류 오류 |
| 종 오판(고양이→개) | dc < 0 이고 dd > 0 | 오탐 | 검출 종류 오류 |
| 개수 부족 | dc ≤ 0 이고 dd ≤ 0 | 미검출, 병합(2→1) | 미검출 |
| 개수 초과 | dc ≥ 0 이고 dd ≥ 0 | 오탐 | 오검출 |

- 문구와 함께 차이값(예: "개 +1")을 병기한다.
- 병합과 미검출은 개수만으로 구분할 수 없어 "개수 부족"으로 묶는다(D11). 구분하려면 정답 박스가 필요하다(T-36).
- 라벨은 `cat_diff`, `dog_diff`와 정답 총수로 계산하며 저장하지 않는다.

**품종 판정**

1. 평가 대상은 `ground_truth.csv`의 `종류` 열에서 파싱한 정답 객체이다. `해당 없음` 사진은 품종 평가에서 제외한다.
2. 종별로 정답 품종 목록과 예측 품종 목록(검출 종이 같은 객체만)을 **순서 무관(다중집합)**으로 비교해 겹치는 개수를 `breed_correct`로 한다.
3. 표기는 공백·대소문자를 정규화하고, 동의어 표(`data/breed_aliases.csv`)와 main-1용 ImageNet→한글 매핑표(`data/breed_labels_imagenet_ko.csv`)를 적용한다.
4. 판정: 전체 일치(`correct = total`이고 예측 객체 수 = `total`) / 일부 일치 / 불일치 / 평가 제외.
5. 정답 품종이 그 버전의 라벨 집합에 없으면 사유를 "미지원"으로 병기하고(D12) `breed_unsupported`에 센다. 정확도는 **전체 기준**과 **지원 품종 기준**을 모두 집계한다.
6. 한계: 다중집합이라 위치를 보지 않는다(왼쪽·오른쪽 품종이 바뀌어도 일치로 본다).

**캡션 규격 (이미지 아래)**

| 버전 | 표시 |
| --- | --- |
| main | `개수 {라벨} (정답 {Tc}/{Td} → 예측 {Pc}/{Pd})` · `품종: 해당 없음` |
| main-1, main-2 | `개수 {라벨} (정답 → 예측, 차이)` · `품종 {전체 일치 / 일부 일치 a/b / 불일치 0/b / 평가 제외}` + (미지원 n) |

**예시 (제출본 결과로 계산한 값)**

| 사진 | main | main-1 | main-2 |
| --- | --- | --- | --- |
| img_001 (정답 개 1: 퍼그) | 개수 일치 | 개수 일치 · 품종 일치(매핑 후) | 개수 일치 · 품종 일치 |
| img_022 (정답 개 2: 골든 리트리버, 푸들) | 개수 초과(고양이 +1) | 개수 초과(고양이 +1) · 품종 불일치 | 종 오판(개→고양이) · 품종 불일치 |
| img_028 (정답 고양이 2: 벵갈, 메인쿤) | 개수 일치 | 개수 초과(개 +1) · 품종 불일치(미지원 2) | 개수 일치 · 품종 2/2 일치 |

"main-2 수정항목"이 보정 내역 표시를 뜻하면, 엔진이 보정 내역(예: 넓은 박스 분리 횟수)을 메타데이터로 반환하게 하는 선택 작업을 T-33에 추가한다.

### 5.5 최종 스키마(V1)에서 달라지는 부분

테이블은 8개를 유지한다.

| 테이블 | 변경 |
| --- | --- |
| `ground_truth_pet` | 유지. 컬럼은 `input_id, version, object_no, species, breed, position_note`만 사용. `box_no, outcome, supported` 제거 |
| `evaluation` | `cat_error, dog_error, is_match, breed_correct, breed_total` 유지. `cat_diff INT NOT NULL`, `dog_diff INT NOT NULL`(예측 − 정답), `breed_unsupported INT NOT NULL DEFAULT 0` 추가 |
| `analysis_run` | 컬럼 변화 없음. `image_width/height`의 의미는 표준 이미지 크기. `metadata_json.settings` 키는 5.1 참고 |
| 나머지 | 변화 없음 |

---

## 6. 작업 카드

### P0. 기준선 재설정

#### T-00A 제출본 보존

- 목적: 원본 해상도 입력으로 만든 제출본을 잃지 않는다. CLI가 `outputs/main*`에 덮어쓰기 때문이다.
- 작업
  1. `git status`가 깨끗한 상태에서 `git tag before-standardization`
  2. `outputs/main`, `outputs/main-1`, `outputs/main-2` → `data/baseline/legacy-original-res/` 로 복사(CSV와 이미지 모두)
  3. `docs/golden/pip_freeze.txt`, `java -version` 출력, 제출본 파일 해시 목록 저장
  4. `data/images`는 건드리지 않는다(원본 유지, D14)
- AC: legacy 폴더에 세 버전의 CSV·이미지가 모두 있고, 태그가 생성되었다.

#### T-00B 표준 입력 도입 (검토 3번)

- 변경 파일
  - `shared/common.py`: `STANDARD_LONG_SIDE`, `standard_size(width, height)`, `standardize_image(image)`
  - `app/detection_engine.py`(main): `read_image` 직후 표준화, 표준 이미지로 검출·`plot()`
  - `app/imagenet_engine.py`, `app/pet_engine.py`: `resize_for_detection`과 `MAX_SIDE`를 공용 함수로 교체(동작 동일), 분류 크롭은 원본에서 유지
  - `app/ai_server.py`: 응답의 `width/height`는 표준 이미지 크기, `settings`에 `coord_space`, `source_width`, `source_height` 추가
  - `source/main.py`, `source/main-1.py`, `source/main-2.py`: `--details-json`의 박스도 표준 좌표
  - `tests/`: `standardize_image` 단위 테스트
- 작업
  1. 5.1의 정의대로 구현한다. 이미 640 이하인 사진은 그대로 통과한다.
  2. 박스 후처리(`split_wide_cat_box` 등 임계값)는 표준 좌표 기준 그대로 둔다(640 기준으로 튜닝된 값이므로 의미가 유지됨).
  3. EXIF 방향 6 샘플 1장으로 `read_image` 결과 크기와 브라우저 표시 방향이 일치하는지 확인한다.
- 검증 (Pass 1)
  - main-1·main-2를 원본 입력(`data/images`)으로 실행해 `pet_counts.csv`와 `pet_classifications.csv`가 **제출본과 동일**한지 확인한다.
  - main은 결과가 달라질 수 있으므로 달라진 사진 목록을 기록한다.
- AC
  - 단위 테스트: 640 초과는 비율 유지 축소, 이하는 그대로, 두 번 적용해도 같은 결과.
  - 같은 사진의 세 버전 `marked.jpg` 크기가 같다.
  - Pass 1에서 main-1·2가 제출본과 동일하다.

#### T-00C 새 기준선 생성

- 작업
  1. `db/reset_database.sql`로 DB를 DROP/CREATE하고 `ACE_STORAGE`를 비운다(현행 `schema.sql` 사용. Flyway는 T-20에서 다시 초기화).
  2. CLI 3개(`main.py`, `main-1.py`, `main-2.py`)를 실행해 `outputs/`에 새 결과를 만든다.
  3. 새 결과와 legacy를 비교한다. main-1·2는 동일, main은 차이 사진을 기록하고 새 마릿수를 확정한다.
  4. 웹에서 40장을 등록하고 3버전을 분석한 뒤 정답 CSV 자동 적용을 확인한다.
  5. `scripts/compare_web.py`로 CLI와 웹의 일치를 확인한다(필요하면 회차 흐름에 맞게 조정).
  6. 결과를 `docs/baseline_report.md`에 기록하고, README의 수치 문구를 "main N/40 (표준 입력)"으로 개정한다.
- AC: 웹 120건 성공, CLI와 웹 결과 일치, 새 main 마릿수 확정.

#### T-00D 기준 자료 분리 (검토 1번)

- 변경 파일
  - 새 기준선: `outputs/main*` → `data/baseline/main`, `main-1`, `main-2`
  - `web/.../benchmark/repository/BenchmarkRepository.java`: `outputs/` 경로를 설정값으로 교체(`startsWith(root)`·`toRealPath` 경로 검사는 유지)
  - `web/.../benchmark/service/BenchmarkService.java`: `"outputs/" + version.id() + ...` 두 곳(`predictions`, `classification`)
  - `application.properties`: `ace.baseline-dir=${ACE_BASELINE_DIR:data/baseline}`
  - `.gitignore`: `outputs/*`는 유지, `data/baseline/`은 추적. `shared/models/*.pt` 무시 후 예외 처리하는 세 줄은 정리
  - `scripts/compare_web.py`, `README.md`, `docs/*`
- 작업: CLI의 기본 출력은 `outputs/<버전>` 그대로 둔다. README에 "기준 결과는 `data/baseline`, 새 실행 결과는 `outputs`"를 명시한다.
- AC
  - `outputs/`를 비워도 `/versions`가 같은 결과를 보인다.
  - CLI를 실행해도 `git status`에서 `data/baseline`이 변하지 않는다.
  - 새로 클론한 폴더에서 같은 결과가 나온다.

#### T-00E 골든 확정

- 작업
  1. T-00C의 웹 회차에서 `round_counts_before.csv`와 `golden_detections.csv`를 저장한다(쿼리는 5.3).
  2. 같은 코드로 회차를 한 번 더 실행해 노이즈를 측정하고 허용 오차를 기록한다.
  3. `/rounds/{id}`와 `/versions` 화면 스냅샷을 저장한다.
  4. `scripts/golden_queries.sql`로 쿼리를 고정하고 `scripts/check_golden.py`가 두 CSV를 비교하게 확장한다(종료 코드 0 = 합격).
  5. 쿼리 수와 화면 응답 시간을 기록한다(T-30 비교용). `logging.level.org.springframework.jdbc.core.JdbcTemplate=DEBUG`를 임시로 켜고, 측정 후 지운다.
  6. `git tag golden-v1`
- AC: 골든 파일, 노이즈 허용 오차, 스냅샷이 `docs/golden/`에 있다.

---

### P1. 구조 정비

#### T-01 `AiClient` 추출·레이어 정리

- 변경 파일: 신규 `analysis/client/AiClient.java`, `AiException`(하위 `AiUnavailableException`, `AiRejectedException`, `AiFailedException`), `AnalysisService`, `DatasetService`, `DatasetController`, `AnalysisController`
- 작업
  1. `AiClient`: `InferenceDO infer(String runId, String sourcePath, String variant)`, `boolean healthy()`. 연결 불가, 4xx, 5xx를 예외 종류로 구분하고 현재 메시지 문구는 `AnalysisService`에서 유지한다.
  2. `DatasetController`가 쓰는 `datasets.datasets()`, `filenames()`, `rounds()`를 `DatasetService`에 같은 이름의 메서드로 추가한다.
  3. `AnalysisController`의 `datasets.roundForRun(runId)`는 `DatasetService.roundOf(runId)`로 대체한다.
- AC: `controller` 패키지에서 `repository` 패키지를 import하지 않는다. 골든 합격.

#### T-02 의존 방향 정리

- 목표 의존 방향

  ```text
  review  → analysis, truth
  dataset → analysis, media, truth
  analysis → truth, media
  truth   → benchmark, media
  ```

- 변경 파일: 신규 `review/`(`ReviewController` = `POST /history/{runId}/truth`, `ReviewService`), `truth/service/TruthService.java`, `truth/service/ReferenceTruthService.java`, `truth/controller/TruthController.java`(이동 후 삭제)
- 작업
  1. `TruthService`는 정답·평가 데이터 처리(`lockInput`, `latest`, `nextVersion`, `save`, `evaluate`, `history`)만 남기고 `analysis` 의존을 제거한다.
  2. `ReferenceTruthService.find(AnalysisRun)`를 `find(String filename, String sourcePath)`로 바꾼다.
  3. 검수 저장 흐름(SHA 확인 → 버전 생성 → 성공한 run 재평가)을 `ReviewService`로 옮긴다. `@Transactional(isolation = READ_COMMITTED)`는 유지한다.
- AC: `truth`가 `kr.ace.web.analysis`를 import하지 않는다. 같은 마릿수 재검수 시 중복 버전이 생기지 않는다. 골든 합격.

#### T-03 테스트 기반 구축

- 변경 파일: `web/build.gradle`(`spring-boot-starter-test`, 필요 시 Boot 4의 모듈형 테스트 스타터, `useJUnitPlatform()`), `web/src/test/java/...`, `tests/`(Python), `requirements-dev.txt`(`pytest`, `httpx`)
- Java 테스트

  | 대상 | 내용 | DB |
  | --- | --- | --- |
  | `CsvTable` | BOM, 따옴표 안 쉼표·줄바꿈, 이중 따옴표, 닫히지 않은 따옴표, 열 수 불일치 | 불필요 |
  | `BenchmarkService` | 임시 폴더 CSV로 중복·누락 행이 "확인 필요"로 표시, 집계 | 불필요 |
  | `MediaService` | 확장자 검사, 경로 이탈 방지, 15MB 초과 | 불필요 |
  | `AnalysisService` | `AiClient` 목으로 정상, 4xx, 5xx, 연결 불가, 응답 형식 오류 | 불필요 |
  | `ReviewService` / `TruthRepository` | 정답 버전 증가, 같은 마릿수 재검수 시 중복 없음 | Testcontainers(D2) |
  | 의존 방향 | `truth`가 `analysis`를 import하지 않음 | 불필요 |

- Python 테스트: FastAPI `TestClient` + 가짜 엔진(`ENGINE_FACTORIES` 딕셔너리로 교체). 잘못된 버전·경로·UUID, 디코딩 실패(422), 같은 `run_id` 재요청, 다른 버전 재요청 시 409.
- AC: `gradlew test`, `pytest` 통과. DB 불필요 테스트는 Docker 없이 실행된다.

---

### P2. 스키마와 품종 평가

#### T-20 Flyway 도입 (V1 = 최종 스키마)

- 변경 파일: `web/build.gradle`(Flyway 스타터 `spring-boot-starter-flyway`, `org.flywaydb:flyway-mysql`), 신규 `db/migration/V1__init.sql`(5.5의 최종 스키마, `IF NOT EXISTS` 없음), `schema.sql` 삭제, `application.properties`, `db/reset_database.sql`, `README.md`

  ```properties
  spring.sql.init.mode=never
  spring.flyway.locations=classpath:db/migration
  spring.flyway.validate-on-migrate=true
  spring.flyway.clean-disabled=true
  ```

- 작업: `reset_database.sql` 상단에 "`ACE_STORAGE`를 함께 비울 것" 경고를 넣는다. GRANT 문을 5.2의 권한으로 갱신한다.
- AC: 빈 `ace_web`에서 테이블 8개와 `flyway_schema_history`가 만들어지고 앱이 기동한다. 새 DB에서 회차를 다시 실행해 골든과 일치한다.

#### T-21 품종 평가 데이터 모델 (검토 6번)

- 변경 파일: `truth/entity/GroundTruthPet.java`(필드 축소), `truth/entity/Evaluation.java`(`catDiff`, `dogDiff`, `breedUnsupported` 추가), `truth/repository/TruthRepository.java`, `AnalysisDetailDO`, `AnalysisService.detail()`, `detail.html`, `history.html`, 문서(ERD·정의서)
- 작업
  1. `TruthRepository.pets()`가 `ground_truth_pet`의 `species, breed, position_note`를 읽도록 정리한다.
  2. `evaluate()`가 `Verdict`(T-33)의 결과로 `cat_error, dog_error, cat_diff, dog_diff, is_match, breed_correct, breed_total, breed_unsupported`를 저장한다.
  3. `detail.html`의 "품종 일치" 열과 "개체별 비교"가 새 값을 표시하게 한다.
  4. 정의서(xlsx), ERD 이미지, `database-design.html`, README의 컬럼·FK 수를 재집계해 갱신한다(ERD 이미지는 제작 도구 확인 필요).
- AC: `grep -rn "box_no\|outcome\|supported" web README.md docs`에서 쓰이지 않는 참조가 없다. 평가 이력이 새 컬럼을 표시한다.

#### T-34 정답 품종 파싱·저장

- 변경 파일: 신규 `truth/service/GroundTruthParser.java`, `benchmark/model`의 `Truth`(`pets` 추가), `BenchmarkService.truth()`, `ReferenceTruthService`
- 작업
  1. `종류` 열 파싱: 구분자 `;`, 각 항목은 `(고양이|개)\d*\((.*)\)`, 품종은 마지막 `:` 뒤, 위치 설명은 `:` 앞(없으면 빈 문자열), `해당 없음`은 빈 목록.
  2. 정답을 자동 적용할 때 `ground_truth_pet`에 저장한다. 파싱에 실패한 행은 "확인 필요"로 표시한다.
  3. 수동 검수(웹에서 마릿수 직접 입력)로 만든 정답은 품종이 없으므로 `breed_total = 0`(평가 제외)으로 표시한다.
- AC: 40장의 정답 객체 50개가 모두 파싱된다(실패 0건). 4장은 빈 목록이다.

#### T-35 품종 표기 정규화·동의어·미지원

- 변경 파일: 신규 `data/breed_aliases.csv`, `data/breed_labels_imagenet_ko.csv`, `truth/service/BreedNormalizer.java`, `BreedVocabulary`(버전별 라벨 집합), 라벨 집합 추출 스크립트
- 작업
  1. 정규화: 공백·대소문자 제거 후 동의어 표 적용. 초기 동의어 2건(카발리에 킹 찰스 스패니얼 ↔ 블렌하임 스패니얼, 웰시 코기 ↔ 펨브로크 웰시 코기).
  2. main-1은 ImageNet 영문 라벨(`pug`, `golden_retriever` 등)을 한글로 매핑한다.
  3. 버전별 라벨 집합(main-1: 매핑표의 한글 집합, main-2: 분류 모델의 클래스 목록)으로 "미지원"을 판정한다.
  4. 매핑·동의어 누락 때문에 오판이 생기지 않도록, 분류 결과에 나타난 라벨 중 표에 없는 것은 "확인 필요"로 로그에 남긴다.
- AC: main-2의 자동 품종 일치가 **27/50**이다. 수동표(`test_cases_classification.csv`)가 정상으로 친 비대상 사진 3장(img_037, 038, 040)을 더하면 수동표의 30과 일치한다.

#### T-33 `Verdict`와 사진별 비교 문구

- 변경 파일: 신규 `Verdict`(순수 계산 클래스, DB·웹 의존 없음), `BenchmarkService`(`/versions`), `AnalysisService`·`DatasetService`(`/rounds`), `version-photo.html`, `round-photo.html`, `round.html`, `versions.html`, `BenchmarkDO.Result`, `RoundDO.Item`
- 작업
  1. `Verdict`: 입력(정답 개수·정답 객체 목록·예측 개수·예측 객체 목록·버전 라벨 집합) → 출력(개수 라벨, 차이값, 품종 판정, `breed_correct/total/unsupported`). 규칙은 5.4.
  2. 두 화면(CSV 기반 `/versions`, DB 기반 `/rounds`)이 같은 클래스를 사용한다.
  3. 캡션을 5.4의 규격으로 교체한다. 현재의 `일치/불일치/확인 필요/자료 없음` 단일 판정은 개수 판정으로 대체하고, "확인 필요"·"자료 없음"은 데이터 상태로 별도 표시한다.
  4. 선택: main-2의 보정 내역 표시(엔진의 메타데이터 반환 포함).
- 테스트: img_001, img_022, img_028의 세 버전 결과(5.4 예시 표), 40장의 개수 판정 분포 재현(부록 A), 수동표 대조(27/50).
- AC: 사진별 비교 화면에서 img_028이 main 일치 / main-1 개수 초과·품종 불일치 / main-2 일치·품종 2/2로 표시된다.

---

### P3. 성능과 정확성

#### T-30 쿼리 최적화·진행 표시 개선 (검토 2번)

- 변경 파일: `analysis/entity/AnalysisRun.java`(`variant` 필드 추가), `AnalysisRepository`(`variant()` 제거, `statusCounts(roundId)` 추가), `TruthRepository`(`latestEvaluationsByRound`, `latestEvaluations(List<String>)`), `DatasetService`(`photos()`, `progress()`), `AnalysisService`(`history()`), `static/round-progress.js`
- 작업
  1. `photos()`에서 run별 호출을 없앤다. `variant`는 `run.variant()`, 평가는 회차 전체를 한 번에 조회한 `Map<runId, Evaluation>`에서 꺼낸다.

     ```sql
     SELECT e.*, g.cat_count, g.dog_count
     FROM evaluation e
     JOIN ground_truth g ON g.input_id = e.input_id AND g.version = e.version
     JOIN analysis_run a ON a.run_id = e.run_id
     WHERE a.round_id = ?
       AND e.version = (SELECT MAX(e2.version) FROM evaluation e2 WHERE e2.run_id = e.run_id)
     ```
  2. `progress()`는 `SELECT status, COUNT(*) ... GROUP BY status`로 계산하고 `running || active.contains(id)` 중복을 정리한다.
  3. `/history` 목록(최대 200건)도 한 번의 조회로 대체한다.
  4. `round-progress.js`: 건수가 바뀌어도 진행 문구만 갱신하고, `running`이 false가 되면 한 번만 reload한다.
- AC: 40장 회차 `/rounds/{id}`의 쿼리가 6개 이하(T-00E 측정값은 240개 이상 예상)이고, 진행 중 반복 reload가 없다. 골든 합격.

#### T-32 AI 서버 예외 분리 (검토 4번)

- 변경 파일: `app/ai_server.py`, `shared/common.py`, `tests/test_ai_server.py`
- 작업
  1. `get_engine(version)`을 분리한다. 모델 로딩 실패(`FileNotFoundError`, `ValueError`, `RuntimeError`)는 로그를 남기고 **503**.
  2. 추론 전에 `read_image(original)`로 디코딩을 검사해 실패하면 **422**. 이 결과로 `source_width/source_height`도 얻는다.
  3. 추론 중 예외는 **500**으로 응답하고 `run_id`를 로그에 남긴다.
  4. `ENGINE_FACTORIES` 딕셔너리로 엔진 생성을 분리해 테스트에서 가짜 엔진을 쓴다.
  5. 엔진이 `model_hashes`를 제공하게 해 서버의 중복 해시 계산을 제거한다.
  6. `/api/health?deep=true`는 세 엔진을 실제로 로드해 본다.
- AC: 모델 파일을 바꾸면 503, 손상된 이미지는 422가 나오고 Spring의 실패 메시지가 각각에 맞게 표시된다.

---

### P4. 평가 방법 개선 (검토 5번)

#### T-40 홀드아웃 데이터 준비

- 작업
  1. 새 사진 20장 이상을 수집한다(기존 40장과 겹치지 않고, 단독·여러 마리·겹침·작은 개체 등 기존 분포를 반영).
  2. `data/holdout/images/hold_001.jpg …`, `data/holdout/ground_truth.csv`(기존과 같은 열: `파일명, 고양이_정답, 개_정답, 합계, 비고, 종류`)
  3. 규칙: 홀드아웃 결과를 보고 임계값이나 규칙을 바꾸지 않는다(바꾸면 그 사진은 더 이상 홀드아웃이 아님).
  4. 출처·촬영 조건·정답 작성자를 `data/holdout/README.md`에 기록한다.
- AC: 사진과 정답 CSV가 쌍으로 있고 파일명이 기존 40장과 겹치지 않는다.

#### T-41 평가 지표 집계

- 변경 파일: `BenchmarkService`, `BenchmarkDO.Summary`, `DatasetService.scores()`, `RoundDO.Score`, `round.html`, `versions.html`, `home.html`
- 지표(버전별)

  | 지표 | 정의 |
  | --- | --- |
  | 고양이·개 MAE | 사진별 절대 오차의 평균 |
  | 개수 판정 분포 | 일치 / 종 오판 / 개수 부족 / 개수 초과 / 다른 동물 오검출 사진 수 |
  | 이전 버전 대비 악화 | 일치 여부와 별개로 총 절대 오차가 늘어난 사진 수 |
  | 품종 정확도 | `breed_correct / breed_total`을 전체 기준과 지원 품종 기준으로 |

- AC: 기준 40장에서 main-1 → main-2 비교에 img_022가 "종 오판 / 악화"로 표시된다. 일치 건수는 새 골든과 같다.

#### T-42 홀드아웃 평가 흐름

- 변경 파일: `BenchmarkService`(`data/ground_truth.csv`와 `data/holdout/ground_truth.csv`를 함께 읽고 항목마다 `set`: `tuning` / `holdout` 부여), `BenchmarkRepository`(`image("original", name)`이 두 폴더를 모두 탐색), `ReferenceTruthService`(SHA 비교 대상 경로 확장), `DatasetService.scores()`, `round.html`, `versions.html`, `home.html`
- 작업
  1. 점수표를 `set`별로 나눠 표시한다("튜닝 40장 기준", "홀드아웃 기준").
  2. 문서와 홈의 수치 문구에 "튜닝에 사용한 40장 기준"을 명시한다.
  3. 홀드아웃 결과는 `docs/Holdout_Evaluation.md`에 한 번만 기록한다.
- AC: 파일명이 기준 CSV에 있고 SHA-256이 같으면 어느 세트든 정답이 표시되고, 점수표에서 두 세트가 구분된다.

#### T-43 변경 요인 분리 — 선택

- 변경 파일: `app/pet_engine.py`(옵션 객체 `PetOptions(iou, dedup, split_wide)`), 신규 `scripts/ablation.py`
- 작업: 기본값은 현재 동작과 동일하게 두고, 조합별(iou 0.70/0.60, dedup 켬/끔, split 켬/끔)로 기준 40장과 홀드아웃에서 지표를 계산해 `outputs/ablation.csv`로 저장한다. main → main-1에서 "분류기 추가" 하나로 변경 요인이 좁혀졌다는 점(표준 입력 이후)을 문서의 변경 요인 표에 정리한다.
- AC: 기본 옵션 결과가 기존 main-2와 동일하고 조합별 표가 생성된다.

#### T-36 정답 박스·IoU 매칭 — 선택 (착수 시 별도 설계)

- 목적: 병합과 미검출을 구분하고 위치까지 검증한다.
- 개요: 표준 이미지 기준 정규화 좌표(`x / width`)로 정답 박스를 저장하고(객체 약 50개), IoU(≥ 0.5 등)로 매칭한다. 정답 박스는 CVAT·Label Studio 같은 도구로 그린 뒤 CSV로 가져온다.
- 영향: `ground_truth_pet`에 박스 컬럼 추가(새 마이그레이션), `Verdict` 확장(병합/미검출 구분, 위치 기반 품종 판정).

---

### P5. 운영

#### T-50 AI 서버 장애 대응

- 변경 파일: `AiClient`, `DatasetService`, `DatasetController`, `round.html`
- 작업
  1. `start()`, `resume()`, `retry()` 시작 전에 `AiClient.healthy()`(타임아웃 3초)를 확인한다. 실패하면 회차를 만들지 않고 "AI 서버에 연결할 수 없습니다. Python 창을 확인해 주세요."를 표시한다.
  2. `schedule()` 루프에서 `AiUnavailableException`이 연속 3회 나오면 루프를 멈추고 남은 run을 PENDING으로 둔다(FAILED로 기록하지 않음). `resume`으로 이어서 실행할 수 있다.
  3. 읽기 타임아웃(120초)과 4xx, 5xx는 지금처럼 해당 run만 FAILED로 기록한다.
- AC: Python을 끈 채 분석을 시작하면 회차가 만들어지지 않는다. 분석 도중 Python을 끄면 남은 건이 PENDING으로 남고, 다시 켠 뒤 "대기 중인 분석 계속"으로 완료된다.

#### T-51 실패 일괄 재시도

- 변경 파일: `DatasetController`(`POST /rounds/{id}/retry-failed`), `DatasetService`, `round.html`
- 작업: `schedule(roundId, run -> "FAILED".equals(run.status()))`를 재사용한다. 실패가 1건 이상이고 진행 중이 아닐 때만 버튼을 보인다. 진행 중이면 기존처럼 409와 안내 문구를 반환한다.
- AC: 실패 N건이 한 번의 클릭으로 재실행되고, 성공 결과는 유지된다.

#### T-52 IntelliJ 실행 구성·README·문서 갱신

- 작업
  1. **DB 비밀번호**: `web/application-local.properties`(Git 제외)에 `spring.datasource.password=...`를 두고 `application.properties`에 `spring.config.import=optional:file:./application-local.properties`를 추가한다. 환경변수 방식(`ACE_DB_PASSWORD`)도 지원한다.
  2. **Spring 실행 구성**: `kr.ace.web.AceApplication` 또는 Gradle `bootRun`, 작업 디렉터리는 반드시 `web`(`../ACE_STORAGE`, `..` 상대경로 때문).
  3. **Python 실행 구성**: 인터프리터 `.venv`, 모듈 `uvicorn app.ai_server:app --host 127.0.0.1 --port 8090`, 작업 디렉터리는 프로젝트 루트(`shared`, `app` import 때문).
  4. 실행 구성을 IntelliJ에서 한 번 만든 뒤 **Store as project file**로 저장하면 `.run/`에 생기므로 그 파일을 커밋한다(XML을 직접 작성하지 않는다).
  5. README에 "IntelliJ로 실행" 절을 추가한다(Gradle 프로젝트로 `web/build.gradle` 연결, Gradle JVM = JDK 17, Python SDK = `.venv`, 환경변수 표).
  6. README와 `docs/`를 갱신한다: 폴더 구조(`data/baseline`, `review` 패키지), GRANT 문, Flyway와 초기화 정책, 표준 입력과 좌표 규칙, 판정 용어표, 지표 정의, 골든 사용법, 홀드아웃 규칙, 테이블·컬럼 수.
- AC: 새로 클론한 팀원이 README만 보고 IntelliJ에서 두 서버를 실행해 분석 한 건을 끝까지 돌릴 수 있다.

---

## 7. 전체 완료 기준

| 항목 | 기준 |
| --- | --- |
| 입력 기준 | 세 버전이 같은 표준 입력을 쓰고, 같은 사진의 `marked.jpg` 크기가 같다 |
| 회귀 | main-1·2는 제출본과 동일, main은 새 기준선, 이후 모든 카드는 골든 합격 |
| 재현성 | 새 클론에서 `/versions`가 동일하게 표시되고 CLI 실행이 `data/baseline`을 바꾸지 않음 |
| 성능 | 40장 회차 화면 쿼리 6개 이하, 진행 중 반복 reload 없음 |
| 정확성 | 모든 버전의 박스가 표준 좌표, 모델 오류(503)와 이미지 오류(422)가 다른 응답 |
| 평가 문구 | 사진별 비교가 개수 판정과 품종 판정을 구분해 표시, 오류 유형 라벨 제공 |
| 품종 평가 | main-2 자동 품종 일치 27/50(+비대상 3 = 수동표 30과 일치), 미지원 품종 병기 |
| 구조 | Controller → Service → Repository, `truth` → `analysis` 의존 없음 |
| 스키마 | Flyway `V1`으로 기동, 테이블 8개, ERD·정의서·README 일치 |
| 테스트 | `gradlew test`, `pytest` 통과 |
| 평가 방법 | 홀드아웃 결과를 별도 기록, 튜닝 40장 기준 문구 명시 |
| 운영 | AI 서버 중단 시 FAILED 폭증 없음, 실패 일괄 재시도 동작, IntelliJ 실행 가이드 |

## 8. 위험과 대응

| 위험 | 대응 |
| --- | --- |
| `data/images`를 640으로 변환하면 분류 크롭 해상도가 떨어져 품종 결과가 달라질 수 있음 | D14 기본값은 변환하지 않음. 변환은 게이트 G1에서 원본 입력 결과와 비교한 뒤에만 채택 |
| main의 입력이 바뀌어 마릿수와 README 수치가 달라짐 | T-00C에서 재측정, legacy는 `data/baseline/legacy-original-res/`에 보존, README에 변경 사유 기록 |
| 동의어·ImageNet 매핑표 누락으로 품종 오판 | 수동표 대조(27/50 + 3)를 테스트에 포함, 표에 없는 라벨은 "확인 필요"로 로그 |
| 다중집합 방식은 위치를 보지 않음 | 한계를 화면과 문서에 명시, 필요 시 T-36 |
| `data/baseline` 이동으로 저장소 크기 증가 | 허용 범위. 부담되면 Git LFS 검토 |
| DB 테스트용 Docker 미설치 | D2 기본값대로 DB 불필요 테스트만 먼저 구성 |
| ERD 이미지 재작성 도구가 불명확 | T-21 착수 전에 제작 도구 확인. 어려우면 Excel 정의서와 HTML 표만 먼저 갱신 |
| 홀드아웃 사진 확보 지연 | T-40은 코드 작업과 독립. T-42는 데이터가 준비된 뒤 진행 |
| 팀원 DB가 구 스키마 | 전원 1회 초기화(5.2), README에 절차 명시 |

## 9. 백로그 (범위 밖)

- `imagenet_engine.py`와 `pet_engine.py`의 공통 함수(`crop_from_original`, `square_pad`, `draw_class_label`)를 `shared/`로 이동, 미사용 import 정리
- 같은 사진에 대해 세 버전이 YOLO 검출을 반복하는 부분의 공유(속도 최적화)
- 등록 시점의 이미지 유효성 검사(확장자 외 실제 디코딩 확인)
- 인증·CSRF(외부 공개 시 Spring Security)
- 모델 파일 약 28MB의 Git LFS 또는 다운로드 스크립트 전환
- `/versions` 화면에 제출본(legacy) 열 추가
- 공개 저장소로 올릴 경우 팀원 실명 노출 확인

---

## 부록 A. 검토 근거 (실측)

| 항목 | 내용 |
| --- | --- |
| 사진 | 40장, 서로 다른 해상도 26종, 긴 변 639~2560px, 세로 사진 11장. 비율은 1.33(12장), 1.5, 1.0 등 다양 |
| EXIF | 방향 태그가 있는 사진은 1장(값 1, 회전 없음) |
| main 현행 | 원본 해상도로 검출하고 `plot()`도 원본 크기. 결과 40장 31.8MB |
| main-1·2 현행 | 긴 변 640으로 축소(`INTER_AREA`, 확대 없음) 후 검출, 결과 40장 5.2MB. 분류 크롭은 원본에서 자름 |
| 제출본 마릿수 일치 | main 28 / main-1 28 / main-2 31 (40장). main과 main-1은 4장에서 서로 다른 결과 |
| 정답 품종 | `ground_truth.csv`의 `종류` 열, 정답 객체 50개 전부 파싱됨. `해당 없음` 4장 |
| 수동 분류표 | 54행 = 객체 50 + 비대상 사진 4. 성공 30(y), 실패 24(n). 오류 유형: 품종 불일치 15, 미검출 4, 검출 종류 오류 4, 다른 동물 오검출 1 |
| main-2 품종 | 자동 25/50, 동의어 2건 허용 시 27/50, 비대상 3장을 더하면 30 = 수동표 |
| main-1 라벨 | ImageNet 영문 라벨(`pug`, `golden_retriever` 등). 한글 매핑 없이 비교하면 0/50. ImageNet의 고양이 품종은 제가 아는 한 5종뿐이라 벵갈·메인쿤 등은 분류할 수 없음 |

**개수 판정 분포 (제출본 기준, 새 규칙 적용)**

| 판정 | main | main-1 | main-2 |
| --- | --- | --- | --- |
| 일치 | 28 | 28 | 31 |
| 개수 부족 | 5 | 4 | 4 |
| 개수 초과 | 3 | 4 | 0 |
| 종 오판(고양이→개) | 3 | 3 | 3 |
| 종 오판(개→고양이) | 0 | 0 | 1 |
| 다른 동물 오검출 | 1 | 1 | 1 |

표준 입력 도입 후 main은 이 분포가 달라질 수 있으므로 T-00C에서 다시 측정한다.

## 부록 B. 이전 계획 대비 변경·정정

| 항목 | 이전(v1) | v4 |
| --- | --- | --- |
| 품종 스키마(검토 6번) | 쓰이지 않는 구조로 보고 제거 | **철회**. 품종 평가로 구현(T-21, T-34) |
| 좌표(D4) | 원본 좌표로 환산(T-31) | **표준 이미지 좌표**로 통일, 표준 입력 도입(T-00B)으로 대체 |
| "IoU 비교 불가" 설명 | 버전 간 IoU 비교가 불가능하다고 서술 | **정정**: 각 행의 박스와 `image_width/height`가 같은 공간이라 정규화하면 비교 가능했다. 표준 입력으로 근본 해결 |
| Flyway(T-20) | `baseline-on-migrate=true`, V1 현행 스키마, V2·V3 추가 | `baseline-on-migrate` 삭제, DB 초기화 후 `V1`에 최종 스키마(D7) |
| 골든(T-00) | 마릿수 120행만 저장, DB 초기화 전에 취득 | 마릿수 + 검출 상세 + 노이즈 측정 + 화면 스냅샷, 표준화·초기화 **이후** 취득(T-00E) |
| 기준 자료 분리(T-10) | 초기 단계에서 현행 `outputs`를 이동 | 새 기준선 생성 후 분리(T-00D), 제출본은 `legacy-original-res`로 보존 |
| 평가 지표(T-41) | 서명 오차 컬럼 추가를 V3 마이그레이션으로 | `V1`에 포함. T-41은 집계 지표·화면 중심 |
| 입력 파일 변환(D14) | 제안 없음 | 기본은 변환하지 않음(분류 크롭이 원본 해상도에 의존). 변환 시 게이트 G1 |
| 신규 카드 | - | T-33(`Verdict`·문구), T-34(정답 파싱), T-35(정규화·미지원), T-36(정답 박스, 선택) |
