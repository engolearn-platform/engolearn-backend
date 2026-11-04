package fit.iuh.engolearn.user.infra;

import fit.iuh.engolearn.user.repository.UserAuthIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAuthIndexInitializer implements ApplicationRunner {
    private final UserAuthIndexRepository indexes;

    @Override
    public void run(ApplicationArguments args) {
        indexes.ensureUniqueIndexes();
    }
}
