package kr.ace.web.documentation.model;

import java.util.List;

public final class SourceStructureDO {
    private SourceStructureDO() {}

    public record Entry(String category, String files, String purpose) {}
    public record Group(String id, String purpose, String path, List<Entry> entries, String views) {}
}
