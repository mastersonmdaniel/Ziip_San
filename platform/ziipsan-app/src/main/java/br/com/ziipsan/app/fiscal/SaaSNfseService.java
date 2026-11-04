package br.com.ziipsan.app.fiscal;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate.Environment;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuerRouter;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer.FiscalDocumentType;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.DuplicateSourceException;
import br.com.ziipsan.commercial.fiscal.port.RestrictedProductionUnavailableException;
import br.com.ziipsan.commercial.fiscal.port.SefinRestrictedClient;
import br.com.ziipsan.commercial.fiscal.port.SimulatedFiscalIssuer;
import br.com.ziipsan.commercial.fiscal.rules.DpsId;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.platform.console.PlatformConsoleService;
import br.com.ziipsan.platform.contract.SaaSContractService;
import br.com.ziipsan.platform.contract.SaaSFiscalRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * NFS-e da venda SaaS pelo motor fiscal portado (P2.13, ADR-0003, ADR-0019).
 *
 * <p>Este servico vive no modulo {@code app} porque a fronteira P0.47 proibe o modulo
 * {@code platform} de importar o motor fiscal: aqui a orquestracao pode usar
 * {@link FiscalIssuanceService} (calculo de tributos, DPS, reserva de numero, autorizacao)
 * sobre {@link PlatformFiscalDocumentRepository} (schema {@code platform}). O que o
 * servico faz, sem atalho:
 *
 * <ol>
 *   <li>carrega a fatura e o contrato (P2.12) e o tomador em
 *       {@code platform.tenant_profile} (fonte unica, DAD-32) — nunca no
 *       {@code contractor_cnpj} do contrato;</li>
 *   <li>valida o CNPJ do tomador por {@link DocumentNumber}, inclusive o alfanumerico
 *       (P1.39, RAS-03): CNPJ invalido nao emite;</li>
 *   <li>exige o contexto fiscal ativo da plataforma ({@code SaaSFiscalRegistry}): sem
 *       contexto registrado, sem emissao — nenhum dado do prestador e inventado;</li>
 *   <li>recusa HOMOLOGATION e PRODUCTION antes de qualquer escrita (falha fechada):
 *       no ambiente local so o simulador autoriza; a autorizacao na producao restrita,
 *       com o cliente mTLS real e o e-CNPJ da Ziip/TGP, e pendencia do dono (P1.8) e
 *       nunca fingida como emissao real;</li>
 *   <li>emite pelo motor (serie fiscal do contexto, numero reservado, DPS, tributos por
 *       vigencia, XML autorizado guardado com hash), congela o tomador do perfil na linha
 *       da NFS-e (o motor nao o grava) e resolve {@code saas_invoice.nfse_document_ref}
 *       com o Id da DPS, o ponto de extensao da P2.12.</li>
 * </ol>
 *
 * <p>Idempotencia: a segunda emissao da mesma fatura devolve a NFS-e autorizada existente,
 * sem consumir outro numero (unicidade de origem do motor + leitura previa). O numero da
 * fatura continua {@code FAT-<seq global>} (P2.12); a numeracao por serie fiscal vale
 * para a NFS-e, nao para a fatura.
 *
 * <p>Limites honestos: o XML emitido e o nao assinado do corpus do porte (a assinatura
 * XMLDSig chega com o cliente real, P2); o tomador entra na DPS so com o nome, como o
 * builder do porte a define — o CNPJ do tomador validado fica registrado na linha da
 * NFS-e e na auditoria, nao no XML; IBS/CBS entram como informativos (2026), como no
 * motor. Cancelamento e substituicao da NFS-e do SaaS nao entram nesta frente: a P2.14
 * usa a leitura daqui para o download, e o cancelamento volta ao dono se preciso.
 */
@Service
public class SaaSNfseService {

    private final SaaSContractService contracts;
    private final PlatformConsoleService console;
    private final SaaSFiscalRegistry registry;
    private final FiscalIssuanceService fiscal;
    private final PlatformFiscalDocumentRepository repository;

    @Autowired
    public SaaSNfseService(DataSource dataSource, SaaSContractService contracts,
                           PlatformConsoleService console, SaaSFiscalRegistry registry) {
        this(contracts, console, registry,
                new PlatformFiscalDocumentRepository(new JdbcTemplate(dataSource)));
    }

    SaaSNfseService(SaaSContractService contracts, PlatformConsoleService console,
                    SaaSFiscalRegistry registry, PlatformFiscalDocumentRepository repository) {
        this.contracts = contracts;
        this.console = console;
        this.registry = registry;
        this.repository = repository;
        this.fiscal = new FiscalIssuanceService(repository,
                new FiscalIssuerRouter(FiscalIssuanceGate.closed(),
                        new SimulatedFiscalIssuer(FiscalDocumentType.NFSE_NACIONAL),
                        new SefinRestrictedClient()),
                repository, Clock.systemUTC());
    }

    /**
     * Emite a NFS-e de uma fatura do contrato SaaS. Idempotente quando ja autorizada:
     * devolve a existente, sem novo numero.
     */
    @Transactional
    public SaaSFiscalRegistry.NfseView issueNfse(UUID invoiceId, UUID actorId, String actorRole) {
        SaaSContractService.InvoiceView invoice = contracts.invoice(invoiceId);
        if ("CANCELLED".equals(invoice.status())) {
            throw conflict("A fatura cancelada nao gera NFS-e.");
        }
        SaaSFiscalRegistry.NfseView existing = registry.nfseByInvoice(invoiceId);
        if (existing != null) {
            if ("AUTHORIZED".equals(existing.status())) {
                return existing;
            }
            throw conflict("A fatura ja tem uma NFS-e em estado " + existing.status()
                    + "; reconcilie-a antes de emitir outra.");
        }
        SaaSContractService.ContractView contract = contracts.contract(invoice.contractId());
        UUID tenantId = contract.tenantId();
        PlatformConsoleService.TenantProfile profile = console.profile(tenantId);
        String takerCnpj = DocumentNumber.normalize(profile.cnpj());
        if (!DocumentNumber.isValidCnpj(takerCnpj)) {
            throw validation("O CNPJ do tomador no perfil do tenant precisa ser valido"
                    + " (14 posicoes, com digitos verificadores).");
        }
        SaaSFiscalRegistry.ContextView context = registry.activeContext();
        if (context == null) {
            throw validation("O contexto fiscal da plataforma nao esta registrado;"
                    + " registre-o antes de emitir a NFS-e.");
        }
        Environment environment = Environment.valueOf(context.environment());
        if (environment != Environment.SIMULATED) {
            // Falha fechada antes de qualquer escrita: o motor recusaria do
            // mesmo jeito, mas aqui o erro e um problema 409, nao um 500, e
            // nenhum numero de serie e consumido.
            throw conflict("A emissao fora do simulador esta indisponivel neste ambiente"
                    + " (producao restrita pendente: cliente mTLS e e-CNPJ da Ziip/TGP).");
        }
        LocalDate competenceDate;
        try {
            competenceDate = LocalDate.parse(invoice.competence() + "-01");
        } catch (RuntimeException e) {
            throw validation("A competencia da fatura precisa estar no formato AAAA-MM.");
        }
        repository.ensureSeries(tenantId, context.id(), FiscalDocumentType.NFSE_NACIONAL.name(),
                context.series());
        FiscalIssuanceService.IssueRequest request = new FiscalIssuanceService.IssueRequest(
                tenantId, context.id(), context.cnpj(), DpsId.TYPE_CNPJ,
                FiscalDocumentType.NFSE_NACIONAL, context.series(), "SAAS_INVOICE", invoiceId,
                invoice.total(), competenceDate, environment, false, profile.legalName(), true);
        FiscalIssuanceService.IssueResult issued;
        try {
            issued = fiscal.issue(request);
        } catch (DuplicateSourceException e) {
            throw conflict("A fatura ja gerou uma NFS-e; a segunda emissao devolve a existente.");
        } catch (RestrictedProductionUnavailableException e) {
            throw conflict(e.getMessage());
        } catch (IllegalStateException e) {
            // Motor: aliquota ausente ou sobreposta, emitente inexistente,
            // autorizador sem autorizacao — nenhum documento valido sai.
            throw validation(e.getMessage());
        }
        repository.recordTaker(tenantId, issued.documentId(), profile.legalName(), takerCnpj);
        contracts.linkNfse(invoiceId, issued.dpsId(), takerCnpj, actorId, actorRole);
        SaaSFiscalRegistry.NfseView view = registry.nfseByInvoice(invoiceId);
        if (view == null) {
            throw new IllegalStateException("NFS-e emitida sem registro de leitura: " + issued.dpsId());
        }
        return view;
    }

    /** Leitura da NFS-e de uma fatura (404 quando a fatura nunca gerou NFS-e). */
    public SaaSFiscalRegistry.NfseView nfseForInvoice(UUID invoiceId) {
        contracts.invoice(invoiceId);
        SaaSFiscalRegistry.NfseView view = registry.nfseByInvoice(invoiceId);
        if (view == null) {
            throw new ApiException.NotFound("A fatura nao tem NFS-e emitida.");
        }
        return view;
    }

    private static ApiException.Validation validation(String message) {
        return new ApiException.Validation(message, List.of(), List.of());
    }

    private static ApiException.Conflict conflict(String message) {
        return new ApiException.Conflict("state-conflict", message);
    }
}
