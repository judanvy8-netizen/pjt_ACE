"""두 회차의 골든 CSV를 비교한다. 일치하면 종료 코드 0."""
import argparse
import math
from pathlib import Path
from compare_web import read_csv

KEYS = ("filename", "variant")
FLOATS = {"x1", "y1", "x2", "y2", "detectionScore", "breedScore"}


def index(path, keys):
    rows = read_csv(path)
    result = {tuple(row[k] for k in keys): row for row in rows}
    if len(result) != len(rows):
        raise ValueError(f"중복 행: {path}")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--tolerance", type=float, default=1e-6)
    args = parser.parse_args()
    errors = 0
    for name, keys in [("round_counts_before.csv", KEYS), ("golden_detections.csv", KEYS + ("number",))]:
        expected = index(args.reference / name, keys)
        actual = index(args.candidate / name, keys)
        for key in expected.keys() ^ actual.keys():
            print(name, key, "행 누락 또는 추가")
            errors += 1
        for key in expected.keys() & actual.keys():
            left, right = expected[key], actual[key]
            if left.keys() != right.keys():
                print(name, key, "열 불일치")
                errors += 1
                continue
            for col, value in left.items():
                other = right[col]
                if value == other:
                    continue
                if col in FLOATS and value and other:
                    if math.isclose(float(value), float(other), rel_tol=0, abs_tol=args.tolerance):
                        continue
                print(name, key, col, value, "→", other)
                errors += 1
    print(f"차이: {errors}건")
    return int(errors > 0)


if __name__ == "__main__":
    raise SystemExit(main())
