package fit.iuh.engolearn;

import fit.iuh.engolearn.config.RuntimeProfileInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EngolearnApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(EngolearnApplication.class);
		application.addInitializers(new RuntimeProfileInitializer());
		application.run(args);
	}

}
