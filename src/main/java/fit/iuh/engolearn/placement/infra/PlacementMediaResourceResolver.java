package fit.iuh.engolearn.placement.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

final class PlacementMediaResourceResolver extends PathResourceResolver {
    @Override protected Resource getResource(String resourcePath, Resource location) throws IOException {
        if(!resourcePath.matches("[a-f0-9]{64}\\.wav")) return null;
        Path base=Path.of(location.getURI());
        Path file=base.resolve(resourcePath);
        if(!Files.isRegularFile(file) || !file.toRealPath().startsWith(base.toRealPath())) return null;
        return super.getResource(resourcePath,location);
    }
}
