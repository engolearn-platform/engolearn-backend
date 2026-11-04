package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingUsageSegment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface SpeakingUsageSegmentRepository extends MongoRepository<SpeakingUsageSegment, String> {
    List<SpeakingUsageSegment> findByLearnerIdAndDay(String learnerId, LocalDate day);
}
