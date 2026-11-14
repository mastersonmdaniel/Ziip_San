package br.com.ziipsan.commercial.fiscal.relief;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deltas do insumo PA-01 fechado: (a) prazo de cancelamento NFAg pela regra
 * L11 do MOC (P-02-16 confirmada com correccao de fonte); (c) vinculo
 * consumo/categoria com guarda de completude; (d) DANFAG condicionado ao
 * MOC; (f) residuo de vencimento.
 */
class Pa01DeltasTest {

    @Test
    @DisplayName("(a) prazo de cancelamento NFAg: regra L11 do MOC — 120h apos o fim do mes da autorizacao, tolerancia 5 min")
    void cancellationDeadlineRuleL11() {
        var guard = new CancellationDeadlineGuard();
        var zone = ZoneId.of("America/Sao_Paulo");
        // Autorizada em 15/01/2027: limite = 31/01/2027 23:59:59 local + 120h = 05/02/2027 23:59:59 local.
        Instant authorized = LocalDateTime.of(2027, 1, 15, 10, 0).atZone(zone).toInstant();
        Instant limit = guard.deadlineFor(authorized, zone);
        assertThat(limit).isEqualTo(
                LocalDateTime.of(2027, 2, 5, 23, 59, 59).atZone(zone).toInstant());
        assertThat(guard.evaluate(request(authorized, zone, limit.minusSeconds(3600), null)))
                .isEqualTo(CancellationDeadlineGuard.Verdict.ALLOWED);
        // Tolerancia de 5 minutos escrita na fonte.
        assertThat(guard.evaluate(request(authorized, zone, limit.plusSeconds(4 * 60), null)))
                .isEqualTo(CancellationDeadlineGuard.Verdict.ALLOWED);
        // Fora do prazo sem liberacao do Fisco: rejeicao 220.
        assertThat(guard.evaluate(request(authorized, zone, limit.plusSeconds(6 * 60), null)))
                .isEqualTo(CancellationDeadlineGuard.Verdict.REJECTED_220_OUT_OF_DEADLINE);
        // Fora do prazo com evento de Manifestacao do Fisco referenciado: excepcao explicita.
        assertThat(guard.evaluate(request(authorized, zone, limit.plusSeconds(6 * 60), "EVT-FISCO-1")))
                .isEqualTo(CancellationDeadlineGuard.Verdict.ALLOWED_BY_FISCO_RELEASE);
        // Exclusoes da mesma tabela do evento: ja substituida (224), doc. de substituicao (524), ja cancelada (218).
        assertThat(guard.evaluate(new CancellationDeadlineGuard.CancellationRequest(
                authorized, zone, limit.minusSeconds(60), false, true, false, null)))
                .isEqualTo(CancellationDeadlineGuard.Verdict.REJECTED_224_ALREADY_SUBSTITUTED);
        assertThat(guard.evaluate(new CancellationDeadlineGuard.CancellationRequest(
                authorized, zone, limit.minusSeconds(60), false, false, true, null)))
                .isEqualTo(CancellationDeadlineGuard.Verdict.REJECTED_524_SUBSTITUTION_DOCUMENT);
        assertThat(guard.evaluate(new CancellationDeadlineGuard.CancellationRequest(
                authorized, zone, limit.minusSeconds(60), true, false, false, null)))
                .isEqualTo(CancellationDeadlineGuard.Verdict.REJECTED_218_ALREADY_CANCELLED);
        assertThat(CancellationDeadlineGuard.PRIMARY_SOURCE).contains("regra L11");
    }

    private static CancellationDeadlineGuard.CancellationRequest request(
            Instant authorized, ZoneId zone, Instant cancelAt, String fiscoRelease) {
        return new CancellationDeadlineGuard.CancellationRequest(
                authorized, zone, cancelAt, false, false, false, fiscoRelease);
    }

    @Test
    @DisplayName("(c) vinculo consumo/categoria: sem vinculo completo, emissao impedida; cobertura lista a falta")
    void consumptionCategoryLinkGuard() {
        var service = new FiscalConsumptionLinkService();
        var pair = new FiscalConsumptionLinkService.ConsumptionCategory("RESIDENCIAL", "RES");
        service.registerPair(pair);
        assertThat(service.coverageGaps()).containsExactly(pair);
        assertThatThrownBy(() -> service.requireForEmission(pair))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Emissao impedida");
        service.link(pair, new FiscalConsumptionLinkService.FiscalClassification("000", "FICTICIO-1"));
        assertThat(service.requireForEmission(pair).cClassTribCode()).isEqualTo("FICTICIO-1");
        assertThat(service.coverageGaps()).isEmpty();
    }

    @Test
    @DisplayName("(d) DANFAG: modelos de bobina do MOC Anexo II entram como referencia; dimensao fisica sem fonte continua recusada")
    void danfagRegistryRefusesSourcelessLayouts() {
        var registry = new DanfagLayoutRegistry();
        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.referenceModels()).isEmpty();
        assertThatThrownBy(() -> new DanfagLayoutRegistry.PhysicalLayout("M1", 111, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[SEM FONTE]");
        // Fonte localizada: MOC Anexo II — os 3 modelos de bobina nomeados.
        registry.registerMocCoilReferenceModels(DanfagLayoutRegistry.MOC_ANNEX_II_SOURCE);
        assertThat(registry.referenceModels()).hasSize(3);
        assertThat(registry.referenceModels())
                .extracting(DanfagLayoutRegistry.ReferenceModel::modelId)
                .containsExactly("BOBINA-OPCAO-1", "BOBINA-OPCAO-2", "BOBINA-COFATURAMENTO");
        // A especificacao fisica (largura) continua a exigir fonte declarada por modelo.
        registry.register(new DanfagLayoutRegistry.PhysicalLayout("M1", 111, "MOC ficticio de teste"));
        assertThat(registry.registered()).hasSize(1);
    }

    @Test
    @DisplayName("(f) residuo de vencimento: so a camada NFAg; parametros declarados, sem defaults")
    void dueDateResiduePolicy() {
        var policy = new DueDateChangeNfagePolicy(true, true,
                DueDateChangeNfagePolicy.DirectDebitBehavior.REQUIRE_RECONFIRMATION);
        assertThat(policy.serviceCodeConferenceRequired()).isTrue();
        assertThat(policy.directDebitBehavior())
                .isEqualTo(DueDateChangeNfagePolicy.DirectDebitBehavior.REQUIRE_RECONFIRMATION);
        assertThatThrownBy(() -> new DueDateChangeNfagePolicy(false, false, null))
                .isInstanceOf(NullPointerException.class);
    }
}
