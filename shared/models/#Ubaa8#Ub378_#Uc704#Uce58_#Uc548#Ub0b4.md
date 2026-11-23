# 모델 위치 안내

모델 파일은 프로젝트의 `shared/models` 폴더에 둠.

- `yolo11n.pt`: 사진에서 개와 고양이의 위치를 찾아 개수를 셈.
  `main.py`, `main-1.py`, `main-2.py`가 사용함.
- `yolo11n-cls.pt`: 잘라 낸 사진을 한 종류로 분류하는 모델.
  `main-1.py`에서는 개와 고양이 모두에 사용함.
  `main-2.py`에서는 개에만 사용함.
  첫 버전 `main.py`에서는 사용하지 않음.
- `cat_oxford_mobilenetv3.pt`: Oxford 사진으로 학습된 고양이 12종 분류 모델.
  `main-2.py`에서 고양이로 검출된 부분에만 사용함.

`yolo11n-cls.pt`는 Ultralytics 공식 모델 파일을 받았음.
기본 분류 대상은 ImageNet의 1000개 종류임.
`main-1.py`에서는 151~268번을 개 품종 후보 118종,
281~285번을 고양이 후보 5종으로 확인함.
이 중 281번 `tabby`와 282번 `tiger cat`은 털무늬를 가리킴.
286번 쿠거와 287번 스라소니는 고양이 품종 후보에서 제외함.
`main-2.py`의 고양이 분류에는 ImageNet 번호를 사용하지 않음.
검출 박스가 겹치거나 동물 여러 마리가 한 박스에 들어가면
분류 이름이 실제 품종과 다를 수 있음.

YOLO 모델 출처: https://github.com/ultralytics/assets/releases/tag/v8.3.0
고양이 모델 출처: https://huggingface.co/WillyArdiyanto/cat-breed-classification-mobilenetv3
고양이 모델 공개 페이지에 표시된 라이선스는 Apache-2.0임.
