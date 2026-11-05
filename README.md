# Lab 3 — Mail Server & Mail Client

Ứng dụng mail desktop bằng Java Swing cho môn Lập trình mạng. Dự án mô phỏng một hệ thống mail trong LAN với **SMTP**, **POP3**, **SQLite** và giao diện client theo phong cách hộp thư hiện đại.

## Chức năng

- Gửi thư nội bộ qua SMTP trên cổng `2525`.
- Nhận, đọc và xóa thư qua POP3 trên cổng `1100`.
- Đăng ký, đăng nhập và đăng xuất tài khoản mail.
- Lưu tài khoản và email trong SQLite.
- Gửi thư qua Gmail SSL (`smtp.gmail.com:465`) với Google App Password.
- Giao diện quản lý server, nhật ký SMTP/POP3, tìm kiếm hộp thư và soạn thư.
- Mật khẩu tài khoản mới được băm PBKDF2-HMAC-SHA256; mật khẩu mẫu cũ được nâng cấp sau lần đăng nhập thành công đầu tiên.

## Yêu cầu

- Windows 10/11 và **JDK 21 trở lên**. Cần cả `java` và `javac`.
- Không cần Maven, Gradle hay cài SQLite riêng: các file JAR cần thiết đã có trong `lib/`.
- Khi demo qua LAN: các máy cùng Wi-Fi/LAN và Windows Firewall cho phép Java nhận kết nối ở cổng 2525, 1100.

Kiểm tra Java trong PowerShell:

```powershell
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
```

Ví dụ `JAVA_HOME`: `C:\Program Files\Java\jdk-21`. Script sẽ ưu tiên biến này; nếu không có thì dùng Java trong `PATH`.

## Tải mã nguồn

```powershell
git clone https://github.com/NhatHao-2805/Lap3_Mail.git
cd Lap3_Mail
```

## Chạy trên một máy

1. Mở `run_server.bat`. Cửa sổ **VKU Mail Server** tự biên dịch mã nguồn và khởi động SMTP `2525`, POP3 `1100`.
2. Mở `run_client.bat` trong cùng thư mục.
3. Ở màn hình tài khoản, nhập Server Host `127.0.0.1`, SMTP `2525`, POP3 `1100`.
4. Chọn **Tạo tài khoản mới** để đăng ký email, họ tên và mật khẩu; sau đó đăng nhập. Hoặc dùng tài khoản mẫu:

   | Email | Mật khẩu |
   |---|---|
   | `dat@vku.udn.vn` | `123456` |
   | `friend@vku.udn.vn` | `123456` |
   | `admin@vku.udn.vn` | `admin123` |

5. Bấm **Soạn thư mới**, gửi tới một tài khoản khác, sau đó bấm **Làm mới hộp thư** ở client nhận.

## Chạy trên hai máy trong LAN

1. Trên máy 1, chạy `run_server.bat` và xem địa chỉ IPv4 hiển thị trong giao diện server.
2. Trên máy 2, chạy `run_client.bat`; thay `127.0.0.1` bằng IP LAN của máy 1, giữ nguyên cổng SMTP `2525` và POP3 `1100`.
3. Đăng nhập hai tài khoản khác nhau và gửi thư nội bộ.

`127.0.0.1` luôn là chính máy đang chạy client, vì vậy chỉ dùng địa chỉ này khi server và client cùng máy. Nếu không kết nối được, kiểm tra server đã chạy, IP LAN, firewall và hai máy có cùng mạng hay không.

## Cổng và giao thức

| Chức năng | Giao thức | Cổng |
|---|---|---:|
| Gửi mail nội bộ | SMTP | 2525 |
| Đăng ký/đăng nhập, nhận thư | POP3 + lệnh `REGISTER` mở rộng | 1100 |
| Gửi qua Gmail | SMTP over SSL | 465 |

## Kiểm thử

Chạy trong PowerShell tại thư mục dự án:

```powershell
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp "lib/*" -d build/check src/ui/*.java src/client/*.java src/server/*.java tests/AccountIntegrationTest.java
New-Item -ItemType Directory -Force build/account-test | Out-Null
Push-Location build/account-test
& "$env:JAVA_HOME\bin\java.exe" -cp "../check;../../lib/*" AccountIntegrationTest
Pop-Location
```

## Lưu ý

- Đây là bài thực hành dành cho localhost/LAN tin cậy. POP3 và SMTP nội bộ chưa dùng TLS; không dùng mật khẩu thật và không mở server trực tiếp ra Internet.
- Base64 trong lệnh đăng ký chỉ dùng để biểu diễn dữ liệu trong giao thức thử nghiệm, không mã hóa đường truyền.
- Chỉ chọn Gmail khi đã bật bảo mật phù hợp và dùng **Google App Password**; không nhập mật khẩu Gmail thông thường.
- Các file sinh khi chạy (`build/`, `mail_database.db`) bị bỏ qua bởi Git. Xóa `src/mail_database.db` nếu muốn khởi tạo lại toàn bộ tài khoản và hộp thư mẫu.
- Client hiện tập trung vào Inbox; chưa triển khai đầy đủ thư đã gửi, đánh dấu sao và thùng rác.

## Cấu trúc dự án

```text
Lap3_Mail/
├── src/
│   ├── client/          # Swing client, SMTP/POP3 và tài khoản
│   ├── server/          # SMTP server, POP3 server, SQLite
│   └── ui/              # Bộ giao diện dùng chung
├── tests/               # Kiểm thử đăng ký/đăng nhập
├── lib/                 # SQLite JDBC và SLF4J
├── run_server.bat
├── run_client.bat
└── README.md
```
