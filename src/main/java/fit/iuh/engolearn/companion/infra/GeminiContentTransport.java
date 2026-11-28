package fit.iuh.engolearn.companion.infra;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import java.util.List;

/** Google SDK boundary kept private to the companion infrastructure package. */
interface GeminiContentTransport extends AutoCloseable {
    GeminiContentStream stream(String model, List<Content> contents, GenerateContentConfig config);

    @Override
    default void close() {}

    interface GeminiContentStream extends Iterable<GenerateContentResponse>, AutoCloseable {
        @Override
        void close();
    }
}
