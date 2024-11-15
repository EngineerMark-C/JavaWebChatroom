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
