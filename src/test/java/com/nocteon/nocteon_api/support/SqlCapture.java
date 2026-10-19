package com.nocteon.nocteon_api.support;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Test-only {@link StatementInspector} that records every SQL statement Hibernate executes
 * into a thread-local list, so tests can assert on the generated SQL instead of on the
 * (indistinguishable) response body.
 *
 * <p>Registered globally via
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}; capture is
 * toggled per test with {@link #start()} / {@link #stop()} so parallel tests do not
 * interfere.
 */
public class SqlCapture implements StatementInspector {

    private static final ThreadLocal<List<String>> CAPTURED = new ThreadLocal<>();

    public static void start() {
        CAPTURED.set(new ArrayList<>());
    }

    public static List<String> stop() {
        List<String> captured = CAPTURED.get();
        CAPTURED.remove();
        return captured == null ? List.of() : captured;
    }

    @Override
    public String inspect(String sql) {
        List<String> captured = CAPTURED.get();
        if (captured != null) {
            captured.add(sql);
        }
        return sql;
    }
}
