package br.com.ziipsan.commercial.billing.social.cycle;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * II-3 - consistencia do SQL pendente com os enums e travas do codigo
 * (o mesmo padrao do teste de SQL pendente da ED-103).
 */
class Ii3PendenteSqlConsistencyTest {

    static String sql() throws Exception {
        List<Path> candidates = List.of(
                Path.of("src/main/resources/db/pendente/II-3__social_tariff_cycle.sql"),
                Path.of("entrega/commercial/ziipsan-billing/src/main/resources/db/pendente/II-3__social_tariff_cycle.sql"),
                Path.of("../../entrega/commercial/ziipsan-billing/src/main/resources/db/pendente/II-3__social_tariff_cycle.sql"));
        for (Path p : candidates) {
            if (Files.exists(p)) {
                return Files.readString(p);
            }
        }
        String override = System.getProperty("ii3.sql.path");
        if (override != null && Files.exists(Path.of(override))) {
            return Files.readString(Path.of(override));
        }
        throw new IllegalStateException("SQL pendente da II-3 nao encontrado a partir de "
                + Path.of("").toAbsolutePath());
    }

    @Test
    void sqlIsPendenteSemFonteAndRegistersAllTables() throws Exception {
        String s = sql();
        assertTrue(s.contains("PENDENTE DE NUMERACAO"));
        assertTrue(s.contains("[SEM FONTE]"));
        assertTrue(s.contains("[A DEFINIR]"));
        for (String t : List.of("social_cycle_runs", "social_cycle_crossings",
                "social_grant_trail", "social_active_search_items")) {
            assertTrue(s.contains("CREATE TABLE IF NOT EXISTS " + t), t);
            assertTrue(s.contains("register_tenant_table('" + t + "')"), t);
        }
        // Nenhuma seed nas tabelas do pacote (o unico INSERT e o de
        // redaccao de auditoria, padrao da V4_2).
        assertFalse(s.contains("INSERT INTO social_"));
        assertFalse(s.contains("DROP TABLE"));
        assertFalse(s.contains("DELETE FROM"));
    }

    @Test
    void sqlStatusChecksMatchCodeEnumsExactly() throws Exception {
        String s = sql();
        for (SocialGrantLifecycle.GrantStatus status : SocialGrantLifecycle.GrantStatus.values()) {
            assertTrue(s.contains("'" + status.name() + "'"), "estado ausente no SQL: " + status.name());
        }
        for (SocialCycleStore.FamilyCrossingSummary.Outcome outcome :
                SocialCycleStore.FamilyCrossingSummary.Outcome.values()) {
            assertTrue(s.contains("'" + outcome.name() + "'"), "resultado ausente no SQL: " + outcome.name());
        }
        for (SocialActiveSearch.Reason reason : SocialActiveSearch.Reason.values()) {
            assertTrue(s.contains("'" + reason.name() + "'"), "motivo ausente no SQL: " + reason.name());
        }
    }

    @Test
    void trailTableCarriesNoBlindIndex() throws Exception {
        String s = sql();
        int start = s.indexOf("CREATE TABLE IF NOT EXISTS social_grant_trail");
        int end = s.indexOf("CREATE TABLE", start + 1);
        String trailBlock = s.substring(start, end < 0 ? s.length() : end);
        assertFalse(trailBlock.contains("family_code_hash"),
                "a trilha nao pode guardar indice cego (regra da V4_2)");
        assertTrue(trailBlock.contains("enrollment_id"));
        assertTrue(trailBlock.contains("sequence_no"));
    }
}
