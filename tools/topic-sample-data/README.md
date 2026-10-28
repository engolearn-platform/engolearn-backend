# CEFR Sample Data Import

Chạy lệnh tại thư mục gốc `engolearn-backend` bằng Windows PowerShell 5.1.

## 1. Kiểm tra trước khi import

```powershell
.\tools\topic-sample-data\import-all-cefr-samples.ps1 -DryRun
```

Chỉ kiểm tra dữ liệu và kết nối; không ghi vào MongoDB.

## 2. Import dữ liệu

```powershell
.\tools\topic-sample-data\import-all-cefr-samples.ps1
```

Đọc kết quả preflight, sau đó nhập `IMPORT` khi được hỏi để xác nhận ghi dữ liệu.

## 3. Chạy kiểm tra regression

```powershell
node tools/topic-sample-data/test-import-all-cefr-samples.cjs
```

Chạy test giả lập dry-run, import và import lại để kiểm tra không tạo bản sao.

## Lưu ý

- Database mặc định: `engolearn_db`.
- Dữ liệu gồm 6 topic từ A1 đến C2.
- Chỉ dùng cho local development/UI preview; audio từ vựng chưa có nên các trường audio để `null`.
- Không xóa collection hoặc ghi đè document đang tồn tại.
- Nếu lệnh báo lỗi, **không chạy import lặp lại** trước khi kiểm tra trạng thái dữ liệu.
