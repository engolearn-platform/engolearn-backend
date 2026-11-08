package fit.iuh.engolearn.placement.infra;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PlacementMediaWebConfig implements WebMvcConfigurer {
    private final Path directory;
    public PlacementMediaWebConfig(@Value("${engolearn.placement.media-directory:./tools/mongo-json-importer/samples/placement-media}") String directory) {
        this.directory=Path.of(directory).toAbsolutePath().normalize();
    }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location=directory.toUri().toString();
        if(!location.endsWith("/")) location+="/";
        registry.addResourceHandler("/api/v1/placement/media/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .resourceChain(false).addResolver(new PlacementMediaResourceResolver());
    }
}
