package fit.iuh.engolearn.media.service.impl;

import com.sun.net.httpserver.HttpServer;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaWavDeliveryVerifierTest {
    private byte[] wav;
    private String sha256;
    private HttpServer server;
    private URI url;

    @BeforeEach
    void serveRegisteredWav() throws Exception {
        wav = Files.readAllBytes(Path.of("tools/mongo-json-importer/samples/assessment-media/sound-check.wav"));
        sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(wav));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/audio");
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void acceptsFull200WithLength() {
        respond(200, null, wav.length, wav);
        verify();
    }

    @Test
    void acceptsFull200WithChunkedLength() {
        respond(200, null, 0, wav);
        verify();
    }

    @Test
    void accepts206ContainingEntireRegisteredRange() {
        respond(206, completeRange(), wav.length, wav);
        verify();
    }

    @Test
    void rejects206WithoutContentRange() {
        respond(206, null, wav.length, wav);
        assertThatThrownBy(this::verify).isInstanceOf(MediaStorageUnavailableException.class);
    }

    @Test
    void rejects206WithConflictingLength() {
        byte[] shortened = Arrays.copyOf(wav, wav.length - 1);
        respond(206, completeRange(), shortened.length, shortened);
        assertThatThrownBy(this::verify).isInstanceOf(MediaStorageUnavailableException.class);
    }

    @Test
    void rejectsChunked200WithTruncatedPayloadBeyondHeader() {
        respond(200, null, 0, Arrays.copyOf(wav, wav.length / 2));
        assertThatThrownBy(this::verify).isInstanceOf(MediaStorageUnavailableException.class);
    }

    @Test
    void rejectsChunked200ThatExceedsRegisteredLength() {
        respond(200, null, 0, Arrays.copyOf(wav, wav.length + 1));
        assertThatThrownBy(this::verify).isInstanceOf(MediaStorageUnavailableException.class);
    }

    @Test
    void rejectsModifiedSampleBytesInOtherwiseValidWav() {
        byte[] altered = wav.clone();
        altered[altered.length - 1] ^= 1;
        respond(206, completeRange(), altered.length, altered);
        assertThatThrownBy(this::verify)
                .isInstanceOf(MediaStorageUnavailableException.class).hasMessageContaining("SHA-256");
    }

    @Test
    void rejectsRedirectWithoutReturningPrivateUrl() {
        server.createContext("/audio", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Location", "/other?secret=test-only");
                exchange.sendResponseHeaders(302, -1);
            }
        });
        server.start();
        assertThatThrownBy(this::verify).isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessageNotContaining("secret");
    }

    @Test
    void suppliedDeadlineAlsoBoundsAStalledBody() {
        var release = new CountDownLatch(1);
        server.createContext("/audio", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, wav.length);
                exchange.getResponseBody().write(wav, 0, 12);
                exchange.getResponseBody().flush();
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try {
            assertThatThrownBy(() -> MediaWavDeliveryVerifier.verify(url, wav.length, sha256, Duration.ofMillis(500)))
                    .isInstanceOf(MediaStorageUnavailableException.class).hasMessageContaining("timed out");
        } finally {
            release.countDown();
        }
    }

    private void verify() {
        MediaWavDeliveryVerifier.verify(url, wav.length, sha256, Duration.ofSeconds(8));
    }

    private String completeRange() {
        return "bytes 0-" + (wav.length - 1) + "/" + wav.length;
    }

    private void respond(int status, String range, long contentLength, byte[] body) {
        server.createContext("/audio", exchange -> {
            try (exchange) {
                if (range != null) exchange.getResponseHeaders().set("Content-Range", range);
                exchange.sendResponseHeaders(status, contentLength);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
    }
}
