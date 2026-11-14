package br.com.ziipsan.commercial.billing.relief;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Guarda de emissao da desoneracao (DESENHO-PA02 secao 4.3, com o gatilho
 * corrigido pelas fontes primarias — NT 5.3): na emissao a partir do corte,
 * se a versao resolvida nao for a desonerada registada, ou falha fechada ou
 * via provisoria MARCADA.
 *
 * <p>Via provisoria (Q-D2 = A; NT 5.3.1-5.3.5): o gatilho e a <b>falta de
 * homologacao</b> da tabela desonerada pela entidade reguladora infranacional
 * ate ao marco (15/11/2026 no cronograma da NT) — nao "nao publicar a
 * tempo". Condicoes: declaracao explicita do tenant no cadastro do programa;
 * comunicacao formal a reguladora e ao titular (registada pelo chamador);
 * caracter provisorio marcado no snapshot; o valor total provisorio ja inclui
 * CBS e IBS; a regularizacao e <b>compensacao das diferencas</b> apuradas, em
 * favor do prestador ou dos usuarios. A operacionalizacao fina da compensacao
 * (retificacao/credito na fatura) continua [SEM FONTE] — P-02-05 parcial.
 *
 * <p>NAO implementado, deliberadamente: a proposta noticiada pela ANA em
 * 04/11/2026 de actualizacao do item 5.3 (retirar 9,25% quando CBS+IBS >
 * 9,25%) e PROPOSTA, nao texto arquivado — pendencia P-02-15.
 */
public final class ReliefEmissionGuard {

    /** Recusa de emissao pela guarda (falha fechada). */
    public static final class ReliefEmissionBlockedException extends RuntimeException {
        public ReliefEmissionBlockedException(String message) {
            super(message);
        }
    }

    public enum Decision { NOT_APPLICABLE, RELIEVED_OK, PROVISIONAL_ALLOWED }

    /** Forma de regularizacao das provisorias (NT 5.3.3): compensacao das diferencas. */
    public static final String REGULARIZATION_FORM = "COMPENSACAO_DAS_DIFERENCAS";

    private ReliefEmissionGuard() {
    }

    /**
     * @param homologationPublished se a reguladora infranacional publicou o
     *        ato de homologacao (definitiva ou provisoria) da tabela
     *        desonerada ate ao marco — e o gatilho da via provisoria
     * @param formalNoticeRecorded se o prestador comunicou formalmente a
     *        reguladora e ao titular (condicao NT 5.3.3-a)
     */
    public static Decision check(ReliefProgram program, LocalDate issuedOn, UUID resolvedVersionId,
                                 List<ReliefLink> links, boolean homologationPublished,
                                 boolean formalNoticeRecorded) {
        Objects.requireNonNull(resolvedVersionId, "versao resolvida e obrigatoria");
        return checkAll(program, issuedOn, List.of(resolvedVersionId), links,
                homologationPublished, formalNoticeRecorded);
    }

    /**
     * Guarda para periodos MULTI-VERSAO (correccao R-01 da REVISAO-PA-02):
     * a emissao so esta desonerada se <b>todas</b> as versoes resolvidas
     * (uma por fatia do periodo) forem desoneradas ligadas ao programa.
     * Basta uma fatia nao desonerada para o conjunto nao estar desonerado —
     * falha fechada ou via provisoria, como no caso de versao unica.
     */
    public static Decision checkAll(ReliefProgram program, LocalDate issuedOn,
                                    java.util.Collection<UUID> resolvedVersionIds,
                                    List<ReliefLink> links, boolean homologationPublished,
                                    boolean formalNoticeRecorded) {
        if (program == null || !program.status().engagesEmissionGuard()
                || issuedOn.isBefore(program.effectiveFrom())) {
            return Decision.NOT_APPLICABLE;
        }
        Objects.requireNonNull(resolvedVersionIds, "versoes resolvidas sao obrigatorias");
        if (resolvedVersionIds.isEmpty()) {
            throw new ReliefEmissionBlockedException(
                    "Emissao em " + issuedOn + " sem nenhuma versao resolvida — programa "
                            + program.code());
        }
        boolean relieved = resolvedVersionIds.stream().allMatch(id -> links.stream()
                .anyMatch(l -> l.programId().equals(program.id())
                        && l.targetKind() == ReliefLink.TargetKind.TARIFF_VERSION
                        && l.relievedVersionId().equals(id)));
        if (relieved) {
            return Decision.RELIEVED_OK;
        }
        if (!homologationPublished && program.provisionalFallbackDeclared()) {
            if (!formalNoticeRecorded) {
                throw new ReliefEmissionBlockedException(
                        "Via provisoria exige a comunicacao formal a reguladora e ao titular "
                                + "registada (NT 5.3.3-a) — programa " + program.code());
            }
            return Decision.PROVISIONAL_ALLOWED;
        }
        throw new ReliefEmissionBlockedException(
                "Emissao em " + issuedOn + " sem versao desonerada do programa " + program.code()
                        + (homologationPublished
                        ? " (homologacao publicada: a via provisoria nao se aplica)"
                        : " (sem declaracao de via provisoria do tenant)"));
    }
}
