# ─────────────────────────────────────────────────────────────────────────────
# Clair Tax – project-level commands
#
# Usage:
#   make dev      build + run backend and frontend locally (reads .env)
#   make build    produce the backend JAR and frontend static export
#   make stop     kill any background dev processes started by 'make dev'
# ─────────────────────────────────────────────────────────────────────────────

.PHONY: dev build stop

# Load .env automatically when it exists
-include .env
export

# ── dev ───────────────────────────────────────────────────────────────────────
# Builds the backend JAR (skipping tests), then launches both servers in the
# foreground. Ctrl+C stops everything cleanly.
dev:
	@echo ""
	@echo "==> Building backend JAR..."
	@cd backend && ./mvnw clean package -DskipTests -q
	@echo ""
	@echo "==> Starting backend on http://localhost:8080"
	@echo "==> Starting frontend on http://localhost:3000"
	@echo "==> Press Ctrl+C to stop both."
	@echo ""
	@trap 'kill 0' INT; \
	  cd backend && java -jar target/backend-0.0.1-SNAPSHOT.jar & \
	  cd frontend && bun run dev & \
	  wait

# ── build ─────────────────────────────────────────────────────────────────────
# Produces:
#   backend/target/backend-0.0.1-SNAPSHOT.jar  → deploy to EC2 with java -jar
#   frontend/.next/                             → Next.js standalone / static export
build:
	@echo ""
	@echo "==> Building backend JAR (tests skipped)..."
	@cd backend && ./mvnw clean package -DskipTests -q
	@echo "    backend/target/backend-0.0.1-SNAPSHOT.jar"
	@echo ""
	@echo "==> Building frontend..."
	@cd frontend && bun run build
	@echo "    frontend/.next/ ready"
	@echo ""
	@echo "==> Build complete."

# ── stop ──────────────────────────────────────────────────────────────────────
# Kills stray Java and bun/node dev processes if 'make dev' was run in the
# background or a previous session left them running.
stop:
	@echo "==> Stopping backend (port 8080)..."
	@-lsof -ti tcp:8080 | xargs kill -9 2>/dev/null || true
	@echo "==> Stopping frontend (port 3000)..."
	@-lsof -ti tcp:3000 | xargs kill -9 2>/dev/null || true
	@echo "==> Done."
