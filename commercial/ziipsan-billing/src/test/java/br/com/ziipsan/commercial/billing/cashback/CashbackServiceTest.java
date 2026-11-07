package br.com.ziipsan.commercial.billing.cashback;

import br.com.ziipsan.commercial.billing.parameters.TaxProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concessão do cashback (P2.34) com a loja em memória: os três casos
 * exigidos pelo aceite — domicílio <b>elegível</b>, <b>não elegível</b> e
 * <b>elegível com duas unidades</b> (devolução única) — mais o perfil
 * desligado, a vigência sem devolução, o acesso registrado (G2) e o bloqueio
 * da ativação do {@code tax_event_date} sem a conferência da LC 227/2026.
 *
 * <p>Valores à mão: período 01/2029, CBS 30,00 e IBS 10,00 → devolução
 * 30,00 + 2,00 = <b>32,00</b>. O resolvedor de {@code tax_event_date} dos
 * casos felizes é um <b>dublê de teste</b> que devolve o fim do período —
 * ele prova a fiação (data separada gravada na devolução), <b>não</b> é a
 * regra da LC 227/2026, cujo texto aguarda conferência.
 */
class CashbackServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final String DOMICILIO = "a".repeat(64);
    private static final YearMonth PERIODO = YearMonth.of(2029, 1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2029-02-10T12:00:00Z"), ZoneOffset.UTC);

    /** Resolvedor dublê: fim do período (só fiação; NÃO é a regra legal). */
    private static final TaxEventDateResolver RESOLVEDOR_DUBLE =
            (regra, de, ate) -> ate;

    private static final TaxProfile PERFIL_LIGADO = new TaxProfile(true,
            TaxProfile.TaxEventDateRule.CONTINUOUS_OPERATION,
            TaxProfile.SplitPaymentMode.SEGREGATED_AT_SETTLEMENT);

    /** Loja em memória fiel à porta: devolução única por (domicílio, período) e acessos contados. */
    static final class LojaMemoria implements CashbackStore {
        EntradaLista entrada;
        final List<DevolucaoRegistrada> devolucoes = new ArrayList<>();
        final List<String> acessos = new ArrayList<>();
        int salvamentos;

        @Override
        public Optional<EntradaLista> buscarEntrada(UUID tenantId, String hash, YearMonth periodo) {
            return Optional.ofNullable(entrada)
                    .filter(e -> e.domicileKeyHash().equals(hash) && e.assessmentMonth().equals(periodo));
        }

        @Override
        public Optional<DevolucaoRegistrada> buscarDevolucao(UUID tenantId, String hash, YearMonth periodo) {
            return devolucoes.stream()
                    .filter(d -> d.domicileKeyHash().equals(hash) && d.assessmentMonth().equals(periodo))
                    .findFirst();
        }

        @Override
        public void registrarAcesso(UUID tenantId, UUID importId, String hash, UUID accessedBy,
                                    String accessType, String purpose, Instant accessedAt) {
            acessos.add(accessType + ":" + hash);
        }

        @Override
        public DevolucaoRegistrada salvarDevolucao(DevolucaoRegistrada d) {
            salvamentos++;
            DevolucaoRegistrada salva = new DevolucaoRegistrada(UUID.randomUUID(), d.tenantId(),
                    d.domicileKeyHash(), d.assessmentMonth(), d.billId(), d.billReferenceMonth(),
                    d.listImportId(), d.listEntryId(),
                    d.cbsTaxAmount(), d.ibsTaxAmount(), d.cbsReturnAmount(), d.ibsReturnAmount(),
                    d.totalReturnAmount(), d.taxEventDate());
            devolucoes.add(salva);
            return salva;
        }
    }

    private static CashbackService.ComandoCashback comando(UUID unidade, UUID conta) {
        return new CashbackService.ComandoCashback(TENANT, DOMICILIO, PERIODO, unidade, conta,
                LocalDate.of(2029, 2, 1), LocalDate.of(2029, 1, 1), LocalDate.of(2029, 1, 31),
                new BigDecimal("30.00"), new BigDecimal("10.00"), OPERADOR);
    }

    private static LojaMemoria lojaElegivel() {
        LojaMemoria loja = new LojaMemoria();
        loja.entrada = new CashbackStore.EntradaLista(UUID.randomUUID(), UUID.randomUUID(), DOMICILIO, PERIODO);
        return loja;
    }

    @Test
    @DisplayName("domicílio elegível: concede 32,00 (CBS 30,00 + IBS 2,00) e grava o tax_event_date separado")
    void eligibleHousehold() {
        LojaMemoria loja = lojaElegivel();
        CashbackService service = new CashbackService(CLOCK, loja, RESOLVEDOR_DUBLE);

        var r = service.conceder(PERFIL_LIGADO, comando(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(r.status()).isEqualTo(CashbackService.Status.CONCEDIDO);
        assertThat(r.devolucao().totalReturnAmount()).isEqualByComparingTo("32.00");
        assertThat(r.devolucao().cbsReturnAmount()).isEqualByComparingTo("30.00");
        assertThat(r.devolucao().ibsReturnAmount()).isEqualByComparingTo("2.00");
        assertThat(r.devolucao().taxEventDate()).isEqualTo(LocalDate.of(2029, 1, 31));
        // A devolução carrega a importação/entrada que fundamentam a elegibilidade (NOT NULL na V4_12).
        assertThat(r.devolucao().listImportId()).isEqualTo(loja.entrada.importId());
        assertThat(r.devolucao().listEntryId()).isEqualTo(loja.entrada.entryId());
        assertThat(loja.salvamentos).isEqualTo(1);
        assertThat(loja.acessos).hasSize(1);
    }

    @Test
    @DisplayName("domicílio não elegível: NAO_ELEGIVEL, nada é gravado e o acesso é registrado mesmo assim (G2)")
    void notEligible() {
        LojaMemoria loja = new LojaMemoria();
        CashbackService service = new CashbackService(CLOCK, loja, RESOLVEDOR_DUBLE);

        var r = service.conceder(PERFIL_LIGADO, comando(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(r.status()).isEqualTo(CashbackService.Status.NAO_ELEGIVEL);
        assertThat(r.devolucao()).isNull();
        assertThat(loja.salvamentos).isZero();
        assertThat(loja.acessos).hasSize(1);
    }

    @Test
    @DisplayName("elegível com DUAS unidades: uma única devolução no período — a segunda unidade recebe a existente")
    void eligibleWithTwoUnitsSingleReturn() {
        LojaMemoria loja = lojaElegivel();
        CashbackService service = new CashbackService(CLOCK, loja, RESOLVEDOR_DUBLE);
        UUID unidadeA = UUID.randomUUID();
        UUID unidadeB = UUID.randomUUID();
        UUID contaA = UUID.randomUUID();
        UUID contaB = UUID.randomUUID();

        var primeira = service.conceder(PERFIL_LIGADO, comando(unidadeA, contaA));
        var segunda = service.conceder(PERFIL_LIGADO, comando(unidadeB, contaB));

        assertThat(primeira.status()).isEqualTo(CashbackService.Status.CONCEDIDO);
        assertThat(segunda.status()).isEqualTo(CashbackService.Status.JA_DEVOLVIDO);
        assertThat(segunda.devolucao().id()).isEqualTo(primeira.devolucao().id());
        assertThat(segunda.devolucao().billId()).isEqualTo(contaA);
        assertThat(loja.salvamentos).isEqualTo(1);
        assertThat(loja.devolucoes).hasSize(1);
    }

    @Test
    @DisplayName("perfil com cashback desligado: NAO_HABILITADO, sem consulta à lista e sem gravação")
    void disabledByProfile() {
        LojaMemoria loja = lojaElegivel();
        CashbackService service = new CashbackService(CLOCK, loja, RESOLVEDOR_DUBLE);
        TaxProfile desligado = new TaxProfile(false, TaxProfile.TaxEventDateRule.CONTINUOUS_OPERATION,
                TaxProfile.SplitPaymentMode.NO_SEGREGATION);

        var r = service.conceder(desligado, comando(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(r.status()).isEqualTo(CashbackService.Status.NAO_HABILITADO);
        assertThat(loja.salvamentos).isZero();
        assertThat(loja.acessos).isEmpty();
    }

    @Test
    @DisplayName("período antes das vigências (12/2026): SEM_DEVOLUCAO_NA_VIGENCIA e nada é gravado")
    void beforeVigencias() {
        LojaMemoria loja = new LojaMemoria();
        YearMonth periodo = YearMonth.of(2026, 12);
        loja.entrada = new CashbackStore.EntradaLista(UUID.randomUUID(), UUID.randomUUID(), DOMICILIO, periodo);
        CashbackService service = new CashbackService(CLOCK, loja, RESOLVEDOR_DUBLE);
        var cmd = new CashbackService.ComandoCashback(TENANT, DOMICILIO, periodo, UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31), new BigDecimal("30.00"), new BigDecimal("10.00"), OPERADOR);

        var r = service.conceder(PERFIL_LIGADO, cmd);

        assertThat(r.status()).isEqualTo(CashbackService.Status.SEM_DEVOLUCAO_NA_VIGENCIA);
        assertThat(loja.salvamentos).isZero();
    }

    @Test
    @DisplayName("ativação pendente: sem a conferência da LC 227/2026, a concessão é recusada e NADA é gravado")
    void pendingTaxEventActivationBlocksGrant() {
        LojaMemoria loja = lojaElegivel();
        CashbackService service = new CashbackService(CLOCK, loja, new TaxEventDateResolver.PendingConfirmation());

        assertThatThrownBy(() -> service.conceder(PERFIL_LIGADO, comando(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(TaxEventDateResolver.PendingConfirmation.MENSAGEM);
        assertThat(loja.salvamentos).isZero();
        assertThat(loja.devolucoes).isEmpty();
        // G2 também no caminho de erro: a consulta à lista ocorreu e o acesso foi registrado antes da recusa.
        assertThat(loja.acessos).hasSize(1);
    }
}
