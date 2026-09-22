# mi-agent

A GreenNode AgentBase agent implementing the **Mi assistant** logic from the MSB DigiBank guideline (Full L1–L3: RAG Q&A → Chat Pay proposals → multi-agent router with Loan/Card/Saving agents). Built on LangGraph + AgentBase Memory.

## Prerequisites

- Python 3.10+
- A GreenNode IAM Service Account ([create one here](https://iam.console.vngcloud.vn/service-accounts))

## Setup

1. Create and activate a virtual environment:
   ```bash
   python3 -m venv venv && source venv/bin/activate
   ```

2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

3. Configure credentials for **local development** (choose one method):

   **Option A** - Environment variables:
   ```bash
   cp .env.example .env
   # Edit .env with your credentials
   ```

   **Option B** - Config file (already created):
   Edit `.greennode.json` with your `client_id` and `client_secret` from your IAM Service Account.

   > **Note**: When deployed on AgentBase Runtime, the IAM service account and Agent Identity are managed by the runtime system and automatically available to the SDK — no manual credential configuration needed in the container.

## Configure LLM

This project uses any OpenAI-compatible LLM provider. Set the following in `.env`:

```
LLM_API_KEY=your-api-key
LLM_BASE_URL=your-provider-base-url
LLM_MODEL=your-model-name
MEMORY_ID=your-memory-id
```

- **GreenNode AIP**: get an API key via `/agentbase-llm`. Set `LLM_BASE_URL=https://maas-llm-aiplatform-hcm.api.vngcloud.vn/v1`
- **OpenAI**: Set `LLM_BASE_URL=https://api.openai.com/v1`, model e.g. `gpt-4o`

## Run Locally

```bash
python3 main.py
```

The agent starts on `http://127.0.0.1:8080`.

Test it:
```bash
curl -X POST http://127.0.0.1:8080/invocations \
  -H "Content-Type: application/json" \
  -H "X-GreenNode-AgentBase-User-Id: test-user" \
  -H "X-GreenNode-AgentBase-Session-Id: test-session-1" \
  -d '{"message": "Hello, agent!"}'
```

Health check:
```bash
curl http://127.0.0.1:8080/health
```

## Deploy to AgentBase Runtime

Use the `/agentbase-deploy` skill to build, push, and deploy. See the [AgentBase Console](https://aiplatform.console.vngcloud.vn) to manage runtimes, identities, and memory.

## Project Structure

- `main.py` - Agent entrypoint (LangGraph graph with router + domain agents, tools, memory)
- `Dockerfile` - Container image definition
- `requirements.txt` - Python dependencies
- `.greennode.json` - AgentBase configuration
- `.env.example` - Environment variable template
- `guideline/` - MSB DigiBank feature analysis & implementation guideline