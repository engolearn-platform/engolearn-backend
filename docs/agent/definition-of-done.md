# Definition of Done

Use this checklist for completion decisions. Change-specific acceptance criteria remain in that change's OpenSpec artifacts; this document defines the reusable minimum.

A task is **Done** only when the common criteria and the applicable task-type criteria below are met. If a required check cannot run or fails, report the task as blocked or partially complete rather than implying it is Done.

## Common criteria

- [ ] The approved scope and its acceptance criteria are satisfied; unrelated changes are excluded.
- [ ] Relevant tests and checks have been run, and the observed results are recorded accurately.
- [ ] `./mvnw verify` (or the project's actual equivalent) has been run after implementation and passed. If the environment prevents it, state this explicitly; do not report the task as fully verified.
- [ ] Existing checks have not been weakened, disabled, suppressed, or bypassed to obtain a pass.
- [ ] Relevant documentation and examples match the resulting code and configuration.
- [ ] Remaining risks, unrun checks, and required manual actions are disclosed.

## Additional criteria by task type

Apply the section matching the work. Apply more than one when a change spans multiple types.

### Feature or behavior change

- [ ] Change-specific acceptance criteria are covered by tests or other explicit verification appropriate to the risk.
- [ ] Relevant success paths, failure paths, and boundary cases are checked.
- [ ] Relevant regression checks pass; API or data-contract documentation is updated if those contracts changed.

### Bug fix

- [ ] A regression test reproduces the bug before the fix when practical, or the report explains why this was not feasible.
- [ ] The fix addresses the identified cause rather than merely hiding the symptom.
- [ ] The regression test and relevant surrounding tests pass.

### Refactor or structural change

- [ ] Behavior is preserved unless an explicit, approved requirement says otherwise.
- [ ] Architecture rules and relevant structural checks pass.
- [ ] Affected API contracts, serialization, persistence mappings, and configuration have been checked as applicable.
- [ ] Data migrations, database resets, or other manual compatibility steps are identified and confirmed separately when required.

### Tests-only change

- [ ] Assertions verify the intended outcome and would fail for the regression or incorrect behavior being guarded against.
- [ ] The tests run successfully and do not depend on accidental state or execution order.
- [ ] Existing coverage or assertions have not been weakened to make the suite pass.

### Build, dependency, or configuration change

- [ ] The project builds and the relevant verification suite passes.
- [ ] Dependency, environment, and configuration changes are necessary, scoped, and documented.
- [ ] Startup or configuration behavior is checked when affected; required secrets or environment values are not committed.

### Documentation-only change

- [ ] Instructions match the repository's current behavior and conventions.
- [ ] Changed links, paths, commands, and examples are checked when practical.
- [ ] Any verification not applicable to a documentation-only change is omitted deliberately and not represented as having run.

## Evidence standard

A task checklist or a statement in a change document is not, by itself, evidence that a command ran. Record the command and its actual result. For manual steps, record what was confirmed and by whom when that information is available.
