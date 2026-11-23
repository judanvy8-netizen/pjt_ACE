"""Download and verify the official Torchvision 0.20 ViT-B/16 checkpoint."""

import hashlib
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "shared/models/vit_b_16-c867db91.pth"
URL = "https://download.pytorch.org/models/vit_b_16-c867db91.pth"
SHA256 = "c867db91d3e12c6cbadabb610d73c24a546bf82d8c03a9fea34f43a712ddb0e9"


def checksum(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main():
    DEST.parent.mkdir(parents=True, exist_ok=True)
    if DEST.is_file() and checksum(DEST) == SHA256:
        print(f"ViT 가중치 확인 완료: {DEST}")
        return
    temporary = DEST.with_suffix(".download")
    try:
        print("공식 Torchvision ViT-B/16 가중치 다운로드 중 (약 346 MB)...", flush=True)
        urllib.request.urlretrieve(URL, temporary)
        if checksum(temporary) != SHA256:
            raise ValueError("다운로드 파일의 SHA-256이 공식 체크포인트와 일치하지 않습니다.")
        temporary.replace(DEST)
        print(f"ViT 가중치 설치 완료: {DEST}")
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
