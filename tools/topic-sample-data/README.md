# CEFR Sample Data Import

Chạy tại thư mục gốc repo bằng Windows PowerShell 5.1.

```powershell
# Kiểm tra trước, không ghi dữ liệu
.\tools\topic-sample-data\import-all-cefr-samples.ps1 -DryRun

# Import; chỉ ghi dữ liệu sau khi xác nhận bằng cách nhập IMPORT
.\tools\topic-sample-data\import-all-cefr-samples.ps1
```

Dùng MongoDB local từ Docker Compose và credentials trong `.env` (`APP_DB`, `MONGO_USER`, `MONGO_PASS`). Mặc định database là `engolearn_db`.

Data pack gồm 6 topic A1–C2, phục vụ local UI/API preview; audio từ vựng chưa có. Tool không xóa hoặc ghi đè document hiện có.
