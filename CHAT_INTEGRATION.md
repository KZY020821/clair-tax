# Agentic Tax Chatbot Integration

## What was added

A floating AI chat assistant that answers Malaysian income tax questions, updates the user's profile, and assigns receipts to a Year of Assessment — all via natural language. Chat history is session-only (sessionStorage, cleared on logout, survives page refresh).

---

## Request flow

```
Frontend ChatWidget (sessionStorage history)
  → POST /api/chat/message  (Spring Boot — authenticated via session cookie)
    → POST {ai_service}/internal/chat/process  (FastAPI — user_id injected by Spring Boot)
      → Intent classifier (facebook/bart-large-mnli, fail-open)
      → DeepSeek API (tool definitions passed per request)
      → READ tools: execute immediately → re-call LLM → return reply
      → WRITE tools: return { pendingAction, requiresConfirmation: true }
  → User sees confirmation banner, clicks Confirm
  → POST /api/chat/confirm → AI service executes write tool
  → Frontend invalidates relevant TanStack Query caches
```

---

## New and modified files

### AI Service (`ai-service/`)

| File | Status | Purpose |
|------|--------|---------|
| `app/config.py` | modified | Added 9 settings: `DEEPSEEK_API_KEY`, `DEEPSEEK_API_BASE_URL`, `DEEPSEEK_CHAT_MODEL`, `DEEPSEEK_MAX_TOKENS`, `DEEPSEEK_TEMPERATURE`, `INTENT_CLASSIFIER_MODEL`, `INTENT_CLASSIFIER_ENABLED`, `HF_HOME`, `CHAT_SLIDING_WINDOW_PAIRS` |
| `app/main.py` | modified | `app.include_router(chat_router)` + prewarm intent classifier in `startup_event` |
| `requirements.txt` | modified | Added `openai==1.30.5`, `transformers==4.41.2` |
| `app/routers/__init__.py` | new | Makes `routers/` a package |
| `app/routers/chat.py` | new | `POST /internal/chat/process`, `POST /internal/chat/confirm` |
| `app/models/chat.py` | new | Pydantic v2 models: `ChatMessage`, `PendingAction`, `ChatProcessRequest`, `ChatProcessResponse`, `ChatConfirmRequest`, `ChatConfirmResponse` |
| `app/clients/deepseek_client.py` | new | Async DeepSeek client via `openai` SDK (OpenAI-compatible). Preserves real `tool_call_id` from response. Raises `DeepSeekError` on failure. |
| `app/services/chat_tools.py` | new | Tool definitions in OpenAI function-calling format. Exports `CHAT_TOOLS`, `WRITE_TOOLS`, `READ_TOOLS`. |
| `app/services/intent_classifier.py` | new | Module-level singleton (`get_classifier_pipeline()`). `prewarm_intent_classifier()` for startup. `classify_intent()` returns `ClassificationResult(intent, confidence)`, fails open to `intent="general"`. |
| `app/services/tool_executor.py` | new | `ToolExecutor` class. Calls Spring Boot with `X-Clair-Internal-Token` + `X-User-Id` headers. One method per tool. Raises `ToolExecutionError` on non-2xx. |
| `app/services/chat_service.py` | new | `ChatService` orchestrator. `process_message()` → intent → LLM → tool routing. `confirm_action()` → execute write tool. Sliding window: `history[-(CHAT_SLIDING_WINDOW_PAIRS * 2):]`. |

**Tool → endpoint mapping (tool_executor.py):**

| Tool | HTTP call |
|------|-----------|
| `get_relief_categories` | `GET /api/policies/{year}` |
| `get_user_year_summary` | `GET /api/user-years/{year}` |
| `update_profile` | `PUT /api/profile` |
| `assign_receipt_to_year` | `POST /api/internal/chat/receipts` |

---

### Spring Boot (`backend/src/main/java/com/clairtax/backend/`)

| File | Status | Purpose |
|------|--------|---------|
| `chat/dto/AiChatMessage.java` | new | `record(role, content)` — message unit sent to/from AI service |
| `chat/dto/PendingActionDto.java` | new | `record(toolName, toolArgs, description)` |
| `chat/dto/ChatMessageRequest.java` | new | Frontend → backend: `record(content, history)` |
| `chat/dto/ChatMessageResponse.java` | new | Backend → frontend: `record(reply, pendingAction, requiresConfirmation)` |
| `chat/dto/ChatConfirmRequest.java` | new | `record(pendingAction)` |
| `chat/dto/ChatConfirmResponse.java` | new | `record(reply, success, error)` |
| `chat/dto/AiChatProcessRequest.java` | new | Spring Boot → AI service: `record(userId, message, history)` |
| `chat/dto/AiChatConfirmRequest.java` | new | Spring Boot → AI service confirm: `record(userId, pendingAction)` |
| `chat/dto/ChatAssignReceiptRequest.java` | new | Internal receipt assign: `record(receiptId, year, reliefCategoryId)` |
| `chat/service/ChatProxyService.java` | new | `RestTemplate` proxy to AI service. 60s read timeout (LLM calls need longer). Falls back to user-friendly message on `RestClientException`. |
| `chat/controller/ChatController.java` | new | `POST /api/chat/message`, `POST /api/chat/confirm`. Resolves current user from session, injects `userId` before forwarding to AI service. |
| `receipt/controller/internal/InternalChatController.java` | new | `POST /api/internal/chat/receipts`. Auth handled by `CompositeCurrentUserProvider` via internal token + X-User-Id. |
| `receipt/config/AiServiceProperties.java` | modified | Added `chatReadTimeoutSeconds` field (default 60), getter, setter |
| `receipt/entity/Receipt.java` | modified | Added `reassignYear(UserPolicyYear, ReliefCategory)` domain method |
| `receipt/service/ReceiptService.java` | modified | Added `assignReceiptToYear(UUID receiptId, Integer year, UUID reliefCategoryId)` — finds receipt by ID + userId, finds UserPolicyYear by userId + year, calls `receipt.reassignYear()`, saves and returns `ReceiptResponse`. |
| `user/service/InternalRequestCurrentUserProvider.java` | new | Verifies `X-Clair-Internal-Token` against `ReceiptProcessingProperties.internalApiToken`. Reads `X-User-Id`, looks up `AppUser` by UUID via `AppUserRepository`. Returns `CurrentUser`. |
| `user/service/CompositeCurrentUserProvider.java` | new | `@Primary @Service implements CurrentUserProvider`. Checks for `X-Clair-Internal-Token` header on each request. If present → delegates to `InternalRequestCurrentUserProvider`. If absent → delegates to `SessionCurrentUserProvider`. Zero impact on existing endpoints. |
| `src/main/resources/application.yml` | modified | Added `clair.ai-service.chat-read-timeout-seconds: ${CLAIR_AI_SERVICE_CHAT_READ_TIMEOUT_SECONDS:60}` |

---

### Frontend (`frontend/app/`)

| File | Status | Purpose |
|------|--------|---------|
| `lib/chat-api.ts` | new | `sendChatMessage(content, history)` → `POST /api/chat/message`. `confirmChatAction(pendingAction)` → `POST /api/chat/confirm`. Zod v4 schemas for request/response. Exports `CHAT_STORAGE_KEY = "clair-tax-chat-history"`. |
| `components/chat-widget.tsx` | new | Floating button (bottom-right, `fixed bottom-6 right-6 z-50`). Opens a `h-[32rem] w-80` panel. sessionStorage restore on mount. Persists messages on every change. Clears on `signed-out` auth event via `subscribeToAuthEvents`. Shows confirmation banner with Confirm/Cancel when `requiresConfirmation=true`. Invalidates TanStack Query caches after confirmed writes. |
| `components/app-shell.tsx` | modified | Added `import ChatWidget` + `<ChatWidget />` just before closing `</div>` of the authenticated layout (inside the `return` for authenticated users). |

**TanStack Query cache invalidation after confirmed actions:**

| Tool | Query keys invalidated |
|------|----------------------|
| `update_profile` | `["profile"]`, `["user-year-workspace"]` |
| `assign_receipt_to_year` | `["user-year-receipts", year]`, `["user-year-workspace", year]`, `["user-years"]` |

---

## Key design decisions

### Authentication between services

`SessionCurrentUserProvider` (existing, unchanged) and `InternalRequestCurrentUserProvider` (new) are both `@Service` beans. `CompositeCurrentUserProvider` is `@Primary` and picks between them based on whether `X-Clair-Internal-Token` is present on the incoming request. Existing endpoints never send this header, so they always fall through to the session provider.

The shared secret is `CLAIR_RECEIPTS_INTERNAL_API_TOKEN` (already in `application.yml` as `clair.receipts.internal-api-token`). The AI service reads it as `BACKEND_INTERNAL_TOKEN` (already in `ai-service/.env`).

### Session-only chat history

- `sessionStorage` key: `CHAT_STORAGE_KEY = "clair-tax-chat-history"`
- Persists across page refresh (same tab)
- Cleared when `subscribeToAuthEvents` fires a `signed-out` event
- Gone when the tab closes
- Not shared across tabs (sessionStorage is per-tab by design)

### Intent classifier

- Model: `facebook/bart-large-mnli` (1.6 GB, downloads to `HF_HOME` on first run)
- Pre-warmed at startup in `startup_event` via `run_in_executor`
- On any failure (load error, classification error): returns `ClassificationResult(intent="general", confidence=0.0)` — chat continues normally
- Disable entirely: `INTENT_CLASSIFIER_ENABLED=false`

### Sliding window

Last `CHAT_SLIDING_WINDOW_PAIRS * 2` messages (default: 20) sent to DeepSeek. System prompt excluded from the count. Full history still in sessionStorage so the user sees everything.

### WRITE vs READ tools

- **READ** (`get_relief_categories`, `get_user_year_summary`): executed immediately inside `process_message`, result fed back to LLM for a natural-language reply
- **WRITE** (`update_profile`, `assign_receipt_to_year`): returned as `{ pendingAction, requiresConfirmation: true }` without execution; executed only after `confirm_action` is called

### assign_receipt_to_year

Operates on existing receipts only (no file upload). The receipt must already exist in the database (uploaded previously via the normal flow). The tool re-assigns which `UserPolicyYear` the receipt belongs to and optionally sets a `ReliefCategory`. Endpoint: `POST /api/internal/chat/receipts`.

---

## Environment variables to set

| Service | Variable | Purpose |
|---------|----------|---------|
| AI service | `DEEPSEEK_API_KEY` | Required for chat to work |
| AI service | `INTENT_CLASSIFIER_ENABLED=false` | Disable BART if memory is constrained |
| AI service | `HF_HOME` | Where Hugging Face caches the BART model (default `/tmp/hf_cache`) |
| AI service | `CHAT_SLIDING_WINDOW_PAIRS` | Number of message pairs sent to LLM (default 10) |
| Spring Boot | `CLAIR_AI_SERVICE_CHAT_READ_TIMEOUT_SECONDS` | LLM call timeout (default 60) |

Existing variables already used by this feature (no new config needed):
- `BACKEND_API_BASE_URL` / `BACKEND_INTERNAL_TOKEN` — already in ai-service `.env`
- `CLAIR_RECEIPTS_INTERNAL_API_TOKEN` — already in backend env

---

## Known limitations

- `assign_receipt_to_year` requires the user to already have the receipt UUID. The LLM cannot fabricate a valid UUID; if it tries, `ReceiptService.assignReceiptToYear` throws `ResourceNotFoundException` → 404 → `ToolExecutionError` → `success=false` reply to user.
- BART + EasyOCR both use PyTorch in the same process. Combined RAM ~3 GB. Use `INTENT_CLASSIFIER_ENABLED=false` in resource-constrained environments.
- The chat feature does not support file uploads in the chat widget (no image upload button). Receipt assignment works on receipts already in the system.
