package fit.iuh.engolearn.shared.media;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryMediaConfig {

    @Bean
    public CloudinaryMediaClient cloudinaryMediaClient(
            @Value("${engolearn.media.cloudinary.url:}") String url,
            @Value("${engolearn.media.cloudinary.cloud-name:}") String cloudName,
            @Value("${engolearn.media.cloudinary.api-key:}") String apiKey,
            @Value("${engolearn.media.cloudinary.api-secret:}") String apiSecret) {
        if (!url.isBlank()) {
            return new CloudinaryMediaClient(new Cloudinary(url), true);
        }
        if (!cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank()) {
            return new CloudinaryMediaClient(new Cloudinary(Map.of(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true)), true);
        }
        return new CloudinaryMediaClient(new Cloudinary(), false);
    }

    /** Compatibility bean for existing consumers such as Speaking. */
    @Bean
    public MediaStorage mediaStorage(CloudinaryMediaClient client) {
        return new CloudinaryMediaStorage(client.cloudinary(), client.configured());
    }
}
