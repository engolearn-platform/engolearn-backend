package fit.iuh.engolearn.subscription;
import fit.iuh.engolearn.config.SecurityConfig;
import fit.iuh.engolearn.shared.security.impl.SecurityContextCurrentUserProvider;
import fit.iuh.engolearn.subscription.controller.SubscriptionController;
import fit.iuh.engolearn.subscription.service.SubscriptionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(SubscriptionController.class)
@Import({SecurityConfig.class,SecurityContextCurrentUserProvider.class})
class SubscriptionSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean SubscriptionService service;
    @Test void publicCatalogAndPrivateGuestRoutesHaveStableSecurityContract() throws Exception {
        when(service.plans()).thenReturn(List.of());
        mvc.perform(get("/api/v1/subscription/plans")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        for(String path:List.of("/api/v1/subscription/me","/api/v1/quota/me/usage")) mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED")).andExpect(jsonPath("$.instance").value(path));
        mvc.perform(post("/api/v1/subscription/me/trial").contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verify(service,never()).summary(anyString());verify(service,never()).activateTrial(anyString(),anyString());
    }
}
