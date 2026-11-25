package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse.ItemResponse;
import fit.iuh.engolearn.placement.exception.PlacementQuestionValidationException;
import fit.iuh.engolearn.placement.service.PlacementDevelopmentAudio;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.repository.PlacementAudioAssetRepository;
import fit.iuh.engolearn.placement.service.PlacementAdminQuestionMedia;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service @Primary @RequiredArgsConstructor
public class PlacementStoredQuestionMediaImpl implements PlacementAdminQuestionMedia {
    private final MediaManagement media;
    private final PlacementAudioAssetRepository assets;
    private final Optional<PlacementDevelopmentAudio> development;

    @Override public String resolve(PlacementQuestion question) {
        if (question.getSkill() != PlacementSkill.LISTENING) return null;
        if (question.getAudioMediaId() != null && !question.getAudioMediaId().isBlank()) {
            return media.resolve(question.getAudioMediaId()).url().toString();
        }
        return development.map(adapter -> adapter.resolve(question)).orElse(null);
    }
    @Override public void requirePlayable(PlacementQuestion question) {
        if (question.getSkill() != PlacementSkill.LISTENING) return;
        if (question.getTranscript() == null || question.getTranscript().isBlank() || resolve(question) == null) {
            throw new PlacementQuestionValidationException(Map.of("audioUrl", "Choose available audio and enter its transcript."));
        }
    }
    @Override public ItemResponse requireSelection(String url) {
        for (var asset : assets.findAll()) {
            String delivery = media.resolve(asset.getMediaId()).url().toString();
            if (delivery.equals(url)) return new ItemResponse(delivery, asset.getDurationSeconds(), asset.getTitle(), asset.getMediaId());
        }
        return development.map(adapter -> adapter.requireSelection(url)).orElseThrow(() ->
                new PlacementQuestionValidationException(Map.of("audioUrl", "Choose audio from the Placement catalog.")));
    }
    @Override public PlacementAdminQuestionMediaResponse catalog(Map<String, String> titles) {
        var items = new ArrayList<ItemResponse>();
        var stored = assets.findAll();
        var hashes = stored.stream().map(asset -> asset.getId()).collect(java.util.stream.Collectors.toSet());
        for (var asset : stored) items.add(new ItemResponse(media.resolve(asset.getMediaId()).url().toString(),
                asset.getDurationSeconds(), titles.getOrDefault(asset.getId(), asset.getTitle()), asset.getMediaId()));
        if (development.isPresent()) items.addAll(development.get().catalog(titles).items().stream()
                .filter(item -> !hashes.stream().anyMatch(hash -> item.audioUrl().endsWith("/" + hash + ".wav"))).toList());
        return new PlacementAdminQuestionMediaResponse(items);
    }
}
