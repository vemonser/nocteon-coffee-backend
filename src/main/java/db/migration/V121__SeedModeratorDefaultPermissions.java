package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V121__SeedModeratorDefaultPermissions extends BaseJavaMigration {

    private static final String[] MODERATOR_DEFAULTS = new String[] {
            "dashboard:read",
            "order:read",
            "cart:read_all",
            "review:read",
            "review:moderate",
            "contact:read",
            "contact:update",
            "notification:read",
            "notification:update",
            "product:read"
    };

    @Override
    public void migrate(Context context) throws Exception {
        Connection con = context.getConnection();
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS roles (" +
                    "name VARCHAR(64) PRIMARY KEY, " +
                    "description VARCHAR(255), " +
                    "is_system BOOLEAN NOT NULL DEFAULT FALSE" +
                    ")");
            st.execute("CREATE TABLE IF NOT EXISTS role_permissions (" +
                    "role_name VARCHAR(64) NOT NULL REFERENCES roles(name) ON DELETE CASCADE, " +
                    "permission VARCHAR(64) NOT NULL, " +
                    "PRIMARY KEY (role_name, permission)" +
                    ")");
        }

        // Ensure the MODERATOR role row exists.
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO roles (name, description, is_system) VALUES (?, ?, ?) ON CONFLICT (name) DO NOTHING")) {
            ps.setString(1, "MODERATOR");
            ps.setString(2, "MODERATOR");
            ps.setBoolean(3, true);
            ps.executeUpdate();
        }

        // Count existing permissions for MODERATOR.
        long count = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM role_permissions WHERE role_name = ?")) {
            ps.setString(1, "MODERATOR");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getLong(1);
                }
            }
        }

        // Only insert defaults when the role has zero permissions (idempotent).
        if (count == 0) {
            for (String perm : MODERATOR_DEFAULTS) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO role_permissions (role_name, permission) VALUES (?, ?) ON CONFLICT (role_name, permission) DO NOTHING")) {
                    ps.setString(1, "MODERATOR");
                    ps.setString(2, perm);
                    ps.executeUpdate();
                }
            }
        }
    }
}
