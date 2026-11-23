"""기준 사진의 기존 웹 회차와 같은 입력으로 세 CLI를 실행해 비교한다."""

import argparse
import csv
import json
import hashlib
import math
import subprocess
import sys
from pathlib import Path
from PIL import Image
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
VARIANTS = ("main", "main-1", "main-2")


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def write_csv(path, fields, rows):
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def get_json(url):
    with urlopen(url, timeout=30) as response:
        return json.load(response)


def matches(cli, web, tolerance):
    if len(cli) != len(web):
        return False
    for expected, observed in zip(cli, web):
        if (expected["object_no"], expected["detected_species"], expected["class_label"], expected["species_match"]) != (
                observed["number"], observed["species"], observed["breed"], observed["classificationStatus"]):
            return False
        pairs = list(zip(expected["box"], [observed[k] for k in ("x1", "y1", "x2", "y2")]))
        pairs.append((expected["det_confidence"], observed["detectionScore"]))
        expected_score = expected["class_confidence"]
        actual_score = observed["breedScore"]
        if expected_score in ("", None) or actual_score is None:
            if expected_score not in ("", None) or actual_score is not None:
                return False
        else:
            pairs.append((expected_score, actual_score))
        if not all(math.isclose(a, b, rel_tol=0, abs_tol=tolerance) for a, b in pairs):
            return False
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--round", required=True, help="완료한 웹 회차의 UUID")
    parser.add_argument("--url", default="http://127.0.0.1:8080")
    parser.add_argument("--output", type=Path, required=True, help="새 증거 폴더 경로")
    parser.add_argument("--tolerance", type=float, default=1e-6)
    args = parser.parse_args()
    output = args.output.resolve()
    base = args.url.rstrip("/")
    photos = get_json(f"{base}/api/rounds/{args.round}/runs")
    truth = {r["파일명"]: (int(r["고양이_정답"]), int(r["개_정답"]))
             for r in read_csv(ROOT / "data/ground_truth.csv")}
    inputs = {photo["filename"]: {item["variant"]: item["run"] for item in photo["items"]} for photo in photos}
    if len(photos) != len(truth) or inputs.keys() != truth.keys() or any(
            set(runs) != set(VARIANTS) for runs in inputs.values()):
        parser.error("기준 사진 40장과 세 버전이 모두 포함된 회차가 필요합니다.")
    output.mkdir(parents=True, exist_ok=False)
    reports, counts, detections = [], [], []
    for variant in VARIANTS:
        cli = output / variant
        subprocess.run([sys.executable, str(ROOT / "source" / f"{variant}.py"), "--output", str(cli),
                        "--details-json", str(cli / "objects.json")], cwd=ROOT, check=True)
        objects = json.loads((cli / "objects.json").read_text(encoding="utf-8"))
        result = {r["filename"]: (int(r["cat"]), int(r["dog"])) for r in read_csv(cli / "pet_counts.csv")}
        for name, expected in sorted(truth.items()):
            run = inputs[name][variant]
            web = get_json(f"{base}/api/runs/{run['runId']}")
            actual = (run["catCount"], run["dogCount"])
            success = run["status"] == "SUCCEEDED"
            source = ROOT / "data/images" / name
            with urlopen(f"{base}/media/{run['runId']}/original", timeout=30) as response:
                same_input = hashlib.sha256(response.read()).digest() == hashlib.sha256(source.read_bytes()).digest()
            with Image.open(cli / f"{Path(name).stem}_pets.jpg") as marked:
                same_size = marked.size == (run["width"], run["height"])
            counts.append(dict(filename=name, variant=variant, cat_count=actual[0], dog_count=actual[1]))
            reports.append(dict(filename=name, variant=variant, success=success, same_input=same_input, same_size=same_size,
                                same_counts=success and actual == result[name],
                                same_objects=success and matches(objects[name], web["objects"], args.tolerance),
                                same_truth=success and actual == expected))
            for pet in web["objects"]:
                detections.append(dict(filename=name, variant=variant, **pet,
                                       image_width=run["width"], image_height=run["height"]))
    write_csv(output / "round_counts_before.csv", list(counts[0]), counts)
    write_csv(output / "golden_detections.csv", [
        "filename", "variant", "number", "species", "x1", "y1", "x2", "y2", "detectionScore", "breed",
        "breedScore", "classificationStatus", "image_width", "image_height"], detections)
    write_csv(output / "comparison.csv", list(reports[0]), reports)
    with (output / "pip_freeze.txt").open("w", encoding="utf-8") as stream:
        subprocess.run([sys.executable, "-m", "pip", "freeze"], stdout=stream, check=True)
    print(f"{len(reports)}건 검증 · {output}")
    return 0 if all(r["same_input"] and r["same_size"] and r["same_counts"] and r["same_objects"] for r in reports) else 1


if __name__ == "__main__":
    raise SystemExit(main())
