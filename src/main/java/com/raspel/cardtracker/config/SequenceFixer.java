package com.raspel.cardtracker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SequenceFixer {

    private final JdbcTemplate jdbcTemplate;

    public SequenceFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void fixSequences() {
        try {
            jdbcTemplate.execute(
                "DO $$\n" +
                "DECLARE\n" +
                "    r RECORD;\n" +
                "BEGIN\n" +
                "    FOR r IN\n" +
                "        SELECT c.relname AS seq_name, replace(c.relname, '_id_seq', '') AS tbl_name\n" +
                "        FROM pg_class c\n" +
                "        JOIN pg_namespace n ON n.oid = c.relnamespace\n" +
                "        WHERE c.relkind = 'S' AND n.nspname = 'public'\n" +
                "    LOOP\n" +
                "        BEGIN\n" +
                "            EXECUTE format('SELECT setval(''%I'', (SELECT COALESCE(MAX(id), 0) + 1 FROM %I))', r.seq_name, r.tbl_name);\n" +
                "        EXCEPTION WHEN OTHERS THEN NULL;\n" +
                "        END;\n" +
                "    END LOOP;\n" +
                "END;\n" +
                "$$;"
            );
            log.info("Database sequences verified and fixed on startup");
        } catch (Exception e) {
            log.warn("Sequence fixer could not run: {}", e.getMessage());
        }
    }
}
