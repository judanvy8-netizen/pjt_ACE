"""사진 20장의 개·고양이를 검출하고, 검출된 동물을 분류해 저장한다."""

import argparse
import math
from pathlib import Path
import sys
import time

import cv2
from ultralytics import YOLO


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from shared.common import image_files, read_image, save_csv, save_image, yolo


TARGETS = ("cat", "dog")
MAX_SIDE = 640
CLASSIFY_SIZE = 224
IGNORED_CLASS_IDS = (286, 287)  # cougar, lynx
CLASS_MODEL = ROOT / "shared/models/yolo11n-cls.pt"
OUTPUT_DIR = ROOT / "outputs/main-1"
BOX_COLORS = ((255, 100, 100), (100, 220, 100), (100, 200, 255), (255, 100, 220))


def resize_for_detection(original):
    """원본 파일을 저장하지 않고 메모리에서만 긴 변을 640픽셀로 줄인다."""
    height, width = original.shape[:2]
    scale = min(1.0, MAX_SIDE / max(width, height))
    if scale == 1.0:
        return original.copy()
    size = (max(1, round(width * scale)), max(1, round(height * scale)))
    return cv2.resize(original, size, interpolation=cv2.INTER_AREA)


def crop_from_original(original, box, small_shape, margin=0.1):
    """축소 사진에서 찾은 박스 위치를 원본 좌표로 바꾸어 동물만 자른다."""
    original_h, original_w = original.shape[:2]
    small_h, small_w = small_shape[:2]
    x1, y1, x2, y2 = box
    pad_x = (x2 - x1) * margin
    pad_y = (y2 - y1) * margin
    ratio_x = original_w / small_w
    ratio_y = original_h / small_h
    left = max(0, math.floor((x1 - pad_x) * ratio_x))
    top = max(0, math.floor((y1 - pad_y) * ratio_y))
    right = min(original_w, math.ceil((x2 + pad_x) * ratio_x))
    bottom = min(original_h, math.ceil((y2 + pad_y) * ratio_y))
    return original[top:bottom, left:right]


def square_pad(image):
    """분류할 때 동물이 잘리지 않도록 짧은 쪽에 여백을 붙인다."""
    height, width = image.shape[:2]
    side = max(height, width)
    top = (side - height) // 2
    left = (side - width) // 2
    return cv2.copyMakeBorder(
        image, top, side - height - top, left, side - width - left,
        cv2.BORDER_CONSTANT, value=(114, 114, 114),
    )


def class_matches_species(index, species, names):
    """현재 ImageNet 모델에서 검출된 동물과 분류 이름의 종류가 맞는지 확인한다."""
    if len(names) != 1000:
        return None
    if species == "dog":
        return 151 <= index <= 268
    return 281 <= index <= 285


def best_class_without_ignored(probs):
    """286·287번을 제외하고 가장 점수가 높은 분류 후보를 고른다."""
    scores = probs.data
    filtered = scores.clone()
    for index in IGNORED_CLASS_IDS:
        if index < len(filtered):
            filtered[index] = -1
    best = int(filtered.argmax().item())
    return best, float(scores[best].item())


def draw_class_label(image, box, number, species, class_name, confidence, used_labels):
    """CSV의 object_no와 같은 번호를 박스와 분류 이름에 표시한다."""
    height, width = image.shape[:2]
    x1, y1, x2, y2 = (round(v) for v in box)
    color = BOX_COLORS[(number - 1) % len(BOX_COLORS)]
    cv2.rectangle(image, (x1, y1), (x2, y2), color, 2)
    short_name = class_name.replace("_", " ")[:25]
    text = f"#{number} {species}: {short_name}"
    if confidence is not None:
        text += f" {confidence:.0%}"
    font = cv2.FONT_HERSHEY_SIMPLEX
    font_scale = 0.43
    thickness = 1
    (text_w, text_h), baseline = cv2.getTextSize(text, font, font_scale, thickness)
    label_w = text_w + 6
    label_h = text_h + baseline + 6
    candidates = (
        (x1, y1 - label_h - 2), (x1, y1 + 2),
        (x2 - label_w, y1 + 2), (x1, y2 + 2),
        (x2 - label_w, y2 - label_h - 2),
    )
    for left, top in candidates:
        left = max(0, min(left, width - label_w))
        top = max(0, min(top, height - label_h))
        right, bottom = left + label_w, top + label_h
        if all(right <= a or left >= c or bottom <= b or top >= d
               for a, b, c, d in used_labels):
            break
    used_labels.append((left, top, right, bottom))
    cv2.rectangle(image, (left, top), (right, bottom), (30, 30, 30), -1)
    cv2.rectangle(image, (left, top), (right, bottom), color, 1)
    cv2.putText(image, text, (left + 3, top + text_h + 3),
                font, font_scale, (255, 255, 255), thickness, cv2.LINE_AA)


def main():
    parser = argparse.ArgumentParser(description="개·고양이 검출과 ImageNet 분류")
    parser.add_argument("--input", default=str(ROOT / "data/images"))
    parser.add_argument("--conf", type=float, default=0.25)
    args = parser.parse_args()

    if not CLASS_MODEL.is_file():
        raise FileNotFoundError(f"분류 모델 파일이 없습니다: {CLASS_MODEL}")

    total_started = time.perf_counter()
    detector = yolo()
    classifier = YOLO(str(CLASS_MODEL))
    if classifier.task != "classify":
        raise ValueError("분류용 YOLO 모델이 필요합니다.")
    class_ids = [index for index, name in detector.names.items() if name in TARGETS]
    files = image_files(args.input)
    if not files:
        raise ValueError(f"처리할 사진이 없습니다: {args.input}")

    counts_rows = []
    class_rows = []
    classification_summaries = []
    images_started = time.perf_counter()
    for path in files:
        original = read_image(path)
        small = resize_for_detection(original)
        result = detector.predict(
            small, classes=class_ids, conf=args.conf, device="cpu", verbose=False,
        )[0]
        marked = small.copy()
        used_labels = []
        counts = {name: 0 for name in TARGETS}
        labels = {name: [] for name in TARGETS}

        for number, box in enumerate(result.boxes, 1):
            species = result.names[int(box.cls[0].item())]
            counts[species] += 1
            coords = [float(value) for value in box.xyxy[0].tolist()]
            crop = crop_from_original(original, coords, small.shape)
            if crop.size == 0:
                labels[species].append((number, "분류 실패"))
                draw_class_label(marked, coords, number, species, "class failed", None, used_labels)
                class_rows.append({
                    "filename": path.name, "object_no": number,
                    "detected_species": species,
                    "det_confidence": round(float(box.conf[0].item()), 4),
                    "class_label": "", "class_confidence": "",
                    "species_match": "crop_empty",
                })
                continue
            class_result = classifier.predict(
                square_pad(crop), imgsz=CLASSIFY_SIZE, device="cpu", verbose=False,
            )[0]
            best, class_confidence = best_class_without_ignored(class_result.probs)
            class_name = str(class_result.names[best])
            matched = class_matches_species(best, species, class_result.names)
            readable_name = class_name.replace("_", " ")
            if matched is False:
                labels[species].append((
                    number, f"불확실: 분류 후보 {readable_name} {class_confidence:.0%} (검출 종류와 다름)"
                ))
            else:
                labels[species].append((number, f"{readable_name} {class_confidence:.0%}"))
            display_name = class_name if matched is not False else "class unclear"
            draw_class_label(
                marked, coords, number, species, display_name,
                class_confidence if matched is not False else None, used_labels,
            )
            class_rows.append({
                "filename": path.name,
                "object_no": number,
                "detected_species": species,
                "det_confidence": round(float(box.conf[0].item()), 4),
                "class_label": class_name,
                "class_confidence": round(class_confidence, 4),
                "species_match": "yes" if matched is True else "no" if matched is False else "unknown",
            })

        save_image(OUTPUT_DIR / f"{path.stem}_pets.jpg", marked)
        counts_rows.append({"filename": path.name, **counts})
        classification_summaries.append((path.name, labels))
        print(f"{path.name}: 고양이 {counts['cat']}마리, 개 {counts['dog']}마리")

    save_csv(OUTPUT_DIR / "pet_counts.csv", ["filename", *TARGETS], counts_rows)
    save_csv(
        OUTPUT_DIR / "pet_classifications.csv",
        ["filename", "object_no", "detected_species", "det_confidence",
         "class_label", "class_confidence", "species_match"],
        class_rows,
    )
    print("\n사진별 분류 결과 (ImageNet 종류 후보)")
    print("분류 후보에서 286(쿠거)·287(스라소니) 제외")
    print("불확실 = 검출 종류와 분류 1순위가 다름. 확률 기준은 적용하지 않음.")
    for filename, labels in classification_summaries:
        cat_count = len(labels["cat"])
        dog_count = len(labels["dog"])
        print(f"\n{filename} | 고양이 {cat_count}마리, 개 {dog_count}마리")
        for species, korean_name in (("cat", "고양이"), ("dog", "개")):
            for number, label in labels[species]:
                print(f"  검출 #{number} {korean_name}: {label}")
        if cat_count == 0 and dog_count == 0:
            print("  개·고양이: 없음")
    print(f"사진 {len(files)}장 처리 및 저장: {time.perf_counter() - images_started:.2f}초")
    print(f"모델 로드 포함 전체: {time.perf_counter() - total_started:.2f}초")
    print(f"결과 위치: {OUTPUT_DIR}")
    print("분류 이름은 ImageNet 모델 결과이며, 확정된 반려동물 품종은 아님.")


if __name__ == "__main__":
    main()
