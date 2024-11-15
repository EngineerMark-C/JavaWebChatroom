//import java.io.*;
//import java.net.*;
//import java.nio.charset.StandardCharsets;
//import java.sql.*;
//import com.google.gson.Gson;
//
//public class Server {
//    public static void main(String[] args) throws IOException {
//        int port = 8000;
//        ServerSocket serverSocket = new ServerSocket(port);
//        System.out.println("Server is listening on port " + port);
//
//        try {
//            while (true) {
//                Socket socket = serverSocket.accept();
//                try {
//                    handleRequest(socket);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                    socket.close();
//                }
//            }
//        } finally {
//            serverSocket.close();
//        }
//    }
//
//    private static void handleRequest(Socket socket) throws IOException, SQLException {
//        // Read the request
//        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//        String requestLine = in.readLine();
//        // Assuming the request is a simple POST with JSON data
//        String[] requestParts = requestLine.split(" ");
//        String method = requestParts[0];
//        String path = requestParts[1];
//
//        // Only handle POST requests to /message
//        if ("POST".equals(method) && "/message".equals(path)) {
//            // Read the request body
//            StringBuilder body = new StringBuilder();
//            String line;
//            while (!(line = in.readLine()).isEmpty()) {
//                body.append(line);
//            }
//            // Parse JSON
//            Gson gson = new Gson();
//            Message message = gson.fromJson(body.toString(), Message.class);
//            // Store in database
//            storeMessageInDatabase(message);
//            // Send response
//            String response = "Message received";
//            sendResponse(socket, response);
//        }
//    }
//
//    private static void storeMessageInDatabase(Message message) throws SQLException {
//        // JDBC connection code
//        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatdata", "root", "200409028");
//        String sql = "INSERT INTO messages (content, recipient) VALUES (?, ?)";
//        PreparedStatement pstmt = conn.prepareStatement(sql);
//        pstmt.setString(1, message.getContent());
//        pstmt.setString(2, message.getRecipient());
//        pstmt.executeUpdate();
//        pstmt.close();
//        conn.close();
//    }
//
//    private static void sendResponse(Socket socket, String response) throws IOException {
//        // Write the response
//        OutputStream out = socket.getOutputStream();
//        String httpResponse = "HTTP/1.1 200 OK\r\nContent-Length: " + response.length() + "\r\n\r\n" + response;
//        out.write(httpResponse.getBytes(StandardCharsets.UTF_8));
//        out.flush();
//    }
//
//    static class Message {
//        private String content;
//        private String recipient;
//
//        // Getters and setters
//    }
//}
