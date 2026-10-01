# identity
- Mục đích: auth, user.
- Sở hữu collection: `users` (chưa implement User document).
- Expose (api/): rỗng phase 1, sẽ thêm IdentityQuery/UserSummary khi làm auth.
- Phụ thuộc: — (qua api/).
- Quy ước riêng: `userId` hiện là orphan FK ở các module khác.
