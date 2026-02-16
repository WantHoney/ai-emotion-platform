# LLM report_json generation (ai.mode=spring)

When `ai.mode=spring` and an API key is configured, `SpringAiClient` asks the LLM to generate
`report_json` using the mock segments/emotions from `AiMockDataFactory` as input. The LLM must
return **RFC8259-compliant JSON only** (no extra text).

## Output schema

The LLM returns an `EmotionAnalysisReport` object:

```json
{
  "overallEmotion": "string",
  "confidence": 0.0,
  "keyMoments": [
    {
      "startMs": 0,
      "endMs": 0,
      "text": "string",
      "emotion": "string",
      "score": 0.0
    }
  ],
  "summary": "string"
}
```

## Persistence

The generated report is stored as `core_report.report_json` during analysis runs, so
`GET /api/analysis/{id}/report` reads the LLM output directly when present.

## Local verification (ai.mode=spring)

1. Start the service with `ai.mode=spring` and a valid API key.
2. Create an analysis as usual (for example, `POST /api/analysis/start`).
3. Trigger the real execution endpoint:

   ```bash
   curl -X POST "http://localhost:8080/api/analysis/<analysisId>/run"
   ```

4. Fetch the report (should return the stored `EmotionAnalysisReport` JSON):

   ```bash
   curl "http://localhost:8080/api/analysis/<analysisId>/report"
   ```

5. (Optional) Verify persistence:

   ```sql
   SELECT report_json FROM core_report WHERE analysis_id = <analysisId>;
   ```

## Error handling

If the LLM returns invalid JSON, the analysis is marked **FAILED** and `error_message`
captures the failure reason.
