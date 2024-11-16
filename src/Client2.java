import java.io.*;
import java.net.*;
import java.awt.Desktop;

public class Client2 {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final int HTTP_PORT = 8081; // 前端服务端口

    public static void main(String[] args) {
        // 启动浏览器打开前端页面
        openWebPage();

        // 原有的客户端代码
        try (
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
        ) {
            // ... 原有代码保持不变 ...

        } catch (IOException e) {
            System.out.println("连接服务器失败: " + e.getMessage());
        }
    }

    private static void openWebPage() {
        String url = "http://localhost:" + HTTP_PORT;
        try {
            // 检查是否支持桌面操作
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                // 检查是否支持浏览器操作
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    System.out.println("正在打开浏览器...");
                    desktop.browse(new URI(url));
                    return;
                }
            }

            // 如果Desktop不支持，尝试使用操作系统特定的命令
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder builder;

            if (os.contains("win")) {
                // Windows
                builder = new ProcessBuilder("cmd", "/c", "start", url);
            } else if (os.contains("mac")) {
                // macOS
                builder = new ProcessBuilder("open", url);
            } else {
                // Linux
                builder = new ProcessBuilder("xdg-open", url);
            }

            builder.start();

        } catch (IOException | URISyntaxException e) {
            System.out.println("无法自动打开浏览器，请手动访问: " + url);
            e.printStackTrace();
        }
    }
}