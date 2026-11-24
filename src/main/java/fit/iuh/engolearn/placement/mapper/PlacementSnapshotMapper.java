package fit.iuh.engolearn.placement.mapper;

import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.vo.*;
import java.util.List;

public final class PlacementSnapshotMapper {
    private PlacementSnapshotMapper() {}
    public static PlacementTestSet copy(PlacementTestSet source, java.util.function.UnaryOperator<PlacementAudioVo> audio) {
        List<PlacementSnapshotItemVo> items = source.getItems() == null ? List.of() : source.getItems().stream().map(item ->
                PlacementSnapshotItemVo.builder().id(item.getId()).code(item.getCode()).order(item.getOrder())
                        .skill(item.getSkill()).level(item.getLevel()).label(item.getLabel())
                        .prompt(item.getPrompt()).passage(item.getPassage())
                        .correctOptionId(item.getCorrectOptionId())
                        .explanation(item.getExplanation()).transcript(item.getTranscript())
                        .audio(item.getAudio() == null ? null : audio.apply(new PlacementAudioVo(
                                item.getAudio().getUrl(), item.getAudio().getDurationSeconds(), item.getAudio().getMediaId())))
                        .options(item.getOptions().stream().map(o ->
                                new PlacementOptionVo(o.getId(), o.getLabel(), o.getText())).toList())
                        .build()).toList();
        return PlacementTestSet.builder().id(source.getId()).code(source.getCode())
                .name(source.getName()).version(source.getVersion()).status(source.getStatus())
                .createdAt(source.getCreatedAt()).updatedAt(source.getUpdatedAt()).revision(source.getRevision())
                .questionIds(source.getQuestionIds() == null ? null : List.copyOf(source.getQuestionIds()))
                .items(items).build();
    }
}
