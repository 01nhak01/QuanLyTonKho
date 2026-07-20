# Hệ thống Quản lý RFID & Mô phỏng Cửa hàng (RFID Management & Store Simulation System)

### 1. Tổng quan:
- Figma: tham khảo giao diện.
- Supabase: nơi lưu trữ dữ liệu.
- Antigravity: tham khảo code.

### 2. Mô tả thiết kế:
## 2.1. Các bảng dữ liệu chính:
# 2.1.1. categories: 
-Các thuộc tính: 
+id: int8
+name: text
+description: text
+created_at: timestamptz
# 2.1.2. product_sizes:
-Các thuộc tính: 
+id: int8
+product_id: int8
+size: text
+stock_quantity: int8
+stock_store: int8
+stock_storage: int8
# 2.1.3. products:
-Các thuộc tính:
+id: int8
+sku: text
+name: text
+price: numberic
+stock_quantity: int4
+created_at: timestampz
+category_id: int8
+image_url: text
+status: text
## 2.2. Các role:
-Nhân viên: có thể nhận tiền mặt, thực hiện các chức năng tìm hàng tại kho để đưa cho Khách hàng, kiểm kê các sản phẩm tại kho.
-Khách hàng: có thể chọn các sản phầm ưu thích, bỏ giỏ và thanh toán sản phẩm mình đã chọn. Nhưng nếu chưa thanh toán sẽ báo lỗi khi ra về.
### 3. Các chức năng mới:
-Có thể di chuyển các nhân vật bằng nút di chuyển ở bàn phím
-Tìm và lấy hàng tại kho đưa cho Khách Hàng
-Kiểm kê để so sánh sản phẩm.