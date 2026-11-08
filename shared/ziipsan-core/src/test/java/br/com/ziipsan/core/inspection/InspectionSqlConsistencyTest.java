package br.com.ziipsan.core.inspection;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consistencia SQL x codigo da ED-24: o SQL pendente declara as tabelas e os
 * CHECKs com os mesmos valores dos enums (mesmo desenho dos testes de
 * consistencia de migration das frentes vizinhas). Resolve o arquivo
 * subindo a partir do diretorio de trabalho ate achar o modulo da entrega.
 */
class InspectionSqlConsistencyTest {

    private static String sql() throws Exception {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("src/main/resources/db/pendente/ED-24__fraud_inspection.sql");
            if (Files.exists(candidate)) return Files.readString(candidate);
            dir = dir.getParent();
        }
        fail("ED-24__fraud_inspection.sql nao encontrado a partir de " + Path.of("").toAbsolutePath());
        return "";
    }

    @Test
    void sqlDeclaresTablesAndEnumChecks() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS fraud_records"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS infraction_notices"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS inspection_criterion_hits"));
        for (FraudRecord.FraudType type : FraudRecord.FraudType.values()) {
            assertTrue(sql.contains("'" + type.name() + "'"), "fraud_type sem " + type);
        }
        for (InspectionCriterion criterion : InspectionCriterion.values()) {
            assertTrue(sql.contains("'" + criterion.name() + "'"), "criterion sem " + criterion);
        }
        for (InspectionParameters.ChargeTiming timing : InspectionParameters.ChargeTiming.values()) {
            assertTrue(sql.contains("'" + timing.name() + "'"), "charge_timing sem " + timing);
        }
        for (InfractionLifecycle.NoticeStatus status : InfractionLifecycle.NoticeStatus.values()) {
            assertTrue(sql.contains("'" + status.name() + "'"), "status sem " + status);
        }
        for (InfractionLifecycle.DebitState state : InfractionLifecycle.DebitState.values()) {
            assertTrue(sql.contains("'" + state.name() + "'"), "debit_state sem " + state);
        }
        assertTrue(sql.contains("INFRACTION_NOTICE"), "tipo de documento do auto ausente no SQL");
        assertTrue(sql.contains("PENDENTE DE NUMERACAO"), "SQL deve declarar a pendencia de numeracao Flyway");
    }
}
