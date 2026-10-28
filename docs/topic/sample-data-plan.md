# Topic Learning — first sample-data verification

Status: scope narrowed to one local A1 topic. No database import is performed by this document.

## Goal

Prove that one realistic topic can be stored in the current MongoDB content model and read through the real backend before expanding the pack to more topics or CEFR levels.

## Deliverables

- `tools/topic-sample-data/a1-cafe-topic.json`: one topic plus documents for `topics`, `topic_versions`, `topic_items`, and `topic_item_versions`.
- `tools/topic-sample-data/import-a1-cafe-topic.mongosh.js`: small local-only importer; dry-run by default, explicit apply, no deletes or overwrites.
- `tools/topic-sample-data/README.md`: commands and verification notes.

## Content scope

- One A1 topic: “At the Café: My First Order”.
- Five visible item versions, ordered from greeting/menu through drinks, sizes, snacks and payment.
- Three vocabulary entries per item, with English examples, Vietnamese translations, IPA, collocations, quick checks and usage tips.
- Two expression groups per item, each with one purpose and two example patterns with usage notes.
- Three `FILL_BLANK` plus three `SITUATIONAL_CHOICE` questions per item: 30 formal quiz questions total.
- No learner progress, attempts, scores, completed results or user profiles are seeded.

## Compatibility and media blockers

Use the existing BE models/VOs and catalog reader as the persistence mapping for this first local verification. The current content models do not exactly match the illustrative types in `topic-learning-schema-v1.json`; the pack documents this difference rather than silently claiming the shapes are identical.

Business rules require UK and US pronunciation audio for publish-ready vocabulary. No confirmed project-hosted audio URLs were identified in the inspected source files. The pack therefore uses null audio fields rather than fabricated URLs. Import is blocked by default until the required media is supplied; an explicit local-preview-only override permits a UI smoke test but does not make the data business-publish-ready.

## Safe import and verification

- Dry-run is the default. The target URI and database must be explicitly supplied.
- Apply is restricted to local MongoDB hosts and refuses system/production-looking database names.
- Conflicting IDs fail before writes. Existing documents are never overwritten or deleted.
- Content documents are inserted before the public topic pointer, so the topic becomes discoverable last.
- Import before starting the app with the `dev` profile: `DevTopicSeedRunner` inserts placeholder topics when the `topics` collection is empty.
- Verify topic list/detail through the actual backend. Learning/quiz verification must be reported separately if the corresponding endpoint is not implemented in the active branch.

## Done criteria

The JSON passes structural and relationship checks, the dry-run reports one topic/five items/15 vocabulary entries/30 formal quiz questions, and the local list/detail API can read the topic. Runtime import and API results must be recorded only after they are actually run; this chat does not have access to the user's local MongoDB.
