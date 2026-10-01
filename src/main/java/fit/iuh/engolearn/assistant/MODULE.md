# assistant
- Mục đích: hỏi đáp AI trong quiz/từ vựng.
- Sở hữu collection: `ai_conversations`.
- Expose (api/): rỗng phase 1.
- Phụ thuộc: `content`, `ai`, `shared` (qua api/).
- Quy ước riêng: `contextRefId` + `ContextType` là FK đa hình tới content, resolve qua `TopicQuery`/`VocabularyQuery` khi cần.
