package br.com.ziipsan.commercial.billing.social.cycle;

import br.com.ziipsan.commercial.billing.social.SocialBenefitConference;
import br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * II-3: o servico do ciclo completo da tarifa social - importa, cruza,
 * concede sem pedido e calcula a busca activa, numa unica corrida com
 * relatorio exacto.
 *
 * <p>E o servico cuja ausencia a ED-57 registou (pendencia 5 dela: quem
 * le a base, classifica, grava inscricoes e marca resultados "ainda nao
 * existe - e frente de integracao propria"). Este pacote entrega o
 * NUCLEO desse servico como dominio puro sobre portas: a importacao
 * entra pela {@link SocialListImportPort} com formato declarado, a
 * persistencia sai pela {@link SocialCycleStore} minimizada, e todas as
 * regras sao as da P1.5/ED-57 por delegacao. A fiacao com as tabelas
 * V4_2/V14_4 e com a OS de lote do importador generico e a integracao
 * (pendencia P-II3-INTEGRACAO declarada no relatorio).
 *
 * <p>Corrida:
 * <ol>
 *   <li>trava PLT-36 da origem da lista (ED-57) - combinacao proibida
 *       recusa a corrida inteira antes de importar;</li>
 *   <li>importacao com deduplicacao e relatorio de recusas por linha;</li>
 *   <li>cruzamento por chave documental com registo de divergencias;</li>
 *   <li>concessao sem pedido de cada familia casada ainda nao inscrita
 *       (ja inscrita e contada a parte, sem segunda concessao); recusa
 *       por familia nao derruba a corrida - entra no relatorio com o
 *       motivo;</li>
 *   <li>busca activa = elegiveis menos inscritos (deste ciclo e da
 *       loja), persistida na loja;</li>
 *   <li>resumo do cruzamento persistido na loja (chaves + resultado).</li>
 * </ol>
 * Nenhum passo consulta situacao de pagamento (Lei 14.898/2024).
 */
public final class SocialTariffCycleService {

    private final SocialListImportPort importPort;
    private final SocialCycleStore store;

    public SocialTariffCycleService(SocialListImportPort importPort, SocialCycleStore store) {
        if (importPort == null || store == null) {
            throw new IllegalArgumentException("Porta de importacao e loja do ciclo sao obrigatorias");
        }
        this.importPort = importPort;
        this.store = store;
    }

    /** Recusa de concessao de uma familia dentro de uma corrida concluida. */
    public record GrantRefusal(String familyCodeHash, SocialGrantLifecycle.RefusalReason reason) {
    }

    /** Relatorio exacto de uma corrida do ciclo (contagens e conjuntos). */
    public record CycleReport(int importedRows, int duplicatesRemoved,
                              List<SocialListImportPort.ImportRejection> importRejections,
                              SocialCrossingEngine.CrossingResult crossing,
                              List<SocialGrantLifecycle.Grant> grantsCreated, int alreadyEnrolled,
                              List<GrantRefusal> grantRefusals,
                              List<SocialActiveSearch.ActiveSearchEntry> activeSearch) {
        public CycleReport {
            importRejections = List.copyOf(importRejections);
            grantsCreated = List.copyOf(grantsCreated);
            grantRefusals = List.copyOf(grantRefusals);
            activeSearch = List.copyOf(activeSearch);
        }
    }

    public CycleReport runCycle(String listContent, DeclaredSocialListFormat format,
                                List<SocialCrossingEngine.UnitView> units,
                                SocialBenefitConference.ListMetadata list,
                                SocialProgramListPolicy.LegalNature legalNature,
                                LocalDate referenceDate, SocialTariffCycleParameters parameters,
                                Instant occurredAt, String actorId) {
        if (list == null || legalNature == null) {
            throw new IllegalArgumentException("Metadados da lista e natureza juridica sao obrigatorios");
        }
        // (1) PLT-36 antes de qualquer importacao (a mesma trava da ED-57).
        SocialProgramListPolicy.requireAllowed(list.listSource(), legalNature);

        // (2) Importacao declarada, com deduplicacao e recusas por linha.
        SocialListImportPort.ImportOutcome outcome = importPort.importBatch(listContent, format);

        // (3) Cruzamento por chave documental (delegado a P1.5).
        SocialCrossingEngine.CrossingResult crossing = SocialCrossingEngine.cross(
                outcome.accepted(), units, referenceDate, parameters);
        store.saveCrossing(crossing);

        // (4) Concessao sem pedido das familias casadas ainda nao inscritas.
        Set<String> enrolledBefore = store.enrolledFamilyHashes();
        List<SocialGrantLifecycle.Grant> grants = new ArrayList<>();
        List<GrantRefusal> refusals = new ArrayList<>();
        int alreadyEnrolled = 0;
        for (SocialCrossingEngine.CrossedFamily family : crossing.matched()) {
            if (enrolledBefore.contains(family.familyCodeHash())) {
                alreadyEnrolled++;
                continue;
            }
            try {
                SocialGrantLifecycle.Grant grant = SocialGrantLifecycle.grantWithoutRequest(
                        family, list, legalNature, referenceDate, parameters, occurredAt, actorId);
                store.saveGrant(grant);
                grants.add(grant);
            } catch (SocialGrantLifecycle.SocialGrantException e) {
                refusals.add(new GrantRefusal(family.familyCodeHash(), e.reason()));
            }
        }

        // (5) Busca activa: elegiveis (casados + nao casados) menos inscritos.
        Set<String> enrolledNow = new LinkedHashSet<>(enrolledBefore);
        for (SocialGrantLifecycle.Grant grant : grants) {
            enrolledNow.add(grant.familyCodeHash());
        }
        List<SocialActiveSearch.ActiveSearchEntry> activeSearch =
                SocialActiveSearch.compute(crossing, enrolledNow);
        store.saveActiveSearch(activeSearch);

        return new CycleReport(outcome.accepted().size(), outcome.duplicatesRemoved().size(),
                outcome.rejections(), crossing, grants, alreadyEnrolled, refusals, activeSearch);
    }
}
