package fit.iuh.engolearn.placement.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

final class PlacementMediaResourceResolver extends PathResourceResolver {
    @Override protected Resource getResource(String resourcePath, Resource location) throws IOException {
        return findFile(Path.of(location.getURI()), resourcePath) == null
                ? null : super.getResource(resourcePath, location);
    }

    static Path findFile(Path directory, String resourcePath) throws IOException {
        if (!resourcePath.matches("[a-f0-9]{64}\\.wav")) return null;
        Path file = directory.resolve(resourcePath);
        if (!Files.isRegularFile(file)) return null;
        Path realFile = file.toRealPath();
        return realFile.startsWith(directory.toRealPath()) ? realFile : null;
    }
}
