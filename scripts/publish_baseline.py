"""검증을 통과한 첫 표준 기준선을 활성화한다."""
import argparse
import shutil
from pathlib import Path
from tempfile import TemporaryDirectory
from compare_web import ROOT, VARIANTS, read_csv


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("verified", type=Path)
    args = parser.parse_args()
    rows = read_csv(args.verified / "comparison.csv")
    if len(rows) != 120 or len({(r["filename"], r["variant"]) for r in rows}) != 120 or not all(
            all(r.get(k) == "True" for k in ("same_input", "same_size", "same_counts", "same_objects")) for r in rows):
        parser.error("CLI·웹 비교 120건이 통과한 증거 폴더가 필요합니다.")
    baseline = ROOT / "data/baseline"
    marker = baseline / "STANDARD_VERIFIED"
    if marker.exists() or any((baseline / v).exists() for v in VARIANTS):
        parser.error("기존 표준 기준선이 있습니다.")
    # main-1·main-2는 제출본과 동일한 판정 결과여야 한다.
    for variant in ("main-1", "main-2"):
        for name in ("pet_counts.csv", "pet_classifications.csv"):
            current = read_csv(args.verified / variant / name)
            legacy = read_csv(baseline / "legacy-original-res" / variant / name)
            if current != legacy:
                parser.error(f"{variant}/{name}: 제출본과 차이가 있습니다.")
    with TemporaryDirectory(dir=baseline) as temp:
        stage = Path(temp)
        for variant in VARIANTS:
            shutil.copytree(args.verified / variant, stage / variant, ignore=shutil.ignore_patterns("objects.json"))
        for variant in VARIANTS:
            (stage / variant).rename(baseline / variant)
    marker.write_text("CLI / web: 120 checked\n", encoding="utf-8")
    print("표준 기준선 활성화")


if __name__ == "__main__":
    main()
