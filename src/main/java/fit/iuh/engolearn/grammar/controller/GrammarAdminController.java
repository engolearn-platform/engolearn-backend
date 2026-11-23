package fit.iuh.engolearn.grammar.controller;

import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Checklist;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.CreateLessonInput;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Lesson;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.LessonInput;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Topic;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.TopicInput;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.TopicPage;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.VersionInput;
import fit.iuh.engolearn.grammar.service.GrammarAdminService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/grammar/admin/topics")
@PreAuthorize("hasAnyRole('CONTENT_MANAGER', 'ADMIN')")
public class GrammarAdminController {
    private static final String LESSONS = "/{id}/lessons";
    private static final String LESSON = LESSONS + "/{lessonId}";
    private final GrammarAdminService service;

    @GetMapping
    public ApiResponse<TopicPage> list(@RequestParam(required = false) @Size(max = 160) String search,
            @RequestParam(required = false) @Pattern(regexp = "A1|A2|B1|B2") String level,
            @RequestParam(required = false) @Pattern(regexp = "DRAFT|IN_PROGRESS|PUBLISHED|ARCHIVED") String status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.list(search, level, status, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Topic> createTopic(@Valid @RequestBody TopicInput input) {
        return ApiResponse.ok(service.createTopic(input));
    }

    @GetMapping("/{id}")
    public ApiResponse<Topic> getTopic(@PathVariable String id) {
        return ApiResponse.ok(service.getTopic(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Topic> updateTopic(@PathVariable String id, @Valid @RequestBody TopicInput input) {
        return ApiResponse.ok(service.updateTopic(id, input));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteTopic(@PathVariable String id, @RequestParam @PositiveOrZero long version) {
        service.deleteTopic(id, version);
        return null;
    }

    @GetMapping("/{id}/checklist")
    public ApiResponse<Checklist> topicChecklist(@PathVariable String id) {
        return ApiResponse.ok(service.topicChecklist(id));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Topic> publishTopic(@PathVariable String id, @Valid @RequestBody VersionInput input) {
        return ApiResponse.ok(service.publishTopic(id, input.version()));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Topic> archiveTopic(@PathVariable String id, @Valid @RequestBody VersionInput input) {
        return ApiResponse.ok(service.archiveTopic(id, input.version()));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Topic> restoreTopic(@PathVariable String id, @Valid @RequestBody VersionInput input) {
        return ApiResponse.ok(service.restoreTopic(id, input.version()));
    }

    @PostMapping(LESSONS)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Lesson> createLesson(@PathVariable String id, @Valid @RequestBody CreateLessonInput input) {
        return ApiResponse.ok(service.createLesson(id, input));
    }

    @GetMapping({LESSON, LESSON + "/preview"})
    public ApiResponse<Lesson> getLesson(@PathVariable String id, @PathVariable String lessonId) {
        return ApiResponse.ok(service.getLesson(id, lessonId));
    }

    @PutMapping(LESSON)
    public ApiResponse<Lesson> updateLesson(@PathVariable String id, @PathVariable String lessonId,
            @Valid @RequestBody LessonInput input) {
        return ApiResponse.ok(service.updateLesson(id, lessonId, input));
    }

    @DeleteMapping(LESSON)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteLesson(@PathVariable String id, @PathVariable String lessonId,
            @RequestParam @PositiveOrZero long version) {
        service.deleteLesson(id, lessonId, version);
        return null;
    }

    @GetMapping(LESSON + "/checklist")
    public ApiResponse<Checklist> lessonChecklist(@PathVariable String id, @PathVariable String lessonId) {
        return ApiResponse.ok(service.lessonChecklist(id, lessonId));
    }

    @PostMapping(LESSON + "/ready")
    public ApiResponse<Lesson> readyLesson(@PathVariable String id, @PathVariable String lessonId,
            @Valid @RequestBody VersionInput input) {
        return ApiResponse.ok(service.readyLesson(id, lessonId, input.version(), Boolean.TRUE.equals(input.confirmPublish())));
    }

    @PostMapping(LESSON + "/apply")
    public ApiResponse<Lesson> applyLesson(@PathVariable String id, @PathVariable String lessonId,
            @Valid @RequestBody VersionInput input) {
        return ApiResponse.ok(service.applyLesson(id, lessonId, input.version()));
    }

    @PostMapping(LESSON + "/discard-draft")
    public ApiResponse<Lesson> discardLessonDraft(@PathVariable String id, @PathVariable String lessonId,
            @Valid @RequestBody VersionInput input) {
        return ApiResponse.ok(service.discardLessonDraft(id, lessonId, input.version()));
    }

    @ModelAttribute
    private void disableCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
