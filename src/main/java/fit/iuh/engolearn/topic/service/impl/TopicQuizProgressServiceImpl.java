package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.mapper.TopicQuizMapper;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.vo.QuizProgressVo;
import fit.iuh.engolearn.topic.model.vo.QuizSubmissionVo;
import fit.iuh.engolearn.topic.service.TopicQuizProgressService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TopicQuizProgressServiceImpl implements TopicQuizProgressService {
    @Override
    public LearningStage firstQuizStage(TopicItemVersion version) {
        return quizCount(version, QuizType.FILL_BLANK) > 0 ? LearningStage.FILL_BLANK
                : quizCount(version, QuizType.SITUATIONAL_CHOICE) > 0 ? LearningStage.SITUATIONAL_CHOICE : null;
    }

    @Override
    public int quizCount(TopicItemVersion version, QuizType type) {
        return version.getQuizzes() == null ? 0 : version.getQuizzes().stream().filter(module -> module.getQuizType() == type)
                .mapToInt(module -> module.getQuestions() == null ? 0 : module.getQuestions().size()).sum();
    }

    @Override
    public Optional<TopicQuizMapper.QuizEntry> firstUnanswered(List<TopicQuizMapper.QuizEntry> sequence,
            List<QuizProgressVo> progress) {
        if (sequence == null) return Optional.empty();
        for (TopicQuizMapper.QuizEntry entry : sequence) {
            if (entry == null || entry.quizId() == null) continue;
            if (!answeredCorrectly(entry, progress)) return Optional.of(entry);
        }
        return Optional.empty();
    }

    @Override
    public boolean answeredCorrectly(TopicQuizMapper.QuizEntry entry, List<QuizProgressVo> progress) {
        if (progress == null) return false;
        for (QuizProgressVo state : progress) {
            if (state == null || !entry.quizId().equals(state.getQuizId()) || state.getSubmissions() == null) continue;
            for (QuizSubmissionVo submission : state.getSubmissions()) {
                if (submission != null && Boolean.TRUE.equals(submission.getIsCorrect())) return true;
            }
        }
        return false;
    }

}
