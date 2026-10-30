# Definition of Done

This definition applies to work completed through **any team workflow**. OpenSpec is optional during the pilot; it is not a prerequisite for applying these quality criteria.

A task is Done when the common criteria and applicable task-type criteria are met. If a required check fails or cannot run, report the task as blocked or not fully verified rather than implying it is Done.

## Common criteria

- [ ] The agreed scope and acceptance criteria, however they are recorded, are satisfied; unrelated changes are excluded.
- [ ] Checks appropriate to the change have been run and their actual results are reported accurately.
- [ ] Existing checks have not been weakened, disabled, suppressed, or bypassed to obtain a pass.
- [ ] Relevant documentation and examples match the resulting code and configuration.
- [ ] New/refactored code follows the [implementation conventions](../architecture/implementation-conventions.md); agreed exceptions and legacy gaps are identified, not silently copied.
- [ ] Remaining risks, unrun checks, and required manual actions are disclosed.
- [ ] Comments explain non-obvious *why* only; no signature-restating Javadoc.

## Additional criteria by task type

Apply the sections that match the work.

### Behavior change or bug fix

- [ ] The intended behavior, relevant failure paths, and important boundary cases are verified in proportion to the risk.
- [ ] A regression test is added for a bug fix when practical; if not, explain why.
- [ ] Relevant regression checks pass; API or data-contract documentation is updated when those contracts change.

### Refactor or structural change

- [ ] Behavior is preserved unless an explicit, agreed requirement says otherwise.
- [ ] Relevant architecture and structural checks pass.
- [ ] Affected API contracts, serialization, persistence mappings, and configuration are checked as applicable.
- [ ] Required migration, database reset, or manual compatibility steps are identified and confirmed separately.

### Tests-only change

- [ ] Assertions verify the intended outcome and would fail for the regression or incorrect behavior being guarded against.
- [ ] Relevant tests run successfully and do not rely on accidental state or execution order.
- [ ] Existing coverage or assertions have not been weakened to make the suite pass.

### Build, dependency, or configuration change

- [ ] The project builds and relevant verification checks pass.
- [ ] Changes are necessary, scoped, and documented.
- [ ] Startup and configuration behavior are checked when affected; secrets and environment-specific credentials are not committed.

### Documentation-only change

- [ ] Instructions match the repository's current behavior and conventions.
- [ ] Changed links, paths, commands, and examples are checked when practical.
- [ ] Application tests are run only when the documentation change affects code/configuration or there is another clear reason.

## Change-level verification

For a change that modifies application code or configuration, run `./mvnw verify` before declaring the change fully verified, when the environment supports it. Use the project's actual equivalent command if needed. If the command cannot run, state the reason and report verification as incomplete. During implementation, prefer faster relevant checks after each meaningful step.

## Evidence standard

A checklist, issue status, or OpenSpec task checkbox is not by itself evidence that a command ran. Record the command and its observed result. For manual steps, record what was confirmed and by whom when available.
