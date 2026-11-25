package fit.iuh.engolearn.companion.controller;

import fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest;
import fit.iuh.engolearn.companion.dto.response.CompanionStreamErrorResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionTurnCompletedResponse;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.service.CompanionTurnResult;
import fit.iuh.engolearn.companion.service.CompanionTurnService;
import fit.iuh.engolearn.config.CompanionProperties;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/api/v1/companion/conversations/{conversationId}/turns")
public class CompanionTurnStreamController {
    private final CompanionTurnService turns;
    private final CompanionProperties properties;
    private final ExecutorService streamExecutor;

    public CompanionTurnStreamController(CompanionTurnService turns, CompanionProperties properties,
            @Qualifier("companionStreamExecutor") ExecutorService streamExecutor) {
        this.turns = turns;
        this.properties = properties;
        this.streamExecutor = streamExecutor;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter turn(@PathVariable String conversationId, @Valid @RequestBody CompanionTurnRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        var prepared = turns.prepareTurn(conversationId, request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Accel-Buffering", "no");
        var emitter = new SseEmitter(properties.getTurnTimeout().toMillis());
        var cancelled = new AtomicBoolean();
        var terminalSent = new AtomicBoolean();
        var started = new AtomicBoolean();
        var upstreamCancel = new AtomicReference<Runnable>();
        Runnable cancel = () -> {
            cancelled.set(true);
            Runnable action = upstreamCancel.getAndSet(null);
            if (action != null) {
                try {
                    action.run();
                } catch (RuntimeException ignored) {
                    // Cancellation is best effort; the reservation is also fenced by its lease.
                }
            }
            if (!started.get()) turns.cancelPreparedTurn(prepared);
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(() -> {
            cancel.run();
            if (terminalSent.compareAndSet(false, true)) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(new CompanionStreamErrorResponse(
                            java.util.UUID.fromString(prepared.turnId()), "COMPANION_DEADLINE_EXCEEDED",
                            "The response took too long. Retry this turn.", true)));
                } catch (IOException | IllegalStateException ignored) {
                    cancelled.set(true);
                }
            }
            emitter.complete();
        });
        emitter.onError(failure -> cancel.run());

        Runnable streamTask = () -> {
            started.set(true);
            try {
                if (cancelled.get()) {
                    turns.cancelPreparedTurn(prepared);
                    return;
                }
                CompanionTurnResult result = turns.streamPreparedTurn(prepared, new CompanionTurnService.TurnObserver() {
                    @Override
                    public void onDelta(String text) {
                        send(emitter, "delta", Map.of("text", text), cancelled);
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }

                    @Override
                    public void setCancelAction(Runnable cancelAction) {
                        if (cancelAction == null) {
                            upstreamCancel.set(null);
                            return;
                        }
                        upstreamCancel.set(cancelAction);
                        if (cancelled.get() && upstreamCancel.compareAndSet(cancelAction, null)) {
                            try {
                                cancelAction.run();
                            } catch (RuntimeException ignored) {
                                // The service still fences and releases the reservation on exit.
                            }
                        }
                    }
                });
                if (!cancelled.get() && terminalSent.compareAndSet(false, true)) {
                    CompanionTurnCompletedResponse completed = new CompanionTurnCompletedResponse(
                            java.util.UUID.fromString(result.turnId()), result.assistantMessage(), result.messageCount(),
                            result.maxMessages(), result.warning());
                    send(emitter, "completed", completed, cancelled);
                    emitter.complete();
                }
            } catch (RuntimeException failure) {
                if (!cancelled.get() && terminalSent.compareAndSet(false, true)) {
                    CompanionStreamErrorResponse error = safeError(java.util.UUID.fromString(prepared.turnId()), failure);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(error));
                        emitter.complete();
                    } catch (IOException | IllegalStateException ignored) {
                        cancelled.set(true);
                    }
                }
            }
        };
        try {
            streamExecutor.execute(streamTask);
        } catch (RejectedExecutionException rejected) {
            turns.cancelPreparedTurn(prepared);
            throw new CompanionException(503, "STREAM_CAPACITY_EXCEEDED",
                    "Companion streaming is busy. Retry this turn.", true);
        }
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Object data, AtomicBoolean cancelled) {
        if (cancelled.get()) throw new java.util.concurrent.CancellationException();
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException failure) {
            cancelled.set(true);
            throw new java.util.concurrent.CancellationException();
        }
    }

    private static CompanionStreamErrorResponse safeError(java.util.UUID turnId, RuntimeException failure) {
        if (failure instanceof CompanionException turnFailure) {
            return new CompanionStreamErrorResponse(turnId, turnFailure.getCode(), turnFailure.getSafeDetail(), turnFailure.isRetryable());
        }
        return new CompanionStreamErrorResponse(turnId, "AI_PROVIDER_UNAVAILABLE",
                "The response could not be generated. Retry this turn.", true);
    }
}
