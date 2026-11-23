package kr.ace.web.benchmark.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UTF-8 CSV의 BOM, 인용된 쉼표·줄바꿈과 이중 따옴표를 처리한다. */
public record CsvTable(List<String> headers, List<Map<String, String>> rows, int invalidRows) {
    public static CsvTable read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new CsvTable(List.of(), List.of(), 0);
        }
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (source.startsWith("\uFEFF")) {
            source = source.substring(1);
        }
        var records = parse(source);
        if (records.isEmpty()) {
            return new CsvTable(List.of(), List.of(), 0);
        }
        var headers = records.get(0);
        var rows = new ArrayList<Map<String, String>>();
        int invalid = 0;
        for (var record : records.subList(1, records.size())) {
            if (record.size() != headers.size()) {
                invalid++;
                continue;
            }
            var row = new LinkedHashMap<String, String>();
            for (int i = 0; i < headers.size(); i++) {
                row.put(headers.get(i), record.get(i));
            }
            rows.add(row);
        }
        return new CsvTable(headers, rows, invalid);
    }

    private static List<List<String>> parse(String text) throws IOException {
        var records = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch != '"') {
                    field.append(ch);
                } else if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (ch == '"' && field.isEmpty()) {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (!row.isEmpty() || !field.isEmpty()) {
                    row.add(field.toString());
                    records.add(row);
                }
                row = new ArrayList<>();
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        if (quoted) {
            throw new IOException("CSV 인용 필드가 닫히지 않았습니다.");
        }
        if (!row.isEmpty() || !field.isEmpty()) {
            row.add(field.toString());
            records.add(row);
        }
        return records;
    }
}
