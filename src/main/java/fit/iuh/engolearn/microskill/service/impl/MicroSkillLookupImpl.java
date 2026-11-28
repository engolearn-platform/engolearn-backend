package fit.iuh.engolearn.microskill.service.impl;

import fit.iuh.engolearn.microskill.contract.MicroSkillLookup;
import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import fit.iuh.engolearn.microskill.repository.MicroSkillRepository;
import fit.iuh.engolearn.microskill.mapper.MicroSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MicroSkillLookupImpl implements MicroSkillLookup {
    private final MicroSkillRepository repository;
    public List<MicroSkillDto> list() { return repository.list().stream().map(MicroSkillMapper::toDto).toList(); }
    public Optional<MicroSkillDto> findById(String id) { return repository.findById(id).map(MicroSkillMapper::toDto); }
    public Optional<MicroSkillDto> findByCanonicalName(String name) {
        return repository.findByCanonicalName(name).map(MicroSkillMapper::toDto);
    }
    public Optional<MicroSkillDto> retainActive(String name) { return repository.retainActive(name).map(MicroSkillMapper::toDto); }
}
