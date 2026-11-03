package br.com.ziipsan.app.job;

import br.com.ziipsan.commercial.registry.service.CustomerDocumentService;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.service.MtrDocumentProtection;
import br.com.ziipsan.security.crypto.FieldProtector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Job da P0.12: recalcula em lote as máscaras já gravadas com o teto vigente (ADR-0008),
 * decifrando e regravando os campos {@code *_masked} de clientes (via
 * {@link CustomerDocumentService}) e do MTR (CPF do motorista e documento do gerador, via
 * {@link MtrDocumentProtection}). A lista de motoristas do transportador não tem coluna de
 * máscara (só cifra) e não entra no recálculo.
 *
 * <p>Disparo: perfil {@code mask-recalculation} (ver {@link MaskRecalculationRunner}), que
 * resolve o tenant, roda este job e encerra o processo — o mesmo padrão do job
 * {@code migrate}. Não roda na subida da API: decifrar o cadastro inteiro a cada boot
 * seria custo e risco sem necessidade. Depois da execução, a varredura do teste
 * {@code MaskRecalculationJobTest} prova que nenhuma máscara gravada ficou acima do teto.
 *
 * <p>O tenant vem do {@link TenantContextHolder}, como numa requisição: o
 * {@code MultiTenantConnectionProvider} resolve o schema a partir dele, então o job lê e
 * grava só no tenant pedido.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaskRecalculationJob {

    private final CustomerDocumentService customers;
    private final FieldProtector protector;

    @PersistenceContext
    private EntityManager em;

    /** Totais do job, por tabela. */
    public record Report(int customersExamined, int customersChanged,
                         int mtrExamined, int mtrChanged,
                         int generatorsExamined, int generatorsChanged) {
    }

    @Transactional
    public Report run() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("job de recálculo sem tenant no contexto");
        }
        var customerResult = customers.recalculateMasks();

        MtrDocumentProtection protection = new MtrDocumentProtection(protector);
        List<WasteMtr> mtrs = em.createQuery(
                        "SELECT m FROM WasteMtr m WHERE m.tenantId = :t AND m.driverCpfCiphertext IS NOT NULL",
                        WasteMtr.class)
                .setParameter("t", tenantId).getResultList();
        int mtrChanged = 0;
        for (WasteMtr mtr : mtrs) {
            if (protection.recalculateDriverCpfMask(mtr)) {
                mtrChanged++;
            }
        }

        List<WasteGenerator> generators = em.createQuery(
                        "SELECT g FROM WasteGenerator g WHERE g.tenantId = :t AND g.documentCiphertext IS NOT NULL",
                        WasteGenerator.class)
                .setParameter("t", tenantId).getResultList();
        int generatorsChanged = 0;
        for (WasteGenerator generator : generators) {
            if (protection.recalculateGeneratorDocumentMask(generator)) {
                generatorsChanged++;
            }
        }

        Report report = new Report(customerResult.examined(), customerResult.changed(),
                mtrs.size(), mtrChanged, generators.size(), generatorsChanged);
        log.info("Recálculo de máscaras concluído: {}", report);
        return report;
    }
}
