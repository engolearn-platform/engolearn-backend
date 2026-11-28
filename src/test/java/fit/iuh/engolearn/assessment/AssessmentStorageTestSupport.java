package fit.iuh.engolearn.assessment;

import com.sun.net.httpserver.HttpServer;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.media.model.enums.MediaDeliveryType;
import fit.iuh.engolearn.media.model.enums.MediaProvider;
import fit.iuh.engolearn.media.model.vo.MediaStorageMetadataVo;
import fit.iuh.engolearn.media.utils.MediaFileDescriptor;
import fit.iuh.engolearn.media.utils.MediaStorageProvider;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.bson.Document;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

@TestConfiguration
public class AssessmentStorageTestSupport {
    @Bean(destroyMethod = "close")
    @Primary
    LocalStorage assessmentTestStorage() throws IOException { return new LocalStorage(); }

    static final class LocalStorage implements MediaStorageProvider, AutoCloseable {
        private final HttpServer server;
        private final Map<String, byte[]> files = new ConcurrentHashMap<>();
        private final Set<String> unavailablePaths = ConcurrentHashMap.newKeySet();
        private final AtomicInteger attempts = new AtomicInteger();
        private volatile int failOnceAt = -1;
        private volatile int extraUploadBytes;

        private final AtomicReference<Runnable> beforeNextRange = new AtomicReference<>();
        void onNextRange(Runnable hook) { beforeNextRange.set(hook); }
        byte[] replaceObject(URI url, byte[] bytes) {
            return files.put(url.getPath().substring(1), bytes);
        }

        URI recoverUnderSameMediaId(MongoTemplate mongo, URI oldUrl, byte[] source) {
            String retiredObject = oldUrl.getPath().substring(1);
            var assets = mongo.getCollection("media_assets");
            var asset = assets.find(new Document("storage.public_id", retiredObject)).first();
            if (asset == null) throw new IllegalStateException("Expected registered media");
            var oldStorage = asset.get("storage", Document.class);
            String restoredObject = retiredObject + "-restored";
            files.put(restoredObject, source.clone());
            var replacement = new Document(oldStorage).append("public_id", restoredObject).append("asset_id", restoredObject);
            var match = new Document("_id", asset.get("_id")).append("storage", oldStorage);
            if (assets.updateOne(match, new Document("$set", new Document("storage", replacement))).getMatchedCount() != 1) {
                throw new IllegalStateException("Storage changed concurrently");
            }
            return urlFor(restoredObject);
        }

        LocalStorage() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    var bytes = files.get(exchange.getRequestURI().getPath().substring(1));
                    String range = exchange.getRequestHeaders().getFirst("Range");
                    if (bytes == null || unavailablePaths.contains(exchange.getRequestURI().getPath())) {
                        exchange.sendResponseHeaders(404, -1);
                    } else if ("bytes=0-11".equals(range) || "bytes=0-".equals(range)) {
                        exchange.getResponseHeaders().set("Content-Type", "audio/wav");
                        var hook = beforeNextRange.getAndSet(null);
                        if (hook != null) hook.run();
                        int length = "bytes=0-11".equals(range) ? Math.min(12, bytes.length) : bytes.length;
                        exchange.getResponseHeaders().set("Content-Range", "bytes 0-" + (length - 1) + "/" + bytes.length);
                        exchange.sendResponseHeaders(206, length);
                        exchange.getResponseBody().write(bytes, 0, length);
                    } else {
                        exchange.getResponseHeaders().set("Content-Type", "audio/wav");
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    }
                }
            });
            server.start();
        }

        void reset() {
            files.clear();
            unavailablePaths.clear();
            attempts.set(0);
            failOnceAt = -1;
            extraUploadBytes = 0;
            beforeNextRange.set(null);
        }

        void setAvailable(URI deliveryUrl, boolean available) {
            if (available) unavailablePaths.remove(deliveryUrl.getPath());
            else unavailablePaths.add(deliveryUrl.getPath());
        }
        int stored() { return files.size(); }
        void failOnceAt(int attempt) { failOnceAt = attempt; }
        void addBytesOnUpload(int count) { extraUploadBytes = count; }

        public MediaStorageMetadataVo upload(String publicId, MediaFileDescriptor descriptor,
                                             MediaAccessPolicy policy, InputStream contents) {
            if (attempts.incrementAndGet() == failOnceAt) {
                throw new MediaStorageUnavailableException("Isolated provider failure");
            }
            try {
                byte[] source = contents.readAllBytes();
                files.put(publicId, Arrays.copyOf(source, source.length + extraUploadBytes));
            } catch (IOException error) {
                throw new MediaStorageUnavailableException("Cannot read fixture", error);
            }
            return MediaStorageMetadataVo.builder().provider(MediaProvider.CLOUDINARY).assetId(publicId)
                    .publicId(publicId).format(descriptor.format()).resourceType(descriptor.resourceType())
                    .deliveryType(MediaDeliveryType.AUTHENTICATED).build();
        }

        public URI publicUrl(MediaStorageMetadataVo storage) { return uri(storage); }
        public URI privateDownloadUrl(MediaStorageMetadataVo storage, Instant expiresAt) { return uri(storage); }
        public void destroy(MediaStorageMetadataVo storage) { files.remove(storage.getPublicId()); }
        public void close() { server.stop(0); }

        private URI uri(MediaStorageMetadataVo storage) {
            return urlFor(storage.getPublicId());
        }

        URI urlFor(String publicId) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + publicId);
        }
    }
}
