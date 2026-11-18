"""05: 반려동물 종류(개/고양이)를 사진에서 탐지."""
from pathlib import Path
import sys
import argparse
import json

# 현재 파일이 source/main.py에 있으므로, 두 단계 위 폴더를 프로젝트 루트로 사용합니다.
ROOT = Path(__file__).resolve().parents[1]

# 프로젝트 안에 있는 shared 패키지를 찾을 수 있도록 검색 경로에 추가합니다.
sys.path.insert(0, str(ROOT))
from shared.common import image_files, save_image, save_csv, standard_objects
from app.detection_engine import DetectionEngine, TARGETS


def main():

    # 1. 실행 옵션을 읽습니다. --input은 사진 폴더, --conf는 검출에 필요한 최소 신뢰도입니다.
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", default=str(ROOT / "data/images"))
    ap.add_argument("--conf", type=float, default=0.25)
    ap.add_argument("--output", type=Path, default=ROOT / "outputs/main")
    ap.add_argument("--details-json", type=Path)
    args = ap.parse_args()

    # 2. YOLO 모델을 불러오고, 모델의 클래스 번호 중 고양이(cat)와 개(dog)에 해당하는 번호를 찾습니다.
    engine = DetectionEngine()

    # 3. 결과 폴더를 준비합니다. outputs 폴더가 없어도 함께 만들고, 이미 있으면 그대로 사용합니다.
    output_dir = args.output
    output_dir.mkdir(parents=True, exist_ok=True)

    # 사진별 집계 결과를 모아 둘 목록입니다. 사진 한 장이 CSV의 한 행이 됩니다.
    rows = []
    details = {}

    # 4. 입력 폴더의 사진을 한 장씩 처리합니다.
    for path in image_files(args.input):

        marked, counts, objects = engine.analyze(path, args.conf)
        if args.details_json:
            details[path.name] = standard_objects(objects, marked.shape[1], marked.shape[0])
        save_image(output_dir / f"{path.stem}_pets.jpg", marked)
        rows.append({"filename": path.name, **counts})
        print(path.name, counts)

    # 6. 모든 사진을 처리한 뒤 filename, cat, dog 열을 가진 CSV 파일을 저장합니다.
    save_csv(output_dir / "pet_counts.csv", ["filename", *TARGETS], rows)
    if args.details_json:
        args.details_json.parent.mkdir(parents=True, exist_ok=True)
        args.details_json.write_text(json.dumps(details, ensure_ascii=False), encoding="utf-8")

# 이 파일을 직접 실행할 때만 main()을 호출합니다.
if __name__ == "__main__":
    main()
