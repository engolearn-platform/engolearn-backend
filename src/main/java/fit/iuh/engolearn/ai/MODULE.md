# ai
- Mục đích: adapter LLM dùng chung (Gemini, Groq).
- Sở hữu collection: —.
- Expose (api/): `LlmClient` (stub phase 1).
- Phụ thuộc: `shared`.
- Quy ước riêng: implementation (`GeminiClient`, `GroqClient`, retry, quota) nằm ở `internal/`.
