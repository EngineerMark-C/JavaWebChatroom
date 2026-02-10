# A JavaWebChatroom
# Socket
Socket（套接字）是计算机网络中一个抽象层，应用程序可以通过它发送或接收数据，它提供了应用程序间通信的端点。Socket是计算机网络通信的基石，它允许在不同计算机上的应用程序通过网络进行数据交换。
以下是关于Socket的一些基本概念：
### 层次结构
Socket通常分为以下两种：
1. **传输层Socket**：如TCP（传输控制协议）和UDP（用户数据报协议）Socket，它们工作在OSI模型的传输层。
2. **应用层Socket**：直接为应用程序提供服务的Socket，如HTTP、FTP等。
### 原理
- **服务器端Socket**：在特定端口上监听来自客户端的连接请求。
- **客户端Socket**：主动发起连接到服务器端的Socket。
### 创建和使用
1. **创建Socket**：在服务器端和客户端，首先需要创建一个Socket实例。
2. **绑定地址和端口**：服务器端的Socket需要绑定到一个地址和端口上，以便客户端能够找到它。
3. **监听连接**：服务器端的Socket需要监听客户端的连接请求。
4. **接受连接**：服务器端接受客户端的连接请求，创建一个新的Socket用于与客户端通信。
5. **发送和接收数据**：通过Socket发送和接收数据。
### 特点
- **面向连接**：如TCP Socket，在数据传输之前需要建立一个连接。
- **无连接**：如UDP Socket，数据传输前不需要建立连接，但可靠性较低。
# 程序设计

## 系统架构概述
1. 考虑到程序须实现用户注册，聊天记录等功能，选择建立数据库以存储用户数据，聊天数据。
2. 为实现Socket通信，需要Socket服务端和客户端。
3. 考虑到需要ui设计，尝试用前端的方法实现。需要Websocket服务器处理数据，HTTP服务器提供静态网页支持。
4. 功能上，系统需要实现：
	- 客户端：注册、登陆和退出聊天室时都有相关提示信息；用户应该可以看到所有在线的用户；聊天时可以群聊，也可以选择某个聊天对象私聊；
	- 服务器端：登录聊天室时必须输入正确的用户名和密码，未注册用户必须先注册；可以显示当前使用的端口，IP地址及在线人数；可以显示所有用户注册、登陆及退出等信息，且在用户登陆和退出时可以实时刷新在线用户列表；可以显示所有聊天记录，并可以将记录保存在文件中；
流程图
![](https://www.engmarkc.com/picgo/2026/02/10/chatroom1.png)
![](https://www.engmarkc.com/picgo/2026/02/10/chatroom2.png)
## 程序实现
### 1. 数据库建立
#### 用户数据
```mysql
CREATE TABLE `chatuser` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT 'Primary Key',
    `create_time` datetime DEFAULT NULL COMMENT 'Create Time',
    `name` varchar(255) DEFAULT NULL,
    `password` char(50) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 47 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
```
- create_time存储创建时间
- name存储用户名
- password存储密码
- 通过建立用户数据表，可以实现用户信息的存储，用户注册后，下次可以直接登录。
#### 聊天数据
```mysql
CREATE TABLE `chat` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT 'Primary Key',
    `create_time` datetime DEFAULT NULL COMMENT 'Create Time',
    `sender` char(50) DEFAULT NULL,
    `receiver` char(50) DEFAULT NULL,
    `messageLen` int DEFAULT NULL,
    `message` text,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 104 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
```
- create_time存储创建时间
- sender存储消息发送者
- receiver存储消息接受者
- message存储消息内容
- 通过建立聊天数据表，可以实现聊天数据的存储，用户聊天数据都会保持，下次用户登陆时能够看到历史聊天数据。
### 2. Socket通信原理实现
使用Java的ServerSocket类实现
#### 服务端
```java
import java.io.*;  
import java.net.*;  
import java.util.*;  
  
public class Server {  
    private static final int PORT = 12345;  
    private static Mysql db; // 数据库实例  
    private static Map<String, PrintWriter> clients = new HashMap<>(); // 在线用户映射  
  
    public static void main(String[] args) {  
        db = new Mysql(); // 初始化数据库连接  
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {  
            System.out.println("服务器启动，等待客户端连接...");  
            while (true) {  
                Socket clientSocket = serverSocket.accept();  
                new ClientHandler(clientSocket).start();  
            }  
        } catch (IOException e) {  
            e.printStackTrace();  
        } finally {  
            db.close(); // 关闭数据库连接  
        }  
    }  
  
    static class ClientHandler extends Thread {  
        private Socket socket;  
        private BufferedReader in;  
        private PrintWriter out;  
        private String username;  
  
        public ClientHandler(Socket socket) {  
            this.socket = socket;  
        }  
  
        @Override  
        public void run() {  
            try {  
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));  
                out = new PrintWriter(socket.getOutputStream(), true);  
  
                // 用户注册或登录  
                while (true) {  
                    out.println("请输入操作：1.注册 2.登录");  
                    String option = in.readLine();  
                    if ("1".equals(option)) {  
                        if (registerUser()) break;  
                    } else if ("2".equals(option)) {  
                        if (loginUser()) break;  
                    } else {  
                        out.println("无效选项，请重新输入！");  
                    }  
                }  
  
                // 聊天功能  
                String message;  
                while ((message = in.readLine()) != null) {  
                    if (message.startsWith("@")) {  
                        // 私聊  
                        String[] parts = message.split(" ", 2);  
                        if (parts.length > 1) {  
                            String receiver = parts[0].substring(1);  
                            String msg = parts[1];  
                            sendMessage(receiver, msg);  
                            db.insertMessage(username, receiver, msg); // 存储私聊消息  
                        } else {  
                            out.println("消息格式错误！使用 @用户名 消息内容");  
                        }  
                    } else {  
                        // 群聊  
                        broadcast(username + ": " + message, username);  
                        db.insertMessage(username, "all", message); // 存储群聊消息  
                    }  
                }  
            } catch (IOException e) {  
                e.printStackTrace();  
            } finally {  
                synchronized (clients) {  
                    if (username != null) {  
                        clients.remove(username);  
                        broadcast(username + " 离开了聊天！", null);  
                    }  
                }  
                try {  
                    socket.close();  
                } catch (IOException e) {  
                    e.printStackTrace();  
                }  
            }  
        }  
  
        private boolean registerUser() throws IOException {  
            out.println("请输入用户名：");  
            String name = in.readLine();  
            out.println("请输入密码：");  
            String password = in.readLine();  
            if (db.insert(name, password)) {  
                out.println("注册成功，请登录！");  
                return false;  
            } else {  
                out.println("注册失败，用户名可能已存在！");  
                return false;  
            }  
        }  
  
        private boolean loginUser() throws IOException {  
            out.println("请输入用户名：");  
            String name = in.readLine();  
            out.println("请输入密码：");  
            String password = in.readLine();  
            if (db.validateLogin(name, password)) {  
                synchronized (clients) {  
                    if (!clients.containsKey(name)) {  
                        this.username = name;  
                        clients.put(username, out);  
                        broadcast(username + " 加入了聊天！", null);  
                        out.println("登录成功，欢迎进入聊天室！");  
                          
                        // 发送历史消息  
                        out.println("=== 历史消息开始 ===");  
                        List<String> historyMessages = db.getHistoryMessages(username);  
                        for (String msg : historyMessages) {  
                            out.println(msg);  
                        }  
                        out.println("=== 历史消息结束 ===");  
                          
                        return true;  
                    } else {  
                        out.println("用户已在线，请换个名字！");  
                    }  
                }  
            } else {  
                out.println("登录失败，请检查用户名和密码！");  
            }  
            return false;  
        }  
  
        private void sendMessage(String receiver, String message) {  
            synchronized (clients) {  
                PrintWriter recipientOut = clients.get(receiver);  
                String timestamp = db.insertMessage(username, receiver, message);  
                if (recipientOut != null) {  
                    recipientOut.println(String.format("[%s] %s (私聊): %s", timestamp, username, message));  
                    out.println(String.format("[%s] 发送给 %s: %s", timestamp, receiver, message));  
                } else {  
                    out.println("用户 " + receiver + " 不在线！");  
                }  
            }  
        }  
  
        private void broadcast(String message, String excludeUser) {  
            String timestamp = "";  
            if (excludeUser != null) {  // 只有实际聊天消息才需要存储和显示时间戳  
                timestamp = db.insertMessage(username, "all", message);  
                message = String.format("[%s] %s", timestamp, message);  
            }  
              
            synchronized (clients) {  
                for (Map.Entry<String, PrintWriter> client : clients.entrySet()) {  
                    if (!client.getKey().equals(excludeUser)) {  
                        client.getValue().println(message);  
                    }  
                }  
            }  
        }  
    }  
}
```
#### 客户端
```java
import java.io.*;  
import java.net.*;  
  
public class Client {  
    private static final String SERVER_IP = "127.0.0.1";  
    private static final int SERVER_PORT = 12345;  
  
    public static void main(String[] args) {  
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);  
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));  
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);  
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {  
  
            System.out.println("连接到服务器...");  
            new Thread(() -> {  
                try {  
                    String serverMessage;  
                    while ((serverMessage = in.readLine()) != null) {  
                        System.out.println(serverMessage);  
                    }  
                } catch (IOException e) {  
                    e.printStackTrace();  
                }  
            }).start();  
  
            String userInput;  
            while ((userInput = console.readLine()) != null) {  
                out.println(userInput);  
            }  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
    }  
}
```
服务器采用多线程模型处理客户端连接，每个新的客户端连接都会创建一个独立的线程，确保并发处理能力。
### 3. WebSocket实现
系统实现了基于WebSocket协议的实时通信，主要通过Java-WebSocket库实现：
```java
public class ChatWebSocketServer extends WebSocketServer {
    private static Map<String, WebSocket> webSocketClients = new ConcurrentHashMap<>();
    private static Mysql db;
    private Gson gson = new Gson();
    private static int serverPort;

    public ChatWebSocketServer(int port, String bindAddress) {
        super(new InetSocketAddress(bindAddress, port));
        ChatWebSocketServer.serverPort = port;
        db = new Mysql();
        setConnectionLostTimeout(0);
    }
```
WebSocket服务器提供了以下核心功能：
- 连接管理：使用ConcurrentHashMap存储客户端连接
- 消息广播：支持全局消息和定向消息
- 会话维护：自动处理连接断开和重连
#### 消息协议设计
系统采用JSON格式进行消息传输，定义了统一的消息格式：
```java
    private static class MessageData {
        String type;        // 消息类型：register/login/chat/system/error/online_users
        String username;    // 用户名
        String password;    // 密码
        String sender;      // 发送者
        String receiver;    // 接收者
        String content;     // 消息内容
        String timestamp;   // 时间戳
        List<String> users; // 在线用户列表
    }
```
消息处理流程包括：
1. 登录处理：
```java
    private void handleLogin(WebSocket conn, MessageData data) {
        if (db.validateLogin(data.username, data.password)) {
            // 检查是否已经有相同用户名的连接
            WebSocket existingConn = webSocketClients.get(data.username);
            if (existingConn != null && existingConn.isOpen()) {
                conn.send(gson.toJson(createMessage("error", data.username, "用户已在线")));
                return;
            }

            // 先移除旧的连接（如果有）
            webSocketClients.remove(data.username);
            // 添加新的连接
            webSocketClients.put(data.username, conn);

            // 发送登录成功消息
            conn.send(gson.toJson(createMessage("system", data.username, "登录成功")));

            // 发送历史消息
            List<String> history = db.getHistoryMessages(data.username);
            for (String msg : history) {
                conn.send(gson.toJson(createMessage("history", data.username, msg)));
            }

            // 广播新用户加入
            broadcastMessage(createMessage("system", "all", data.username + " 加入了聊天室"));

            // 广播在线用户列表
            broadcastOnlineUsers();

            // 添加登录日志
            Server.addLog(data.username + " 登录成功", "user");
        } else {
            conn.send(gson.toJson(createMessage("error", data.username, "登录失败")));
        }
    }
```
2. 聊天消息处理：
```java
    private void handleChat(MessageData data) {
        String timestamp = db.insertMessage(data.sender, data.receiver, data.content);
        MessageData response = createMessage("chat", data.receiver, data.content);
        response.sender = data.sender;
        response.timestamp = timestamp;

        if ("all".equals(data.receiver)) {
            broadcastMessage(response);
            // 添加群聊日志
            Server.addLog(data.sender + " 发送群聊消息: " + data.content, "chat");
        } else {
            // 私聊消息
            WebSocket receiverConn = webSocketClients.get(data.receiver);
            if (receiverConn != null && receiverConn.isOpen()) {
                receiverConn.send(gson.toJson(response));
            }
            // 发送给发送者的确认
            WebSocket senderConn = webSocketClients.get(data.sender);
            if (senderConn != null && senderConn.isOpen()) {
                senderConn.send(gson.toJson(response));
            }
            // 添加私聊日志
            Server.addLog(data.sender + " 向 " + data.receiver + " 发送私聊消息: " + data.content, "chat");
        }
    }
```
并发控制
- 使用ConcurrentHashMap管理WebSocket连接
- 使用synchronized块保护共享资源
- 实现线程安全的消息广播机制
#### 安全机制
1. 用户认证：
```java
    private void handleLogin(MessageData data) {
        if (!db.validateLogin(data.username, data.password)) {
            MessageData response = new MessageData();
            response.type = "error";
            response.content = "登录失败：用户名或密码错误";
            out.println(new Gson().toJson(response));
            return;
        }

        synchronized (clients) {
            if (clients.containsKey(data.username)) {
                MessageData response = new MessageData();
                response.type = "error";
                response.content = "登录失败：该用户已在线";
                out.println(new Gson().toJson(response));
                return;
            }
            clients.put(data.username, out);
            username = data.username;
        }

        // 登录成功消息
        MessageData loginSuccess = new MessageData();
        loginSuccess.type = "system";
        loginSuccess.content = "登录成功";
        out.println(new Gson().toJson(loginSuccess));

        // 广播新用户加入
        MessageData joinMessage = new MessageData();
        joinMessage.type = "system";
        joinMessage.content = username + " 加入了聊天室";
        broadcast(joinMessage.content, username);

        // 广播在线用户列表
        broadcastOnlineUsers();

        // 发送历史消息
        List<String> history = db.getHistoryMessages(username);
        for (String msg : history) {
            MessageData historyMessage = new MessageData();
            historyMessage.type = "history";
            historyMessage.content = msg;
            out.println(new Gson().toJson(historyMessage));
        }
        Server.addLog(data.username + " 登录成功", "user");
    }
```
2. 连接状态管理：
```java
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String username = getUsernameByConnection(conn);
        if (username != null) {
            webSocketClients.remove(username);
            broadcastMessage(createMessage("system", "all", username + " 离开了聊天室"));
            // 广播更新后的在线用户列表
            broadcastOnlineUsers();
            // 添加用户退出日志
            Server.addLog(username + " 退出了聊天室", "user");
        }
    }
```
#### 消息广播与私聊实现
###### 消息广播
系统实现了两种广播机制：
1. 全局广播：
```java
    private void broadcastMessage(MessageData message) {
        String messageJson = new Gson().toJson(message);
        synchronized (clients) {
            for (PrintWriter writer : clients.values()) {
                writer.println(messageJson);
            }
        }
    }
```
2. 在线用户列表广播：
```java
        MessageData response = new MessageData();
        response.type = "online_users";
        synchronized (clients) {
            response.users = new ArrayList<>(clients.keySet());
        }
        String messageJson = new Gson().toJson(response);
        
        synchronized (clients) {
            for (PrintWriter writer : clients.values()) {
                writer.println(messageJson);
            }
        }
    }
```
##### 私聊实现
私聊消息的处理流程：
```java
    private void privateMessage(MessageData data) {
        PrintWriter recipientOut;
        synchronized (clients) {
            recipientOut = clients.get(data.receiver);
        }
        
        data.timestamp = db.insertMessage(data.sender, data.receiver, data.content);
        String messageJson = new Gson().toJson(data);
        
        if (recipientOut != null) {
            // 发送给接收者
            recipientOut.println(messageJson);
            // 发送给发送者的确认
```
