package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.ContactFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.CustomerFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.EconomyFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.LinkFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.UnitFact;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Massa sintetica da P3.32: fabrica de fatos LIMPOS (que nao disparam regra
 * nenhuma) e do JSON completo de {@code quality.rules}; cada teste suja um
 * fato de cada vez e confere a regra correspondente.
 *
 * <p>Limiares da massa (todos declarados aqui, nenhum no codigo de producao):
 * 3 ciclos sem leitura; intervalo de verificacao de 7 anos; possivel obito
 * aos 100 anos; janela de nao entrega de 90 dias; area minima de 20 m2
 * (RESIDENTIAL) e 30 m2 (COMMERCIAL) por economia; divisao CNAE 47 so
 * COMMERCIAL, divisao 10 INDUSTRIAL ou COMMERCIAL.
 */
final class QualityFixture {

    static final LocalDate REF = LocalDate.of(2026, 11, 7);

    static final String FULL_RULES_JSON = """
            {
              "rules": {
                "UNIT_WITHOUT_READING": {"thresholds": {"cyclesWithoutReading": 3}},
                "CONTACT_UNVERIFIED": {"severity": "LOW"}
              },
              "thresholds": {
                "meterVerificationIntervalYears": 7,
                "possiblyDeceasedAgeYears": 100,
                "undeliveredWindowDays": 90
              },
              "minAreaPerEconomyM2": {"RESIDENTIAL": 20, "COMMERCIAL": 30},
              "cnaeCompatibility": {"47": ["COMMERCIAL"], "10": ["INDUSTRIAL", "COMMERCIAL"]}
            }""";

    private QualityFixture() {
    }

    static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    static QualityParameters fullParams() {
        return QualityParameters.fromValues(Map.of(QualityParameters.RULES_KEY, FULL_RULES_JSON));
    }

    static QualityParameters paramsWithRules(String rulesJson) {
        return QualityParameters.fromValues(Map.of(QualityParameters.RULES_KEY, rulesJson));
    }

    /** Cliente limpo: documento presente e valido, validacao REGULAR, nascido em 1990. */
    static CustomerFact customer(UUID id) {
        return new CustomerFact(id, "PF", true, "hash-" + id, false, Boolean.TRUE,
                "REGULAR", LocalDate.of(1990, 1, 1), false);
    }

    /** Unidade limpa: ACTIVE, com coordenada propria e do endereco, sem CNAE nem area. */
    static UnitFact unit(UUID id) {
        return new UnitFact(id, "ACTIVE", null, null, null, null, id(900_000 + Math.abs(id.hashCode() % 10_000)),
                true, Boolean.TRUE, null, null, false);
    }

    static LinkFact userLink(UUID unitId, UUID customerId) {
        return new LinkFact(unitId, customerId, "USER", true);
    }

    static ContactFact verifiedContact(UUID customerId) {
        return new ContactFact(customerId, "VERIFIED", null, null);
    }

    static EconomyFact economies(UUID unitId, String regulatoryClass, int count) {
        return new EconomyFact(unitId, regulatoryClass, count, true);
    }

    /** Achados de um codigo so, para o teste conferir a regra isolada na massa. */
    static List<QualityFinding> only(CadastralQualityEngine.EvaluationResult result,
                                     QualityRuleCode code) {
        return result.findings().stream().filter(f -> f.ruleCode() == code).toList();
    }
}
