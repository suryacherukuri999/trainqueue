.PHONY: app infra down clean test logs

# One command: build + run the whole stack (infra + all services + worker image).
# Console on http://localhost:5173, API on :8080, gateway on :8081.
app:
	docker compose --profile app up --build -d
	@echo "console: http://localhost:5173   api: http://localhost:8080"

# Just the infrastructure (Postgres, Kafka, Redis, Mongo, LocalStack, Elasticsearch),
# for running api/scheduler/gateway/console from source on the host.
infra:
	docker compose up -d

down:
	docker compose --profile app down

# down + wipe volumes (fresh, empty databases)
clean:
	docker compose --profile app down -v

# the same checks CI runs
test:
	cd api && ./mvnw -q verify
	cd scheduler && ./mvnw -q verify
	cd gateway && npm ci && npm test && npm run build
	cd console && npm ci && npm test && npm run build

logs:
	docker compose --profile app logs -f
