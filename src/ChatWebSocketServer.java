import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import com.google.gson.Gson;
import java.net.InetSocketAddress;
import java.util.*;

public class ChatWebSocketServer extends WebSocketServer {
    private static Map<String, WebSocket> webSocketClients = new HashMap<>();
    private static Mysql db;
    private Gson gson = new Gson();

    public ChatWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        db = new Mysql();
        setConnectionLostTimeout(0);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("新的WebSocket连接：" + conn.getRemoteSocketAddress());
        MessageData response = createMessage("system", "all", "连接成功");
        conn.send(gson.toJson(response));
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String username = getUsernameByConnection(conn);
        if (username != null) {
            webSocketClients.remove(username);
            broadcastMessage(createMessage("system", "all", username + " 离开了聊天室"));
            // 添加用户退出日志
            Server.addLog(username + " 退出了聊天室", "user");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            MessageData messageData = gson.fromJson(message, MessageData.class);
            
            switch (messageData.type) {
                case "login":
                    handleLogin(conn, messageData);
                    break;
                case "register":
                    handleRegister(conn, messageData);
                    break;
                case "chat":
                    handleChat(messageData);
                    break;
                default:
                    conn.send(gson.toJson(createMessage("error", "system", "未知的消息类型")));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            conn.send(gson.toJson(createMessage("error", "system", "消息处理错误")));
        }
    }

    private void handleLogin(WebSocket conn, MessageData data) {
        if (db.validateLogin(data.username, data.password)) {
            if (!webSocketClients.containsKey(data.username)) {
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
            } else {
                conn.send(gson.toJson(createMessage("error", data.username, "用户已在线")));
            }
        } else {
            conn.send(gson.toJson(createMessage("error", data.username, "登录失败")));
        }
    }
    
    private void handleRegister(WebSocket conn, MessageData data) {
        System.out.println("收到注册请求：用户名=" + data.username + "，密码=" + data.password);
        if (db.insert(data.username, data.password)) {
            System.out.println("注册成功：用户名=" + data.username);
            conn.send(gson.toJson(createMessage("system", data.username, "注册成功")));
        } else {
            System.out.println("注册失败：用户名=" + data.username);
            conn.send(gson.toJson(createMessage("error", data.username, "注册失败，用户名已存在")));
        }
    }

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

    private MessageData createMessage(String type, String receiver, String content) {
        MessageData message = new MessageData();
        message.type = type;
        message.receiver = receiver;
        message.content = content;
        message.timestamp = new Date().toString();
        return message;
    }

    private void broadcastMessage(MessageData message) {
        String messageJson = gson.toJson(message);
        for (WebSocket client : webSocketClients.values()) {
            if (client.isOpen()) {
                client.send(messageJson);
            }
        }
    }

    private String getUsernameByConnection(WebSocket conn) {
        for (Map.Entry<String, WebSocket> entry : webSocketClients.entrySet()) {
            if (entry.getValue() == conn) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("WebSocket错误：" + ex.getMessage());
        if (conn != null) {
            System.out.println("来自客户端：" + conn.getRemoteSocketAddress());
        }
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket服务器启动在端口：" + getPort());
        setConnectionLostTimeout(0);
    }

    // 消息数据类
    private static class MessageData {
        String type;
        String username;
        String password;
        String sender;
        String receiver;
        String content;
        String timestamp;
    }
}