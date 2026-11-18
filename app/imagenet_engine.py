"""main-1 검출 및 ImageNet 분류."""
import math
from pathlib import Path

import cv2
from ultralytics import YOLO


# source 폴더의 상위인 프로젝트 루트를 기준으로 모델과 사진 경로를 잡는다.
ROOT = Path(__file__).resolve().parents[1]
from shared.common import MODEL_PATH, read_image, yolo, standardize_image, model_hashes


# 검출 대상과 모델 입력 크기
TARGETS = ("cat", "dog")
CLASSIFY_SIZE = 224
IGNORED_CLASS_IDS = (286, 287)  # cougar, lynx
CLASS_MODEL = ROOT / "shared/models/yolo11n-cls.pt"
OUTPUT_DIR = ROOT / "outputs/main-1"
BOX_COLORS = ((255, 100, 100), (100, 220, 100), (100, 200, 255), (255, 100, 220))


# 사진 전처리와 검출 좌표 변환
def crop_from_original(original, box, small_shape, margin=0.1):
    """축소 사진에서 찾은 박스 위치를 원본 좌표로 바꾸어 동물만 자른다."""
    original_h, original_w = original.shape[:2]
    small_h, small_w = small_shape[:2]
    x1, y1, x2, y2 = box
    pad_x = (x2 - x1) * margin
    pad_y = (y2 - y1) * margin
    # 축소본 좌표에 가로·세로 배율을 곱해 원본 좌표로 되돌린다.
    ratio_x = original_w / small_w
    ratio_y = original_h / small_h
    # 여백까지 포함하되, 자르는 범위는 원본 사진의 테두리 안으로 제한한다.
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
        image,
        top,
        side - height - top,
        left,
        side - width - left,
        cv2.BORDER_CONSTANT,
        value=(114, 114, 114),
    )


# ImageNet 분류 후보 선택과 검출 종류 확인
def class_matches_species(index, species, names):
    """현재 ImageNet 모델에서 검출된 동물과 분류 이름의 종류가 맞는지 확인한다."""
    if len(names) != 1000:
        return None
    if species == "dog":
        return 151 <= index <= 268
    return 281 <= index <= 285


def best_class_without_wild_cats(probs, species):
    """고양이 후보에서만 286·287번 야생동물을 제외한다."""
    scores = probs.data
    if species == "cat":
        filtered = scores.clone()
        for index in IGNORED_CLASS_IDS:
            if index < len(filtered):
                filtered[index] = -1
        best = int(filtered.argmax().item())
    else:
        best = int(scores.argmax().item())
    return best, float(scores[best].item())


# 결과 이미지에 박스와 분류 라벨 그리기
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
    # 박스 주변의 여러 위치를 순서대로 확인해 기존 라벨과 겹치지 않는 곳을 찾는다.
    candidates = (
        (x1, y1 - label_h - 2), (x1, y1 + 2),
        (x2 - label_w, y1 + 2), (x1, y2 + 2),
        (x2 - label_w, y2 - label_h - 2),
    )
    for left, top in candidates:
        left = max(0, min(left, width - label_w))
        top = max(0, min(top, height - label_h))
        right, bottom = left + label_w, top + label_h
        if all(
            right <= a or left >= c or bottom <= b or top >= d
            for a, b, c, d in used_labels
        ):
            break
    used_labels.append((left, top, right, bottom))
    cv2.rectangle(image, (left, top), (right, bottom), (30, 30, 30), -1)
    cv2.rectangle(image, (left, top), (right, bottom), color, 1)
    cv2.putText(image, text, (left + 3, top + text_h + 3),
                font, font_scale, (255, 255, 255), thickness, cv2.LINE_AA)



class ImageNetEngine:
    def __init__(self):
        if not CLASS_MODEL.is_file():
            raise FileNotFoundError(f"분류 모델 파일이 없습니다: {CLASS_MODEL}")
        self.detector = yolo()
        self.classifier = YOLO(str(CLASS_MODEL))
        self.model_hashes = model_hashes(MODEL_PATH, CLASS_MODEL)
        if self.classifier.task != "classify":
            raise ValueError("분류용 YOLO 모델이 필요합니다.")

    def process(self, path, confidence=0.25, *, image=None):
        detector = self.detector
        classifier = self.classifier
        class_ids = [i for i, name in detector.names.items() if name in TARGETS]
        class_rows = []
        original = read_image(path) if image is None else image
        small = standardize_image(original)
        result = detector.predict(
            small, classes=class_ids, conf=confidence, device="cpu", verbose=False,
        )[0]
        marked = small.copy()
        used_labels = []
        counts = {name: 0 for name in TARGETS}
        labels = {name: [] for name in TARGETS}

        # 4. 검출한 박스별로 원본 사진에서 영역을 잘라 YOLO 분류기에 전달한다.
        for number, box in enumerate(result.boxes, 1):
            species = result.names[int(box.cls[0].item())]
            counts[species] += 1
            coords = [float(value) for value in box.xyxy[0].tolist()]
            crop = crop_from_original(original, coords, small.shape)
            # 자른 영역이 비어 있으면 분류 실패를 기록한다. 마릿수는 검출 결과로 집계한다.
            if crop.size == 0:
                labels[species].append((number, "분류 실패"))
                draw_class_label(marked, coords, number, species, "class failed", None, used_labels)
                class_rows.append({
                    "filename": path.name,
                    "box": coords,
                    "object_no": number,
                    "detected_species": species,
                    "det_confidence": round(float(box.conf[0].item()), 4),
                    "class_label": "",
                    "class_confidence": "",
                    "species_match": "crop_empty",
                })
                continue
            # 여백을 넣어 정사각형으로 만든 뒤 가장 점수가 높은 분류 후보를 고른다.
            class_result = classifier.predict(
                square_pad(crop), imgsz=CLASSIFY_SIZE, device="cpu", verbose=False,
            )[0]
            best, class_confidence = best_class_without_wild_cats(class_result.probs, species)
            class_name = str(class_result.names[best])
            matched = class_matches_species(best, species, class_result.names)
            readable_name = class_name.replace("_", " ")
            if matched is False:
                labels[species].append((
                    number, f"불확실: 분류 후보 {readable_name} {class_confidence:.0%} (검출 종류와 다름)"
                ))
            else:
                labels[species].append((number, f"{readable_name} {class_confidence:.0%}"))
            # 검출 종류와 분류 후보가 다르면 이미지에 불확실로 표시한다.
            display_name = class_name if matched is not False else "class unclear"
            draw_class_label(
                marked, coords, number, species, display_name,
                class_confidence if matched is not False else None, used_labels,
            )
            class_rows.append({
                "filename": path.name,
                "box": coords,
                "object_no": number,
                "detected_species": species,
                "det_confidence": round(float(box.conf[0].item()), 4),
                "class_label": class_name,
                "class_confidence": round(class_confidence, 4),
                "species_match": "yes" if matched is True else "no" if matched is False else "unknown",
            })

        return marked, counts, class_rows, labels

        
    def analyze(self, path, *, image=None):
        marked, counts, rows, _ = self.process(path, image=image)
        for row in rows:
            row.pop("filename")
            if row["class_confidence"] == "":
                row["class_confidence"] = None
        return marked, counts, rows
