"""사진을 검출한 뒤 개와 고양이를 YOLO 분류 모델로 분류한다."""

import argparse
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from app.imagenet_engine import ImageNetEngine, OUTPUT_DIR, TARGETS
from shared.common import image_files, save_csv, save_image, standard_objects


def main():
    # 1. 실행 옵션을 읽는다. --conf는 검출 점수의 최소 기준이다.
    parser = argparse.ArgumentParser(description="개·고양이 검출과 ImageNet 분류")
    parser.add_argument("--input", default=str(ROOT / "data/images"))
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--output", type=Path, default=OUTPUT_DIR)
    parser.add_argument("--details-json", type=Path)
    args = parser.parse_args()

    total_started = time.perf_counter()
    engine = ImageNetEngine()
    files = image_files(args.input)
    if not files:
        raise ValueError(f"처리할 사진이 없습니다: {args.input}")

    counts_rows = []
    class_rows = []
    details = {}
    classification_summaries = []
    images_started = time.perf_counter()

    # 3. 사진마다 축소본에서 개와 고양이의 위치를 찾는다.
    for path in files:
        marked, counts, objects, labels = engine.process(path, args.conf)
        if args.details_json:
            details[path.name] = standard_objects(objects, marked.shape[1], marked.shape[0])
        for item in objects:
            item.pop("box")
        class_rows.extend(objects)

        # 검출 박스를 그린 사진과 사진별 마릿수 기록을 남긴다.
        save_image(args.output / f"{path.stem}_pets.jpg", marked)
        counts_rows.append({"filename": path.name, **counts})
        classification_summaries.append((path.name, labels))
        print(f"{path.name}: 고양이 {counts['cat']}마리, 개 {counts['dog']}마리")

    # 5. 모든 사진을 처리한 뒤 마릿수 CSV와 객체별 분류 CSV를 저장한다.
    save_csv(args.output / "pet_counts.csv", ["filename", *TARGETS], counts_rows)
    save_csv(
        args.output / "pet_classifications.csv",
        [
            "filename",
            "object_no",
            "detected_species",
            "det_confidence",
            "class_label",
            "class_confidence",
            "species_match",
        ],
        class_rows,
    )
    print("\n사진별 분류 결과 (ImageNet 종류 후보)")
    print("ImageNet 고양이 후보에서 286(쿠거)·287(스라소니) 제외")
    print("불확실 = 검출 종류와 분류 1순위가 다름. 확률 기준은 적용하지 않음.")
    # 콘솔에는 CSV와 같은 객체 번호로 사진별 분류 결과를 표시한다.
    for filename, labels in classification_summaries:
        cat_count = len(labels["cat"])
        dog_count = len(labels["dog"])
        print(f"\n{filename} | 고양이 {cat_count}마리, 개 {dog_count}마리")
        detected = [
            (number, korean_name, label)
            for species, korean_name in (("cat", "고양이"), ("dog", "개"))
            for number, label in labels[species]
        ]
        for number, korean_name, label in sorted(detected):
            print(f"  검출 #{number} {korean_name}: {label}")
        if cat_count == 0 and dog_count == 0:
            print("  개·고양이: 없음")
    print(f"사진 {len(files)}장 처리 및 저장: {time.perf_counter() - images_started:.2f}초")
    print(f"모델 로드 포함 전체: {time.perf_counter() - total_started:.2f}초")
    print(f"결과 위치: {args.output}")
    print("분류 이름은 ImageNet 모델 결과이며, 확정된 반려동물 품종은 아님.")
    if args.details_json:
        args.details_json.parent.mkdir(parents=True, exist_ok=True)
        args.details_json.write_text(json.dumps(details, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
