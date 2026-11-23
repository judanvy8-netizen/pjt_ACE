from types import SimpleNamespace
from uuid import uuid4

import numpy as np
from fastapi.testclient import TestClient

from app import ai_server
from shared.common import standardize_image


def test_standard_size():
    original = np.zeros((960, 1280, 3), dtype=np.uint8)
    small = standardize_image(original)
    assert small.shape == (480, 640, 3)
    assert np.array_equal(small, standardize_image(small))


def test_route_distinguishes_model_and_decode_errors_and_reuses_result(tmp_path, monkeypatch):
    monkeypatch.setattr(ai_server, "STORAGE", tmp_path)
    def broken_model():
        raise ValueError("model")
    monkeypatch.setitem(ai_server.ENGINE_FACTORIES, "main-1", broken_model)
    monkeypatch.setitem(ai_server.ENGINE_FACTORIES, "main", lambda: SimpleNamespace(
        model_hashes={}, analyze=lambda path, *, image: (image, {"cat": 0, "dog": 0}, [])))
    with TestClient(ai_server.app) as client:
        photo = tmp_path / "datasets" / str(uuid4()) / str(uuid4()) / "original.jpg"
        photo.parent.mkdir(parents=True)
        photo.write_bytes(b"broken")
        request = {"run_id": str(uuid4()), "relative_path": photo.relative_to(tmp_path).as_posix(), "version": "main"}
        assert client.post("/api/infer", json=request).status_code == 422
        ai_server.save_image(photo, np.zeros((20, 30, 3), dtype=np.uint8))
        assert client.post("/api/infer", json=dict(request, version="main-1")).status_code == 503
        first = client.post("/api/infer", json=request)
        assert first.status_code == 200
        assert first.json()["settings"]["coord_space"] == "standard"
        assert client.post("/api/infer", json=request).json() == first.json()
        assert client.post("/api/infer", json=dict(request, version="main-1")).status_code == 409
        assert client.post("/api/infer", json=dict(request, relative_path="../a.jpg")).status_code == 400
