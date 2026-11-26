package fit.iuh.engolearn.topic.dto.request;

import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import java.math.BigDecimal;
import java.util.List;

/** One typed resource section is supplied with the current published version identity. */
public record TopicItemMinorRequest(BigDecimal revision, String itemVersionId,
                                    TopicManagementItemDetail.Context context,
                                    List<TopicManagementItemDetail.Vocabulary> vocabularies,
                                    List<TopicManagementItemDetail.ExpressionBlock> expressions,
                                    List<TopicManagementItemDetail.QuizModule> quizzes) {}
