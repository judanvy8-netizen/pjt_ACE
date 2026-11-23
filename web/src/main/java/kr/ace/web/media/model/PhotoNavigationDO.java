package kr.ace.web.media.model;

import java.util.List;

/** 사진 비교 화면에서 하나의 목록과 현재 위치로 이동 링크를 계산합니다. */
public record PhotoNavigationDO(String basePath, List<Choice> choices, int index) {
    public record Choice(String key, String filename) {}

    public static PhotoNavigationDO of(String basePath, List<Choice> choices, String current) {
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).key().equals(current)) {
                return new PhotoNavigationDO(basePath, List.copyOf(choices), index);
            }
        }
        throw new IllegalArgumentException("현재 사진이 이동 목록에 없습니다.");
    }

    public Choice previous() {
        return index == 0 ? null : choices.get(index - 1);
    }

    public Choice next() {
        return index + 1 == choices.size() ? null : choices.get(index + 1);
    }

    public String path(Choice choice) {
        return basePath + "/" + choice.key();
    }
}
