"""실제 분류 모델로 버전별 라벨 목록을 내보낸다."""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from app.imagenet_engine import ImageNetEngine
from app.pet_engine import CAT_CLASS_NAMES
from shared.common import save_csv


def main():
    names = ImageNetEngine().classifier.names
    if len(names) != 1000 or any(names[i].startswith("n0") for i in (151, 281)):
        raise ValueError("ImageNet 영문 라벨을 확인할 수 없습니다.")
    rows = []
    for version in ("main-1", "main-2"):
        for i in range(151, 269):
            rows.append({"version": version, "species": "dog", "label": names[i]})
        cats = [names[i] for i in range(281, 286)] if version == "main-1" else CAT_CLASS_NAMES
        rows.extend({"version": version, "species": "cat", "label": label} for label in cats)
    save_csv(ROOT / "data/breed_vocabulary.csv", ["version", "species", "label"], rows)


if __name__ == "__main__":
    main()
