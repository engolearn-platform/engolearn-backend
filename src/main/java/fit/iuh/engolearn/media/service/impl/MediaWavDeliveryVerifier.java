package fit.iuh.engolearn.media.service.impl;

import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class MediaWavDeliveryVerifier {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private MediaWavDeliveryVerifier() {}

    static void verify(URI uri, long expectedBytes, String expectedSha256, Duration timeout) {
        download(uri, expectedBytes, expectedSha256, timeout, false);
    }

    static byte[] download(URI uri, long expectedBytes, String expectedSha256, Duration timeout, boolean retainBytes) {
        byte[] expectedDigest = parseDigest(expectedSha256);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Accept-Encoding", "identity")
                    .GET().build();
        } catch (IllegalArgumentException ex) {
            throw unavailable("Provider did not return an HTTP WAV delivery URL");
        }
        var download = CLIENT.sendAsync(request, response -> {
            validateHeaders(response, expectedBytes);
            return new DigestSubscriber(expectedBytes, retainBytes);
        });
        try {
            var verified = download.get(timeout.toMillis(), TimeUnit.MILLISECONDS).body();
            if (!MessageDigest.isEqual(expectedDigest, verified.digest())) {
                throw unavailable("Delivered WAV does not match its registered SHA-256");
            }
            return verified.bytes();
        } catch (InterruptedException ex) {
            download.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable("WAV delivery verification was interrupted");
        } catch (TimeoutException ex) {
            download.cancel(true);
            throw unavailable("WAV delivery verification timed out");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof MediaStorageUnavailableException invalid) throw invalid;
            // An HTTP client exception can contain the signed delivery URL.
            throw unavailable("WAV delivery could not be read");
        }
    }

    private static void validateHeaders(HttpResponse.ResponseInfo response, long expectedBytes) {
        HttpHeaders headers = response.headers();
        List<String> ranges = headers.allValues("Content-Range");
        if (response.statusCode() == 206) {
            String expectedRange = "bytes 0-" + (expectedBytes - 1) + "/" + expectedBytes;
            if (ranges.size() != 1 || !expectedRange.equalsIgnoreCase(ranges.getFirst())) {
                throw unavailable("WAV delivery did not return the complete registered range");
            }
        } else if (response.statusCode() != 200) {
            throw unavailable("WAV delivery returned HTTP " + response.statusCode());
        } else if (!ranges.isEmpty()) {
            throw unavailable("WAV delivery returned a Content-Range with HTTP 200");
        }

        if (headers.allValues("Content-Encoding").stream().anyMatch(value -> !"identity".equalsIgnoreCase(value))) {
            throw unavailable("WAV delivery did not return the original bytes");
        }
        List<String> lengths = headers.allValues("Content-Length");
        if (lengths.size() > 1 || (!lengths.isEmpty() && !Long.toString(expectedBytes).equals(lengths.getFirst()))) {
            throw unavailable("WAV delivery length does not match the registered size");
        }
    }

    private static byte[] parseDigest(String sha256) {
        if (sha256 == null || sha256.length() != 64) throw unavailable("Registered WAV has no valid SHA-256");
        try {
            return HexFormat.of().parseHex(sha256);
        } catch (IllegalArgumentException ex) {
            throw unavailable("Registered WAV has no valid SHA-256");
        }
    }

    private static MediaStorageUnavailableException unavailable(String message) {
        return new MediaStorageUnavailableException(message);
    }

    private record DownloadedWav(byte[] digest, byte[] bytes) {}

    private static final class DigestSubscriber implements HttpResponse.BodySubscriber<DownloadedWav> {
        private final CompletableFuture<DownloadedWav> result = new CompletableFuture<>();
        private final ByteBuffer content;
        private final MessageDigest digest;
        private long remaining;
        private Flow.Subscription subscription;

        private DigestSubscriber(long expectedBytes, boolean retainBytes) {
            remaining = expectedBytes;
            content = retainBytes ? ByteBuffer.allocate(Math.toIntExact(expectedBytes)) : null;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("JDK does not provide SHA-256", ex);
            }
        }

        @Override public CompletionStage<DownloadedWav> getBody() { return result; }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int bytes = buffer.remaining();
                if (bytes > remaining) {
                    result.completeExceptionally(unavailable("WAV delivery exceeded the registered size"));
                    subscription.cancel();
                    return;
                }
                if (content != null) content.put(buffer.duplicate());
                digest.update(buffer);
                remaining -= bytes;
            }
            subscription.request(1);
        }

        @Override public void onError(Throwable error) { result.completeExceptionally(error); }

        @Override
        public void onComplete() {
            if (remaining == 0) {
                result.complete(new DownloadedWav(digest.digest(), content == null ? null : content.array()));
            } else {
                result.completeExceptionally(unavailable("WAV delivery ended before the registered size"));
            }
        }
    }
}
