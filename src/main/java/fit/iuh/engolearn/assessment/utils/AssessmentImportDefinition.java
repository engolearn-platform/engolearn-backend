package fit.iuh.engolearn.assessment.utils;

import fit.iuh.engolearn.assessment.service.AssessmentMicroSkillCatalog;
import fit.iuh.engolearn.assessment.dto.request.AssessmentImportAudio;
import fit.iuh.engolearn.assessment.dto.request.DraftContentRequest;
import fit.iuh.engolearn.assessment.model.AssessmentTimeline;
import fit.iuh.engolearn.assessment.model.enums.AssessmentSection;
import fit.iuh.engolearn.assessment.model.enums.AssessmentUnitKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static fit.iuh.engolearn.assessment.utils.AssessmentWorkbook.invalid;

public record AssessmentImportDefinition(String series, String name, DraftContentRequest content,
                                         Map<Integer, String> audioFiles, String soundCheckFile,
                                         Map<String, AssessmentImportAudio> manifest) {
    private static final String FILE = "(?i)[a-z0-9][a-z0-9_.-]{0,118}\\.wav";

    public static boolean validFileName(String name) { return name != null && name.matches(FILE); }

    public static AssessmentImportDefinition read(byte[] workbook, List<AssessmentImportAudio> files,
                                                 List<AssessmentMicroSkillCatalog.Entry> catalog) {
        var sheets = AssessmentWorkbook.read(workbook, List.of("De", "Phan", "Cau"));
        var metadata = data(sheets, "De", List.of("MaDe", "TenDe", "AmThuWav"), 1).getFirst();
        String metaAt = at("De", metadata);
        String series = text(metadata, 0, 80, metaAt, "MaDe", true);
        if (!series.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,79}")) throw invalid(metaAt + "MaDe chứa ký tự không hợp lệ.");
        String name = text(metadata, 1, 180, metaAt, "TenDe", true);
        String soundCheck = metadata.cell(2);
        var manifest = manifest(files);
        var units = data(sheets, "Phan", List.of("Phan", "Loai", "DoanDoc", "TepWav"), 26);
        var rows = data(sheets, "Cau", List.of("Cau", "Phan", "Muc", "MaKyNang", "NoiDung",
                "A", "B", "C", "D", "DapAn", "GiaiThich", "Transcript"), 50);
        var definitions = new HashMap<String, AssessmentMicroSkillCatalog.Entry>();
        catalog.stream().filter(AssessmentMicroSkillCatalog.Entry::active)
                .forEach(entry -> definitions.put(entry.code(), entry));
        Map<String, DraftContentRequest.Skill> skills = new LinkedHashMap<>();
        List<DraftContentRequest.Unit> content = new ArrayList<>();
        Map<Integer, String> audioFiles = new LinkedHashMap<>();
        var selectedFiles = new HashSet<String>();
        int questionIndex = 0;

        for (int index = 0; index < units.size(); index++) {
            var row = units.get(index);
            String place = at("Phan", row) + "phần " + (index + 1) + ": ";
            order(row, 0, index + 1, place, "Phan");
            var kind = expectedKind(index);
            if (!row.cell(1).equals(kind.name())) throw invalid(place + "Loai phải là " + kind + ".");
            boolean listening = index < 12;
            String passage = text(row, 2, 12_000, place, "DoanDoc", kind == AssessmentUnitKind.READING_PASSAGE);
            String filename = row.cell(3);
            String hash = null;
            if (listening) {
                hash = audio(filename, selectedFiles, manifest, place, index < 10
                        ? AssessmentTimeline.listeningSingleAudioLimitSeconds() : 120);
                audioFiles.put(index, filename);
            } else if (!filename.isEmpty()) {
                throw invalid(place + "phần Đọc không dùng WAV.");
            }

            var questions = new ArrayList<DraftContentRequest.Question>();
            int size = kind.name().endsWith("PASSAGE") ? 5 : 1;
            for (int i = 0; i < size; i++) {
                var q = rows.get(questionIndex++);
                String questionAt = at("Cau", q) + "câu " + questionIndex + ": ";
                order(q, 0, questionIndex, questionAt, "Cau");
                order(q, 1, index + 1, questionAt, "Phan");
                String level = q.cell(2);
                if (!List.of("A1", "A2", "B1", "B2").contains(level)) throw invalid(questionAt + "Muc phải là A1, A2, B1 hoặc B2.");
                String code = q.cell(3);
                var skill = definitions.get(code);
                var section = listening ? AssessmentSection.LISTENING : AssessmentSection.READING;
                if (skill == null || !section.name().equals(skill.section())) {
                    throw invalid(questionAt + "MaKyNang " + code + " phải đang dùng trong nhóm " + section + ".");
                }
                skills.putIfAbsent(code, new DraftContentRequest.Skill(code, skill.label(), null, section));
                String prompt = text(q, 4, 2000, questionAt, "NoiDung", true);
                var options = new ArrayList<String>();
                for (int option = 0; option < 4; option++) {
                    options.add(text(q, 5 + option, 600, questionAt, String.valueOf((char) ('A' + option)), true));
                }
                String answer = q.cell(9).toUpperCase(Locale.ROOT);
                if (!answer.matches("[A-D]")) throw invalid(questionAt + "DapAn phải là A, B, C hoặc D.");
                questions.add(new DraftContentRequest.Question(level, code, prompt, options, answer.charAt(0) - 'A',
                        text(q, 10, 2000, questionAt, "GiaiThich", true),
                        text(q, 11, 4000, questionAt, "Transcript", listening)));
            }
            content.add(new DraftContentRequest.Unit(kind, passage, null, questions, hash));
        }
        if (!soundCheck.isEmpty()) audio(soundCheck, selectedFiles, manifest, metaAt + "AmThuWav: ", 50);
        if (!selectedFiles.equals(manifest.keySet())) {
            var extra = manifest.keySet().stream().filter(file -> !selectedFiles.contains(file)).toList();
            throw invalid("WAV không được tham chiếu trong De hoặc Phan: " + String.join(", ", extra) + ".");
        }
        return new AssessmentImportDefinition(series, name, new DraftContentRequest("import", name,
                List.copyOf(skills.values()), content), audioFiles, soundCheck.isEmpty() ? null : soundCheck, manifest);
    }

    private static Map<String, AssessmentImportAudio> manifest(List<AssessmentImportAudio> files) {
        if (files == null || files.size() > 13) throw invalid("Chọn 12 WAV của đề và tối đa một WAV âm thử riêng.");
        var names = new LinkedHashMap<String, AssessmentImportAudio>();
        var digests = new HashSet<String>();
        for (var file : files) {
            if (file == null || !validFileName(file.name()) || file.sha256() == null || !file.sha256().matches("[a-f0-9]{64}")
                    || !Double.isFinite(file.seconds()) || file.seconds() <= 0 || file.seconds() > 120) {
                throw invalid("WAV không hợp lệ: " + (file == null ? "" : file.name()) + ".");
            }
            String name = file.name().toLowerCase(Locale.ROOT);
            if (names.putIfAbsent(name, file) != null) throw invalid("Tên WAV bị trùng: " + file.name() + ".");
            if (!digests.add(file.sha256())) throw invalid("Nội dung WAV bị trùng: " + file.name() + ".");
        }
        return names;
    }

    private static String audio(String name, HashSet<String> used, Map<String, AssessmentImportAudio> manifest,
                                String place, int limit) {
        if (!validFileName(name)) throw invalid(place + "nhập tên tệp .wav đã chọn.");
        String key = name.toLowerCase(Locale.ROOT);
        var asset = manifest.get(key);
        if (asset == null) throw invalid(place + "thiếu WAV " + name + ".");
        if (!used.add(key)) throw invalid(place + "WAV " + name + " đã được dùng.");
        if (asset.seconds() > limit) throw invalid(place + name + " vượt " + limit + " giây.");
        return asset.sha256();
    }

    private static List<AssessmentWorkbook.Row> data(Map<String, List<AssessmentWorkbook.Row>> sheets, String name,
                                                      List<String> header, int count) {
        var rows = sheets.get(name);
        if (rows.isEmpty() || rows.getFirst().line() != 1) throw invalid("Sheet " + name + " thiếu dòng tiêu đề.");
        for (int i = 0; i < header.size(); i++) {
            if (!rows.getFirst().cell(i).equals(header.get(i))) {
                throw invalid(name + ", dòng 1: giữ cột " + header.get(i) + " như trong mẫu.");
            }
        }
        if (rows.size() != count + 1) {
            throw invalid("Sheet " + name + " cần " + count + " dòng dữ liệu. Hiện có " + (rows.size() - 1) + ".");
        }
        for (var row : rows) {
            if (row.cells().keySet().stream().anyMatch(column -> column >= header.size())) {
                throw invalid(at(name, row) + "có dữ liệu ngoài cột của mẫu.");
            }
        }
        return rows.subList(1, rows.size());
    }

    private static AssessmentUnitKind expectedKind(int index) {
        if (index < 10) return AssessmentUnitKind.LISTENING_SINGLE;
        if (index < 12) return AssessmentUnitKind.LISTENING_PASSAGE;
        if (index < 22) return AssessmentUnitKind.READING_SINGLE;
        return AssessmentUnitKind.READING_PASSAGE;
    }

    private static void order(AssessmentWorkbook.Row row, int i, int expected, String place, String field) {
        if (!String.valueOf(expected).equals(row.cell(i))) throw invalid(place + field + " phải bằng " + expected + ".");
    }

    private static String text(AssessmentWorkbook.Row row, int i, int max, String place, String field, boolean required) {
        String value = row.cell(i);
        if (required && value.isEmpty()) throw invalid(place + "thiếu " + field + ".");
        if (value.length() > max) throw invalid(place + field + " vượt " + max + " ký tự.");
        return value.isEmpty() ? null : value;
    }

    private static String at(String sheet, AssessmentWorkbook.Row row) {
        return sheet + ", dòng " + row.line() + ", ";
    }
}
