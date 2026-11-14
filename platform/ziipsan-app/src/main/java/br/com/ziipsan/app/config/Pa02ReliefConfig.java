package br.com.ziipsan.app.config;

import br.com.ziipsan.app.job.CertificateAlertScheduler;
import br.com.ziipsan.app.job.InMemoryCertificateAlertLogPort;
import br.com.ziipsan.app.job.RegistryCertificateSourcePort;
import br.com.ziipsan.commercial.billing.relief.ReliefBillingAssembly;
import br.com.ziipsan.commercial.billing.relief.RelievedVersionGenerator;
import br.com.ziipsan.commercial.fiscal.relief.CancellationDeadlineGuard;
import br.com.ziipsan.commercial.fiscal.relief.CertificateAlertPlanner;
import br.com.ziipsan.commercial.fiscal.relief.ContingencyTypeRegistry;
import br.com.ziipsan.commercial.fiscal.relief.DanfagLayoutRegistry;
import br.com.ziipsan.commercial.fiscal.relief.FiscalCodeRegistry;
import br.com.ziipsan.commercial.fiscal.relief.FiscalConsumptionLinkService;
import br.com.ziipsan.commercial.fiscal.relief.SubstitutionLinkService;
import br.com.ziipsan.commercial.fiscal.relief.SubstitutionTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fiacao do PA-02 no ziipsan-app (anti-P-ACCESSCONTROL-01: modelo sem bean =
 * funcao morta). Os pacotes billing/fiscal NAO estao no scanBasePackages da
 * aplicacao, por isso cada servico novo do PA-02 entra aqui como bean
 * explicito, com teste de fiacao/contexto proprio
 * ({@code Pa02WiringContextTest}).
 *
 * <p><b>Correccao R-03 (REVISAO-PA-02):</b> a promessa desta config passa a
 * bater com o codigo — TODOS os servicos do pacote tem bean:
 * gerador + ledger, montagem viva de faturacao
 * ({@link ReliefBillingAssembly}), guarda de prazo de cancelamento,
 * servicos de vinculos (consumo/categoria e substituicao), registos
 * fiscais (CFOP, NCM, contingencia, DANFAG), planeador de alertas e as
 * DUAS portas do {@link CertificateAlertScheduler} — com
 * {@code ziipsan.fiscal.certificate-alert.enabled=true} o scheduler
 * constroi-se a partir do contexto (provado por teste de contexto
 * verdadeiro, nao construindo o scheduler a mao).
 */
@Configuration
public class Pa02ReliefConfig {

    @Bean
    public RelievedVersionGenerator.GenerationLedger pa02GenerationLedger() {
        return new RelievedVersionGenerator.GenerationLedger();
    }

    @Bean
    public RelievedVersionGenerator pa02RelievedVersionGenerator(
            RelievedVersionGenerator.GenerationLedger ledger) {
        return new RelievedVersionGenerator(ledger);
    }

    /** Montagem viva do schedule com desoneracao (seleccao -> resolucao -> guarda). */
    @Bean
    public ReliefBillingAssembly pa02ReliefBillingAssembly() {
        return new ReliefBillingAssembly();
    }

    @Bean
    public CertificateAlertPlanner pa02CertificateAlertPlanner() {
        return new CertificateAlertPlanner();
    }

    @Bean
    public SubstitutionTypes pa02SubstitutionTypes() {
        return new SubstitutionTypes();
    }

    @Bean
    public CancellationDeadlineGuard pa02CancellationDeadlineGuard() {
        return new CancellationDeadlineGuard();
    }

    @Bean
    public FiscalConsumptionLinkService pa02FiscalConsumptionLinkService() {
        return new FiscalConsumptionLinkService();
    }

    @Bean
    public SubstitutionLinkService pa02SubstitutionLinkService(SubstitutionTypes types) {
        return new SubstitutionLinkService(types);
    }

    @Bean(name = "pa02CfopCodeRegistry")
    public FiscalCodeRegistry pa02CfopCodeRegistry() {
        return new FiscalCodeRegistry(FiscalCodeRegistry.Kind.CFOP);
    }

    @Bean(name = "pa02NcmCodeRegistry")
    public FiscalCodeRegistry pa02NcmCodeRegistry() {
        return new FiscalCodeRegistry(FiscalCodeRegistry.Kind.NCM);
    }

    @Bean
    public ContingencyTypeRegistry pa02ContingencyTypeRegistry() {
        return new ContingencyTypeRegistry();
    }

    @Bean
    public DanfagLayoutRegistry pa02DanfagLayoutRegistry() {
        return new DanfagLayoutRegistry();
    }

    /** Porta de origem do scheduler: registo de certificados de referencia. */
    @Bean
    public RegistryCertificateSourcePort pa02CertificateSourcePort() {
        return new RegistryCertificateSourcePort();
    }

    /** Porta de registo/despacho do scheduler: idempotencia (certificado, limiar, dia). */
    @Bean
    public InMemoryCertificateAlertLogPort pa02CertificateAlertLogPort() {
        return new InMemoryCertificateAlertLogPort();
    }
}
