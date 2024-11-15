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

    // ClientHandler 内部类
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

                // 处理登录
                username = in.readLine();
                String password = in.readLine();
                
                if (username == null || password == null) {
                    return;
                }

                // 验证登录
                if (!db.validateLogin(username, password)) {
                    out.println("登录失败：用户名或密码错误");
                    return;
                }

                synchronized (clients) {
                    if (clients.containsKey(username)) {
                        out.println("登录失败：该用户已在线");
                        return;
                    }
                    clients.put(username, out);
                }

                // 发送登录成功消息
                out.println("登录成功");
                broadcast(username + " 加入了聊天室", null);
                broadcastOnlineUsers(); // 广播在线用户列表

                // 发送历史消息
                out.println("=== 历史消息开始 ===");
                List<String> history = db.getHistoryMessages(username);
                for (String msg : history) {
                    out.println(msg);
                }
                out.println("=== 历史消息结束 ===");

                // 处理消息
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("@")) {
                        // 私聊消息
                        int spaceIndex = message.indexOf(" ");
                        if (spaceIndex != -1) {
                            String receiver = message.substring(1, spaceIndex);
                            String content = message.substring(spaceIndex + 1);
                            privateMessage(username, receiver, content);
                        }
                    } else {
                        // 群聊消息
                        String timestamp = db.insertMessage(username, "all", message);
                        broadcast(username + ": " + message, username, timestamp);
                        out.println(String.format("[%s] 你: %s", timestamp, message));
                    }
                }
            } catch (IOException e) {
                System.out.println("客户端连接异常：" + e.getMessage());
            } finally {
                try {
                    synchronized (clients) {
                        if (username != null) {
                            clients.remove(username);
                            broadcast(username + " 离开了聊天室", null);
                            broadcastOnlineUsers(); // 广播在线用户列表
                        }
                    }
                    socket.close();
                } catch (IOException e) {
                    System.out.println("关闭socket时发生错误：" + e.getMessage());
                }
            }
        }

        private void privateMessage(String sender, String receiver, String message) {
            PrintWriter recipientOut = clients.get(receiver);
            String timestamp = db.insertMessage(sender, receiver, message);
            if (recipientOut != null) {
                recipientOut.println(String.format("[%s] %s (私聊): %s", timestamp, sender, message));
                out.println(String.format("[%s] 发送给 %s: %s", timestamp, receiver, message));
            } else {
                out.println("用户 " + receiver + " 不在线！");
            }
        }

        private void broadcast(String message, String excludeUser, String timestamp) {
            String formattedMessage = message;
            if (timestamp != null) {
                formattedMessage = String.format("[%s] %s", timestamp, message);
            }
            
            synchronized (clients) {
                for (Map.Entry<String, PrintWriter> client : clients.entrySet()) {
                    if (!client.getKey().equals(excludeUser)) {
                        client.getValue().println(formattedMessage);
                    }
                }
            }
        }

        private void broadcast(String message, String excludeUser) {
            broadcast(message, excludeUser, null);
        }

        // 在用户登录成功后广播在线用户列表
        private void broadcastOnlineUsers() {
            synchronized (clients) {
                String userList = "ONLINE_USERS:" + String.join(",", clients.keySet());
                for (PrintWriter writer : clients.values()) {
                    writer.println(userList);
                }
            }
        }
    }
}
