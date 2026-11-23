"""ACE 배치 분석. 웹과 같은 추론기를 사용해 기존 CSV를 생성한다."""

import argparse
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from app.vit_engine import ViTEngine, TARGETS
from shared.common import image_files, save_csv, save_image, standard_objects

FIELDS = ["filename", "object_no", "detected_species", "det_confidence",
          "class_label", "class_confidence", "species_match"]


def main():
    parser = argparse.ArgumentParser(description="개·고양이 검출 및 품종 후보 분류")
    parser.add_argument("--input", default=str(ROOT / "data/images"))
    parser.add_argument("--output", type=Path, default=ROOT / "outputs/main-3")
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--details-json", type=Path, help="검증용 객체 좌표와 분류 결과를 JSON으로 저장")
    args = parser.parse_args()
    files = image_files(args.input)
    if not files:
        parser.error("처리할 사진이 없습니다.")
    started = time.perf_counter()
    engine = ViTEngine()
    images_started = time.perf_counter()
    counts_rows, class_rows = [], []
    details = {}
    for path in files:
        marked, counts, objects = engine.analyze(path, args.conf)
        if args.details_json:
            details[path.name] = standard_objects(objects, marked.shape[1], marked.shape[0])
        save_image(args.output / f"{path.stem}_pets.jpg", marked)
        counts_rows.append({"filename": path.name, **counts})
        for item in objects:
            class_rows.append({"filename": path.name, **{field: item[field] for field in FIELDS[1:]}})
        print(f"{path.name}: 고양이 {counts['cat']}마리, 개 {counts['dog']}마리")
    save_csv(args.output / "pet_counts.csv", ["filename", *TARGETS], counts_rows)
    save_csv(args.output / "pet_classifications.csv", FIELDS, class_rows)
    if args.details_json:
        args.details_json.parent.mkdir(parents=True, exist_ok=True)
        args.details_json.write_text(json.dumps(details, ensure_ascii=False), encoding="utf-8")
    print("\n사진별 분류 결과 (YOLO 검출 + ViT-B/16 견종·종류 확인 + Oxford 12종 묘종)")
    for row in class_rows:
        species = "고양이" if row["detected_species"] == "cat" else "개"
        confidence = row["class_confidence"]
        if confidence is None:
            label = "분류 실패"
        else:
            label = f"{row['class_label']} {confidence:.0%}"
            if row["species_match"] == "no":
                label = f"불확실: 분류 후보 {label} (검출 종류와 다름)"
        print(f"{row['filename']} | #{row['object_no']} {species}: {label}")
    print(f"사진 {len(files)}장 처리 및 저장: {time.perf_counter() - images_started:.2f}초")
    print(f"모델 로드 포함 전체: {time.perf_counter() - started:.2f}초")
    print(f"결과 위치: {args.output}")


if __name__ == "__main__":
    main()
