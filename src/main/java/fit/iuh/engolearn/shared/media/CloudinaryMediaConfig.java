package fit.iuh.engolearn.shared.media;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryMediaConfig {
    @Bean
    public MediaStorage mediaStorage(
            @Value("${engolearn.media.cloudinary.url:}") String url,
            @Value("${engolearn.media.cloudinary.cloud-name:}") String cloudName,
            @Value("${engolearn.media.cloudinary.api-key:}") String apiKey,
            @Value("${engolearn.media.cloudinary.api-secret:}") String apiSecret) {
        if (!url.isBlank()) return new CloudinaryMediaStorage(new Cloudinary(url), true);
        if (!cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank()) {
            return new CloudinaryMediaStorage(new Cloudinary(Map.of(
                    "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret,
                    "secure", true)), true);
        }
        return new CloudinaryMediaStorage(new Cloudinary(), false);
    }
}
