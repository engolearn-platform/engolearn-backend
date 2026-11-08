# Subscription development seed

`dev-main-manifest.json` is the single development seed for Free, Trial and
Premium. It includes Vietnamese plan/benefit descriptions and the three existing
development fixture accounts. Free and Trial each allow 3600 speaking seconds
daily, with 1 and 2 proficiency checks monthly. Premium allows 7200 speaking
seconds daily and 5 proficiency checks monthly, priced at 199000 VND monthly or
1590000 VND annually. It creates no paid period or quota usage.

With the existing development replica database available and the `dev` profile
configured in `.env`, run from the backend directory:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--engolearn.subscription.publish-manifest=tools/subscription/dev-main-manifest.json'
```

This publishes the complete catalog atomically on a new or explicitly reset
development database. Omit the publication argument on normal restarts. The
separate catalog patch script was removed; no second seed step is needed.

Existing databases are not updated in place by this seed. An earlier receipt for
the same manifest ID with different content causes a fingerprint conflict;
existing policy versions/account histories can also prevent publication. Do not
delete receipts alone or rewrite histories to force a replay. Any migration or
targeted reset/reseed requires a separate decision. Existing camelCase data needs an explicit compatibility decision against the
[current storage contract](../../docs/technical/subscription/data-model.md) before running
the refactored backend. No reset or seed execution is performed
by editing these files.

## Optional shared Assessment / Learning Path account

After the catalog exists, `assessment-learningpath-demo-account.json` initializes only
`pfc-demo-learner-v2`. Its plan and policy arrays are empty: it reuses the current catalog
and does not change prices, quotas, current periods or other account histories.
Import the demo User/Assessment records first; see the
[dataset instructions](../mongo-json-importer/README.md#shared-assessment--learning-path-demo).

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.profiles.active=dev --engolearn.assessment.media-directory=./tools/mongo-json-importer/samples/assessment-media --engolearn.subscription.publish-manifest=tools/subscription/assessment-learningpath-demo-account.json'
```

Publish this account manifest once and omit the flag on normal restarts. An unchanged
manifest receipt is idempotent. If the same account already has Subscription history but
this receipt is absent, publication refuses to replace that history. Inspect the existing
state instead of deleting it. This manifest must not be sent to the Mongo JSON importer.
