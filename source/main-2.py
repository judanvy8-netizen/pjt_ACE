"""사진을 검출한 뒤 개는 YOLO, 고양이는 Oxford 모델로 분류한다."""

import argparse
import math
from pathlib import Path
import sys
import time

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import torch
from torchvision import transforms
from torchvision.models import mobilenet_v3_large
from ultralytics import YOLO


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from shared.common import image_files, read_image, save_csv, save_image, yolo


TARGETS = ("cat", "dog")
MAX_SIDE = 640
CLASSIFY_SIZE = 224
DOG_CLASS_MODEL = ROOT / "shared/models/yolo11n-cls.pt"
CAT_CLASS_MODEL = ROOT / "shared/models/cat_oxford_mobilenetv3.pt"
# 공개 Oxford 고양이 데이터셋의 클래스 순서. 원본의 Abyssunian 오타만 표시할 때 바로잡음.
CAT_CLASS_NAMES = (
    "Abyssinian", "Bengal", "Birman", "Bombay", "British Shorthair",
    "Egyptian Mau", "Maine Coon", "Persian", "Ragdoll", "Russian Blue",
    "Siamese", "Sphynx",
)
CAT_TRANSFORM = transforms.Compose((
    transforms.Resize(256),
    transforms.CenterCrop(CLASSIFY_SIZE),
    transforms.ToTensor(),
    transforms.Normalize((0.485, 0.456, 0.406), (0.229, 0.224, 0.225)),
))
OUTPUT_DIR = ROOT / "outputs/main-2"
BOX_COLORS = ((255, 100, 100), (100, 220, 100), (100, 200, 255), (255, 100, 220))
# KKF 견종명과 한국애묘인연합의 TICA 한국어 표기를 우선 사용한다.
# 품종 외 ImageNet 후보나 국내 표기를 확인하지 못한 이름은 따로 표시한다.
MODEL_LABEL_KO = {
    "Abyssinian": "아비시니안", "Airedale": "에어데일 테리어",
    "Angora": "앙고라 토끼(개·고양이 품종 아님)", "basenji": "바센지",
    "Bengal": "뱅갈", "Birman": "버만", "bull_mastiff": "불마스티프",
    "cocker_spaniel": "잉글리쉬 코커 스파니엘",
    "dingo": "딩고(개 품종 후보 밖)",
    "Egyptian Mau": "이집션마우",
    "guinea_pig": "기니피그(개·고양이 품종 아님)",
    "Labrador_retriever": "래브라도 리트리버", "Lhasa": "라사 압소",
    "Maine Coon": "메인쿤", "Newfoundland": "뉴펀들랜드",
    "Ragdoll": "랙돌", "Saluki": "살루키",
    "Siamese_cat": "샴 고양이",
    "totem_pole": "토템 기둥(개·고양이 품종 아님)",
    "Walker_hound": "워커 폭스하운드(국내 공식명 미확인)",
    "Bombay": "봄베이", "British Shorthair": "브리티시 쇼트헤어",
    "Persian": "페르시안", "Russian Blue": "러시안 블루",
    "Siamese": "샴", "Sphynx": "스핑크스",
}
LABEL_FONT = ImageFont.truetype("C:/Windows/Fonts/malgun.ttf", 15)


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


def dog_class_matches(index, names):
    """ImageNet 분류 후보가 개 품종 범위에 속하는지 확인한다."""
    if len(names) != 1000:
        return None
    return 151 <= index <= 268


def best_class(probs):
    """개 분류 모델에서 가장 점수가 높은 후보를 고른다."""
    scores = probs.data
    best = int(scores.argmax().item())
    return best, float(scores[best].item())


def load_cat_classifier():
    """공개된 Oxford 고양이 12종 MobileNetV3 가중치를 읽는다."""
    if not CAT_CLASS_MODEL.is_file():
        raise FileNotFoundError(f"고양이 분류 모델 파일이 없습니다: {CAT_CLASS_MODEL}")
    saved = torch.load(CAT_CLASS_MODEL, map_location="cpu", weights_only=True)
    model = mobilenet_v3_large(weights=None, num_classes=len(CAT_CLASS_NAMES))
    model.load_state_dict({key.removeprefix("model."): value
                           for key, value in saved.items()}, strict=True)
    model.eval()
    return model


def classify_cat(model, crop):
    """검출된 고양이 영역에서 Oxford 12품종 중 한 후보를 고른다."""
    rgb = cv2.cvtColor(square_pad(crop), cv2.COLOR_BGR2RGB)
    image = CAT_TRANSFORM(Image.fromarray(rgb)).unsqueeze(0)
    with torch.inference_mode():
        scores = model(image).softmax(dim=1)[0]
    index = int(scores.argmax().item())
    return CAT_CLASS_NAMES[index], float(scores[index].item())


def korean_label(name):
    """모델의 영문 후보 이름을 결과표에서 읽기 쉬운 한국어로 표시한다."""
    return MODEL_LABEL_KO.get(name, f"번역 미등록({name})")


def draw_class_label(image, box, number, species, class_name, confidence, used_labels):
    """CSV의 object_no와 같은 번호를 박스와 분류 이름에 표시한다."""
    height, width = image.shape[:2]
    x1, y1, x2, y2 = (round(v) for v in box)
    color = BOX_COLORS[(number - 1) % len(BOX_COLORS)]
    cv2.rectangle(image, (x1, y1), (x2, y2), color, 2)
    korean_species = "고양이" if species == "cat" else "개"
    text = f"#{number} {korean_species}: {class_name}"
    if confidence is not None:
        text += f" {confidence:.0%}"
    text_box = LABEL_FONT.getbbox(text)
    text_w = text_box[2] - text_box[0]
    text_h = text_box[3] - text_box[1]
    label_w = text_w + 6
    label_h = text_h + 6
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
    label_image = Image.fromarray(cv2.cvtColor(image[top:bottom, left:right], cv2.COLOR_BGR2RGB))
    ImageDraw.Draw(label_image).text((3 - text_box[0], 3 - text_box[1]), text,
                                     font=LABEL_FONT, fill=(255, 255, 255))
    image[top:bottom, left:right] = cv2.cvtColor(np.asarray(label_image), cv2.COLOR_RGB2BGR)


def box_iou(first, second):
    """두 박스가 차지하는 영역이 얼마나 같은지 계산한다."""
    x1 = max(first[0], second[0])
    y1 = max(first[1], second[1])
    x2 = min(first[2], second[2])
    y2 = min(first[3], second[3])
    intersection = max(0, x2 - x1) * max(0, y2 - y1)
    first_area = max(0, first[2] - first[0]) * max(0, first[3] - first[1])
    second_area = max(0, second[2] - second[0]) * max(0, second[3] - second[1])
    union = first_area + second_area - intersection
    return intersection / union if union else 0.0


def remove_duplicate_boxes(candidates):
    """거의 같은 위치를 개와 고양이로 이중 검출한 경우 한 박스만 남긴다."""
    kept = []
    for candidate in candidates:
        duplicate_at = next((index for index, previous in enumerate(kept)
                             if previous["species"] != candidate["species"]
                             and box_iou(previous["coords"], candidate["coords"]) >= 0.90), None)
        if duplicate_at is None:
            kept.append(candidate)
            continue

        previous = kept[duplicate_at]
        cat = candidate if candidate["species"] == "cat" else previous
        dog = candidate if candidate["species"] == "dog" else previous
        if dog["matched"] is False:
            winner = cat
        elif (abs(cat["det_confidence"] - dog["det_confidence"]) <= 0.05
              and cat["class_confidence"] is not None
              and dog["class_confidence"] is not None
              and cat["class_confidence"] - dog["class_confidence"] >= 0.20):
            winner = cat
        else:
            winner = max((previous, candidate), key=lambda item: item["det_confidence"])
        kept[duplicate_at] = winner
    return kept


def split_wide_cat_box(detector, small, original, candidate, cat_id, cat_classifier):
    """넓은 고양이 박스를 다시 살펴 두 마리가 따로 검출될 때만 나눈다."""
    if candidate["species"] != "cat":
        return [candidate]
    x1, y1, x2, y2 = candidate["coords"]
    width, height = x2 - x1, y2 - y1
    if width < 120 or height < 80 or width / height < 1.5:
        return [candidate]

    left, top, right, bottom = (round(value) for value in (x1, y1, x2, y2))
    middle = round((left + right) / 2)
    if middle <= left or middle >= right:
        return [candidate]

    tiles = ((small[top:bottom, left:middle], left),
             (small[top:bottom, middle:right], middle))
    tile_boxes = []
    for tile, offset_x in tiles:
        found = detector.predict(
            tile, classes=[cat_id], conf=0.40, iou=0.40,
            imgsz=640, device="cpu", verbose=False,
        )[0]
        if len(found.boxes) == 0:
            return [candidate]
        best = found.boxes[0]
        local = [float(value) for value in best.xyxy[0].tolist()]
        tile_boxes.append({
            "coords": [local[0] + offset_x, local[1] + top,
                       local[2] + offset_x, local[3] + top],
            "det_confidence": float(best.conf[0].item()),
        })

    # 전체 사진을 더 크게 보았을 때도 한쪽 동물이 따로 보여야 분리한다.
    detailed = detector.predict(
        small, classes=[cat_id], conf=0.40, iou=0.40,
        imgsz=960, device="cpu", verbose=False,
    )[0]
    base_area = width * height
    separate = []
    for box in detailed.boxes:
        coords = [float(value) for value in box.xyxy[0].tolist()]
        area = max(0, coords[2] - coords[0]) * max(0, coords[3] - coords[1])
        center = (coords[0] + coords[2]) / 2
        if (box_iou(candidate["coords"], coords) > 0.20
                and area < base_area * 0.70
                and abs(center - (x1 + x2) / 2) > width * 0.15):
            separate.append((float(box.conf[0].item()), coords, center))
    if not separate:
        return [candidate]

    confidence, detailed_coords, center = max(separate)
    side = 0 if center < (x1 + x2) / 2 else 1
    tile_boxes[side] = {"coords": detailed_coords, "det_confidence": confidence}
    if box_iou(tile_boxes[0]["coords"], tile_boxes[1]["coords"]) >= 0.30:
        return [candidate]

    refined = []
    for item in tile_boxes:
        crop = crop_from_original(original, item["coords"], small.shape)
        if crop.size == 0:
            return [candidate]
        class_name, class_confidence = classify_cat(cat_classifier, crop)
        refined.append({
            "species": "cat", "coords": item["coords"],
            "det_confidence": item["det_confidence"],
            "class_name": class_name,
            "class_confidence": class_confidence, "matched": True,
        })
    return refined


def main():
    parser = argparse.ArgumentParser(description="개·고양이 검출, 고양이 Oxford 12품종 분류")
    parser.add_argument("--input", default=str(ROOT / "data/images"))
    parser.add_argument("--output", default=str(OUTPUT_DIR))
    parser.add_argument("--conf", type=float, default=0.25)
    args = parser.parse_args()

    if not DOG_CLASS_MODEL.is_file():
        raise FileNotFoundError(f"개 분류 모델 파일이 없습니다: {DOG_CLASS_MODEL}")

    total_started = time.perf_counter()
    detector = yolo()
    dog_classifier = YOLO(str(DOG_CLASS_MODEL))
    if dog_classifier.task != "classify":
        raise ValueError("개 분류용 YOLO 모델이 필요합니다.")
    cat_classifier = load_cat_classifier()
    output_dir = Path(args.output)
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
            small, classes=class_ids, conf=args.conf, iou=0.60,
            device="cpu", verbose=False,
        )[0]
        marked = small.copy()
        used_labels = []
        counts = {name: 0 for name in TARGETS}
        labels = {name: [] for name in TARGETS}
        candidates = []

        for box in result.boxes:
            species = result.names[int(box.cls[0].item())]
            coords = [float(value) for value in box.xyxy[0].tolist()]
            det_confidence = float(box.conf[0].item())
            crop = crop_from_original(original, coords, small.shape)
            if crop.size == 0:
                candidates.append({
                    "species": species, "coords": coords,
                    "det_confidence": det_confidence,
                    "class_name": "", "class_confidence": None, "matched": None,
                })
                continue
            if species == "cat":
                class_name, class_confidence = classify_cat(cat_classifier, crop)
                matched = True  # 고양이 전용 모델의 출력은 모두 고양이 품종임.
            else:
                class_result = dog_classifier.predict(
                    square_pad(crop), imgsz=CLASSIFY_SIZE, device="cpu", verbose=False,
                )[0]
                best, class_confidence = best_class(class_result.probs)
                class_name = str(class_result.names[best])
                matched = dog_class_matches(best, class_result.names)

            candidates.append({
                "species": species, "coords": coords,
                "det_confidence": det_confidence,
                "class_name": class_name,
                "class_confidence": class_confidence, "matched": matched,
            })

        kept = remove_duplicate_boxes(candidates)
        refined = []
        cat_id = next(index for index, name in detector.names.items() if name == "cat")
        for candidate in kept:
            refined.extend(split_wide_cat_box(
                detector, small, original, candidate, cat_id, cat_classifier,
            ))

        for number, candidate in enumerate(refined, 1):
            species = candidate["species"]
            coords = candidate["coords"]
            class_name = candidate["class_name"]
            class_confidence = candidate["class_confidence"]
            matched = candidate["matched"]
            counts[species] += 1
            if class_confidence is None:
                labels[species].append((number, "분류 실패"))
                draw_class_label(marked, coords, number, species, "분류 실패", None, used_labels)
                class_rows.append({
                    "filename": path.name, "object_no": number,
                    "detected_species": species,
                    "det_confidence": round(candidate["det_confidence"], 4),
                    "class_label": "", "class_confidence": "",
                    "species_match": "crop_empty",
                })
                continue
            readable_name = korean_label(class_name)
            if matched is False:
                labels[species].append((
                    number, f"불확실: 분류 후보 {readable_name} {class_confidence:.0%} (검출 종류와 다름)"
                ))
            else:
                labels[species].append((number, f"{readable_name} {class_confidence:.0%}"))
            display_name = readable_name if matched is not False else "종류 불확실"
            draw_class_label(
                marked, coords, number, species, display_name,
                class_confidence if matched is not False else None, used_labels,
            )
            class_rows.append({
                "filename": path.name,
                "object_no": number,
                "detected_species": species,
                "det_confidence": round(candidate["det_confidence"], 4),
                "class_label": readable_name,
                "class_confidence": round(class_confidence, 4),
                "species_match": "yes" if matched is True else "no" if matched is False else "unknown",
            })

        save_image(output_dir / f"{path.stem}_pets.jpg", marked)
        counts_rows.append({"filename": path.name, **counts})
        classification_summaries.append((path.name, labels))
        print(f"{path.name}: 고양이 {counts['cat']}마리, 개 {counts['dog']}마리")

    save_csv(output_dir / "pet_counts.csv", ["filename", *TARGETS], counts_rows)
    save_csv(
        output_dir / "pet_classifications.csv",
        ["filename", "object_no", "detected_species", "det_confidence",
         "class_label", "class_confidence", "species_match"],
        class_rows,
    )
    print("\n사진별 분류 결과 (고양이: Oxford 12품종 / 개: ImageNet 후보)")
    print("고양이는 Oxford 모델로 분류함. ImageNet의 286·287번은 고양이 품종 후보가 아님.")
    print("개 분류의 불확실 = 검출은 개인데 분류 1순위가 개 품종이 아님.")
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
    print(f"결과 위치: {output_dir}")
    print("분류 이름은 각 모델의 예측 후보이며, 사진의 확정 품종은 아님.")


if __name__ == "__main__":
    main()
