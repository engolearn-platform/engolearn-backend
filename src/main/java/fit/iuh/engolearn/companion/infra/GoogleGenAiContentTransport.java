package fit.iuh.engolearn.companion.infra;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import java.util.List;

/** Direct SDK transport required to preserve native Gemini tool-call continuation fields. */
final class GoogleGenAiContentTransport implements GeminiContentTransport {
    private final Client client;

    GoogleGenAiContentTransport(Client client) {
        this.client = client;
    }

    @Override
    public GeminiContentStream stream(String model, List<Content> contents, GenerateContentConfig config) {
        ResponseStream<GenerateContentResponse> responseStream =
                client.models.generateContentStream(model, contents, config);
        return new GeminiContentStream() {
            @Override
            public java.util.Iterator<GenerateContentResponse> iterator() {
                return responseStream.iterator();
            }

            @Override
            public void close() {
                responseStream.close();
            }
        };
    }

    @Override
    public void close() {
        client.close();
    }
}
