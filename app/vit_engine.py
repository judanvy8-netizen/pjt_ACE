"""main-3: YOLO boxes, ViT-B/16 species evidence and dog breeds, Oxford cat breeds.

ViT is an ImageNet image classifier, not an object detector. YOLO supplies boxes.
The Oxford model supplies the twelve domestic cat breed candidates missing from
ImageNet's five broad cat categories. A strong ViT contradiction may correct the
YOLO species; weak evidence never changes the detected species.
"""

from pathlib import Path
import csv

import cv2
import torch
from PIL import Image
from torchvision.models import ViT_B_16_Weights, vit_b_16

from app.pet_engine import (
    CAT_CLASS_MODEL, TARGETS, classify_cat, crop_from_original,
    draw_class_label, load_cat_classifier, box_iou,
    remove_duplicate_boxes, split_wide_cat_box, square_pad,
)
from shared.common import MODEL_PATH, model_hashes, read_image, standardize_image, yolo

VIT_MODEL = MODEL_PATH.with_name("vit_b_16-c867db91.pth")
WEIGHTS = ViT_B_16_Weights.IMAGENET1K_V1
DOG_IDS = range(151, 269)
CAT_IDS = range(281, 286)
CORRECTION_CONFIDENCE = 0.20
CORRECTION_RATIO = 3.0
BREED_CONFIDENCE = 0.10


def deduplicate_same_species(candidates):
    """Drop near-identical boxes and a broad box containing a tighter one."""
    kept = []
    for item in candidates:
        for index, previous in enumerate(kept):
            if item["species"] != previous["species"]:
                continue
            first, second = item["coords"], previous["coords"]
            intersection = max(0, min(first[2], second[2]) - max(first[0], second[0])) * \
                max(0, min(first[3], second[3]) - max(first[1], second[1]))
            a = max(0, first[2] - first[0]) * max(0, first[3] - first[1])
            b = max(0, second[2] - second[0]) * max(0, second[3] - second[1])
            if box_iou(first, second) >= 0.90:
                if item["det_confidence"] > previous["det_confidence"]:
                    kept[index] = item
                break
            if min(a, b) and intersection / min(a, b) >= 0.95 and \
                    min(a, b) / max(a, b) >= 0.45:
                # The tighter crop can lose the animal's defining features.
                # Keep the stronger detection when two boxes describe one pet.
                if item["det_confidence"] > previous["det_confidence"]:
                    kept[index] = item
                break
        else:
            kept.append(item)
    return kept


class ViTEngine:
    """One-photo inference with the same result contract as the existing engines."""

    def __init__(self):
        if not VIT_MODEL.is_file():
            raise FileNotFoundError(
                f"ViT 가중치가 없습니다: {VIT_MODEL}. "
                "python scripts/download_vit.py를 먼저 실행하세요."
            )
        self.detector = yolo()
        self.cat_classifier = load_cat_classifier()
        self.vit = vit_b_16(weights=None)
        self.vit.load_state_dict(torch.load(VIT_MODEL, map_location="cpu", weights_only=True))
        self.vit.eval()
        self.transform = WEIGHTS.transforms()
        self.names = WEIGHTS.meta["categories"]
        with (MODEL_PATH.parents[2] / "data/breed_labels_imagenet_ko.csv").open(
                encoding="utf-8-sig", newline="") as source:
            self.korean_names = {
                row["label"].lower().replace("_", "").replace(" ", ""): row["canonical"]
                for row in csv.DictReader(source)
            }
        self.model_hashes = model_hashes(MODEL_PATH, VIT_MODEL, CAT_CLASS_MODEL)

    def classify_vit(self, crop):
        rgb = cv2.cvtColor(square_pad(crop), cv2.COLOR_BGR2RGB)
        tensor = self.transform(Image.fromarray(rgb)).unsqueeze(0)
        with torch.inference_mode():
            scores = self.vit(tensor).softmax(dim=1)[0]
        dog_id = max(DOG_IDS, key=lambda idx: float(scores[idx]))
        cat_id = max(CAT_IDS, key=lambda idx: float(scores[idx]))
        return (self.names[dog_id], float(scores[dog_id]),
                self.names[cat_id], float(scores[cat_id]))

    def analyze(self, path, confidence=0.25, *, image=None):
        path = Path(path)
        original = read_image(path) if image is None else image
        small = standardize_image(original)
        detector = self.detector
        class_ids = [i for i, name in detector.names.items() if name in TARGETS]
        result = detector.predict(small, classes=class_ids, conf=confidence,
                                  iou=0.60, device="cpu", verbose=False)[0]
        candidates = []
        for box in result.boxes:
            species = result.names[int(box.cls[0].item())]
            coords = [float(v) for v in box.xyxy[0].tolist()]
            det_score = float(box.conf[0].item())
            crop = crop_from_original(original, coords, small.shape)
            if crop.size == 0:
                candidates.append(dict(species=species, coords=coords,
                    det_confidence=det_score, class_name="", class_confidence=None,
                    matched=None))
                continue

            dog_name, dog_score, _, cat_score = self.classify_vit(crop)
            if species == "cat" and dog_score >= CORRECTION_CONFIDENCE \
                    and dog_score >= cat_score * CORRECTION_RATIO:
                species = "dog"
            elif species == "dog" and cat_score >= CORRECTION_CONFIDENCE \
                    and cat_score >= dog_score * CORRECTION_RATIO:
                species = "cat"

            if species == "cat":
                name, score = classify_cat(self.cat_classifier, crop)
                matched = cat_score >= dog_score
            elif dog_score >= BREED_CONFIDENCE:
                name, score = dog_name, dog_score
                matched = dog_score >= cat_score
            else:
                name, score, matched = "", None, None
            candidates.append(dict(species=species, coords=coords,
                det_confidence=det_score, class_name=name,
                class_confidence=score, matched=matched))

        kept = deduplicate_same_species(remove_duplicate_boxes(candidates))
        cat_id = next(i for i, name in detector.names.items() if name == "cat")
        refined = []
        for candidate in kept:
            refined.extend(split_wide_cat_box(detector, small, original,
                                               candidate, cat_id, self.cat_classifier))

        marked = small.copy()
        counts = {name: 0 for name in TARGETS}
        objects = []
        labels = []
        for number, candidate in enumerate(refined, 1):
            species = candidate["species"]
            score = candidate["class_confidence"]
            matched = candidate["matched"]
            if score is None:
                label = ""
            else:
                name = candidate["class_name"]
                label = self.korean_names.get(name.lower().replace("_", "").replace(" ", ""),
                                               name.replace("_", " "))
            status = "unknown" if score is None else "yes" if matched else "no"
            display = label if score is not None and matched else "품종 불확실"
            draw_class_label(marked, candidate["coords"], number, species,
                             display, score if matched else None, labels)
            counts[species] += 1
            objects.append(dict(object_no=number, detected_species=species,
                box=candidate["coords"], det_confidence=round(candidate["det_confidence"], 4),
                class_label=label, class_confidence=round(score, 4) if score is not None else None,
                species_match=status))
        return marked, counts, objects
