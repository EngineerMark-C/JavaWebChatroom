import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.net.InetAddress;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

public class SimpleHttpServer {
    public static void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/server_status", new ServerStatusHandler());
        server.setExecutor(null);
        server.start();
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            path = path.equals("/") ? "/index.html" : path;
            
            // 从resources目录读取文件
            String filePath = "src/main/resources/static" + path;
            File file = new File(filePath);
            
            if (file.exists()) {
                String contentType = getContentType(path);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, file.length());
                
                try (OutputStream os = exchange.getResponseBody();
                    FileInputStream fs = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = fs.read(buffer)) != -1) {
                        os.write(buffer, 0, count);
                    }
                }
            } else {
                String response = "404 File Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "text/javascript";
            return "text/plain";
        }
    }

    static class ServerStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    // 创建状态响应数据
                    Map<String, Object> status = new HashMap<>();
                    status.put("ipAddress", InetAddress.getLocalHost().getHostAddress());
                    status.put("port", Server.WEBSOCKET_PORT);
                    status.put("onlineCount", Server.getOnlineCount());
                    status.put("onlineUsers", Server.getOnlineUsers());
                    
                    // 获取所有聊天记录
                    List<Map<String, String>> chatLogs = new ArrayList<>();
                    List<Server.LogEntry> logs = Server.getServerLogs();
                    for (Server.LogEntry log : logs) {
                        Map<String, String> logMap = new HashMap<>();
                        logMap.put("timestamp", log.timestamp);
                        logMap.put("message", log.message);
                        logMap.put("type", log.type);
                        chatLogs.add(logMap);
                    }
                    status.put("logs", chatLogs);
                    
                    String response = new Gson().toJson(status);
                    
                    // 设置CORS和Content-Type头
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    
                    // 发送响应
                    exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes("UTF-8"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String error = "{\"error\": \"Internal Server Error\"}";
                    exchange.sendResponseHeaders(500, error.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(error.getBytes());
                    }
                }
            }
        }
    }
} 