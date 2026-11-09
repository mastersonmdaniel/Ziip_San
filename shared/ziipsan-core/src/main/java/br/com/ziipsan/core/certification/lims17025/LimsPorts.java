package br.com.ziipsan.core.certification.lims17025;

import br.com.ziipsan.core.certification.governance.SupplierRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas tipadas da ED-51 para os vizinhos — a ED-51 CONSUME por estas
 * interfaces, sem duplicar o que cada entrega ja faz:
 * <ul>
 *   <li>{@link SupplierCatalogPort} — cadastro de fornecedor da P3.31
 *       (JOR-18): laboratorio externo, provedor de ensaio de
 *       proficiencia e fornecedor de material de referencia sao LIDOS
 *       deste cadastro (registro real {@link SupplierRecord} da
 *       governanca da P3.31, tabela {@code cert_suppliers} da V21_3);
 *       nenhum cadastro paralelo de fornecedor nasce aqui;</li>
 *   <li>{@link LabReportTemplatePort} — emissao do laudo por MODELO do
 *       motor de modelos da P2.30 (JOR-06): a ED-51 entrega a chave do
 *       modelo e as linhas do conteudo; nenhum texto de laudo e
 *       redigido nem renderizado por motor proprio aqui — a
 *       renderizacao e o registro de prova sao do motor.</li>
 * </ul>
 * As amostras/resultados da ED-50 sao referenciados por id (a cadeia
 * de custodia e o resultado sao da ED-50; a ED-51 nao os duplica).
 */
public final class LimsPorts {

    private LimsPorts() {}

    /** Leitura do cadastro de fornecedor da P3.31 (JOR-18), por tenant. */
    public interface SupplierCatalogPort {
        Optional<SupplierRecord> findSupplier(UUID tenantId, UUID supplierId);
    }

    /**
     * Emissao de laudo por modelo do motor da P2.30 (JOR-06). Devolve a
     * referencia do documento gerado, registrada pelo motor (prova da
     * emissao). {@code payloadLines} sao as linhas deterministicas do
     * conteudo montadas pela ED-51 (ensaios, resultados, incertezas,
     * validacoes); a fiacao serializa e entrega ao motor.
     */
    public interface LabReportTemplatePort {
        String render(UUID tenantId, String templateKey, List<String> payloadLines);
    }
}
