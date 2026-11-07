package br.com.ziipsan.operations.metering.hydrometry;

import java.util.Set;

/**
 * Classes antigas A–D (pré-155/2022): aceitas SOMENTE na leitura do parque migrado.
 * Cadastro novo nunca nasce com classe antiga — nasce com modelo aprovado (classe de
 * exatidão 1 ou 2 + R do catálogo). A migração de dados está no SQL pendente da ED-14:
 * a classe antiga é preservada em {@code meters.legacy_metrological_class} e o hidrômetro
 * é marcado {@code migrated_park = true}; {@code metrological_class} deixa de ser gravada.
 */
public final class LegacyMetrologicalClassRule {
    private LegacyMetrologicalClassRule() {}
    public static final Set<String> LEGACY_CLASSES = Set.of("A", "B", "C", "D");

    public static boolean isLegacyClass(String value) {
        return value != null && LEGACY_CLASSES.contains(value.trim().toUpperCase());
    }

    /** Cadastro novo: classe antiga é recusada; o caminho é o modelo do catálogo. */
    public static void requireNewRegistrationFreeOfLegacyClass(String legacyClass) {
        if (isLegacyClass(legacyClass)) throw new IllegalArgumentException(
            "Classe metrológica antiga '" + legacyClass + "' não é aceita em cadastro novo (Portaria 155/2022): informe o modelo aprovado do catálogo");
    }

    /** Leitura de classe antiga só é válida para hidrômetro do parque migrado. */
    public static void requireLegacyClassOnlyOnMigratedPark(String legacyClass, boolean migratedPark) {
        if (isLegacyClass(legacyClass) && !migratedPark) throw new IllegalArgumentException(
            "Classe antiga '" + legacyClass + "' só é aceita no parque migrado (migrated_park)");
    }
}
