package com.nocteon.nocteon_api.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the Flyway layout that replaced the old V1..V124 chain. The old chain had
 * deleted/renamed versions (V119/V121 survived only as Java migrations) and table
 * changes scattered outside their owning file; a duplicate or missing version makes
 * the application fail at startup, so it is checked here instead of in production.
 */
class FlywayMigrationLayoutTest {

    private static final Path MIGRATION = Path.of("src/main/resources/db/migration");
    private static final Path SEED = Path.of("src/main/resources/db/seed");
    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__([A-Za-z0-9_]+)\\.sql$");

    private record Migration(Path path, int version, String name) {
    }

    private List<Migration> read(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .map(p -> {
                        Matcher m = VERSIONED.matcher(p.getFileName().toString());
                        assertThat(m.matches())
                                .as("migration filename must be V<number>__<lower_snake>.sql: %s", p)
                                .isTrue();
                        return new Migration(p, Integer.parseInt(m.group(1)), m.group(2));
                    })
                    .toList();
        }
    }

    @Test
    void schemaAndSeedVersionsAreUnique() throws IOException {
        List<Migration> migrations = read(MIGRATION);
        List<Migration> seeds = read(SEED);

        Map<Integer, Path> seen = new HashMap<>();
        for (Migration m : Stream.concat(migrations.stream(), seeds.stream()).toList()) {
            Path previous = seen.put(m.version(), m.path());
            assertThat(previous)
                    .as("duplicate Flyway version V%d: %s and %s", m.version(), previous, m.path())
                    .isNull();
        }
    }

    @Test
    void schemaAndSeedRangesDoNotOverlap() throws IOException {
        List<Migration> migrations = read(MIGRATION);
        List<Migration> seeds = read(SEED);

        assertThat(migrations).isNotEmpty();
        assertThat(seeds).isNotEmpty();
        assertThat(migrations).allSatisfy(m -> assertThat(m.version()).isLessThan(100));
        assertThat(seeds).allSatisfy(m -> assertThat(m.version()).isGreaterThanOrEqualTo(100));
    }

    @Test
    void everyMigrationFileHasSql() throws IOException {
        for (Migration m : Stream.concat(read(MIGRATION).stream(), read(SEED).stream()).toList()) {
            String sql = Files.readString(m.path());
            assertThat(sql)
                    .as("%s must contain at least one SQL statement", m.path())
                    .contains(";");
        }
    }

    @Test
    void rolesAndPermissionsHaveAHomeInTheSchema() throws IOException {
        String auth = Files.readString(MIGRATION.resolve("V2__auth_tables.sql"));
        assertThat(auth).contains("CREATE TABLE public.roles");
        assertThat(auth).contains("CREATE TABLE public.role_permissions");

        String reference = Files.readString(MIGRATION.resolve("V11__reference_data.sql"));
        assertThat(reference).contains("INSERT INTO public.roles");
        assertThat(reference).contains("INSERT INTO public.role_permissions");
    }

    @Test
    void referenceDataSequencesArePositionedAfterTheirRows() throws IOException {
        String reference = Files.readString(MIGRATION.resolve("V11__reference_data.sql"));
        assertThat(reference)
                .as("store_settings has a seeded row with id 1; is_called must be true")
                .contains("setval('public.store_settings_id_seq', 1, true)");
        assertThat(reference).contains("setval('public.shipping_zones_id_seq', 5, true)");
    }

    @Test
    void demoSeedDataIsFollowedBySequenceResets() throws IOException {
        Path reset = SEED.resolve("V117__reset_sequences.sql");
        assertThat(reset).exists();
        String sql = Files.readString(reset);
        assertThat(sql).contains("pg_get_serial_sequence");
        assertThat(sql).contains("order_number_seq");
        assertThat(sql).contains("setval");
    }

    @Test
    void seedMigrationsDoNotManageTransactionsThemselves() throws IOException {
        for (Migration m : read(SEED)) {
            String sql = Files.readString(m.path()).toUpperCase();
            assertThat(sql)
                    .as("%s must let Flyway own the transaction", m.path())
                    .doesNotContain("BEGIN;")
                    .doesNotContain("COMMIT;");
        }
    }
}
