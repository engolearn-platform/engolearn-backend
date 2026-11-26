package fit.iuh.engolearn.placement.model;

public final class PlacementLegacySeed {
    public static final String CODE = "PLT-V3-DEV-001";
    public static final String V1_ID = "PLT-V3-DEV-001-V1";
    public static final String V2_ID = "PLT-V3-DEV-001-V2";
    public static final String V3_ID = "PLT-V3-DEV-001-V3";

    private PlacementLegacySeed() {
    }

    public static boolean matches(PlacementTestSet set) {
        return CODE.equals(set.getCode()) && (set.getVersion() == 1 && V1_ID.equals(set.getId())
                || set.getVersion() == 2 && V2_ID.equals(set.getId())
                || set.getVersion() == 3 && V3_ID.equals(set.getId()));
    }
}
