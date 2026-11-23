-- Spring Boot를 종료한 뒤 MySQL 관리자 계정으로 실행한다.
-- ACE_STORAGE는 두 서버를 종료하고 별도 백업 폴더로 옮긴 뒤 새 저장소를 사용한다.
-- 기존 분석·정답·평가 기록이 삭제된다.
DROP DATABASE IF EXISTS ace_web;
CREATE DATABASE ace_web CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
