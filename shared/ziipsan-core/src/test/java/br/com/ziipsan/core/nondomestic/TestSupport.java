package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.effluent.EffluentCatalog;
import br.com.ziipsan.core.effluent.EffluentStandard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Massa SINTETICA dos testes da ED-83: parametros genericos
 * PARAM_A/PARAM_B com limites arbitrarios de fixture — NAO sao
 * valores de nenhuma norma (nem da NR 11/ANA, nem da CONAMA), nem
 * padrao real de nenhum prestador. Os limites reais entram por carga
 * revisada dos catalogos (prestador sobre ED-105); percentuais,
 * tarifas, precos e taxas dos testes sao entradas de fixture
 * calculadas a mao, como nas demais entregas.
 */
final class TestSupport {

    static final UUID TENANT = UUID.fromString("83838383-8383-8383-8383-838383838383");
    static final UUID UNIT_ID = UUID.fromString("83838383-8383-8383-8383-8383838383u1".replace('u', 'a'));
    static final UUID CASUAL_UNIT_ID = UUID.fromString("83838383-8383-8383-8383-8383838383a2");
    static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    static final Instant T0 = Instant.parse("2026-03-10T08:00:00Z");

    private TestSupport() {}

    static EffluentStandard federalMax(String version, String key, String max, boolean reviewed) {
        return new EffluentStandard(key, "Parametro " + key, "u", EffluentStandard.Comparator.MAX,
                null, new BigDecimal(max), null, "NORMA-SINTETICA [FIXTURE]",
                EffluentStandard.Jurisdiction.FEDERAL, null,
                EffluentStandard.PublicationStatus.PUBLISHED, reviewed,
                reviewed ? "RT-FIXTURE" : null, JAN, null);
    }

    static EffluentCatalog catalog(String version, EffluentStandard... standards) {
        return new EffluentCatalog(version, List.of(standards));
    }

    /** Catalogo do prestador (versao PRESTADOR-1) sobre a base da ED-105 (versao BASE-1). */
    static UserStandardCatalog userCatalog(EffluentStandard providerLine, EffluentStandard baseLine) {
        EffluentCatalog provider = providerLine == null
                ? EffluentCatalog.empty("PRESTADOR-1") : catalog("PRESTADOR-1", providerLine);
        EffluentCatalog base = baseLine == null
                ? EffluentCatalog.empty("BASE-1") : catalog("BASE-1", baseLine);
        return new UserStandardCatalog("PRESTADOR-1", provider, base);
    }

    static EffluentCharacterization characterization() {
        return new EffluentCharacterization(
                List.of(new EffluentCharacterization.CharacterizedParameter(
                        "PARAM_A", new BigDecimal("8"), null, "u")),
                "PRE-TRATAMENTO DECLARADO [FIXTURE]", true, "USUARIO-1", T0);
    }

    /** Gerador autorizado de fixture: industria, rota comercial, autorizacao desde 2026-01-01 aberta. */
    static DischargerRegistration authorizedDischarger() {
        DischargerRegistration d = new DischargerRegistration(UUID.randomUUID(), TENANT, UNIT_ID,
                DischargerRegistration.Route.COMMERCIAL_UNIT, DischargerRegistration.Kind.INDUSTRY,
                null, "PONTO-1");
        d.recordCharacterization(characterization());
        d.recordCommitmentTerm("TERMO-1 [FIXTURE]");
        d.authorize("AUT-1 [FIXTURE]", JAN, null);
        return d;
    }
}
