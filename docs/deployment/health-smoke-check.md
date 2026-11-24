# Staging health checks and post-deploy smoke test

This baseline targets EngoLearn Spring Boot 4.1 on Render (backend), MongoDB Atlas, and optional Vercel frontend proxy.

## Health endpoints

| Endpoint | Meaning | Success |
| --- | --- | --- |
| GET /actuator/health/liveness | Is the application running? | HTTP 200, status UP |
| GET /actuator/health/readiness | Is Spring Boot ready to serve? | HTTP 200, status UP |
| GET /actuator/health | Combined overall health including MongoDB | HTTP 200, status UP |

Readiness intentionally keeps Spring Boot's default readiness-state-only group. A transient MongoDB failure should not cause Render to repeatedly restart a healthy JVM. Overall health and the topics API are checked separately to detect MongoDB problems.

Only the Actuator health endpoint is exposed via HTTP. Health details and component names remain hidden; the Security filter chain denies non-health paths under /actuator/** even if exposure settings change later.

## Local verification

Start MongoDB via the project's Docker Compose, then run Spring Boot with profile dev and a valid MONGODB_URI in the local .env file. From the repository root on Windows PowerShell, run:

    .\scripts\smoke-test.ps1 -BackendBaseUrl http://localhost:8080

## Staging verification

After the new commit has been deployed on the **same** Render Web Service, run:

    .\scripts\smoke-test.ps1 -BackendBaseUrl https://engolearn-backend-deploy.onrender.com -FrontendBaseUrl https://engolearn-frontend-git-dev-hthinh24s-projects.vercel.app

The script is read-only. It waits for readiness (up to 180 seconds for a potential Render Free cold start), checks liveness and overall health, validates the public topic-list response, and confirms that GET /api/v1/auth/me returns 401 without cookies. If a frontend URL is specified, it verifies topic and auth API forwarding through Vercel.

It does not create accounts, mutate data, or trigger payment flows. Optional parameters: -TimeoutSec (default 30), -MaxReadyWaitSec (default 180).

## Render settings — only after this PR is deployed

1. Open the existing staging Web Service: https://dashboard.render.com/web/srv-dc20d0qfngtc738s08vg
2. Go to Settings and set **Health Check Path** to /actuator/health/readiness.
3. Keep the same Render service and domain. Keep Auto-Deploy disabled until the team has a reliable CI gate.
4. Trigger the manual deployment from branch dev and confirm the deployed commit.
5. Run the smoke-test script above.
6. Confirm unauthenticated requests to /actuator/env do not expose any data.

Do not point Render's readiness check at the combined /actuator/health endpoint while database availability is variable.

## Failure triage

- Readiness fails: inspect Render deploy events, startup logs, and cold-start progress.
- Readiness passes but overall health fails: inspect MongoDB Atlas connectivity, IP allowlist, credentials and ping.
- Health passes but topics fails: inspect API logs and business/database access.
- Backend calls pass but Vercel proxy fails: inspect frontend vercel.json rewrites and deployment alias.
- Bad deploy: roll back application code using Render Deploys history; a code rollback does **not** reverse database migrations.
