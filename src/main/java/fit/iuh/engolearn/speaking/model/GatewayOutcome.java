package fit.iuh.engolearn.speaking.model;

import java.util.Objects;

public sealed interface GatewayOutcome
        permits GatewayOutcome.Success, GatewayOutcome.EmptyCandidate,
        GatewayOutcome.MalformedProviderResponse, GatewayOutcome.HttpFailure,
        GatewayOutcome.Timeout, GatewayOutcome.Interrupted,
        GatewayOutcome.MissingProviderMetadata, GatewayOutcome.TransportFailure {

    record Success(ProviderRawDraft draft) implements GatewayOutcome {
        public Success {
            Objects.requireNonNull(draft, "draft");
        }
    }

    record EmptyCandidate(ProviderRawDraft.ProviderMetadata metadata) implements GatewayOutcome {
        public EmptyCandidate {
            Objects.requireNonNull(metadata, "metadata");
        }
    }

    record MalformedProviderResponse(ProviderFailure failure) implements GatewayOutcome {
        public MalformedProviderResponse {
            requireFailure(failure);
        }
    }

    record HttpFailure(ProviderFailure failure) implements GatewayOutcome {
        public HttpFailure {
            requireFailure(failure);
        }
    }

    record Timeout(ProviderFailure failure) implements GatewayOutcome {
        public Timeout {
            requireFailure(failure);
        }
    }

    record Interrupted(ProviderFailure failure) implements GatewayOutcome {
        public Interrupted {
            requireFailure(failure);
        }
    }

    record MissingProviderMetadata(ProviderFailure failure) implements GatewayOutcome {
        public MissingProviderMetadata {
            requireFailure(failure);
        }
    }

    record TransportFailure(ProviderFailure failure) implements GatewayOutcome {
        public TransportFailure {
            requireFailure(failure);
        }
    }

    private static void requireFailure(ProviderFailure failure) {
        Objects.requireNonNull(failure, "failure");
    }

    enum FailureCode {
        MALFORMED_WRAPPER,
        MALFORMED_CANDIDATE,
        RATE_LIMITED_429,
        SERVER_5XX,
        OTHER_HTTP,
        TRANSPORT,
        TIMEOUT,
        INTERRUPTED,
        MISSING_METADATA
    }

    record ProviderFailure(
            FailureCode code,
            String message,
            Integer httpStatus,
            ProviderRawDraft.ProviderMetadata metadata,
            Throwable cause
    ) {
        public ProviderFailure {
            Objects.requireNonNull(code, "code");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("message must be nonblank");
        }
    }
}
