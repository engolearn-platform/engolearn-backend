package fit.iuh.engolearn.config;
import fit.iuh.engolearn.subscription.dto.request.PublicationManifest;
import fit.iuh.engolearn.subscription.service.SubscriptionPublicationService;
import java.nio.file.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;
@Configuration
public class SubscriptionOperatorConfig {
    @Bean
    @ConditionalOnProperty("engolearn.subscription.publish-manifest")
    public ApplicationRunner publishSubscriptionManifest(SubscriptionPublicationService service,ObjectMapper mapper,
        @Value("${engolearn.subscription.publish-manifest}") String path) {
        return arguments->{
            PublicationManifest manifest=mapper.readValue(Files.readString(Path.of(path)),PublicationManifest.class);
            service.publish(manifest);
            System.out.println("Subscription manifest published: "+manifest.manifestId()+" environment="+manifest.environment());
            for(var policy:manifest.policies()) System.out.println("Subscription policy published: "+policy.id()+" scope="+policy.scope()+" version="+policy.version());
        };
    }
}
