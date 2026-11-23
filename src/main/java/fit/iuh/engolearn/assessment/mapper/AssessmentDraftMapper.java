package fit.iuh.engolearn.assessment.mapper;

import fit.iuh.engolearn.assessment.dto.request.DraftContentRequest;
import fit.iuh.engolearn.assessment.dto.request.DraftContentRequest.*;
import fit.iuh.engolearn.assessment.dto.response.AssessmentDraftResponse;
import fit.iuh.engolearn.assessment.model.*;
import fit.iuh.engolearn.assessment.model.vo.*;
import java.util.*;
import java.util.stream.IntStream;

public final class AssessmentDraftMapper {
    private AssessmentDraftMapper() {}

    public static AssessmentDraftResponse response(AssessmentDraft draft) {
        var skills = draft.getSkills().stream()
                .map(s -> new Skill(s.getCode(), s.getName(), s.getExample(), s.getSection())).toList();
        var units = IntStream.range(0, draft.getUnits().size()).mapToObj(i -> {
            var u = draft.getUnits().get(i);
            var sourceQuestions = u.getQuestions() == null ? List.<AssessmentQuestionVo>of() : u.getQuestions();
            var questions = sourceQuestions.stream().map(q -> new Question(q.getLevel(), q.getSkillCode(),
                    q.getPrompt(), q.getOptions() == null ? List.of() : q.getOptions(), q.getCorrectOption() < 0 ? null : q.getCorrectOption(),
                    q.getExplanation(), q.getTranscript())).toList();
            String digest = u.getAudioMediaId() == null || draft.getSourceAudioUnits().get(i) != null
                    ? null : u.getAudioSha256();
            return new Unit(u.getKind(), u.getPassage(), draft.getSourceAudioUnits().get(i), questions, digest);
        }).toList();
        return new AssessmentDraftResponse(draft.getSeries(), draft.getName(), draft.getVersion(),
                draft.getRevision(), skills, units);
    }

    public static void applyContent(AssessmentDraft draft, DraftContentRequest input, AssessmentPaper source,
                                    Map<String, AssessmentAudioAsset> uploaded) {
        draft.setName(input.name());
        draft.setSkills(input.skills().stream()
                .map(s -> new AssessmentSkillVo(s.code(), s.name(), s.example(), s.section())).toList());
        var units = new ArrayList<AssessmentUnitVo>();
        int number = 0;
        for (Unit unit : input.units()) {
            var audio = unit.sourceAudioUnit() == null ? null : source.getUnits().get(unit.sourceAudioUnit());
            var questions = new ArrayList<AssessmentQuestionVo>();
            for (Question q : unit.questions()) {
                questions.add(new AssessmentQuestionVo(++number, q.level(), q.skillCode(), q.prompt(), q.options(),
                        q.correctOption() == null ? -1 : q.correctOption(), q.explanation(), q.transcript()));
            }
            var asset = uploaded.get(unit.audioSha256());
            units.add(new AssessmentUnitVo(unit.kind(), unit.passage(), audio == null ? null : audio.getAudioPath(),
                    asset == null ? (audio == null ? null : audio.getAudioSha256()) : asset.getId(),
                    questions, asset == null ? (audio == null ? null : audio.getAudioMediaId()) : asset.getMediaId()));
        }
        draft.setUnits(units);
        draft.setSourceAudioUnits(input.units().stream().map(Unit::sourceAudioUnit).toList());
    }
}
