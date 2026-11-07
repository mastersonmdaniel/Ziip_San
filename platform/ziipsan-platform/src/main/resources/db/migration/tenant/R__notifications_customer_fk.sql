-- Repetível que completa as FKs para customers das tabelas da P3.36
-- (notifications e notification_preferences, V1_15).
--
-- Por que uma repetível para a FK de uma migration versionada: a história do tenant é
-- única e ordenada por versão, logo a V1_15 (1.15) roda ANTES da V3_1 do registry (3.1),
-- que cria customers — numa instalação fresca o bloco condicional da V1_15 pula (a tabela
-- ainda não existe) e é esta repetível, que roda depois de TODAS as versionadas, quem
-- cria as FKs quando customers já existe. Num tenant em upgrade, onde a V1_15 encontrou
-- customers pronta, as FKs já nasceram lá e os blocos abaixo são no-op (duplicate_object).
-- Nos dois caminhos, um tenant totalmente migrado termina com as FKs aplicadas.
-- Precedente direto: R__service_protocol_order_fk.sql (P1.36), que fecha o mesmo tipo de
-- lacuna de ordem entre a V8_1 e a V9_1.
--
-- A guarda é a da integração P1-C (a mesma da V1_15 corrigida e da V8_1): não basta
-- customers existir — exige o UNIQUE (tenant_id, id) via pg_constraint, porque num
-- classpath parcial a tabela pode existir sem essa chave e o ALTER quebraria (42830).
-- Este arquivo NÃO cria nenhum outro objeto: só fecha a lacuna de ordem.
DO $$
BEGIN
    IF to_regclass('notifications') IS NOT NULL
        AND to_regclass('notification_preferences') IS NOT NULL
        AND EXISTS (
            SELECT 1 FROM pg_constraint c
            WHERE c.conrelid = to_regclass('customers')
              AND c.contype IN ('u', 'p')
              AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
                   FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
                  = ARRAY['tenant_id', 'id']
        ) THEN
        BEGIN
            ALTER TABLE notifications ADD CONSTRAINT fk_notifications_customer
                FOREIGN KEY (tenant_id, recipient_customer_id) REFERENCES customers (tenant_id, id);
        EXCEPTION WHEN duplicate_object THEN
            NULL;
        END;
        BEGIN
            ALTER TABLE notification_preferences ADD CONSTRAINT fk_notification_prefs_customer
                FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id);
        EXCEPTION WHEN duplicate_object THEN
            NULL;
        END;
    END IF;
END $$;
