package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.dto.response.*;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.model.CompanionConversation;
import fit.iuh.engolearn.companion.model.CompanionMessage;
import fit.iuh.engolearn.companion.repository.CompanionConversationRepository;
import fit.iuh.engolearn.companion.repository.CompanionMessageRepository;
import fit.iuh.engolearn.companion.service.CompanionConversationService;
import fit.iuh.engolearn.config.CompanionProperties;
import fit.iuh.engolearn.shared.messaging.MongoMessageTransactions;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanionConversationServiceImpl implements CompanionConversationService {
    private final CurrentUserProvider users;
    private final CompanionConversationRepository conversations;
    private final CompanionMessageRepository messages;
    private final CompanionProperties properties;
    private final MongoMessageTransactions transactions;

    @Override public ConversationResponse create() {
        Instant now = Instant.now();
        CompanionConversation conversation = CompanionConversation.builder().learnerId(users.requireCurrentUserId())
                .messageCount(0).nextSequence(1).activeTurnGeneration(0).createdAt(now).updatedAt(now).build();
        return response(transactions.required(() -> conversations.insert(conversation)));
    }

    @Override public ConversationPageResponse list(String before, int limit) {
        checkLimit(limit);
        Cursor cursor = before == null ? null : decode(before);
        List<CompanionConversation> rows = conversations.listOwned(users.requireCurrentUserId(),
                cursor == null ? null : cursor.updatedAt(), cursor == null ? null : cursor.id(), limit + 1);
        boolean more = rows.size() > limit;
        List<CompanionConversation> page = more ? rows.subList(0, limit) : rows;
        String next = more ? encode(page.get(page.size() - 1)) : null;
        return new ConversationPageResponse(page.stream().map(this::response).toList(), next);
    }

    @Override public ConversationResponse get(String conversationId) {
        return response(owned(conversationId));
    }

    @Override public void delete(String conversationId) {
        String learner = users.requireCurrentUserId();
        transactions.required(() -> {
            CompanionConversation conversation = conversations.findOwned(conversationId, learner)
                    .orElseThrow(CompanionConversationServiceImpl::notFound);
            conversations.deleteOwned(conversation.getId(), learner);
            messages.deleteConversationMessages(conversation.getId());
            return null;
        });
    }

    @Override public MessagePageResponse messages(String conversationId, Long beforeSequence, int limit) {
        checkLimit(limit);
        owned(conversationId);
        List<CompanionMessage> rows = messages.newest(conversationId, beforeSequence, limit + 1);
        boolean more = rows.size() > limit;
        List<CompanionMessage> page = more ? rows.subList(0, limit) : rows;
        java.util.ArrayList<CompanionMessage> chronological = new java.util.ArrayList<>(page);
        java.util.Collections.reverse(chronological);
        Long next = more && !chronological.isEmpty() ? chronological.get(0).getSequence() : null;
        return new MessagePageResponse(chronological.stream().map(this::message).toList(), next);
    }

    private CompanionConversation owned(String id) {
        return conversations.findOwned(id, users.requireCurrentUserId()).orElseThrow(CompanionConversationServiceImpl::notFound);
    }

    private ConversationResponse response(CompanionConversation value) {
        int threshold = (int) Math.ceil(properties.getMaxMessages() * properties.getWarningPercent() / 100.0);
        return new ConversationResponse(value.getId(), value.getTitle(), value.getCreatedAt(), value.getUpdatedAt(),
                value.getMessageCount(), properties.getMaxMessages(), threshold);
    }

    private MessageResponse message(CompanionMessage value) {
        return new MessageResponse(value.getId(), value.getSequence(), value.getRole().name(), value.getContent(), value.getCreatedAt());
    }

    private void checkLimit(int limit) {
        if (limit < 1 || limit > 100) throw new CompanionException(400, "INVALID_LIMIT", "Limit must be between 1 and 100", false);
    }

    private String encode(CompanionConversation row) {
        String raw = row.getUpdatedAt().toString() + "\n" + row.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\n", 2);
            if (parts.length != 2 || parts[1].isBlank()) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), parts[1]);
        } catch (RuntimeException ex) {
            throw new CompanionException(400, "INVALID_CURSOR", "Conversation cursor is invalid", false);
        }
    }

    private static CompanionException notFound() {
        return new CompanionException(404, "CONVERSATION_NOT_FOUND", "Conversation was not found", false);
    }

    private record Cursor(Instant updatedAt, String id) {}
}
