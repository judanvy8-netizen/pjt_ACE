"""공유 폴더의 사진을 분석하는 로컬 FastAPI 서버."""

import json
import re
import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Literal

import cv2
from threading import Lock
from uuid import UUID

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from shared.common import IMAGE_EXTENSIONS, MODEL_PATH, STANDARD_LONG_SIDE, read_image, save_image, standard_objects

ROOT = Path(__file__).resolve().parents[1]
STORAGE = Path(os.getenv("ACE_STORAGE", str(ROOT / "ACE_STORAGE"))).resolve()
CLASS_MODEL = MODEL_PATH.with_name("yolo11n-cls.pt")
CAT_CLASS_MODEL = MODEL_PATH.with_name("cat_oxford_mobilenetv3.pt")
MAX_BYTES = 15 * 1024 * 1024
LOCK = Lock()
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app):
    app.state.engines = {}
    (STORAGE / "inbox").mkdir(parents=True, exist_ok=True)
    (STORAGE / "results").mkdir(parents=True, exist_ok=True)
    yield


app = FastAPI(title="ACE AI", lifespan=lifespan)


class InferRequest(BaseModel):
    run_id: UUID
    relative_path: str
    version: Literal["main", "main-1", "main-2"] = "main-2"


def detection_factory():
    from app.detection_engine import DetectionEngine
    return DetectionEngine()


def imagenet_factory():
    from app.imagenet_engine import ImageNetEngine
    return ImageNetEngine()


def pet_factory():
    from app.pet_engine import PetEngine
    return PetEngine()


ENGINE_FACTORIES = {"main": detection_factory, "main-1": imagenet_factory, "main-2": pet_factory}


def get_engine(version):
    if version not in app.state.engines:
        try:
            app.state.engines[version] = ENGINE_FACTORIES[version]()
        except Exception:
            logger.exception("Model loading failed: %s", version)
            raise HTTPException(503, "모델을 불러오지 못했습니다.") from None
    return app.state.engines[version]


@app.get("/api/health")
def health(deep: bool = False):
    missing = [p.name for p in (MODEL_PATH, CLASS_MODEL, CAT_CLASS_MODEL) if not p.is_file()]
    failures = []
    if deep:
        with LOCK:
            for version in ENGINE_FACTORIES:
                try:
                    get_engine(version)
                except HTTPException:
                    failures.append(version)
    return {"ok": not missing and not failures, "versions": list(ENGINE_FACTORIES),
            "missing_models": missing, "failed_versions": failures}


@app.post("/api/infer")
def infer(body: InferRequest):
    run_id = str(body.run_id)
    relative = Path(body.relative_path)
    relative_name = relative.as_posix()
    uuid_pattern = r"[a-f0-9-]{36}"
    legacy = re.fullmatch(rf"inbox/{uuid_pattern}/original\.[a-z0-9]+", relative_name)
    dataset = re.fullmatch(rf"datasets/{uuid_pattern}/{uuid_pattern}/original\.[a-z0-9]+", relative_name)
    if not (legacy or dataset):
        raise HTTPException(400, "등록된 사진 경로가 필요합니다.")
    original = (STORAGE / relative).resolve()
    if original != STORAGE / relative:
        raise HTTPException(400, "공유 폴더 밖의 파일은 분석할 수 없습니다.")
    if original.suffix.lower() not in IMAGE_EXTENSIONS:
        raise HTTPException(415, "지원하지 않는 이미지 형식입니다.")
    if not original.is_file():
        raise HTTPException(404, "업로드한 사진을 찾을 수 없습니다.")
    if original.stat().st_size > MAX_BYTES:
        raise HTTPException(413, "이미지는 15MB 이하여야 합니다.")
    output = STORAGE / "results" / run_id
    if output.resolve() != output or any((output / name).is_symlink() for name in ("analysis.json", "analysis.tmp", "marked.jpg")):
        raise HTTPException(400, "결과 폴더 경로가 올바르지 않습니다.")
    receipt = output / "analysis.json"
    # YOLO 모델 인스턴스와 실행별 결과 파일을 직렬로 사용한다.
    with LOCK:
        if receipt.is_file() and (output / "marked.jpg").is_file():
            saved = json.loads(receipt.read_text(encoding="utf-8"))
            if (saved.get("engine_version") != f"ace-{body.version}"
                    or saved.get("source_path", relative_name) != relative_name):
                raise HTTPException(409, "같은 실행 ID에 다른 버전을 사용할 수 없습니다.")
            return saved
        started = time.perf_counter()
        try:
            original_image = read_image(original)
        except (OSError, ValueError, cv2.error):
            raise HTTPException(422, "이미지를 읽을 수 없습니다.") from None
        engine = get_engine(body.version)
        try:
            marked, counts, objects = engine.analyze(original, image=original_image)
            objects = standard_objects(objects, marked.shape[1], marked.shape[0])
            save_image(output / "marked.jpg", marked)
        except Exception:
            logger.exception("ACE inference failed for %s", run_id)
            raise HTTPException(500, "AI 분석에 실패했습니다.") from None
        result = {
            "run_id": run_id, "source_path": relative_name, "cat_count": counts["cat"], "dog_count": counts["dog"],
            "result_path": f"results/{run_id}/marked.jpg",
            "width": marked.shape[1], "height": marked.shape[0], "objects": objects,
            "elapsed_ms": round((time.perf_counter() - started) * 1000),
            "engine_version": f"ace-{body.version}", "models": engine.model_hashes,
            "settings": {"conf": 0.25, "device": "cpu", "coord_space": "standard", "standard_long_side": STANDARD_LONG_SIDE,
                         "source_width": original_image.shape[1], "source_height": original_image.shape[0],
                         **({"iou": 0.60} if body.version == "main-2" else {})},
        }
        temporary = receipt.with_suffix(".tmp")
        temporary.write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
        temporary.replace(receipt)
        return result
