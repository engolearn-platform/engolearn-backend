# Multi-tool evaluation scenarios — Phase 1

Status: agreed product-scope baseline (2026-11-27); detailed evaluation cases and acceptance thresholds pending capability discovery. Not an executed evaluation or an approved release gate.

Extends the [Phase 1 evaluation plan](phase-1-evaluation-plan.md) for learner-facing business reads. These are representative questions, not new use cases or a mandatory list of tool calls. Use the existing Assessment capability and other authorized sources where needed. Do not assume that the required data or public contracts exist.

| ID | Group | Learner prompt |
| --- | --- | --- |
| GUID-01 | Learning Guidance | Hôm nay tui nên học gì? |
| GUID-02 | Learning Guidance | Tui muốn cải thiện kỹ năng yếu nhất của mình thì nên học gì? |
| GUID-03 | Learning Guidance | Tui đang học dở những gì và nên tiếp tục từ đâu? |
| DISC-01 | Learning Discovery | App có chủ đề nào phù hợp với trình độ của tui không? |
| DISC-02 | Learning Discovery | Tui muốn học tiếng Anh để đi phỏng vấn, app có nội dung gì phù hợp? |
| DISC-03 | Learning Discovery | Giới thiệu vài bài học phù hợp mà tui chưa học. |
| REFL-01 | Learning Reflection | Tuần này tui đã học được những gì? |
| REFL-02 | Learning Reflection | Hiện tại tui đã hoàn thành bao nhiêu nội dung học tập? |
| REFL-03 | Learning Reflection | So với trước đây thì tui đã tiến bộ như thế nào? |

## Evaluation intent

- Guidance: recommendations grounded in actual proficiency, progress and available next activities.
- Discovery: recommendations grounded in the published, authorized catalog, level and relevant learning history.
- Reflection: accurate summaries of recorded learning activity and progress, with explicit limits on missing or non-comparable historical data.

Evaluate tool selection and composition, groundedness, personalization and actionability where applicable. A tool is not required when trusted context already supplies the needed facts. Do not prescribe tool names or a fixed call order before discovery. Do not invent data, conflate unavailable data with no data, disclose unauthorized content or perform business writes.

After discovery, expand each scenario into synthetic fixtures, expected and forbidden behavior, trace/record checks and semantic review criteria following the existing evaluation plan. Include missing data, restricted data and dependency failures. Run against real provider output in an isolated environment only; no real learner data. No cases have been run for this extension yet.
