package fit.iuh.engolearn.topic.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;

import java.math.BigDecimal;
import java.util.List;

public record TopicItemQuizzesRequest(BigDecimal revision,
                                      @JsonProperty(value = "quizzes", required = true)
                                      List<TopicManagementItemDetail.QuizModule> quizzes) {}
