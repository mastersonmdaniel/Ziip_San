package br.com.ziipsan.core.apparentloss;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-95 para as dependencias EXECUTADAS — esta entrega nao
 * duplica nenhuma delas:
 * <ul>
 *   <li><b>ED-62 / hub P3.37</b>: {@link PropertyRegistrySource} e a
 *       fonte do cadastro imobiliario municipal (CAD-103) sobre o hub
 *       de integracoes — a ED-62 entregou os conectores ERP sobre o
 *       mesmo hub; o conector municipal concreto (leiaute do ente) e
 *       fiacao da integracao, e os registros chegam a esta porta ja
 *       no retrato canonico de {@link PropertyRegistryRecord} (com
 *       base legal e finalidade declaradas — sem elas o record nem
 *       se constroi);</li>
 *   <li><b>P1.3 / P2.23 / ED-14 / ED-17</b>: {@link UnitFactsPort}
 *       monta o {@link UnitFacts} dos donos de cada fato (cadastro,
 *       ligacao/ramal, hidrometria, telemetria quando houver);</li>
 *   <li><b>ED-24 / motor de OS da P1.31</b>: {@link WorkOrderPort} e a
 *       UNICA porta de efeito do servico — abre OS de vistoria, de
 *       fiscalizacao (fluxo da ED-24) ou de recadastramento em lote,
 *       e so e chamada depois de decisao humana registrada. NAO
 *       existe porta de debito, de corte nem de infracao nesta
 *       entrega: e impossivel, por construcao da API, uma regra
 *       gerar debito ou corte (ver o teste nomeado do aceite);</li>
 *   <li><b>Registro de parametros do core</b>: {@link TenantParameters}
 *       resolve os parametros do tenant como texto cru; o parse
 *       estrito e de {@link LossParameters}.</li>
 * </ul>
 */
public final class LossPorts {

    private LossPorts() {
    }

    /** Fonte do cadastro municipal (CAD-103) sobre o hub de integracoes (ED-62/P3.37). */
    public interface PropertyRegistrySource {
        /** Registros do lote/sincronizacao identificado (ja no retrato canonico). */
        List<PropertyRegistryRecord> fetchBatch(UUID tenantId, String syncMode);
    }

    /** Retratos de unidade/ligacao montados dos donos de cada fato. */
    public interface UnitFactsPort {
        List<UnitFacts> factsForTenant(UUID tenantId);

        Optional<UnitFacts> facts(UUID tenantId, UUID unitId);
    }

    /** Tipos de OS que a decisao humana pode gerar a partir desta entrega. */
    public enum WorkOrderKind {
        /** OS de vistoria. */
        FIELD_SURVEY,
        /** OS de fiscalizacao — fluxo da ED-24 (o auto, se houver, e dela). */
        INSPECTION,
        /** OS de recadastramento em lote. */
        BATCH_RE_REGISTRATION
    }

    /**
     * Porta de OS (motor da P1.31). E a unica saida com efeito do
     * servico e so e invocada com decisao humana registrada.
     * [A DEFINIR] o codigo do tipo da OS de fiscalizacao segue a
     * proposta {@code INSPECTION} da ED-24 (pendencia O9-5 do dono).
     */
    public interface WorkOrderPort {
        UUID openWorkOrder(UUID tenantId, WorkOrderKind kind, UUID unitId, UUID propertyId, String reason);
    }

    /** Parametros declarados do tenant, como texto cru (parse em LossParameters). */
    public interface TenantParameters {
        Optional<String> resolve(UUID tenantId, String parameterName);

        default Optional<Integer> resolveInt(UUID tenantId, String parameterName) {
            return resolve(tenantId, parameterName).map(String::trim).map(Integer::valueOf);
        }
    }

    /**
     * Persistencia desta entrega (a fiacao JDBC e da integracao; aqui o contrato).
     *
     * <p>Correcao D4 (revisao da ED-95): a proveniencia da decisao viaja
     * DENTRO do modelo — {@code saveFinding} recebe o {@code Finding} ja
     * com {@code decidedBy/decidedAt} (e o {@code evaluationChannel},
     * sempre BACKOFFICE) e {@code saveSuspicion} recebe a
     * {@code Suspicion} ja com {@code decidedBy/decidedAt/
     * inspectionWorkOrderId}; o adaptador JDBC grava essas colunas dos
     * records, sem decisao "que acompanha a chamada" por fora do
     * contrato. Correcao D1: {@code suspicion(tenantId, id)} permite
     * decidir a suspeita pelo seu id.
     */
    public interface LossStore {
        void saveProperty(PropertyRegistryRecord r);

        List<PropertyRegistryRecord> properties(UUID tenantId);

        void saveFinding(CrossMatch.Finding f);

        Optional<CrossMatch.Finding> finding(UUID tenantId, UUID findingId);

        List<CrossMatch.Finding> findings(UUID tenantId);

        void saveSuspicion(FraudRules.Suspicion s);

        Optional<FraudRules.Suspicion> suspicion(UUID tenantId, UUID suspicionId);

        List<FraudRules.Suspicion> suspicions(UUID tenantId);

        void saveOutcome(UUID tenantId, InspectionAccuracy.RuleOutcome o);

        List<InspectionAccuracy.RuleOutcome> outcomes(UUID tenantId);

        void saveSelfReport(SelfReport.Case c);

        List<SelfReport.Case> selfReports(UUID tenantId);
    }
}
