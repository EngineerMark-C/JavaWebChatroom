import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int SOCKET_PORT = 12345;
    private static final int WEBSOCKET_PORT = 8080;
    private static final int HTTP_PORT = 8081;
    private static Mysql db; // 数据库实例
    private static Map<String, PrintWriter> clients = new HashMap<>(); // 在线用户映射

    public static void main(String[] args) {
        // 启动HTTP服务器
        try {
            SimpleHttpServer.start(HTTP_PORT);
            System.out.println("HTTP服务器启动在端口: " + HTTP_PORT);
        } catch (IOException e) {
            System.out.println("HTTP服务器启动失败：");
            e.printStackTrace();
        }

        // 启动WebSocket代理服务器（用于Web客户端）
        WebSocketProxy wsProxy = new WebSocketProxy(WEBSOCKET_PORT);
        wsProxy.start();
        System.out.println("WebSocket代理服务器启动在端口: " + WEBSOCKET_PORT);

        // 启动传统Socket服务器
        ServerSocket serverSocket = null;
        db = new Mysql();

        try {
            serverSocket = new ServerSocket(SOCKET_PORT);
            System.out.println("Socket服务器启动，等待客户端连接...");

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    new ClientHandler(clientSocket).start();
                } catch (IOException e) {
                    System.out.println("客户端连接处理错误：");
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.out.println("服务器启动失败：");
            e.printStackTrace();
        } finally {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
                if (db != null) {
                    db.close();
                }
                wsProxy.stop(1000); // 添加超时参数
            } catch (IOException e) {
                System.out.println("服务器关闭时发生错误：");
                e.printStackTrace();
            } catch (InterruptedException e) {
                System.out.println("WebSocket代理关闭时发生中断：");
                e.printStackTrace();
                Thread.currentThread().interrupt(); // 重新设置中断标志
            }
        }
    }

// 客户端处理类
static class ClientHandler extends Thread {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("收到Socket消息：" + inputLine);
                handleMessage(inputLine);
            }
        } catch (IOException e) {
            System.out.println("客户端连接异常：" + e.getMessage());
        } finally {
            try {
                synchronized (clients) {
                    if (username != null) {
                        clients.remove(username);
                        MessageData offlineMessage = new MessageData();
                        offlineMessage.type = "system";
                        offlineMessage.content = username + " 离开了聊天室";
                        broadcastMessage(offlineMessage);
                        broadcastOnlineUsers();
                    }
                }
                socket.close();
            } catch (IOException e) {
                System.out.println("关闭socket时发生错误：" + e.getMessage());
            }
        }
    }

    private void handleMessage(String message) {
        try {
            MessageData messageData = new Gson().fromJson(message, MessageData.class);
            if (messageData == null || messageData.type == null) {
                throw new JsonSyntaxException("Invalid message format");
            }

            switch (messageData.type) {
                case "register":
                    handleRegister(messageData);
                    break;
                case "login":
                    handleLogin(messageData);
                    break;
                case "chat":
                    handleChat(messageData);
                    break;
                default:
                    sendError("未知的消息类型");
                    break;
            }
        } catch (JsonSyntaxException e) {
            System.out.println("JSON解析错误：" + e.getMessage());
            sendError("消息格式错误");
        } catch (Exception e) {
            e.printStackTrace();
            sendError("服务器内部错误");
        }
    }

    private void sendError(String message) {
        MessageData response = new MessageData();
        response.type = "error";
        response.content = message;
        out.println(new Gson().toJson(response));
    }

    private void handleRegister(MessageData data) {
        System.out.println("收到注册请求：用户名=" + data.username + "，密码=" + data.password);
        if (db.insert(data.username, data.password)) {
            System.out.println("注册成功：用户名=" + data.username);
            // 注册成功，发送系统消息
            MessageData response = new MessageData();
            response.type = "system";
            response.content = "注册成功";
            out.println(new Gson().toJson(response));
        } else {
            System.out.println("注册失败：用户名=" + data.username);
            // 注册失败，发送错误消息
            MessageData response = new MessageData();
            response.type = "error";
            response.content = "注册失败，用户名已存在";
            out.println(new Gson().toJson(response));
        }
    }

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
    }

    private void handleChat(MessageData data) {
        data.sender = username; // 设置发送者
        if (data.receiver != null && !data.receiver.isEmpty() && !data.receiver.equals("all")) {
            // 私聊消息
            privateMessage(data);
        } else {
            // 群聊消息
            data.receiver = "all";
            data.timestamp = db.insertMessage(data.sender, "all", data.content);
            broadcastMessage(data);
            
            // 发送确认消息给发送者
            MessageData confirmMessage = new MessageData();
            confirmMessage.type = "chat";
            confirmMessage.content = data.content;
            confirmMessage.sender = data.sender;
            confirmMessage.timestamp = data.timestamp;
            out.println(new Gson().toJson(confirmMessage));
        }
    }

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
            out.println(messageJson);
        } else {
            // 接收者不在线
            MessageData errorResponse = new MessageData();
            errorResponse.type = "error";
            errorResponse.content = "用户 " + data.receiver + " 不在线！";
            out.println(new Gson().toJson(errorResponse));
        }
    }

    // 重载的 broadcast 方法，不含 timestamp
    private void broadcast(String message, String excludeUser) {
        MessageData broadcastMessage = new MessageData();
        broadcastMessage.type = "system";
        broadcastMessage.content = message;
        broadcastMessage.timestamp = new Date().toString();
        broadcastMessage.sender = "系统";
        broadcastMessage.receiver = "all";
        
        String messageJson = new Gson().toJson(broadcastMessage);
        synchronized (clients) {
            for (Map.Entry<String, PrintWriter> client : clients.entrySet()) {
                if (!client.getKey().equals(excludeUser)) {
                    client.getValue().println(messageJson);
                }
            }
        }
    }

    private void broadcastOnlineUsers() {
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

    private void broadcastMessage(MessageData message) {
        String messageJson = new Gson().toJson(message);
        synchronized (clients) {
            for (PrintWriter writer : clients.values()) {
                writer.println(messageJson);
            }
        }
    }
}

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
}