# Chat — Lab 1 Lập trình mạng

Ứng dụng chat bằng **Java Swing**, kết hợp **TCP client–server**, **UDP Multicast**, **UDP Broadcast** và **SQLite**. Mã nguồn được tách từ `AllLab/Lab1_Chat`, đóng gói để chạy độc lập trên Windows bằng JDK 21.

## Chức năng

- Chat phòng chung và nhắn tin riêng qua TCP; hiển thị danh sách người dùng online.
- Gửi văn bản tiếng Việt và emoji.
- Gửi file, video và ghi âm giọng nói; nhận file vào `downloads/`.
- Chat nhóm UDP Multicast và thông báo UDP Broadcast trong LAN.
- Dò IP server bằng UDP Broadcast, hoặc nhập IP thủ công.
- SQLite lưu tài khoản và thông tin tin nhắn qua server.
- Mở nhiều client để demo với các tên người dùng khác nhau.

Video được truyền dưới dạng file, không phải gọi video trực tiếp. Việc mở video phụ thuộc trình phát trên máy nhận.

## Yêu cầu

- **JDK 21 trở lên** (gồm cả `java` và `javac`); nên dùng cùng JDK 21 trên các máy demo.
- Windows có giao diện đồ họa để chạy các file `.bat`.
- Hai máy cùng mạng LAN/Wi-Fi khi demo qua mạng; micro và loa nếu thử ghi âm.
- Thư viện SQLite JDBC và SLF4J đã có trong `lib/`; không cần Maven hay cài SQLite riêng.

Kiểm tra trong PowerShell:

```powershell
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
```

Ví dụ `JAVA_HOME`: `C:\Program Files\Java\jdk-21` (không thêm `\bin`). Các script ưu tiên `JAVA_HOME`, nếu không có thì dùng Java trên `PATH`. Sau khi đổi biến môi trường, mở lại cửa sổ terminal/File Explorer nếu môi trường cũ vẫn còn hiệu lực.

## Tải mã nguồn

```powershell
git clone https://github.com/NhatHao-2805/Lap3_Mail.git
cd Lap3_Mail
```

Hoặc chọn **Code → Download ZIP**, giải nén toàn bộ rồi mở thư mục dự án.

## Chạy trên một máy

1. Mở `run_server.bat`. Server tự biên dịch và khởi động, lắng nghe TCP cổng **8888**. Kiểm tra nhật ký server đã báo đang lắng nghe.
2. Mở `run_client.bat`. Nhập Server IP `127.0.0.1`, port `8888`.
3. Chọn tên người dùng, nhập mật khẩu và bấm **Vào phòng Chat**. Tên chưa có sẽ được tạo khi đăng nhập lần đầu; tên đã có cần đúng mật khẩu.
4. Mở thêm `run_client.bat` và dùng tên khác để thử chat phòng chung, chat riêng, gửi file và ghi âm.

Cả hai script tự biên dịch bằng UTF-8 và tạo class tương thích Java 21, tránh lỗi lấy class từ máy chạy JDK mới hơn. Cửa sổ console giữ lại thông báo khi khởi động lỗi.

## Chạy trên hai máy

### Máy 1 — Server

1. Chạy `run_server.bat`.
2. Xem **Server IP LAN** trong giao diện. Có thể dùng `ipconfig` để xác nhận IPv4 của Wi-Fi/Ethernet đang kết nối; tránh IP adapter VPN/ảo.
3. Cho phép Java qua Windows Firewall trên mạng riêng nếu được hỏi.

### Máy 2 — Client

1. Chạy `run_client.bat`.
2. Nhập IP LAN của máy 1, ví dụ `192.168.1.15`, port `8888`; hoặc bấm **Dò LAN**.
3. Nhập tên và mật khẩu, bấm **Vào phòng Chat**.
4. Máy 1 cũng có thể mở thêm client để hai bên trao đổi.

**Không nhập `127.0.0.1` trên máy 2 để kết nối máy 1**: địa chỉ đó luôn là chính máy đang chạy client. Không cần chạy server trên cả hai máy.

## Các cổng và giao thức

| Chức năng | Giao thức / địa chỉ | Cổng |
|---|---|---:|
| Đăng nhập, chat chung/riêng, file, voice và video | TCP tới máy server | 8888 |
| Tự tìm server | UDP Broadcast | 8889 |
| Thông báo toàn LAN | UDP Broadcast `255.255.255.255` | 8890 |
| Chat nhóm | UDP Multicast `230.0.0.1` | 4446 |

Multicast/Broadcast phụ thuộc thiết bị mạng và firewall. Wi-Fi khách, AP isolation, VPN hoặc router chặn multicast có thể làm các chức năng UDP không hoạt động dù TCP vẫn chat được. Nhiều client cùng máy có thể nhận broadcast khác nhau tùy cách hệ điều hành chia sẻ cổng; nên demo UDP bằng hai máy thật.

## Kịch bản demo

1. Mở server và 2–6 client với tên khác nhau.
2. Gửi tin tiếng Việt và emoji vào phòng chung.
3. Chọn một người online và gửi tin riêng.
4. Gửi file nhỏ; kiểm tra file trong `downloads/` ở máy nhận.
5. Thử ghi âm và gửi video ngắn qua TCP.
6. Chọn nhóm Multicast, sau đó kênh Broadcast để thử UDP.
7. Xem nhật ký và database phía server.

## Cấu trúc

```text
Chat/
├── src/
│   ├── common/          # Message, MessageType dùng chung
│   ├── server/          # ChatServer, ClientHandler, DatabaseManager
│   └── client/          # Swing UI, TCP, UDP và ghi âm
├── lib/                 # SQLite JDBC và SLF4J
├── compile.bat
├── run_server.bat
├── run_client.bat
└── README.md
```

Sinh khi chạy: `build/classes/`, `chat_database.db` trên máy server, `downloads/` trên máy client. Các dữ liệu này được bỏ qua bởi Git; database và file cá nhân từ AllLab không được đưa vào repository.

## Biên dịch/chạy thủ công

Trong PowerShell, tại thư mục `Chat`:

```powershell
.\compile.bat
& "$env:JAVA_HOME\bin\java.exe" -cp 'build/classes;lib/*' server.ChatServer
# Mở PowerShell khác trong cùng thư mục:
& "$env:JAVA_HOME\bin\java.exe" -cp 'build/classes;lib/*' client.ChatClientGUI
```

Trên Linux/macOS có desktop và JDK 21, có thể biên dịch/chạy thủ công bằng dấu `:` thay cho `;` trong classpath; các script `.bat` chỉ dành cho Windows. Chưa kiểm tra giao diện trên Linux/macOS.

## Lỗi thường gặp và lưu ý

| Hiện tượng | Kiểm tra / xử lý |
|---|---|
| `UnsupportedClassVersionError` | Dùng JDK 21 trở lên cho cả `java` và `javac`; đặt đúng `JAVA_HOME`, chạy lại script để biên dịch. |
| Không tìm thấy `java` / `javac` | Cài **JDK**, kiểm tra `JAVA_HOME` hoặc `PATH`; JRE riêng không đủ để tự biên dịch. |
| Không kết nối được | Mở server trước; kiểm tra IP, TCP 8888, cùng LAN và firewall. |
| `Address already in use` | Đã có server hoặc ứng dụng khác dùng cổng; đóng bản server bị mở trùng. |
| Không dò được server | Nhập IP thủ công; kiểm tra UDP 8889 và thiết lập mạng. |
| Sai mật khẩu / tên đang online | Dùng mật khẩu lúc tạo tài khoản; mỗi client đang chạy cần tên riêng. |
| SQLite không lưu dữ liệu | Giữ đủ các JAR trong `lib/`, kiểm tra log `[DB]` và quyền ghi thư mục. |
| Không ghi/phát được voice | Kiểm tra micro, quyền micro của Java, thiết bị âm thanh và loa. |
| File lớn gây chậm hoặc thiếu bộ nhớ | File được đọc vào RAM; dùng file nhỏ khi demo, chưa có truyền theo khối/giới hạn dung lượng. |

### Giới hạn của bài thực hành

- Đây là ứng dụng học tập trong LAN tin cậy: TCP chưa mã hóa TLS, mật khẩu đang lưu dạng văn bản trong SQLite; không dùng mật khẩu thật hoặc mở server ra Internet.
- Khi SQLite không khả dụng, code hiện cho phép đăng nhập mà không xác thực database và không lưu lịch sử. Kiểm tra log database trước khi demo xác thực.
- Server đang gửi 30 bản ghi gần nhất cho người mới đăng nhập, chưa lọc riêng lịch sử tin nhắn riêng theo người nhận. Không dùng dữ liệu riêng tư khi demo.
- Database lưu thông tin tin nhắn/tên file, không lưu lại toàn bộ byte đính kèm để tải lại từ lịch sử sau khi restart.
- UDP không bảo đảm giao nhận/thứ tự; file, voice và video dùng TCP. Cả client vẫn cần đăng nhập TCP trước khi vào giao diện có các kênh UDP.

## Thư viện

- `sqlite-jdbc-3.45.1.0.jar`: Xerial SQLite JDBC.
- `slf4j-api-2.0.17.jar`, `slf4j-simple-2.0.17.jar`: SLF4J.

Các thư viện giữ giấy phép của dự án gốc trong metadata JAR. Repository chưa đặt giấy phép riêng cho mã nguồn ứng dụng.
