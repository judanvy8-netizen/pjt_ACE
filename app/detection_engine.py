"""main의 개·고양이 검출."""

from shared.common import read_image, yolo, standardize_image, MODEL_PATH, model_hashes

TARGETS = ("cat", "dog")


class DetectionEngine:
    def __init__(self):
        self.model = yolo()
        self.model_hashes = model_hashes(MODEL_PATH)
        self.ids = [i for i, name in self.model.names.items() if name in TARGETS]

    def analyze(self, path, confidence=0.25, *, image=None):
        result = self.model.predict(standardize_image(read_image(path) if image is None else image), classes=self.ids, conf=confidence,
                                    device="cpu", verbose=False)[0]
        counts = dict.fromkeys(TARGETS, 0)
        objects = []
        for number, box in enumerate(result.boxes, 1):
            species = result.names[int(box.cls[0].item())]
            counts[species] += 1
            objects.append({
                "object_no": number, "detected_species": species,
                "box": box.xyxy[0].tolist(), "det_confidence": round(float(box.conf[0].item()), 4),
                "class_label": "", "class_confidence": None, "species_match": "not_applicable",
            })
        return result.plot(), counts, objects
