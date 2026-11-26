# Hướng dẫn chuẩn bị dữ liệu JSON cho MongoDB Importer

Tài liệu này hướng dẫn cách cấu trúc file `.json` để sử dụng với công cụ
`tools/mongo-json-importer/import.ps1` trong repository
`engolearn-backend`.

## 1. Cấu trúc file bắt buộc

Mỗi file JSON phải có một object ở cấp cao nhất và chứa field
`collections`.

``` json
{
  "metadata": {
    "name": "my-sample-data",
    "version": 1
  },
  "collections": {
    "users": [
      {
        "_id": "user-001",
        "name": "Alice",
        "email": "alice@example.com"
      },
      {
        "_id": "user-002",
        "name": "Bob",
        "email": "bob@example.com"
      }
    ],
    "products": [
      {
        "_id": "product-001",
        "name": "Keyboard",
        "price": 100
      }
    ]
  }
}
```

Ý nghĩa:

-   `metadata`: tùy chọn; dùng để mô tả dataset. Importer bỏ qua field
    này.
-   `collections`: bắt buộc; chứa các collection cần import.
-   Tên property bên trong `collections` chính là tên collection
    MongoDB.
-   Giá trị của mỗi collection phải là một array các document.

Có thể đặt nhiều collection trong cùng một file JSON.

## 2. Quy tắc bắt buộc

### 2.1. Mỗi collection phải là một array

Đúng:

``` json
{
  "collections": {
    "users": [
      { "_id": "user-001", "name": "Alice" },
      { "_id": "user-002", "name": "Bob" }
    ]
  }
}
```

Sai:

``` json
{
  "collections": {
    "users": {
      "_id": "user-001",
      "name": "Alice"
    }
  }
}
```

### 2.2. Mỗi document phải có `_id` hợp lệ

Mỗi document phải có field `_id` và giá trị không được là `null`.

``` json
{
  "_id": "user-001",
  "name": "Alice"
}
```

Importer không tự tạo `_id`. Nếu thiếu `_id` hoặc `_id` là `null`, quá
trình kiểm tra dữ liệu sẽ thất bại.

Không được có hai document cùng `_id` trong một collection thuộc toàn bộ
các file đang được import cùng lượt.

### 2.3. Document phải là object

Mỗi phần tử trong array collection phải là một JSON object, không phải
string, number, boolean, array hoặc `null`.

### 2.4. Tên collection phải hợp lệ

Dùng tên collection MongoDB thông thường. Importer từ chối tên rỗng, tên
chứa ký tự `$` hoặc ký tự null, và tên bắt đầu bằng `system.`.

## 3. Các kiểu dữ liệu được hỗ trợ

Importer đọc dữ liệu bằng MongoDB Extended JSON (`EJSON.parse`). Có thể
sử dụng các kiểu JSON thông thường:

``` json
{
  "_id": "user-001",
  "name": "Alice",
  "age": 22,
  "score": 8.5,
  "active": true,
  "description": null,
  "roles": ["USER", "ADMIN"],
  "profile": {
    "city": "Ho Chi Minh City"
  }
}
```

Các kiểu dữ liệu thông thường gồm:

-   String
-   Number
-   Boolean
-   Null
-   Array
-   Object lồng nhau

Với kiểu dữ liệu đặc thù của MongoDB, sử dụng MongoDB Extended JSON.

### 3.1. ObjectId

``` json
{
  "_id": {
    "$oid": "507f1f77bcf86cd799439011"
  }
}
```

### 3.2. Date

``` json
{
  "_id": "event-001",
  "createdAt": {
    "$date": "2026-11-06T10:00:00.000Z"
  }
}
```

### 3.3. Decimal128

``` json
{
  "_id": "price-001",
  "amount": {
    "$numberDecimal": "123.45"
  }
}
```

### 3.4. Binary

MongoDB Extended JSON cũng hỗ trợ dữ liệu binary thông qua `$binary`
theo đúng định dạng Extended JSON.

## 4. Quy tắc chuyển đổi ObjectId tự động

Mặc định, importer tự chuyển chuỗi gồm đúng 24 ký tự hexadecimal thành
BSON ObjectId nếu tên field khớp một trong các pattern:

-   `_id`
-   `*_id`
-   `*_ids`

Ví dụ:

``` json
{
  "_id": "507f1f77bcf86cd799439011",
  "user_id": "507f1f77bcf86cd799439012",
  "category_ids": [
    "507f1f77bcf86cd799439013",
    "507f1f77bcf86cd799439014"
  ]
}
```

Các giá trị trên được chuyển thành ObjectId khi import. Những chuỗi
không khớp định dạng 24 ký tự hexadecimal được giữ nguyên.

**Lưu ý:** Nếu hệ thống sử dụng ID dạng String, đặc biệt là chuỗi 24 ký
tự hexadecimal, hãy kiểm tra cấu hình trước khi import. Có thể cấu hình
`MONGO_IMPORT_OBJECT_ID_FIELDS` trong file `.env` riêng của importer:

-   Đặt `none` để tắt chuyển đổi tự động.
-   Hoặc khai báo danh sách tên field/pattern cần chuyển đổi, phân tách
    bằng dấu phẩy. Pattern chỉ dùng chữ cái, chữ số, `_` và `*`.

Không nên mặc định rằng mọi field kết thúc bằng `_id` đều phải là
ObjectId; hãy căn cứ vào kiểu dữ liệu thực tế của ứng dụng.

## 5. Quan hệ giữa các collection

Importer không tự kiểm tra foreign key, không tự tạo document được tham
chiếu và không xác nhận các reference có tồn tại hay không.

Nếu một collection tham chiếu đến document thuộc collection khác, hãy
khai báo collection được tham chiếu trước collection phụ thuộc.

Ví dụ:

``` json
{
  "collections": {
    "users": [
      {
        "_id": "user-001",
        "name": "Alice"
      }
    ],
    "orders": [
      {
        "_id": "order-001",
        "user_id": "user-001",
        "total": 500
      }
    ]
  }
}
```

Trong ví dụ trên, `orders.user_id` tham chiếu đến `users._id`, vì vậy
`users` được khai báo trước `orders`.

Thứ tự này giúp dữ liệu dễ đọc và phù hợp với dependency, nhưng importer
không tự enforce tính toàn vẹn tham chiếu.

## 6. Nhiều file JSON trong cùng một lần import

Có thể chia dataset thành nhiều file để dễ quản lý. Khi import nhiều
file cùng lượt:

-   Importer đọc và kiểm tra tất cả file trước khi ghi dữ liệu.
-   Các document thuộc cùng collection được gom lại để xử lý.
-   Không được trùng `_id` trong cùng collection giữa các file.
-   Khi không truyền `-File`, importer đọc tất cả file `*.json` nằm trực
    tiếp trong thư mục `tools/mongo-json-importer/samples/`, theo thứ tự
    tên file.

Nếu cần kiểm soát chính xác file đầu vào, hãy truyền `-File` khi chạy
lệnh.

## 7. Ví dụ file hoàn chỉnh

``` json
{
  "metadata": {
    "name": "learning-sample",
    "version": 1
  },
  "collections": {
    "users": [
      {
        "_id": "user-001",
        "name": "Alice",
        "email": "alice@example.com",
        "active": true
      }
    ],
    "courses": [
      {
        "_id": "course-001",
        "title": "English Basics",
        "level": "A1",
        "active": true
      }
    ],
    "enrollments": [
      {
        "_id": "enrollment-001",
        "user_id": "user-001",
        "course_id": "course-001",
        "enrolledAt": {
          "$date": "2026-11-06T10:00:00.000Z"
        }
      }
    ]
  }
}
```

Đây chỉ là ví dụ minh họa format. Hãy thay tên collection, field và kiểu
dữ liệu theo schema thực tế của ứng dụng.

## 8. Chạy kiểm tra và import

Chạy các lệnh từ repository root.

### Kiểm tra trước, không ghi dữ liệu

``` powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\my-data.json -DryRun
```

`-DryRun` thực hiện preflight và báo cáo số collection, số document, số
`_id` đã tồn tại cùng các thông tin liên quan. Lệnh này không ghi dữ
liệu.

### Import dữ liệu

``` powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\my-data.json
```

Nếu bật xác nhận (mặc định `MONGO_IMPORT_REQUIRE_CONFIRMATION=true`),
nhập `IMPORT` khi được yêu cầu để xác nhận thao tác ghi.

### Chính sách khi `_id` đã tồn tại

Cấu hình `MONGO_IMPORT_CONFLICT_POLICY` trong `.env` của importer:

  -----------------------------------------------------------------------
  Giá trị                             Hành vi
  ----------------------------------- -----------------------------------
  `skip`                              Giữ document hiện có; chỉ insert
                                      document chưa tồn tại. Đây là mặc
                                      định.

  `fail`                              Dừng trước khi ghi nếu phát hiện có
                                      bất kỳ `_id` nào đã tồn tại.

  `replace`                           Thay thế toàn bộ document hiện có
                                      bằng document trong file; field cũ
                                      không có trong dữ liệu mới sẽ bị
                                      xóa.
  -----------------------------------------------------------------------

`replace` có thể làm mất dữ liệu. Luôn chạy `-DryRun` và kiểm tra kỹ
trước khi sử dụng.

## 9. Checklist trước khi gửi file JSON

-   [ ] File là JSON hợp lệ.
-   [ ] Có object `collections` ở cấp cao nhất.
-   [ ] Mỗi collection có giá trị là một array.
-   [ ] Mỗi phần tử trong array là một object.
-   [ ] Mỗi document có `_id` khác `null`.
-   [ ] Không trùng `_id` trong cùng collection giữa các file import
    cùng lượt.
-   [ ] Tên collection hợp lệ.
-   [ ] Kiểu dữ liệu ObjectId/String đã được kiểm tra theo cấu hình
    importer.
-   [ ] Các collection được sắp xếp dễ hiểu theo dependency.
-   [ ] Đã chạy `-DryRun` và xem báo cáo trước khi import thật.

## 10. Giới hạn cần biết

Importer kiểm tra cấu trúc file, hình dạng document và ID trùng lặp
trong input; công cụ **không** xác thực schema nghiệp vụ, kiểu dữ liệu
theo model ứng dụng hoặc tính hợp lệ của quan hệ tham chiếu.

Dữ liệu được ghi theo batch, không phải một transaction cho toàn bộ lượt
import. Nếu batch sau thất bại, các batch đã ghi thành công trước đó
không được rollback tự động.

Vì vậy, hãy dùng dataset phù hợp với schema của ứng dụng và kiểm tra kỹ
trước khi import vào database.

## Tài liệu tham khảo

-   [MongoDB JSON Importer
    README](https://github.com/engolearn-platform/engolearn-backend/blob/main/tools/mongo-json-importer/README.md)
-   [Importer
    implementation](https://github.com/engolearn-platform/engolearn-backend/blob/main/tools/mongo-json-importer/importer.mongosh.js)
-   [Example
    JSON](https://github.com/engolearn-platform/engolearn-backend/blob/main/tools/mongo-json-importer/samples/example.json)
