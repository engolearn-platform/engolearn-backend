package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.mapper.TopicQuizMapper;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.vo.QuizProgressVo;

import java.util.List;
import java.util.Optional;

public interface TopicQuizProgressService {
    LearningStage firstQuizStage(TopicItemVersion version);

    int quizCount(TopicItemVersion version, QuizType type);

    Optional<TopicQuizMapper.QuizEntry> firstUnanswered(
            List<TopicQuizMapper.QuizEntry> sequence, List<QuizProgressVo> progress);

    boolean answeredCorrectly(TopicQuizMapper.QuizEntry entry, List<QuizProgressVo> progress);
}
