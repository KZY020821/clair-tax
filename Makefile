# ─────────────────────────────────────────────────────────────────────────────
# Clair Tax – project-level commands
#
# Usage:
#   make dev      build + run backend, frontend, and AI service locally
#   make build    produce the backend JAR, frontend export, and AI service deps
#   make stop     kill any background dev processes started by 'make dev'
# ─────────────────────────────────────────────────────────────────────────────

.PHONY: dev build stop train-batch train-with-malaysian

# Load .env automatically when it exists
-include .env
export

# ── dev ───────────────────────────────────────────────────────────────────────
# Builds the backend JAR (skipping tests), then launches all three servers in
# the foreground. Ctrl+C stops everything cleanly.
dev:
	@echo ""
	@echo "==> Building backend JAR..."
	@cd backend && ./mvnw clean package -DskipTests -q
	@echo ""
	@echo "==> Starting backend   on http://localhost:8080"
	@echo "==> Starting AI service on http://localhost:8000"
	@echo "==> Starting frontend  on http://localhost:3000"
	@echo "==> Press Ctrl+C to stop all."
	@echo ""
	@trap 'kill 0' INT; \
	  (cd backend && java -jar target/backend-0.0.1-SNAPSHOT.jar) & \
	  (cd ai-service && .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000) & \
	  (cd frontend && bun run dev) & \
	  wait

# ── build ─────────────────────────────────────────────────────────────────────
# Produces:
#   backend/target/backend-0.0.1-SNAPSHOT.jar  → deploy to EC2 with java -jar
#   frontend/.next/                             → Next.js standalone / static export
#   ai-service/.venv/                           → Python venv with all deps installed
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
	@echo "==> Installing AI service dependencies..."
	@cd ai-service && python3.11 -m venv .venv && .venv/bin/pip install -q -r requirements.txt
	@echo "    ai-service/.venv/ ready"
	@echo ""
	@echo "==> Build complete."

# ── stop ──────────────────────────────────────────────────────────────────────
# Kills stray processes if 'make dev' was run in the background or a previous
# session left them running.
stop:
	@echo "==> Stopping backend (port 8080)..."
	@-lsof -ti tcp:8080 | xargs kill -9 2>/dev/null || true
	@echo "==> Stopping AI service (port 8000)..."
	@-lsof -ti tcp:8000 | xargs kill -9 2>/dev/null || true
	@echo "==> Stopping frontend (port 3000)..."
	@-lsof -ti tcp:3000 | xargs kill -9 2>/dev/null || true
	@echo "==> Done."

# ── train-batch ───────────────────────────────────────────────────────────────
# Fully automated pipeline: CSV ground truth + EasyOCR → labeled JSONL → model.
# Prerequisites: ai-service/.venv must exist (run 'make build' first).
train-batch:
	@echo ""
	@echo "==> Step 1/2: Importing batch_1 labels (OCR in progress, ~1489 images)..."
	@cd ai-service && .venv/bin/python -m app.cli.import_batch_dataset \
		--batch-dir ./samples/batch_1/batch_1 \
		--output ./data/labeled.jsonl
	@echo ""
	@echo "==> Step 2/2: Training receipt models..."
	@cd ai-service && .venv/bin/python -m app.cli.train_receipt_models \
		--manifest ./data/labeled.jsonl \
		--output ./model_artifacts/receipt_postprocessor.joblib \
		--candidate-table-dir ./data/tables
	@echo ""
	@echo "==> Training complete. Artifact: ai-service/model_artifacts/receipt_postprocessor.joblib"

# ── train-with-malaysian ──────────────────────────────────────────────────────
# Adds Malaysian receipt data on top of the already-generated labeled.jsonl,
# then retrains the model on the combined dataset.
# Prerequisites: data/labeled.jsonl must exist (run 'make train-batch' first).
train-with-malaysian:
	@echo ""
	@echo "==> Step 1/3: OCR-ing Malaysian receipts..."
	@cd ai-service && .venv/bin/python -m app.cli.import_manual_labels \
		--image-dir ./samples/malaysian \
		--labels-csv ./samples/malaysian/labels.csv \
		--output ./data/labeled_malaysian.jsonl
	@echo ""
	@echo "==> Step 2/3: Merging batch_1 + Malaysian datasets..."
	@cat ai-service/data/labeled.jsonl ai-service/data/labeled_malaysian.jsonl \
		> ai-service/data/labeled_combined.jsonl
	@echo ""
	@echo "==> Step 3/3: Retraining on combined dataset..."
	@cd ai-service && .venv/bin/python -m app.cli.train_receipt_models \
		--manifest ./data/labeled_combined.jsonl \
		--output ./model_artifacts/receipt_postprocessor.joblib \
		--candidate-table-dir ./data/tables
	@echo ""
	@echo "==> Done. Artifact: ai-service/model_artifacts/receipt_postprocessor.joblib"
