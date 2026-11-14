package br.com.ziipsan.app;

import br.com.ziipsan.app.config.Pa02ReliefConfig;
import br.com.ziipsan.app.job.CertificateAlertScheduler;
import br.com.ziipsan.app.job.InMemoryCertificateAlertLogPort;
import br.com.ziipsan.app.job.RegistryCertificateSourcePort;
import br.com.ziipsan.commercial.billing.relief.ReliefBillingAssembly;
import br.com.ziipsan.commercial.billing.relief.ReliefProgram;
import br.com.ziipsan.commercial.billing.relief.ReliefProgramStatus;
import br.com.ziipsan.commercial.billing.relief.ReliefRate;
import br.com.ziipsan.commercial.billing.relief.ReliefRoundingPolicy;
import br.com.ziipsan.commercial.billing.relief.RelievedVersionGenerator;
import br.com.ziipsan.commercial.billing.tariff.CategoryRule;
import br.com.ziipsan.commercial.billing.tariff.TariffBlock;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;
import br.com.ziipsan.commercial.fiscal.port.CertificateVault;
import br.com.ziipsan.commercial.fiscal.relief.CancellationDeadlineGuard;
import br.com.ziipsan.commercial.fiscal.relief.CertificateAlertPlanner;
import br.com.ziipsan.commercial.fiscal.relief.ContingencyTypeRegistry;
import br.com.ziipsan.commercial.fiscal.relief.DanfagLayoutRegistry;
import br.com.ziipsan.commercial.fiscal.relief.FiscalCodeRegistry;
import br.com.ziipsan.commercial.fiscal.relief.FiscalConsumptionLinkService;
import br.com.ziipsan.commercial.fiscal.relief.SubstitutionLinkService;
import br.com.ziipsan.commercial.fiscal.relief.SubstitutionTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fiacao do PA-02 no ziipsan-app (anti-P-ACCESSCONTROL-01): cada servico do
 * pacote tem bean resolvivel no contexto — TODOS (correccao R-03). O
 * scheduler diario constroi-se A PARTIR DO CONTEXTO com
 * {@code enabled=true} (correccao R-03: nao mais construido a mao), e o
 * gerador entregue pelo contexto EXECUTA uma geracao real (correccao R-05:
 * a assercao vazia foi substituida por prova de comportamento).
 */
class Pa02WiringContextTest {

    @Test
    @DisplayName("contexto resolve TODOS os beans do PA-02 (R-03)")
    void contextResolvesAllPa02Beans() {
        try (var ctx = new AnnotationConfigApplicationContext(Pa02ReliefConfig.class)) {
            assertThat(ctx.getBean(RelievedVersionGenerator.class)).isNotNull();
            assertThat(ctx.getBean(RelievedVersionGenerator.GenerationLedger.class)).isNotNull();
            assertThat(ctx.getBean(ReliefBillingAssembly.class)).isNotNull();
            assertThat(ctx.getBean(CertificateAlertPlanner.class)).isNotNull();
            assertThat(ctx.getBean(SubstitutionTypes.class)).isNotNull();
            assertThat(ctx.getBean(CancellationDeadlineGuard.class)).isNotNull();
            assertThat(ctx.getBean(FiscalConsumptionLinkService.class)).isNotNull();
            assertThat(ctx.getBean(SubstitutionLinkService.class)).isNotNull();
            assertThat(ctx.getBean(ContingencyTypeRegistry.class)).isNotNull();
            assertThat(ctx.getBean(DanfagLayoutRegistry.class)).isNotNull();
            assertThat(ctx.getBean("pa02CfopCodeRegistry", FiscalCodeRegistry.class)).isNotNull();
            assertThat(ctx.getBean("pa02NcmCodeRegistry", FiscalCodeRegistry.class)).isNotNull();
            assertThat(ctx.getBean(RegistryCertificateSourcePort.class)).isNotNull();
            assertThat(ctx.getBean(InMemoryCertificateAlertLogPort.class)).isNotNull();
        }
    }

    @Test
    @DisplayName("R-05: o gerador do contexto EXECUTA — versao desonerada real (30,0000 -> 27,0000 a 10%)")
    void contextGeneratorReallyGenerates() {
        try (var ctx = new AnnotationConfigApplicationContext(Pa02ReliefConfig.class)) {
            var generator = ctx.getBean(RelievedVersionGenerator.class);
            UUID programId = UUID.randomUUID();
            ReliefProgram program = new ReliefProgram(programId, "DESON-2027", "fic", null,
                    "ato ficticio", LocalDate.of(2027, 1, 1), ReliefProgramStatus.ACTIVE, null,
                    ReliefRoundingPolicy.tenantDefault(), null, null, "Reguladora ficticia",
                    "Reserva ficticia", null, null);
            TariffVersion source = new TariffVersion(UUID.randomUUID(), LocalDate.of(2026, 1, 1),
                    null, List.of(CategoryRule.forCategory(UUID.randomUUID(), 10,
                            new BigDecimal("30.0000"), null,
                            List.of(new TariffBlock(20, new BigDecimal("4.0000")),
                                    new TariffBlock(null, new BigDecimal("9.0000"))))), null);
            ReliefRate rate = new ReliefRate(UUID.randomUUID(), programId,
                    ReliefRate.ScopeKind.TENANT, null, new BigDecimal("10"),
                    "apuracao ficticia", LocalDate.of(2026, 1, 1), null);
            var result = generator.generateTariffVersion(program, source, rate,
                    UUID.randomUUID(), UUID.randomUUID(), 4);
            assertThat(result.reused()).isFalse();
            assertThat(result.relievedVersion().rules().get(0).minCharge())
                    .isEqualByComparingTo("27.0000");
            assertThat(result.relievedVersion().rules().get(0).blocks().get(0).pricePerM3())
                    .isEqualByComparingTo("3.6000");
            assertThat(result.link().factor()).isEqualByComparingTo("0.900000");
            // Idempotencia pelo ledger DO CONTEXTO: segunda geracao reutiliza a ligacao.
            var again = generator.generateTariffVersion(program, source, rate,
                    UUID.randomUUID(), UUID.randomUUID(), 4);
            assertThat(again.reused()).isTrue();
            assertThat(again.link()).isEqualTo(result.link());
        }
    }

    @Test
    @DisplayName("R-03: com enabled=true o scheduler constroi-se DO CONTEXTO e o job corre pelas portas-bean (idempotente)")
    void schedulerBuildsFromRealContextWhenEnabled() {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("ziipsan.fiscal.certificate-alert.enabled", "true")));
        ctx.register(Pa02ReliefConfig.class, CertificateAlertScheduler.class);
        ctx.refresh();
        try (ctx) {
            var scheduler = ctx.getBean(CertificateAlertScheduler.class);
            assertThat(scheduler).isNotNull();
            // Certificado a 10 dias do vencimento, registado na porta-bean de origem.
            var source = ctx.getBean(RegistryCertificateSourcePort.class);
            UUID tenant = UUID.randomUUID();
            source.register(new CertificateAlertPlanner.TenantCertificate(tenant,
                    new CertificateVault.CertificateRef(UUID.randomUUID(), UUID.randomUUID(),
                            "thumbprint-ficticio", "vault:ficticio", "RSA",
                            OffsetDateTime.now().plusDays(10))));
            scheduler.runDaily();
            var log = ctx.getBean(InMemoryCertificateAlertLogPort.class);
            assertThat(log.dispatched()).hasSize(1);
            assertThat(log.dispatched().get(0).tenantId()).isEqualTo(tenant);
            // Segunda corrida no mesmo dia: idempotencia (certificado, limiar, dia).
            scheduler.runDaily();
            assertThat(log.dispatched()).hasSize(1);
        }
    }

    @Test
    @DisplayName("R-03: sem a propriedade, o scheduler NAO existe no contexto (desligado por omissao)")
    void schedulerAbsentWhenDisabled() {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.register(Pa02ReliefConfig.class, CertificateAlertScheduler.class);
        ctx.refresh();
        try (ctx) {
            assertThatThrownBy(() -> ctx.getBean(CertificateAlertScheduler.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }
}
