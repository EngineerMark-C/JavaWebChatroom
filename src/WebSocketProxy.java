import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class WebSocketProxy extends WebSocketServer {
    private static final int SOCKET_PORT = 12345;
    private Map<WebSocket, Socket> clientSockets = new HashMap<>();
    private Map<WebSocket, PrintWriter> clientWriters = new HashMap<>();
    private Map<WebSocket, BufferedReader> clientReaders = new HashMap<>();

    public WebSocketProxy(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket代理服务器已启动在端口: " + getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("新的WebSocket连接：" + conn.getRemoteSocketAddress());
        try {
            // 为每个WebSocket连接创建一个Socket连接到聊天服务器
            Socket socket = new Socket("localhost", SOCKET_PORT);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            clientSockets.put(conn, socket);
            clientWriters.put(conn, writer);
            clientReaders.put(conn, reader);

            // 启动一个线程来读取Socket的响应
            new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        conn.send(line); // 转发给WebSocket客户端
                    }
                } catch (IOException e) {
                    System.out.println("读取Socket消息时发生错误：" + e.getMessage());
                    conn.close();
                }
            }).start();

        } catch (IOException e) {
            System.out.println("创建Socket连接时发生错误：" + e.getMessage());
            conn.close();
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("WebSocket连接关闭：" + conn.getRemoteSocketAddress());
        try {
            Socket socket = clientSockets.get(conn);
            if (socket != null) {
                socket.close();
            }
            PrintWriter writer = clientWriters.get(conn);
            if (writer != null) {
                writer.close();
            }
            BufferedReader reader = clientReaders.get(conn);
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            System.out.println("关闭Socket连接时发生错误：");
            e.printStackTrace();
        }

        clientSockets.remove(conn);
        clientWriters.remove(conn);
        clientReaders.remove(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        PrintWriter writer = clientWriters.get(conn);
        if (writer != null) {
            // 转发WebSocket消息到Socket服务器
            writer.println(message);
            System.out.println("消息已转发到Socket：" + message);
        } else {
            System.out.println("未找到对应的Socket连接");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("WebSocket代理错误：");
        if (conn != null) {
            System.out.println("来自连接：" + conn.getRemoteSocketAddress());
        }
        ex.printStackTrace();
    }
}