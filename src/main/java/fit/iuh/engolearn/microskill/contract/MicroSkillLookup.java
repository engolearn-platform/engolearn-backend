package fit.iuh.engolearn.microskill.contract;

import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import java.util.*;

/** Catalog reads include inactive definitions for historical display. Canonical names are immutable. */
public interface MicroSkillLookup {
    List<MicroSkillDto> list();
    Optional<MicroSkillDto> findById(String id);
    Optional<MicroSkillDto> findByCanonicalName(String canonicalName);
    /** Atomically retains an active definition before a consumer persists a reference; absent means unavailable. */
    Optional<MicroSkillDto> retainActive(String canonicalName);
}
