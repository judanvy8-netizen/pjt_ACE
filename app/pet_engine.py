"""ACE 제출본 main-2의 검출·품종 분류·박스 보정."""

import os
from functools import lru_cache
import math
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import torch
from torchvision import transforms
from torchvision.models import mobilenet_v3_large
from ultralytics import YOLO


# source 폴더의 상위인 프로젝트 루트를 기준으로 모델과 사진 경로를 잡는다.
ROOT = Path(__file__).resolve().parents[1]
from shared.common import MODEL_PATH, read_image, yolo, standardize_image, model_hashes


# 검출 대상과 모델 입력 크기
TARGETS = ("cat", "dog")
CLASSIFY_SIZE = 224
# 개 후보는 YOLO 분류기, 고양이 후보는 Oxford 12품종 분류기로 처리한다.
DOG_CLASS_MODEL = ROOT / "shared/models/yolo11n-cls.pt"
CAT_CLASS_MODEL = ROOT / "shared/models/cat_oxford_mobilenetv3.pt"
# 공개 Oxford 고양이 데이터셋의 클래스 순서. 원본의 Abyssunian 오타만 표시할 때 바로잡음.
CAT_CLASS_NAMES = (
    "Abyssinian", "Bengal", "Birman", "Bombay", "British Shorthair",
    "Egyptian Mau", "Maine Coon", "Persian", "Ragdoll", "Russian Blue",
    "Siamese", "Sphynx",
)
# Oxford 모델에 전달하기 전에 크기와 픽셀값을 학습 때 사용한 형태로 맞춘다.
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
    "Bengal": "벵갈", "Birman": "버만", "bull_mastiff": "불마스티프",
    "cocker_spaniel": "잉글리쉬 코커 스파니엘",
    "dingo": "딩고(개 품종 후보 밖)",
    "Egyptian Mau": "이집션 마우",
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
    "pug": "퍼그", "Chihuahua": "치와와", "boxer": "복서",
    "golden_retriever": "골든 리트리버", "German_shepherd": "저먼 셰퍼드",
    "dalmatian": "달마티안", "Siberian_husky": "시베리안 허스키",
    "Blenheim_spaniel": "블렌하임 스패니얼", "basset": "바셋하운드",
    "Persian_cat": "페르시안 고양이(개 품종 아님)",
    "miniature_schnauzer": "미니어처 슈나우저", "beagle": "비글",
    "Pembroke": "펨브로크 웰시 코기", "Great_Dane": "그레이트데인",
    "Italian_greyhound": "이탈리안 그레이하운드",
    "Gordon_setter": "고든 세터", "Doberman": "도베르만",
    "Norwegian_elkhound": "노르웨이 엘크하운드", "Rottweiler": "로트바일러",
    "Eskimo_dog": "에스키모 도그(허스키 계열)",
    "Afghan_hound": "아프간 하운드", "whippet": "휘핏",
}
@lru_cache(maxsize=1)
def label_font():
    """Windows 맑은 고딕을 우선 사용하고, 다른 환경에서는 지정한 글꼴을 읽는다."""
    configured = os.getenv("ACE_LABEL_FONT")
    if configured:
        return ImageFont.truetype(configured, 15)
    try:
        return ImageFont.truetype("C:/Windows/Fonts/malgun.ttf", 15)
    except OSError:
        return ImageFont.load_default(size=15)


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


# 개·고양이 분류 모델과 분류 결과 해석
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


# 결과 이미지에 박스와 분류 라벨 그리기
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
    font = label_font()
    text_box = font.getbbox(text)
    text_w = text_box[2] - text_box[0]
    text_h = text_box[3] - text_box[1]
    label_w = text_w + 6
    label_h = text_h + 6
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
    label_image = Image.fromarray(cv2.cvtColor(image[top:bottom, left:right], cv2.COLOR_BGR2RGB))
    ImageDraw.Draw(label_image).text((3 - text_box[0], 3 - text_box[1]), text,
                                     font=font, fill=(255, 255, 255))
    image[top:bottom, left:right] = cv2.cvtColor(np.asarray(label_image), cv2.COLOR_RGB2BGR)


# 검출 박스 보정
def box_iou(first, second):
    """두 박스가 차지하는 영역이 얼마나 같은지 계산한다."""
    x1 = max(first[0], second[0])
    y1 = max(first[1], second[1])
    x2 = min(first[2], second[2])
    y2 = min(first[3], second[3])
    # IoU는 두 박스가 겹친 면적을 두 박스가 차지한 전체 면적으로 나눈 값이다.
    intersection = max(0, x2 - x1) * max(0, y2 - y1)
    first_area = max(0, first[2] - first[0]) * max(0, first[3] - first[1])
    second_area = max(0, second[2] - second[0]) * max(0, second[3] - second[1])
    union = first_area + second_area - intersection
    return intersection / union if union else 0.0


def remove_duplicate_boxes(candidates):
    """거의 같은 위치를 개와 고양이로 이중 검출한 경우 한 박스만 남긴다."""
    kept = []
    for candidate in candidates:
        duplicate_at = next(
            (
                index
                for index, previous in enumerate(kept)
                if previous["species"] != candidate["species"]
                and box_iou(previous["coords"], candidate["coords"]) >= 0.90
            ),
            None,
        )
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

    # 넓은 고양이 박스를 좌우로 나누고, 각 영역에서 한 마리씩 다시 찾는다.
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


class PetEngine:
    """CLI와 웹에서 공통으로 사용하는 사진 한 장 추론기."""

    def __init__(self):
        if not DOG_CLASS_MODEL.is_file():
            raise FileNotFoundError(f"개 분류 모델 파일이 없습니다: {DOG_CLASS_MODEL}")
        self.detector = yolo()
        self.dog_classifier = YOLO(str(DOG_CLASS_MODEL))
        if self.dog_classifier.task != "classify":
            raise ValueError("개 분류용 YOLO 모델이 필요합니다.")
        self.cat_classifier = load_cat_classifier()
        self.model_hashes = model_hashes(MODEL_PATH, DOG_CLASS_MODEL, CAT_CLASS_MODEL)

    def analyze(self, path, confidence=0.25, *, image=None):
        detector = self.detector
        dog_classifier = self.dog_classifier
        cat_classifier = self.cat_classifier
        class_ids = [index for index, name in detector.names.items() if name in TARGETS]
        try:
            original = read_image(path) if image is None else image
        except cv2.error as exc:
            raise ValueError("이미지를 읽을 수 없습니다.") from exc
        small = standardize_image(original)
        result = detector.predict(
            small, classes=class_ids, conf=confidence, iou=0.60,
            device="cpu", verbose=False,
        )[0]
        marked = small.copy()
        used_labels = []
        counts = {name: 0 for name in TARGETS}
        candidates = []

        # 4. 검출된 종류에 맞는 분류 모델을 사용하고, 박스별 결과를 후보 목록에 담는다.
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

        # 5. 거의 같은 위치의 개·고양이 박스는 하나로 정리하고, 넓은 고양이 박스는 재검출한다.
        kept = remove_duplicate_boxes(candidates)
        refined = []
        cat_id = next(index for index, name in detector.names.items() if name == "cat")
        for candidate in kept:
            refined.extend(split_wide_cat_box(
                detector, small, original, candidate, cat_id, cat_classifier,
            ))

        objects = []
        for number, candidate in enumerate(refined, 1):
            species = candidate["species"]
            score = candidate["class_confidence"]
            matched = candidate["matched"]
            name = korean_label(candidate["class_name"]) if score is not None else ""
            status = "crop_empty" if score is None else {True: "yes", False: "no", None: "unknown"}[matched]
            display = "분류 실패" if score is None else "종류 불확실" if matched is False else name
            draw_class_label(marked, candidate["coords"], number, species, display,
                             score if matched is not False else None, used_labels)
            counts[species] += 1
            objects.append({
                "object_no": number, "detected_species": species,
                "box": candidate["coords"],
                "det_confidence": round(candidate["det_confidence"], 4),
                "class_label": name,
                "class_confidence": round(score, 4) if score is not None else None,
                "species_match": status,
            })
        return marked, counts, objects
