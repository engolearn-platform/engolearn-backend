package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementTestSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlacementTestSetVersions {
    private PlacementTestSetVersions() {}
    static List<PlacementTestSet> latest(List<PlacementTestSet> sets) {
        Map<String,PlacementTestSet> versions=new LinkedHashMap<>();
        for(var set:sets) {
            String key=set.getCode()==null || set.getCode().isBlank() ? "id:"+set.getId() : "code:"+set.getCode();
            versions.merge(key,set,(old,next)->next.getVersion()>old.getVersion()?next:old);
        }
        return new ArrayList<>(versions.values());
    }
}
