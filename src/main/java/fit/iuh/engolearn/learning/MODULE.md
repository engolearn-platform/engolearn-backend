# learning
- Mục đích: tiến độ học topic.
- Sở hữu collection: `user_topic_progress`, `user_topic_item_progress`.
- Expose (api/): `TopicItemCompleted` (event).
- Phụ thuộc: `content::api`, `identity::api`, `shared`.
- Endpoints: `GET /api/topics` (filter level/category + paging, `userId` optional — ẩn danh thì `progress=null`), `GET /api/topics/{topicId}` (`version` optional mặc định latest published, full items + progress từng item). Aggregation nằm ở `ExploreService`.
- Quy ước riêng: `ProgressReach` (String stage/step) thay cho `TopicItemPart` của content để tránh xuyên module; tham chiếu chéo bằng ID.
