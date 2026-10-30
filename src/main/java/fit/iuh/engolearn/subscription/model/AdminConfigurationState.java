package fit.iuh.engolearn.subscription.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("subscription_admin_configuration_state")
public record AdminConfigurationState(@Id String id,long revision) {}
