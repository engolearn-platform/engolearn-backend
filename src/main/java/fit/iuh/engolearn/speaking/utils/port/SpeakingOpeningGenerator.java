package fit.iuh.engolearn.speaking.utils.port;

import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;

public interface SpeakingOpeningGenerator {
    Draft generate(SpeakingContentSnapshot content);

    record Draft(String reply, String hintExampleEn) {}
}
