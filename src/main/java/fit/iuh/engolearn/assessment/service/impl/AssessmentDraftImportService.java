package fit.iuh.engolearn.assessment.service.impl;

import fit.iuh.engolearn.assessment.service.AssessmentMicroSkillCatalog;
import fit.iuh.engolearn.assessment.dto.request.AssessmentImportAudio;
import fit.iuh.engolearn.assessment.dto.response.AssessmentDraftResponse;
import fit.iuh.engolearn.assessment.dto.response.AssessmentImportPreview;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.mapper.AssessmentDraftMapper;
import fit.iuh.engolearn.assessment.model.AssessmentAudioAsset;
import fit.iuh.engolearn.assessment.model.AssessmentDraft;
import fit.iuh.engolearn.assessment.model.AssessmentDraftImport;
import fit.iuh.engolearn.assessment.model.AssessmentPaper;
import fit.iuh.engolearn.assessment.model.vo.AssessmentSkillVo;
import fit.iuh.engolearn.assessment.repository.AssessmentDraftRepository;
import fit.iuh.engolearn.assessment.service.AssessmentCloudAudio;
import fit.iuh.engolearn.assessment.service.AssessmentPaperSelection;
import fit.iuh.engolearn.assessment.service.AssessmentTransactions;
import fit.iuh.engolearn.assessment.utils.AssessmentImportDefinition;
import fit.iuh.engolearn.assessment.utils.AssessmentWav;
import fit.iuh.engolearn.assessment.utils.AssessmentWorkbook;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import static fit.iuh.engolearn.assessment.utils.AssessmentWorkbook.invalid;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class AssessmentDraftImportService {
    private final AssessmentDraftRepository drafts;
    private final AssessmentMicroSkillCatalog skills;
    private final AssessmentPaperSelection selection;
    private final AssessmentCloudAudio audio;
    private final AssessmentTransactions transactions;

    public byte[] template() throws IOException {
        try (var input = new ClassPathResource("assessment/pfc-workbook.xlsx").getInputStream()) {
            return input.readAllBytes();
        }
    }

    public AssessmentImportAudio inspect(String name, InputStream input) throws IOException {
        if (!AssessmentImportDefinition.validFileName(name)) throw invalid("Tên tệp WAV không hợp lệ: " + name + ".");
        byte[] bytes = input.readNBytes(AssessmentWav.MAX_BYTES + 1);
        if (bytes.length == 0 || bytes.length > AssessmentWav.MAX_BYTES) {
            throw invalid(name + ": WAV rỗng hoặc vượt 4.000.000 byte.");
        }
        try {
            return new AssessmentImportAudio(name, HexFormat.of().formatHex(digest().digest(bytes)),
                    AssessmentWav.duration(bytes, 120));
        } catch (AssessmentException error) {
            throw invalid(name + ": " + error.getMessage());
        }
    }

    public AssessmentImportPreview preview(InputStream input, List<AssessmentImportAudio> files) throws IOException {
        byte[] workbook = workbook(input);
        var definition = AssessmentImportDefinition.read(workbook, files, skills.list());
        String token = token(workbook, files);
        var draft = prepare(definition, token, false);
        return new AssessmentImportPreview(AssessmentDraftMapper.response(draft), token,
                definition.audioFiles(), definition.soundCheckFile());
    }

    public AssessmentDraftResponse create(InputStream input, List<AssessmentImportAudio> files, String expectedToken)
            throws IOException {
        byte[] workbook = workbook(input);
        String token = token(workbook, files);
        if (!token.equals(expectedToken)) {
            throw new AssessmentException(409, "IMPORT_PREVIEW_CHANGED", "Tệp đã thay đổi. Hãy kiểm tra và xem thử lại.");
        }
        var replay = replay(token);
        if (replay != null) return replay;

        var definition = AssessmentImportDefinition.read(workbook, files, skills.list());
        var draft = prepare(definition, token, true);
        return transactions.execute("paper:" + definition.series(), () -> {
            var repeated = replay(token);
            if (repeated != null) return repeated;
            if (drafts.bySeries(definition.series()) != null) {
                throw new AssessmentException(409, "DRAFT_EXISTS", "Mã này đã có bản nháp. Nhập bộ khác hoặc mở bản đang có.");
            }
            var latest = drafts.latestPaper(definition.series());
            draft.setVersion(latest == null ? 1 : latest.getVersion() + 1);
            draft.setDemonstration(latest != null && latest.isDemonstration());
            draft.setRevision(UUID.randomUUID().toString());
            draft.setSkills(draft.getSkills().stream().map(skill -> {
                var definitionEntry = skills.retainActive(skill.getCode());
                return new AssessmentSkillVo(skill.getCode(), definitionEntry.label(), skill.getExample(), skill.getSection());
            }).toList());
            drafts.insert(draft);
            var sound = definition.soundCheckFile() == null ? null
                    : definition.manifest().get(definition.soundCheckFile().toLowerCase(Locale.ROOT)).sha256();
            drafts.insertImportReceipt(AssessmentDraftImport.builder().id(token).series(definition.series())
                    .soundCheckSha256(sound).build());
            return AssessmentDraftMapper.response(draft);
        });
    }

    private AssessmentDraftResponse replay(String token) {
        var receipt = drafts.importReceipt(token);
        if (receipt == null) return null;
        var existing = drafts.bySeries(receipt.getSeries());
        if (existing != null && token.equals(existing.getImportToken())) return AssessmentDraftMapper.response(existing);
        throw new AssessmentException(409, "IMPORT_ALREADY_USED", "Gói này đã được nhập. Kiểm tra bản nháp hoặc các phiên bản đề.");
    }

    private AssessmentDraft prepare(AssessmentImportDefinition definition, String token, boolean committedMedia) {
        var assets = new HashMap<String, AssessmentAudioAsset>();
        for (var file : definition.manifest().values()) {
            var asset = committedMedia ? audio.find(file.sha256())
                    : new AssessmentAudioAsset(file.sha256(), "preview:" + file.sha256(), file.seconds());
            if (asset == null) {
                throw new AssessmentException(422, "IMPORT_MEDIA_MISSING", file.name() + ": Media chưa nhận tệp này.");
            }
            if (Math.abs(asset.getDurationSeconds() - file.seconds()) > 0.000001) {
                throw invalid(file.name() + ": thời lượng Media khác lần kiểm, hãy kiểm lại WAV.");
            }
            assets.put(file.sha256(), asset);
        }
        var draft = AssessmentDraft.builder().id("draft:" + definition.series()).series(definition.series())
                .name(definition.name()).version(1).revision("import-preview").importToken(token).build();
        AssessmentDraftMapper.applyContent(draft, definition.content(), null, assets);
        var paper = AssessmentPaper.builder().id(draft.getId()).series(draft.getSeries()).version(1)
                .rulesVersion(2).skills(draft.getSkills()).units(draft.getUnits()).build();
        try {
            selection.validateStructure(paper);
        } catch (AssessmentException validation) {
            throw invalid("Ma trận trong Phan/Cau: " + validation.getMessage());
        }
        return draft;
    }

    private static byte[] workbook(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(AssessmentWorkbook.MAX_BYTES + 1);
        if (bytes.length == 0 || bytes.length > AssessmentWorkbook.MAX_BYTES) {
            throw invalid("Tệp .xlsx phải có dữ liệu và tối đa 2 MB.");
        }
        return bytes;
    }

    private static String token(byte[] workbook, List<AssessmentImportAudio> files) {
        if (files == null || files.size() > 13 || files.stream().anyMatch(file -> file == null
                || !AssessmentImportDefinition.validFileName(file.name()) || file.sha256() == null
                || !file.sha256().matches("[a-f0-9]{64}"))) {
            throw invalid("Danh sách WAV của lần kiểm không hợp lệ.");
        }
        var digest = digest();
        digest.update(digest().digest(workbook));
        files.stream().sorted(Comparator.comparing(AssessmentImportAudio::name)).forEach(file ->
                digest.update((file.name() + "|" + file.sha256() + "\n").getBytes(StandardCharsets.UTF_8)));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
