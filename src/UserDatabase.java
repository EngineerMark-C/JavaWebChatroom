import java.sql.*;

public class UserDatabase {
    // JDBC 驱动名及数据库 URL
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    static final String DB_URL = "jdbc:mysql://localhost:3306/chatdata?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // 数据库的用户名与密码
    static final String USER = "root";
    static final String PASS = "200409028";

    private Connection conn;

    // 构造方法，用于初始化数据库连接
    public UserDatabase() {
        try {
            // 注册 JDBC 驱动
            Class.forName(JDBC_DRIVER);
            // 打开连接
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            System.out.println("数据库连接成功！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 检查用户名是否存在
    private boolean userExists(String name) {
        String sql = "SELECT COUNT(*) FROM chatuser WHERE name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // 插入数据
    public boolean insert(String name, String password) {
        if (userExists(name)) {
            System.out.println("用户名已存在，插入失败！");
            return false;
        }
        String sql = "INSERT INTO chatuser (name, password) VALUES (?, ?)";
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

    // 更新数据
    public boolean update(String name, String password) {
        if (!userExists(name)) {
            System.out.println("用户名不存在，更新失败！");
            return false;
        }
        String sql = "UPDATE chatuser SET password = ? WHERE name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, password);
            pstmt.setString(2, name);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 删除数据
    public boolean delete(String name) {
        if (!userExists(name)) {
            System.out.println("用户名不存在，删除失败！");
            return false;
        }
        String sql = "DELETE FROM chatuser WHERE name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 查询数据
    public void queryAll() {
        String sql = "SELECT id, name, password FROM chatuser";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String password = rs.getString("password");
                System.out.println("ID: " + id + ", Name: " + name + ", Password: " + password);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

    // 测试方法
    public static void main(String[] args) {
        UserDatabase db = new UserDatabase();

        // 插入数据
        System.out.println("插入数据测试：");
        db.insert("Alice", "1234");
        db.insert("Bob", "5678");
        db.insert("Alice", "9999"); // 测试重复插入

        // 查询数据
        System.out.println("当前数据库内容：");
        db.queryAll();

        // 更新数据
        System.out.println("更新数据测试：");
        db.update("Alice", "4321"); // 更新存在用户
        db.update("Charlie", "1111"); // 更新不存在用户

        // 查询更新后的数据
        System.out.println("更新后数据库内容：");
        db.queryAll();

        // 删除数据
        System.out.println("删除数据测试：");
        db.delete("Bob"); // 删除存在用户
        db.delete("Charlie"); // 删除不存在用户

        // 查询删除后的数据
        System.out.println("删除后数据库内容：");
        db.queryAll();

        // 关闭数据库
        db.close();
    }
}
