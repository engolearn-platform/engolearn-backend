# content
- Mục đích: topic, từ vựng, versioning, admin review.
- Sở hữu collection: `vocabularies`, `topics`, `topics_version`, `topic_items`.
- Expose (api/): `TopicQuery` (`findPublishedTopics`, `getPublishedTopicDetail`, `findVersionNumbers`) + records `TopicCardView`, `TopicDetailView`, `TopicItemDetailView`...; `VocabularyQuery` vẫn stub.
- Endpoint đọc cho learner: không có controller ở content, learning gọi qua `TopicQuery` rồi tự ghép progress (backend-structure.md §7.1).
- Phụ thuộc: `shared`.
- Quy ước riêng: embedded class flatten cùng package `model`, không sub-package `vo`/`embedded`.
