package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.common.enums.Permission;

public class V119__CreateRolesAndRolePermissions extends BaseJavaMigration {

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

        // seed roles
        for (Role r : Role.values()) {
            String name = r.name();
            String desc = name;
            boolean system = true;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO roles (name, description, is_system) VALUES (?, ?, ?) ON CONFLICT (name) DO NOTHING")) {
                ps.setString(1, name);
                ps.setString(2, desc);
                ps.setBoolean(3, system);
                ps.executeUpdate();
            }
            for (Permission p : r.getPermissions()) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO role_permissions (role_name, permission) VALUES (?, ?) ON CONFLICT (role_name, permission) DO NOTHING")) {
                    ps.setString(1, name);
                    ps.setString(2, p.getPermission());
                    ps.executeUpdate();
                }
            }
        }
    }
}
