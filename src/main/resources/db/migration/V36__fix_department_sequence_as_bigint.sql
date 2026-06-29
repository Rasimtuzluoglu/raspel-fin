DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'department_id_seq' AND relkind = 'S') THEN
        ALTER SEQUENCE department_id_seq AS BIGINT;
    END IF;
END;
$$;
