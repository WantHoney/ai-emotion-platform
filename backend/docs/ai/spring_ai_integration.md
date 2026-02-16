# Spring AI Integration (Phase: Mainline)

## Goal
This phase introduces a pluggable `AiClient` abstraction and a Spring AI placeholder implementation without changing existing DB schema or persistence semantics. The mock flow remains the default and keeps the runbook mock-only E2E path working.

## Architecture
```
AudioAnalysisService
  -> AiClient (interface)
     -> MockAiClient (default, returns mock segments/emotions)
     -> SpringAiClient (LLM/stub report_json generator)
  -> ReportMockRepository (idempotent inserts for segments/emotions)
  -> CoreReportRepository (upsert report_json)
```

### AiClient I/O
**Input** (`AiAnalysisRequest`)
- `audioId` or `AudioFile` metadata (`storage_path` included)
- Optional: `language`, `sampleRate`, `modelName`

**Output** (`AiAnalysisResult`)
- `segments`: list of `{startMs, endMs, text, emotions}`
- `overallEmotions`: aggregated summary list
- `summaryJson`: JSON object for `audio_analysis.summary_json`
- `reportJson`: JSON object/string for `core_report.report_json` (may be `null` in mock mode, falling back to server-side aggregation)

## Spring AI placeholder behavior
- `SpringAiClient` reuses mock segments/emotions as the input structure for report generation.
- It tries the following in order:
  1. **Spring AI ChatClient** (if configured)
  2. **HTTP stub** via `ai.baseUrl` (optional)
  3. **Deterministic fallback** (local report_json builder)
- ASR is **not** implemented yet. The integration point is inside `SpringAiClient` where the request already carries `AudioFile` metadata.

### report_json schema (fixed)
The LLM/stub should output JSON matching this schema:
```json
{
  "summary": {
    "overallEmotion": "HAPPY",
    "confidence": 0.82,
    "language": "zh"
  },
  "segments": [
    {
      "startMs": 0,
      "endMs": 8000,
      "text": "...",
      "emotions": [
        {"code": "HAPPY", "nameZh": "开心", "score": 0.82}
      ]
    }
  ],
  "insights": ["..."],
  "generatedAt": "2024-01-01T00:00:00Z",
  "model": "gpt-4o-mini"
}
```

## Configuration
Add the following to `application.yaml` (defaults are already set):
```yaml
ai:
  mode: mock        # mock | spring
  provider: spring  # optional provider label
  model: gpt-4o-mini
  apiKey:           # required when using ChatClient
  baseUrl:          # optional HTTP stub endpoint
  language: zh
  sampleRate: 16000
```

## How to switch
- **Mock (default):** `ai.mode=mock`
- **Spring AI:** `ai.mode=spring` and provide `ai.apiKey` (ChatClient) or `ai.baseUrl` (HTTP stub)

## Validation (mock mode)
1. Start the app with `ai.mode=mock`.
2. Create analysis and call `/api/analysis/{analysisId}/mock-run`.
3. Ensure segments/emotions are inserted and `core_report` is populated (via server-side aggregation).
