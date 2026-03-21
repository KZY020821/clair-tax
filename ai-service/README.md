# AI Service

Minimal FastAPI scaffold for OCR and AI orchestration work.

## Requirements

- Python 3.11

## Local setup

1. Create a virtual environment:
   `python3 -m venv .venv`
2. Activate it:
   `source .venv/bin/activate`
3. Install dependencies:
   `pip install -r requirements.txt`
4. Start the app:
   `uvicorn app.main:app --reload --host 0.0.0.0 --port 8000`

## Health check

- `http://localhost:8000/health`
- `http://localhost:8000/api/demo-summary`

## Useful commands

- `pytest`

## Current scope

The service exposes a health check and a deterministic demo summary endpoint for frontend integration verification.
