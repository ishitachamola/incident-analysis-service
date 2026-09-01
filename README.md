# AI Incident Analysis Platform

An event-driven incident investigation platform for simulated microservices. Ingests logs, deployments, and metrics via Kafka, auto-detects incidents, and uses a RAG pipeline (PostgreSQL + pgvector, Spring AI, LLM) over runbooks and historical incidents to produce grounded, cited root-cause analyses.

> Status: early scaffolding (infra + service skeletons). Full README (architecture, data flow, RAG design, running locally, evaluation results) will be filled in as the corresponding milestones land — see `docs/`.

## Modules

- `incident-service` — incident/service/deployment CRUD, timeline construction
- `log-ingestion-service` — Kafka consumer for simulated logs/deployments, incident detection
- `ai-analysis-service` — RAG retrieval + LLM analysis + conversational follow-up
- `frontend` — Angular UI
- `sample-data` — synthetic runbooks, historical incidents, log fixtures
- `docs` — architecture, API, and AI design documentation

## Running locally (infra only, for now)

```bash
cp .env.example .env   # fill in real values
docker compose up -d   # starts Postgres+pgvector and Kafka
./mvnw clean install   # builds all service modules
```

Application services are run individually during development (`./mvnw -pl incident-service spring-boot:run`, etc.) until they're added to `docker-compose.yml` in a later milestone.
