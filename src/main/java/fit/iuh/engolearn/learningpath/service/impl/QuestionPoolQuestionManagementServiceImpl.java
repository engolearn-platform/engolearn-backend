package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolQuestionsRequest;
import fit.iuh.engolearn.learningpath.dto.response.*;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolPassageNotFoundException;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolQuestionNotFoundException;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolQuestionRepository;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageManagementService;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionPoolQuestionManagementServiceImpl implements QuestionPoolQuestionManagementService {
    private final QuestionPoolQuestionRepository questions;
    private final QuestionPoolPassageManagementService passages;

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolQuestionListResponse list(ListQuestionPoolQuestionsRequest request) {
        var page = questions.search(request.keyword(), request.skill(), request.microSkill(), request.level(),
                request.status(), request.passageId(), request.page(), request.size());
        var items = page.getContent().stream().map(question -> new QuestionPoolQuestionListItemResponse(
                question.getQuestionId(), question.getSkill(), question.getMicroSkill(), question.getLevel(),
                question.getPassageId(), question.getPrompt(), question.getStatus(), question.getRevision(),
                question.getCreatedAt(), question.getUpdatedAt())).toList();
        return new QuestionPoolQuestionListResponse(items, request.page(), request.size(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolQuestionDetailResponse detail(String questionId) {
        var question = questions.findByQuestionId(questionId).orElseThrow(QuestionPoolQuestionNotFoundException::new);
        QuestionPoolPassageDetailResponse passage = null;
        if (question.getPassageId() != null && !question.getPassageId().isBlank()) {
            try {
                passage = passages.detail(question.getPassageId());
            } catch (QuestionPoolPassageNotFoundException missingReference) {
                // Keep the question inspectable so an editor can see its dangling passage reference.
            }
        }
        var options = question.getOptions() == null ? null : question.getOptions().stream()
                .map(option -> option == null ? null : new QuestionPoolQuestionOptionResponse(option.getOptionId(), option.getText())).toList();
        return new QuestionPoolQuestionDetailResponse(question.getQuestionId(), question.getSkill(), question.getMicroSkill(),
                question.getLevel(), question.getPassageId(), question.getPrompt(), options, question.getCorrectOptionId(),
                question.getExplanation(), question.getStatus(), question.getRevision(), question.getCreatedBy(), question.getUpdatedBy(),
                question.getCreatedAt(), question.getUpdatedAt(), passage);
    }
}
