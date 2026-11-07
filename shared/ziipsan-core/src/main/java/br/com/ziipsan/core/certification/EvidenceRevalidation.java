package br.com.ziipsan.core.certification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lista de evidencias a revalidar quando uma norma ganha edicao nova
 * (ED-76): o cruzamento do diff de clausulas ({@link ClauseMapRevision})
 * com os vinculos de evidencia dos tenants que usam a norma.
 *
 * <p>Quem entra na lista:
 * <ul>
 *   <li>clausula ALTERADA ou REMOVIDA na edicao nova: cada evidencia
 *       vinculada a ela, nos tenants cuja ativacao esta na edicao
 *       anterior, precisa ser revalidada contra a edicao nova (ou
 *       substituida, no caso da removida);</li>
 *   <li>clausula INCLUIDA: nao ha evidencia antiga para revalidar — o
 *       item nasce como LACUNA, um por tenant que usa a norma, com a
 *       lista de evidencias vazia, para o tenant saber que a edicao nova
 *       pede evidencia que ele ainda nao tem;</li>
 *   <li>tenant em outra edicao (que nao a anterior do diff) ou com outra
 *       norma: nao entra. Revalidar quem nao esta na edicao comparada
 *       geraria trabalho falso.</li>
 * </ul>
 *
 * <p>As entradas sao os vinculos que o tenant ja tem, projetados por
 * quem chama como {@link TenantClauseEvidence}. A projecao real NAO e
 * so {@code cert_clause_map} + {@code cert_evidence_link} (V21_1): o
 * {@code standardCode} e a edicao em uso so existem em
 * {@code cert_standard_activation} — a leitura junta as tres tabelas
 * ({@code cert_standard_activation} via {@code activation_id} de
 * {@code cert_clause_map}, e {@code cert_evidence_link} via
 * {@code clause_map_id}), no schema de cada tenant. Essa projecao de
 * repositorio e responsabilidade de quem chama e esta FORA do escopo
 * desta entrega (ver RELATORIO-ED-76): esta classe e a regra pura do
 * cruzamento — nao le banco, nao inventa evidencia e nao marca nada
 * como revalidado: ela produz a lista; a revalidacao em si e ato do
 * tenant.
 *
 * <p>Vinculos desativados nao entram: a coluna {@code active} de
 * {@code cert_evidence_link} (acrescentada pela V21_2) viaja em
 * {@link EvidenceRef#active()} e {@link #plan} descarta as evidencias
 * inativas dos itens — quem chama deve carregar a coluna na projecao,
 * nao filtrar por conta propria nem omiti-la.
 *
 * <p>Limitacao conhecida (achado 4 da revisao da ED-76): o conjunto de
 * tenants da edicao anterior sai apenas das linhas de {@code usages}.
 * Um tenant que usa a norma na edicao anterior mas nao tem nenhuma
 * linha projetada (sem linha de mapa/evidencia na entrada) NAO recebe
 * as lacunas de clausulas incluidas ({@code CLAUSE_ADDED}), embora o
 * aceite fale em "tenants que usam a norma" — com a entrada sendo so
 * {@code usages}, esse tenant e invisivel para a regra.
 *
 * <p>A ordem da lista e deterministica (tenant, numero de clausula,
 * motivo), para a mesma revisao gerar sempre a mesma lista e o teste
 * poder comparar listas inteiras.
 */
public final class EvidenceRevalidation {

    private EvidenceRevalidation() {
    }

    /** Por que um item esta na lista de revalidacao. */
    public enum Reason {
        /** Clausula mudou de conteudo em palavras proprias na edicao nova. */
        CLAUSE_CHANGED,
        /** Clausula nao existe mais na edicao nova. */
        CLAUSE_REMOVED,
        /** Clausula nova na edicao nova: lacuna de evidencia, nao revalidacao. */
        CLAUSE_ADDED
    }

    /**
     * Uma evidencia vinculada: o par tipo + id do {@code cert_evidence_link}
     * da V21_1, com o {@code active} do vinculo (coluna da V21_2). Vinculo
     * desativado ({@code active == false}) nao e evidencia a revalidar:
     * {@link #plan} o descarta dos itens.
     */
    public record EvidenceRef(CertificationService.EvidenceKind evidenceKind, UUID evidenceId,
                              String evidenceRef, boolean active) {

        /** Vinculo sem a coluna {@code active} informada: nasce ativo, como o DEFAULT da V21_2. */
        public EvidenceRef(CertificationService.EvidenceKind evidenceKind, UUID evidenceId,
                           String evidenceRef) {
            this(evidenceKind, evidenceId, evidenceRef, true);
        }
    }

    /**
     * Uma linha do mapa de um tenant com as evidencias vinculadas a ela.
     * Projecao de leitura de quem chama, juntando tres tabelas do schema
     * do tenant: {@code cert_standard_activation} (de onde saem
     * {@code standardCode} = {@code standard_code} e {@code editionInUse}
     * = {@code edition}, via {@code activation_id}), {@code cert_clause_map}
     * (a linha, cujo {@code id} e o {@code clauseMapId}) e
     * {@code cert_evidence_link} (as evidencias, cada uma com o seu
     * {@code active} da V21_2 carregado em {@link EvidenceRef}).
     */
    public record TenantClauseEvidence(UUID tenantId, String standardCode, String editionInUse,
                                       String clauseNumber, UUID clauseMapId,
                                       List<EvidenceRef> evidences) {

        public TenantClauseEvidence {
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }
    }

    /**
     * Um item da lista: o que revalidar (ou a lacuna), onde e por que.
     * {@code clauseMapId} e o id da linha de {@code cert_clause_map} a
     * revalidar, propagado da entrada — quem consome a lista nao precisa
     * rejuntar por tenant+norma+clausula. Nos itens de lacuna
     * ({@code CLAUSE_ADDED}) ele e {@code null}: a clausula nova ainda
     * nao tem linha no mapa do tenant.
     */
    public record RevalidationItem(UUID tenantId, String standardCode, String fromEdition,
                                   String toEdition, String clauseNumber, UUID clauseMapId,
                                   Reason reason, List<EvidenceRef> evidences) {

        public RevalidationItem {
            if (evidences == null) {
                throw new IllegalArgumentException("evidences is required");
            }
            evidences = List.copyOf(evidences);
        }
    }

    /** A lista completa de uma revisao, com os totais por motivo. */
    public record RevalidationPlan(String standardCode, String fromEdition, String toEdition,
                                   List<RevalidationItem> items) {

        public RevalidationPlan {
            if (items == null) {
                throw new IllegalArgumentException("items is required");
            }
            items = List.copyOf(items);
        }

        /** Itens de um motivo so. */
        public List<RevalidationItem> byReason(Reason reason) {
            return items.stream().filter(item -> item.reason() == reason).toList();
        }

        /** Tenants distintos atingidos pela revisao. */
        public Set<UUID> tenantIds() {
            Set<UUID> out = new LinkedHashSet<>();
            for (RevalidationItem item : items) {
                out.add(item.tenantId());
            }
            return out;
        }
    }

    /**
     * Gera a lista de revalidacao de uma revisao. Sem mudanca no diff, a
     * lista sai vazia — nem lacuna nem revalidacao.
     *
     * <p>So evidencias de vinculos ATIVOS ({@link EvidenceRef#active()})
     * entram nos itens: um vinculo desativado (V21_2) e descartado aqui,
     * mesmo que quem chama o passe. O item de uma clausula
     * alterada/removida permanece mesmo se todos os seus vinculos
     * estiverem inativos (a lista de evidencias sai vazia): a linha do
     * mapa existe e a clausula mudou.
     *
     * <p>Limitacao conhecida (achado 4): os tenants das lacunas
     * {@code CLAUSE_ADDED} sao os que aparecem em {@code usages} na
     * edicao anterior; tenant sem nenhuma linha projetada nao recebe
     * lacuna (ver o javadoc da classe).
     */
    public static RevalidationPlan plan(ClauseMapRevision.ClauseMapDiff diff,
                                        List<TenantClauseEvidence> usages) {
        if (diff == null || usages == null) {
            throw new IllegalArgumentException("diff and usages are required");
        }
        List<RevalidationItem> items = new ArrayList<>();
        Set<UUID> tenantsOnOldEdition = new LinkedHashSet<>();
        for (TenantClauseEvidence usage : usages) {
            if (!diff.standardCode().equals(usage.standardCode())
                    || !diff.fromEdition().equals(usage.editionInUse())) {
                continue;
            }
            tenantsOnOldEdition.add(usage.tenantId());
            Reason reason = null;
            if (diff.changed().stream().anyMatch(c -> c.clauseNumber().equals(usage.clauseNumber()))) {
                reason = Reason.CLAUSE_CHANGED;
            } else if (diff.removed().stream().anyMatch(e -> e.clauseNumber().equals(usage.clauseNumber()))) {
                reason = Reason.CLAUSE_REMOVED;
            }
            if (reason != null) {
                List<EvidenceRef> activeEvidences = usage.evidences().stream()
                        .filter(EvidenceRef::active).toList();
                items.add(new RevalidationItem(usage.tenantId(), diff.standardCode(),
                        diff.fromEdition(), diff.toEdition(), usage.clauseNumber(),
                        usage.clauseMapId(), reason, activeEvidences));
            }
        }
        for (ClauseCuration.ClauseEntry addedClause : diff.added()) {
            for (UUID tenantId : tenantsOnOldEdition) {
                items.add(new RevalidationItem(tenantId, diff.standardCode(),
                        diff.fromEdition(), diff.toEdition(), addedClause.clauseNumber(),
                        null, Reason.CLAUSE_ADDED, List.of()));
            }
        }
        items.sort(Comparator.comparing((RevalidationItem i) -> i.tenantId().toString())
                .thenComparing(RevalidationItem::clauseNumber)
                .thenComparing(i -> i.reason().name()));
        return new RevalidationPlan(diff.standardCode(), diff.fromEdition(), diff.toEdition(), items);
    }
}
