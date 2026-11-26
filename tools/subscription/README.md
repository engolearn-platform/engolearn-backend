# Subscription development data (legacy entry)

The former startup manifest flow has been retired. Development catalog examples now live in
[`subscription-development-sample.json`](../mongo-json-importer/samples/subscription-development-sample.json)
and are loaded with the generic Mongo JSON importer. The importer guide documents its safety
checks and target requirements.

Run from the repository root, after configuring both importer environment files and confirming
that the configured target is a disposable local development database:

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\subscription-development-sample.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\subscription-development-sample.json
```

Review the dry-run report before confirming the import. The sample is demonstration data; it
does not establish production data or a completed import. Do not use it to replace an existing
catalog or learner history. The old `dev-main-manifest.json` remains a fixture for
`AssessmentMongoIntegrationTest`, not a supported runtime seed. The former account-only
manifest is retired; the shared Assessment demo account is included in `assessment-demo.json`.

Admin catalog updates continue through the Subscription Admin API. The old
`compose.replica.yaml` is a legacy isolated fixture utility, not the supported team runtime;
use root Compose and the [runtime configuration contract](../../docs/architecture/runtime-configuration.md).
Existing camelCase data still needs an explicit compatibility decision against the
[current storage contract](../../docs/technical/subscription/data-model.md) before running a
refactored backend.
