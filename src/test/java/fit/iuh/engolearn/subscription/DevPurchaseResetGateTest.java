package fit.iuh.engolearn.subscription;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.subscription.controller.DevPurchaseResetController;
import fit.iuh.engolearn.subscription.service.DevPurchaseResetService;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class DevPurchaseResetGateTest {
    @ParameterizedTest @CsvSource({"dev,unset,false","dev,false,false","prod,true,false","dev;prod,true,false","dev,true,true"})
    void endpointRequiresDevWithoutProdAndExplicitFlag(String profiles,String flag,boolean present) {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles.split(";"));
            if(!flag.equals("unset"))context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("engolearn.payment.dev-reset.enabled",flag)));
            context.registerBean(CurrentUserProvider.class,()->mock(CurrentUserProvider.class));
            context.registerBean(DevPurchaseResetService.class,()->mock(DevPurchaseResetService.class));
            context.register(DevPurchaseResetController.class);context.refresh();
            assertThat(!context.getBeansOfType(DevPurchaseResetController.class).isEmpty()).isEqualTo(present);
        }
    }
}
