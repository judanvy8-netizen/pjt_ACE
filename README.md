# ACE 반려동물 사진 분석 웹서비스 · 화면 개선판

사진을 데이터셋으로 등록하고 **분석 시작**을 누르면 Python이 같은 사진을 `main`, `main-1`, `main-2`, `main-3`로 각각 처리합니다. 웹에서 원본과 네 결과를 비교하고, 기준 사진은 CSV 정답을 확인해 검수할 수 있습니다.

**기존 ACE Web5에서 업데이트하는 경우** 기존 서버를 종료하고 새 소스로 JAR를 다시 빌드하세요. 같은 MySQL DB와 사진이 들어 있는 `ACE_STORAGE` 경로를 사용합니다. 이번 변경은 기존 Flyway V1 스키마와 호환됩니다.

~~~powershell
Set-Location .\web
.\gradlew.bat clean test bootJar
Set-Location ..
~~~


## 1. 준비할 프로그램

| 구분 | 버전 또는 용도 |
| --- | --- |
| 운영 환경 | Windows PowerShell, 로컬 PC |
| Java | JDK 17. `java -version`과 `javac -version`에서 17 확인 |
| 웹 서버 | Spring Boot 4.0.9, Spring MVC, Thymeleaf, Spring JDBC |
| 빌드 | Gradle Wrapper 8.14.3. 별도의 Gradle 설치는 필요하지 않음 |
| AI 서버 | Python 3.11, FastAPI, PyTorch CPU, Ultralytics |
| DB | MySQL Server 8.4, ACE 전용 DB `ace_web` |
| 기본 주소 | 웹 `127.0.0.1:8080`, Python `127.0.0.1:8090`, MySQL `127.0.0.1:3306` |

ZIP을 풀고 프로젝트 폴더를 엽니다. 아래 경로는 예시입니다. **명령을 입력할 때 실제 압축을 푼 경로를 사용하세요.** 이 README에서 ‘프로젝트 루트’는 `README.md`, `web`, `source`가 같이 있는 폴더입니다.

~~~powershell
Set-Location 'D:\LSK\pjt_ACE_web5'
java -version
javac -version
py -3.11 --version
~~~

JDK가 여러 개면 IntelliJ의 Gradle JVM도 JDK 17로 지정합니다. 터미널에서 17이 표시되지 않으면 Windows의 `JAVA_HOME`과 `Path`를 JDK 17에 맞춘 뒤 새 PowerShell을 엽니다.

## 2. 초기 환경 설정 · 한 번만

### 2.1 MySQL 8.4에서 ACE용 DB 만들기

MySQL을 먼저 시작합니다. MySQL Workbench에서 관리자 계정으로 연결해 아래 SQL을 실행하거나, PowerShell에서 `mysql -u root -p`로 접속한 뒤 실행합니다. `여기에_내_비밀번호` 부분은 실제로 사용할 비밀번호로 바꿉니다.

~~~sql
CREATE DATABASE IF NOT EXISTS ace_web
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'ace_app'@'localhost'
  IDENTIFIED BY '여기에_내_비밀번호';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON ace_web.* TO 'ace_app'@'localhost';
~~~

계정이 이미 있다면 `CREATE USER IF NOT EXISTS`는 기존 비밀번호를 그대로 사용합니다. 관리자 연결에서 `ALTER USER`로 변경하거나 기존 비밀번호를 사용하세요. 로그인은 `mysql -h 127.0.0.1 -u ace_app -p ace_web`로 확인할 수 있습니다. `mysql` 명령을 찾지 못하면 Workbench를 이용하거나 MySQL의 `bin` 폴더를 확인하세요.

### 기존 프로젝트에서 교체하는 경우

- **4차 개선판의 DB를 사용 중이면** DB와 기존 `ACE_STORAGE`를 그대로 사용합니다. 서버를 종료하고 새 소스로 JAR를 다시 빌드한 뒤 실행하세요. 다른 경로에 압축을 풀었다면 기존 `ACE_STORAGE` 절대 경로를 두 서버에 동일하게 지정합니다.
- **초기화 후 시작하기로 했다면** 아래 절차를 적용하세요.

1. 기존 Spring과 Python을 모두 종료합니다. 기존 기록이 필요하면 DB와 `ACE_STORAGE`를 백업합니다.
2. DB 초기화가 필요한 경우 MySQL Workbench 관리자 연결에서 `db/reset_database.sql`을 실행합니다. **기존 분석·정답·평가 DB 기록이 삭제됩니다.** 이미 빈 `ace_web`을 만들었다면 이 SQL은 건너뜁니다.
3. 두 서버가 사용하던 `ACE_STORAGE`를 백업 폴더로 옮기거나 새 프로젝트에 빈 저장소를 사용합니다. `data`의 원본·정답·기준 자료는 그대로 둡니다.
4. 위의 권한을 적용한 뒤 Spring을 시작합니다. Flyway가 빈 DB에 `V1__init.sql`을 적용해 업무 테이블 8개와 `flyway_schema_history`를 만듭니다. 기존 DB 자동 이관을 사용하지 않습니다. 이후 실행에는 DB 초기화를 반복하지 않습니다.

### 2.2 Python 가상환경과 라이브러리 설치

프로젝트 루트에서 다음 명령을 순서대로 실행합니다.

~~~powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install torch==2.5.1 torchvision==0.20.1 --index-url https://download.pytorch.org/whl/cpu
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m pip check
~~~

`shared\models`에 `yolo11n.pt`, `yolo11n-cls.pt`, `cat_oxford_mobilenetv3.pt` 세 파일이 들어 있습니다. main-3의 공식 ViT-B/16 가중치는 용량이 약 346 MB라 저장소에 포함하지 않습니다. 설치 후 `.\.venv\Scripts\python.exe scripts\download_vit.py`를 실행해 내려받고 SHA-256을 확인합니다. 기본 사진 40장은 `data\images`에 있습니다. 의존성 설치와 첫 Gradle 빌드에는 패키지 저장소에 접속할 수 있는 인터넷 연결이 필요합니다.

### 2.3 Spring Boot 빌드

프로젝트 루트에서 아래 명령을 실행합니다.

~~~powershell
Set-Location .\web
.\gradlew.bat --version
.\gradlew.bat clean bootJar
Get-ChildItem .\build\libs\ace-web-1.0.0.jar
Set-Location ..
~~~

Wrapper가 처음에 Gradle을 받습니다. `--version`의 JVM이 17인지 확인하세요. `BUILD SUCCESSFUL`과 JAR 파일을 확인하면 실행 단계로 넘어갑니다.

## 3. 처음 실행 · PowerShell 창 2개

### 창 1 · Python AI 서버

프로젝트 루트에서 실행합니다.

~~~powershell
.\.venv\Scripts\python.exe -m uvicorn app.ai_server:app --host 127.0.0.1 --port 8090
~~~

`Application startup complete`가 뜨면 AI 서버가 요청을 받을 수 있습니다. 각 버전의 모델은 그 버전의 첫 분석 요청에서 로드됩니다. 창을 열어 둔 채 브라우저에서 [AI 상태 확인](http://127.0.0.1:8090/api/health)을 열어 `"ok":true`와 지원 버전 목록을 확인하세요.

### 창 2 · Spring Boot 서버

새 PowerShell을 열어 프로젝트 루트로 이동한 뒤 아래 명령을 실행합니다. 비밀번호 입력란은 MySQL에서 만든 **ace_app 계정 비밀번호**입니다.

~~~powershell
$credential = Get-Credential -UserName 'ace_app' -Message 'ACE MySQL 계정 비밀번호'
$env:ACE_DB_USER = $credential.UserName
$env:ACE_DB_PASSWORD = $credential.GetNetworkCredential().Password
Set-Location .\web
java -jar .\build\libs\ace-web-1.0.0.jar
~~~

브라우저에서 [ACE 홈](http://127.0.0.1:8080)을 열고 **사진 분석**을 선택합니다. `data\images\img_001.jpg`를 데이터셋으로 등록하고 **분석 시작**을 누릅니다. 회차 화면에서 성공 4건과 사진별 비교를 확인하세요.

두 서버는 기본값으로 프로젝트 루트의 `ACE_STORAGE`를 공유합니다. Spring을 실행할 때 작업 폴더는 반드시 `web`으로 맞춥니다. 저장 위치를 바꾸면 두 PowerShell 창에서 서버 시작 전에 같은 절대 경로를 지정하세요.

~~~powershell
$env:ACE_STORAGE = 'D:\LSK\pjt_ACE_web5\ACE_STORAGE'
~~~

## 4. 두 번째 실행부터 · Start.ps1 / Stop.ps1 간편 실행

최초 설정(2장)과 JAR 빌드가 끝났다면 이후에는 **PowerShell 명령어 한 줄로 MySQL 확인 → Python AI 서버 → Spring Boot 서버를 실행**합니다. 두 서버를 따로 확인하려면 3장의 수동 실행 방법을 사용하세요.

### 4.1 스크립트 파일 위치

프로젝트 루트의 `scripts` 폴더에 아래 두 파일이 들어 있습니다.

~~~text
pjt_ACE_web5/
  scripts/
    Start.ps1     서버 시작(MySQL 자동 시작 포함)
    Stop.ps1      서버 종료(Python, Spring Boot)
~~~

### 4.2 서버 시작

PowerShell을 열고 **프로젝트 루트**에서 다음 명령어를 실행합니다.

~~~powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Start.ps1
~~~

- **MySQL**: 3306 포트를 확인합니다. MySQL이 중지되어 있으면 Windows MySQL 서비스를 자동 시작하고 준비될 때까지 확인합니다. 시작 권한이 없으면 **관리자 권한 PowerShell**에서 실행하세요. MySQL 서비스가 여러 개 발견되면 자동 선택하지 않고 안내합니다.
- **Python AI**: 가상환경의 Python으로 8090 포트 서버를 시작합니다.
- **Spring Boot**: `ace_app`의 MySQL 비밀번호 입력창이 나오면 비밀번호를 입력합니다. `web/build/libs/ace-web-1.0.0.jar`를 8080 포트로 시작합니다.
- **접속**: 마지막에 출력되는 `http://127.0.0.1:8080/`을 브라우저에서 엽니다. 서버가 준비되는 데 시간이 더 걸릴 수 있으므로 접속이 안 되면 잠시 후 다시 확인하거나 로그를 확인합니다.

등록 사진이 프로젝트 밖의 저장소에 있다면 스크립트에 해당 저장소를 지정합니다. 아래는 기존 원본이 `C:\LSK\ACE_STORAGE`에 있는 설치 예입니다.

~~~powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Start.ps1 -StoragePath 'C:\LSK\ACE_STORAGE'
~~~

스크립트는 실행 전에 JDK 17을 확인하고 선택한 `java.exe`와 사진 저장 경로를 출력합니다. 자동 탐색이 어려우면 `-Jdk17Home 'C:\Program Files\Java\jdk-17'`을 추가하거나 3장의 수동 실행 절차를 사용하세요. 수동 실행 시 두 창의 `ACE_STORAGE`를 동일하게 지정하고 Spring 창의 `java -version`을 확인합니다.

### 4.3 서버 종료

프로젝트 루트에서 아래 명령어를 실행합니다.

~~~powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Stop.ps1
~~~

`Stop.ps1`은 해당 `Start.ps1`이 실행한 **Python AI 서버와 Spring Boot 서버만 종료**합니다. **MySQL 서비스는 종료하지 않습니다.**

### 4.4 로그와 확인 사항

| 구분 | 위치 또는 주소 |
| --- | --- |
| ACE 웹 | `http://127.0.0.1:8080/` |
| AI 상태 | `http://127.0.0.1:8090/api/health` |
| Python 로그 | `logs/ai.out.log`, `logs/ai.err.log` |
| Spring 로그 | `logs/spring.out.log`, `logs/spring.err.log` |
| 실행 프로세스 기록 | `.run/ace-processes.json` (종료 시 사용) |

- 이미 8080 또는 8090 포트를 사용 중이면 중복 실행을 막기 위해 시작이 중단됩니다. 기존 서버를 정상 종료한 후 다시 실행하세요.
- Python 가상환경과 JAR 파일이 없으면 **2장 초기 설치/빌드**를 먼저 수행하세요. `Start.ps1`은 매번 빌드하지 않습니다.
- Java, HTML, CSS 또는 `web` 설정을 바꾸면 JAR를 다시 빌드하고, Python 코드를 변경하면 AI 서버를 재시작해야 반영됩니다.
- 별도로 `ACE_STORAGE`를 지정한다면 실행 전에 **Start.ps1을 실행하는 PowerShell**에서 절대 경로를 지정하세요. 시작한 두 서버에 같은 설정이 전달됩니다.
- 스크립트는 MySQL 포트 연결 여부를 확인하지만, **DB 계정 인증 및 애플리케이션의 정상 작동은 별도로 확인**해야 합니다. 비밀번호는 스크립트 파일에 저장하지 않습니다.

## 5. 홈, 버전 비교, 사진 분석과 정답 검수

### 5.1 홈과 버전 비교

- **홈**에서 ACE의 의미와 슬로건, 팀원 7명의 역할, 인트로 영상을 볼 수 있습니다. 영상은 재생 버튼을 누르면 시작합니다.
- **버전 비교**에서 같은 사진 40장에 대한 `main`, `main-1`, `main-2`의 제출 결과를 비교합니다. 보관 제출본 마릿수 일치는 28/40, 28/40, 31/40장입니다. 표준 입력의 새 기준선이 활성화되면 화면은 검증된 새 값을 표시합니다. T23·T28·T29는 main-1에서 틀렸고 main-2에서 마릿수가 정답과 일치한 사례입니다.
- 사진명을 누르면 원본과 세 버전의 검출 이미지, 마릿수 판정과 개체별 품종 후보가 표시됩니다. 상단에서 **01~40번 사진 선택**과 **이전 사진 / 다음 사진**을 사용할 수 있습니다. **오답·품종 검증표**는 제출 시점의 main-2에 대한 수기 평가 기록입니다.
- 이 화면은 프로젝트의 `data`, `outputs`와 검증 CSV를 읽습니다. 기본 작업 폴더는 `web`입니다. 다른 폴더에서 실행할 때는 `ACE_PROJECT_ROOT`에 `data`와 `outputs`가 있는 절대 경로를 지정하세요.

### 5.2 데이터셋 등록과 세 버전 분석

1. **사진 분석**에서 이름을 입력하고 사진을 선택합니다. 최대 40장, 장당 최대 15MB입니다. 화면 오른쪽의 **사진 분석 안내** 카드에서 다음 절차를 확인할 수 있습니다.
2. **데이터셋 등록**을 누릅니다. 원본 저장과 등록이 끝나면 상세 화면이 열립니다.
3. **분석 시작**을 누릅니다. 사진마다 세 버전을 순서대로 실행하므로 40장이면 결과 120건이 생깁니다. 회차 화면은 처리 진행을 갱신합니다.
4. 회차의 **사진별 결과**에서 사진명을 눌러 원본과 세 버전 결과를 비교합니다. 상단의 사진 선택과 이전·다음 버튼은 같은 회차에서 이동합니다. 40장을 등록했다면 01~40번을 선택할 수 있습니다.
5. 사진에 마우스를 올리면 **확대**가 표시됩니다. 클릭해서 크게 보고 **닫기** 또는 `Esc`로 닫습니다. 터치 화면에서는 확대 표시를 바로 볼 수 있습니다.
6. **분석 이력**에서 동일 회차·사진의 어느 모델 행을 선택해도 같은 사진 비교 화면이 열립니다. **버전 비교 → 웹에서 분석한 데이터셋 선택**으로도 회차를 찾아갈 수 있습니다.
7. 실패한 사진·버전은 회차 화면에서 **다시 시도**합니다. Spring 서버를 중간에 종료했다면 재실행 후 **대기 중인 분석 계속**을 누릅니다. **새 회차 분석 시작**은 기존 결과를 보존하며 다시 분석합니다.

원본은 `ACE_STORAGE/datasets/<데이터셋 ID>/<사진 ID>/`에 보관합니다. 결과는 각 회차의 사진·버전 실행 ID로 `ACE_STORAGE/results/<실행 ID>/`에 저장합니다. 기존 기준 비교 자료인 `outputs`는 웹 분석에서 그대로 사용합니다.

### 5.3 CSV 정답 검수

1. 사진 비교 화면 상단에서 **고양이 / 개 마릿수**와 **개체별 위치·품종**, **비고**를 확인합니다. 예를 들어 `img_029.jpg`는 고양이 2마리이며 왼쪽은 페르시안, 오른쪽은 스코티시 폴드입니다.
2. 각 버전 카드의 마릿수와 품종 요약을 비교합니다. 아래쪽의 **main-1 품종 후보**와 **main-2 품종 후보** 표에서 박스 번호, 검출 점수, 품종, 분류 점수 및 상태를 확인합니다. `main`은 종류별 마릿수를 집계합니다.
3. 화면 아래 **검수 저장**을 누르면 해당 등록 사진의 상태가 **미검수 → 검수완료**로 바뀝니다. **분석 이력**으로 돌아가면 같은 등록 사진의 모델 행에 이 상태가 표시됩니다. 마릿수 일치 여부는 별도의 **평가 결과** 열에서 확인합니다.

- 검수는 DB의 입력 사진 ID 단위입니다. 같은 데이터셋 사진의 여러 회차는 저장한 정답을 공유합니다. 같은 이름의 파일을 새 데이터셋에 등록하면 새 입력 사진으로 관리됩니다.
- 등록 파일과 기준 이미지의 이름 및 SHA-256이 일치할 때 CSV 정답을 연결합니다. 연결할 정답이 없거나 성공한 분석이 하나도 없으면 검수 버튼을 표시하지 않습니다. 예를 들어 토끼가 있는 `img_039.jpg`는 고양이 0마리·개 0마리이며 토끼 설명은 비고에 표시됩니다.
- 검수 전 카드 판정은 CSV 기준 미리보기입니다. 저장 후 성공 결과의 평가를 DB에 반영합니다. 아직 처리 중이거나 실패한 모델의 판정은 분석에 성공한 뒤 생성됩니다.
- 품종 판정은 같은 종 안에서 품종 목록을 순서와 무관하게 대조하며 CSV의 위치 설명은 검수 참고용입니다. 지원 정보가 확인되지 않은 품종은 별도 상태로 표시합니다.

### 5.4 분석 이력 요약과 실패 기록 삭제

- **분석 이력** 상단에서 전체 분석, 평가 완료, 마릿수 일치·불일치, 일치율, 고양이와 개의 평균 절대오차를 봅니다. 실행 1건은 사진 한 장을 모델 하나로 처리한 결과입니다. 일치율과 평균 절대오차는 실행별 최신 평가를 기준으로 계산합니다.
- 목록에는 **전체 / 마릿수 일치만 보기 / 오답만 보기 / 실패만 보기**가 있습니다. **오답만 보기**는 마릿수 또는 품종 불일치가 있는 평가를 보여줍니다. 목록은 선택한 조건의 최근 200건이며 상단 요약은 전체 이력 집계입니다. 오른쪽 카드에 비교·검수 절차가 표시됩니다.
- **실패만 보기**에서 삭제할 행을 체크하고 **선택한 실패 기록 삭제**를 누릅니다. 사진명, 모델, 건수를 확인한 뒤 삭제를 확정하거나 취소합니다. 표 머리 체크박스는 화면에 표시된 실패 행 전체를 선택합니다.
- 확정 후 기록을 `DISMISSED` 상태로 바꿔 이력과 실패 재시도 목록에서 제외합니다. 회차 화면에서는 해당 모델 자리와 삭제 건수가 표시됩니다. 원본, 성공 결과와 검수 정답은 보존됩니다. 해당 사진을 세 버전으로 다시 분석하려면 **새 회차 분석 시작**을 사용합니다.

### 5.5 서버 환경

- 상단 공통 메뉴의 **DB 설계 오른쪽의 서버 환경**에서 **Spring Boot 웹**, **Python AI**, **MySQL DB**의 상태를 확인합니다. 같은 패널의 **다시 확인**으로 상태와 확인 시각을 갱신합니다.
- Spring은 자체 상태 API, Python은 AI 서버의 `/api/health`, MySQL은 ACE의 DB 연결로 실행한 `SELECT 1`을 기준으로 합니다. Python API가 응답해도 모델 준비 상태가 정상이 아니면 **점검 필요**로 표시됩니다.
- Spring 웹 자체가 중지되면 새 페이지를 열 수 없습니다. 이미 열린 페이지에서 다시 확인하면 웹 연결 오류와 조회할 수 없는 항목이 표시됩니다.

### 5.6 성능 검증, 기술·판정 규칙, 소스 구조

- **성능 검증**에서는 버전별 일치 장수·일치율, 정답 마릿수 구간별 막대, 기준 사진 지도를 확인합니다. 사진 번호를 누르면 기존 비교 화면이 열립니다. 보관 제출본은 main 28/40, main-1 28/40, main-2 31/40입니다. 검증된 표준 기준선이 활성화되면 해당 CSV를 사용합니다.
- **기술·판정 규칙**은 이미지 표준화, 버전별 검출·분류 방식, 마릿수와 품종 비교, 지원 범위 및 Python API를 설명합니다.
- **소스 구조**는 처음에 **Java 소스 구조 → dataset**을 표시합니다. 도메인을 선택해 실제 패키지와 주요 파일, 관련 Thymeleaf 화면을 확인할 수 있습니다. **Python 소스 구조**에서는 `app/`, `source/`, `shared/`를 조회합니다.

## 6. 프로젝트 구조와 Spring MVC

~~~text
pjt_ACE_web5/
  app/
    ai_server.py           FastAPI 요청 처리와 결과 파일 저장
    detection_engine.py   main 공용 검출
    imagenet_engine.py    main-1 공용 검출·분류
    pet_engine.py         main-2 공용 검출·분류·보정
  source/
    main.py               개·고양이 검출
    main-1.py             ImageNet 품종 후보 분류
    main-2.py             웹과 같은 엔진으로 배치 추론
  shared/models/          모델 3개
  data/                  사진 40장, 정답과 출처
  outputs/               제출 시점 CLI 결과와 새 비교 CSV
  db/                    DB 생성 SQL
  scripts/
    Start.ps1             MySQL 연결 확인, Python·Spring 시작
    Stop.ps1              스크립트로 시작한 Python·Spring 종료
    compare_web.py        CLI와 웹 결과 40장 비교
  web/
    gradlew.bat           Windows Gradle Wrapper
    src/main/
      java/kr/ace/web/    도메인별 Spring MVC 소스
      resources/
        templates/       Thymeleaf 화면과 사진 이동 공통 조각
        static/          CSS, 확대와 사진 선택 동작
        db/migration/    Flyway V1 최종 스키마
  ACE_STORAGE/           실행 후 생기는 원본·검출 이미지
  docs/                  승인 범위와 구현·검증 상세
  개발기록.md             변경 이력
~~~

| 도메인 | Controller | Service | Repository | Entity / DO |
| --- | --- | --- | --- | --- |
| `dataset` | 등록, 회차, 사진별 비교·검수 요청 | 순차 처리와 진행·재시도, CSV 미리보기 | 데이터셋과 회차 조회 | `Dataset`, `AnalysisRound` / `RoundDO` |
| `analysis` | 분석 이력과 상세 | 분석 흐름과 FastAPI 호출, 저장 재시도 | 분석 실행과 검출 객체 조회·저장 | `AnalysisRun`, `DetectedPet` / `InferenceDO`, `HistoryItemDO`, `AnalysisDetailDO` |
| `media` | 이력의 원본·결과 이미지 제공 | 업로드 파일 저장, 경로 검사 | 입력 사진 메타데이터 저장 | `InputMedia` / `UploadDO`, `PhotoNavigationDO` |
| `benchmark` | 홈, 버전 비교, 검증표 | 사진별 결과와 점수 집계 | 비교 이미지와 CSV 읽기 | `BenchmarkDO` |
| `truth` | CSV 기준 확인 | 파일명·SHA-256 확인, 정답 데이터 제공 | 정답과 평가 읽기·저장 | `GroundTruth`, `GroundTruthPet`, `Evaluation` |
| `review` | CSV 정답 검수 | 정답 버전과 성공한 분석의 평가를 연결 | truth·analysis 서비스/저장소 활용 | 검수 흐름 |
| `evaluation` | — | 종별 다중집합, 오차, 품종 표기 계산 | — | `Verdict` |
| `environment` | 공통 서버 상태 API | AI 상태 조회와 DB 연결 확인 | 기존 DataSource 사용 | `ServerStatusDO` |
| `documentation` | 소스 구조·기술 안내 화면 | 도메인과 Python 폴더 설명 | — | `SourceStructureDO` |

저장 도메인에는 `controller`, `service`, `repository`, `entity` 패키지가 있습니다. CSV 조회 도메인인 benchmark는 Repository가 파일을 읽습니다. 계층 간 전달 객체가 필요한 도메인은 `model`에 DO(Data Object)를 둡니다. Entity는 DB 행에 대응하는 Java record이며 JDBC로 읽고 씁니다.

등록 요청은 Spring MVC에서 파일과 DB에 저장합니다. 분석 시작 요청은 회차를 생성하고 백그라운드에서 FastAPI를 호출합니다. 비교 화면은 저장된 결과를 Thymeleaf로 보여줍니다. Python은 예측만 담당하고, 정답과 비교하거나 이력을 저장하는 일은 Spring이 맡습니다.

## 7. 표준 입력, 40장 검증과 기준선

원본 `data/images`는 그대로 사용합니다. EXIF 방향을 반영해 읽은 뒤, 세 엔진 모두 긴 변이 640px을 넘으면 비율을 유지해 줄입니다. 작은 사진은 확대하지 않습니다. main-1·main-2의 품종 분류는 원본에서 자른 영역을 사용합니다. 박스 좌표와 결과 이미지 크기는 표준 이미지 기준입니다. 정답과 예측의 마릿수 차이는 예측 − 정답입니다.

`data/baseline/legacy-original-res`에는 제출본 결과가 있고, CLI 기본 출력은 `outputs/<버전>`입니다. 공통 비교 화면은 저장된 기준 자료를 읽으며, 표준 기준선이 확정되기 전에는 제출본 수치를 표시합니다.

1. 웹에서 `data/images` 40장을 등록해 세 버전 분석을 끝냅니다. 주소의 `/rounds/` 다음 부분이 회차 UUID입니다.
2. 프로젝트 루트에서 아래 명령을 실행합니다. 기존에 없는 증거 폴더를 지정합니다.

~~~powershell
.\.venv\Scripts\python.exe .\scripts\compare_web.py --round '회차-UUID' --output .\docs\golden
ound1
~~~

3. 두 번째 회차도 완료한 뒤 `round2`로 같은 작업을 하고 비교합니다.

~~~powershell
.\.venv\Scripts\python.exe .\scripts\check_golden.py .\docs\golden
ound1 .\docs\golden
ound2
~~~

4. 120건의 입력 해시·크기·마릿수·검출 상세가 일치하는지 확인합니다. 같은 코드의 두 회차에 실수 차이가 있다면 관측값과 허용 오차를 기록합니다. main-1·main-2의 마릿수와 품종 CSV를 제출본과 대조하고, main의 변경 건수와 새 일치 건수를 기록합니다. 기준 확정 후 다음을 실행합니다.

~~~powershell
.\.venv\Scripts\python.exe .\scripts\publish_baseline.py .\docs\golden
ound1
~~~

비교 지표에서 MAE는 평가한 사진의 종류별 절대 오차 평균입니다. ‘절대 오차 증가’는 같은 사진의 두 종류 절대 오차 합계를 앞 버전과 비교합니다. 품종 전체 점수는 정답 개체가 분모이며, 미지원 품종도 포함합니다. 견종의 전체 지원 범위를 아직 확인하지 못한 점수는 **매핑 확인 필요**로 표시합니다. `scripts/export_breed_vocabulary.py`로 실제 라벨을 내보내고 번역표를 보완할 수 있습니다. 홀드아웃 자료는 [준비 안내](data/holdout/README.md)를 참고하세요.

## 8. 오류 해결

| 증상 | 확인할 곳 |
| --- | --- |
| `py -3.11` 실행 실패 | Python 3.11과 Windows Python Launcher 설치 상태 |
| `ModuleNotFoundError` | `.venv\Scripts\python.exe`로 실행했는지, `pip check` 결과 |
| 모델 파일 오류 | `shared\models`에 세 모델이 모두 있는지 |
| `Access denied`, `Unknown database` | MySQL 계정·비밀번호·DB 생성 및 권한 |
| 8080·8090 포트 사용 중 | `netstat -ano`로 사용 중인 포트 확인. Spring은 `ACE_WEB_PORT`, AI는 Uvicorn `--port`로 변경하고 Spring의 `ACE_AI_URL`도 맞춤 |
| 공유 폴더 오류 | 두 창의 `ACE_STORAGE`가 같은 위치인지, Spring을 `web`에서 실행했는지 |
| 글꼴이 깨짐 | Windows 맑은 고딕 확인. 다른 한글 글꼴 사용 시 AI 창에 `ACE_LABEL_FONT`를 글꼴 파일 절대 경로로 지정 |
| 결과 저장 실패 | MySQL을 확인하고 이력에서 **같은 실행 다시 시도** |
| Gradle 첫 다운로드 실패 | Gradle 배포 서버와 Maven Central 접속 및 JDK 17 확인 |

초기 구현은 [ACE_Web_Implementation.md](docs/ACE_Web_Implementation.md), 3차 변경과 확인 범위는 [ACE_Web_Revision_3_Implementation.md](docs/ACE_Web_Revision_3_Implementation.md)를 참고하세요.

## 9. DB 설계 자료 보기

웹 서버를 실행한 뒤 상단 **DB 설계** 메뉴를 선택하거나 [DB 설계](http://127.0.0.1:8080/db-design)를 엽니다. 한글 개념 E-R 다이어그램, MySQL 물리 ERD, 테이블별 컬럼과 JSON 항목을 볼 수 있습니다. 다이어그램을 누르면 원본 크기로 열리고 **Excel 정의서 다운로드**를 누르면 정의서를 받을 수 있습니다.

오프라인에서는 `web\src\main\resources\static\db-design` 폴더에서 PNG와 Excel 파일을 직접 엽니다. 설계 자료는 ACE의 Flyway `V1__init.sql`을 기준으로 작성했습니다. 프로젝트를 다시 빌드하면 설계 화면과 파일도 JAR에 포함됩니다.


## 10. IntelliJ 실행과 테스트

### 이번 변경 화면 확인

화면별 변경과 검수 저장 흐름은 [사진 비교·검수 구현 문서](docs/ACE_Photo_Review_Implementation.md)에서 볼 수 있습니다.

1. `web` 폴더에서 `.\gradlew.bat clean test bootJar`를 실행하고 서버를 켭니다.
2. **버전 비교**에서 `img_029.jpg`를 엽니다. 정답 품종 두 개, 사진 4장과 품종 후보 표를 확인합니다. 01번에서는 이전, 40번에서는 다음 이동이 비활성화됩니다.
3. **사진 분석**에서 오른쪽 안내에 따라 `data/images`의 기준 사진을 등록하고 분석합니다. 분석 회차에서 사진을 선택해 확대와 사진 이동을 확인합니다.
4. **분석 이력**에서 같은 사진의 세 모델 행이 같은 회차별 사진 상세로 열리는지 확인합니다. 검수 저장 후 새로고침하고, 해당 사진은 **검수완료**, 다른 미검수 사진은 **미검수**인지 확인합니다.
5. 같은 이름으로 내용이 다른 사진을 등록해 CSV 정답을 연결하지 않는지 확인합니다.

### 화면 개선판 확인 순서

1. 데이터셋 상세에서 우측 **사진 분석 안내**와 기존 회차 열기를 확인합니다.
2. **분석 이력**의 전체·일치·오답·실패 필터에서 대표 기록을 확인합니다. 실패 행 두 건을 선택한 뒤 취소하고, 다시 한 건을 선택해 삭제합니다. 삭제한 행만 목록에서 빠지고 회차에는 해당 모델에 **삭제됨**이 표시되는지 확인합니다.
3. 정상 상태에서 **서버 환경**을 연 뒤 Python만 종료해 **다시 확인**을 누릅니다. 웹·DB와 Python 상태가 개별적으로 표시되는지 확인하고, Python을 다시 시작해 회복 상태를 확인합니다.
4. **소스 구조**가 Java부터 열리는지 확인하고 Python 탭으로 이동합니다. **성능 검증**에서 기준 사진을 선택해 비교 화면으로 이동하고 **기술·판정 규칙**을 확인합니다.

### IntelliJ 설정

- `web/build.gradle`을 Gradle 프로젝트로 열고 Gradle JVM은 JDK 17로 지정합니다. Java 메인 클래스는 `kr.ace.web.AceApplication`, 작업 디렉터리는 프로젝트의 `web`입니다.
- Python 실행 구성은 프로젝트 루트를 작업 디렉터리로 지정하고 `.venv` 인터프리터로 `uvicorn` 모듈을 실행합니다. 인수는 `app.ai_server:app --host 127.0.0.1 --port 8090`입니다.
- DB 비밀번호는 실행 환경변수 `ACE_DB_PASSWORD`나 `web/application-local.properties`의 `spring.datasource.password`로 지정합니다. 로컬 파일은 Git에서 제외합니다.
- 두 서버가 켜지면 [http://127.0.0.1:8080](http://127.0.0.1:8080)에 접속합니다. AI가 꺼져 있으면 분석 회차를 만들지 않습니다. 처리 도중 연결이 끊기면 서버를 다시 켠 뒤 대기 중 분석을 계속하고, 실패 상태는 일괄 재시도할 수 있습니다.

~~~powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest
Set-Location .\web
.\gradlew.bat clean test bootJar
~~~

이번 변경과 검증 조건은 [개선판 구현 기록](docs/ACE_Web_Improvement_Implementation.md)을 확인하세요.

| 설정 | 기본값 또는 용도 |
| --- | --- |
| ACE_DB_URL / ACE_DB_USER / ACE_DB_PASSWORD | 로컬 ace_web / ace_app / 사용자 비밀번호 |
| ACE_WEB_PORT | 8080 |
| ACE_AI_URL | http://127.0.0.1:8090 |
| ACE_STORAGE | 프로젝트 루트의 ACE_STORAGE. 두 서버에서 같은 경로를 지정 |
| ACE_PROJECT_ROOT | Spring 작업 폴더가 web일 때 상위 폴더 |
| ACE_BASELINE_DIR | 프로젝트 루트 기준 data/baseline |

IntelliJ에서 실제 환경값을 입력한 뒤 실행 구성의 **Store as project file**을 선택하면 팀과 구성을 공유할 수 있습니다. 비밀번호는 공유 실행 구성에 넣지 마세요. 전체 Gradle 빌드와 MySQL·AI 통합 실행, 120건 골든, 홀드아웃 실측은 현장 확인 항목입니다.


## WEB7 변경사항 (2026-11-16)

- WEB6의 전체 프로젝트와 기존 기능을 유지하며 웹 UI를 확장했습니다.
- 상단 공통 헤더의 ☾/☀ 버튼으로 라이트/다크 테마를 전환합니다. 선택은 브라우저 localStorage(`ace-theme`)에 저장됩니다.
- 회차별 사진 상세(`/rounds/{id}/photos/{inputId}`): 원본·main·main-1·main-2 이미지를 가로 4열로 표시합니다. 이미지 오른쪽 상단의 중복 비고는 만들지 않습니다.
- main/main-1/main-2 버전별 비고, 사진 전체 비고를 입력 후 **비고 저장** 버튼으로 MySQL에 저장할 수 있습니다. 검수 저장과 별개입니다.
- **품종 별칭 규칙 안내** 팝업, **품종 별칭 규칙 조회** 및 상세 팝업을 추가했습니다. API: `GET /api/breed-aliases?q=허스키`. 조회 데이터는 프로젝트의 실제 CSV를 사용합니다(미등록 별칭을 임의 생성하지 않음).
- `V2__photo_notes.sql` Flyway 마이그레이션이 DB에 `photo_note` 테이블을 추가합니다. 기존 DB는 DROP하지 않습니다.
- `application-local.properties`는 민감 정보가 있으므로 ZIP에 포함되지 않습니다. 기존 환경변수 `ACE_DB_PASSWORD` 또는 로컬 설정을 사용하세요.
- 프로젝트를 `C:\LSK\pjt_ACE_web7`에 압축 해제하고 `web` 폴더에서 `gradlew.bat bootJar`를 실행합니다. 기존 WEB6과 동시 실행하는 경우 8080 포트 충돌을 주의하세요.

## main-3 · ViT-B/16 비교 모델

AI 담당자가 제공한 main-3 실행 예시를 설계 참고로 사용했습니다. 위치 검출은 YOLO11n, 잘라낸 개체의 견종·종류 확인은 Torchvision ViT-B/16, 고양이 12품종 분류는 기존 Oxford MobileNetV3가 담당합니다. ViT는 객체 검출기가 아니며 ImageNet의 개 품종 118개와 고양이 일반 범주 5개를 사용합니다. 품종·종류 후보는 수기 정답과 별도로 확인해야 합니다.

~~~powershell
.\.venv\Scripts\python.exe scripts\download_vit.py
.\.venv\Scripts\python.exe source\main-3.py --input data\images --output outputs\main-3
~~~

가중치는 Torchvision 공식 배포본으로 내려받고 SHA-256을 확인합니다. 용량 때문에 GitHub에는 넣지 않습니다. 새 웹 회차는 40장 기준 최대 160건이며, 기존 회차의 세 버전 결과는 그대로 남습니다. 기존 기준선의 main-3 값은 미측정으로 표시됩니다.
