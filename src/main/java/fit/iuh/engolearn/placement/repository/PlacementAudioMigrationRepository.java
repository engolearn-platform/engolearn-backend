package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class PlacementAudioMigrationRepository {
    private static final String ROUTE = "/api/v1/placement/media/";
    private final MongoTemplate mongo;
    public Set<String> requiredHashes() {
        Set<String> hashes = new TreeSet<>();
        for (var q : mongo.findAll(PlacementQuestion.class)) {
            if (q.getSkill() != fit.iuh.engolearn.placement.model.enums.PlacementSkill.LISTENING || q.getAudioMediaId() != null) continue;
            add(hashes, q.getAudioUrl());
            if (!q.isAdminAudioOverride() && q.getProvenance() != null) add(hashes, ROUTE + q.getProvenance().getAudioSha256() + ".wav");
        }
        for (var set : mongo.findAll(PlacementTestSet.class)) if (set.getItems() != null) {
            for (var item : set.getItems()) if (item.getAudio() != null && item.getAudio().getMediaId() == null) add(hashes, item.getAudio().getUrl());
        }
        return hashes;
    }
    private void add(Set<String> hashes, String url) {
        if (url == null) return;
        var known = java.util.regex.Pattern.compile("(?:/api/v1/placement/media/|http://127\\.0\\.0\\.1:8892/audio/)([a-f0-9]{64})\\.wav").matcher(url);
        if (known.matches()) hashes.add(known.group(1));
    }
    public long link(String hash, String mediaId) {
        String url = ROUTE + hash + ".wav";
        String legacy = "http://127.0.0.1:8892/audio/" + hash + ".wav";
        var missing = Criteria.where("skill").is("LISTENING").and("audio_media_id").is(null).orOperator(
                Criteria.where("audio_url").in(url, legacy), Criteria.where("admin_audio_override").ne(true).and("provenance.audio_sha256").is(hash));
        long changed = mongo.updateMulti(Query.query(missing), new Update().set("audio_media_id", mediaId)
                .inc("revision", 1), PlacementQuestion.class).getModifiedCount();
        var entry = Criteria.where("audio.url").in(url, legacy).and("audio.media_id").is(null);
        var filter = Criteria.where("clip.audio.url").in(url, legacy).and("clip.audio.media_id").is(null);
        changed += mongo.updateMulti(Query.query(Criteria.where("items").elemMatch(entry)),
                new Update().set("items.$[clip].audio.media_id", mediaId).inc("revision", 1).filterArray(filter),
                PlacementTestSet.class).getModifiedCount();
        return changed;
    }
}
