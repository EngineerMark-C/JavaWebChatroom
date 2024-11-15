import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Mysql {
    // JDBC 驱动名及数据库 URL
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    static final String DB_URL = "jdbc:mysql://localhost:3306/chatdata?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // 数据库的用户名与密码
    static final String USER = "root";
    static final String PASS = "200409028";

    private Connection conn;

    // 构造方法初始化数据库连接
    public Mysql() {
        try {
            Class.forName(JDBC_DRIVER);
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            System.out.println("数据库连接成功！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 插入用户
    public boolean insert(String name, String password) {

        if (isUsernameExists(name)) {
            return false; // 用户名已存在
        }
        String sql = "INSERT INTO chatuser (name, create_time,password) VALUES (?, NOW(),?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, password);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 检查用户名是否已经存在
    private boolean isUsernameExists(String name) {
        String sql = "SELECT COUNT(*) FROM chatuser WHERE name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt(1);
                return count > 0; // 如果返回的 count 大于 0，表示用户名已经存在
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false; // 用户名不存在
    }

    // 插入消息
    public String insertMessage(String sender, String receiver, String message) {
        String sql = "INSERT INTO chat (create_time, sender, receiver, messageLen, message) VALUES (NOW(), ?, ?, ?, ?)";
        String timestamp = "";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, String.valueOf(message.length()));
            pstmt.setString(4, message);
            pstmt.executeUpdate();
            
            // 获取插入消息的时间戳
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    String timeSql = "SELECT create_time FROM chat WHERE id = LAST_INSERT_ID()";
                    try (Statement stmt = conn.createStatement();
                         ResultSet timeRs = stmt.executeQuery(timeSql)) {
                        if (timeRs.next()) {
                            timestamp = timeRs.getString(1);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return timestamp;
    }

    // 查询消息
    public void queryMessages(String receiver) {
        String sql = "SELECT create_time, sender, message FROM chat WHERE receiver = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, receiver);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String time = rs.getString("create_time");
                    String sender = rs.getString("sender");
                    String message = rs.getString("message");
                    System.out.println(time + " " + sender + ": " + message);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean validateLogin(String name, String password) {
        String sql = "SELECT COUNT(*) FROM chatuser WHERE name = ? AND password = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0; // 如果存在符合条件的记录，返回 true
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false; // 如果查询失败或用户不存在，返回 false
    }

    // 添加新方法来获取历史消息
    public List<String> getHistoryMessages(String username) {
        List<String> messages = new ArrayList<>();
        // 获取所有群聊消息和与该用户相关的私聊消息
        String sql = "SELECT create_time, sender, receiver, message FROM chat " +
                    "WHERE receiver = 'all' OR receiver = ? OR sender = ? " +
                    "ORDER BY create_time ASC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String time = rs.getString("create_time");
                    String sender = rs.getString("sender");
                    String receiver = rs.getString("receiver");
                    String message = rs.getString("message");
                    
                    if (receiver.equals("all")) {
                        messages.add(String.format("[%s] %s: %s", time, sender, message));
                    } else {
                        messages.add(String.format("[%s] %s (私聊): %s", time, sender, message));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    // 关闭数据库连接
    public void close() {
        try {
            if (conn != null) {
                conn.close();
                System.out.println("数据库连接关闭！");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
