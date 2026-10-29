package fit.iuh.engolearn.topic.utils;

import fit.iuh.engolearn.shared.enums.CefrLevel;



public class MockLearnerLevelProvider implements LearnerLevelProvider {

    @Override
    public CefrLevel getCurrentLevel() {
        return CefrLevel.C2;
    }
}
