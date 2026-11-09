package br.com.ziipsan.core.certification.lims17025;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Autorizacao de analista POR METODO (aceite da ED-51): o analista so
 * executa o ensaio de um metodo se houver autorizacao vigente para
 * AQUELE metodo — autorizacao para um metodo nao autoriza outro, e
 * autorizacao vencida nao autoriza.
 *
 * <p>Segregacao: quem autoriza nao pode ser o proprio analista
 * (padrao de separacao proposta/aceite do ADR-0041, aplicado aqui a
 * autorizacao) — autoautorizacao e recusada no nascimento.
 *
 * <p>So com a norma ligada: a concessao passa pela porta da
 * {@link Lims17025Activation} na fabrica {@link #grant}.
 */
public record AnalystAuthorization(String analystRef, String methodRef,
                                   LocalDate authorizedFrom, LocalDate authorizedTo,
                                   String authorizedByRef) {

    public AnalystAuthorization {
        if (analystRef == null || analystRef.isBlank())
            throw new IllegalArgumentException("analystRef e obrigatorio");
        if (methodRef == null || methodRef.isBlank())
            throw new IllegalArgumentException("methodRef e obrigatorio (a autorizacao e POR METODO)");
        Objects.requireNonNull(authorizedFrom, "authorizedFrom e obrigatorio");
        if (authorizedTo != null && authorizedTo.isBefore(authorizedFrom)) {
            throw new IllegalArgumentException("authorizedTo nao pode ser anterior a authorizedFrom");
        }
        if (authorizedByRef == null || authorizedByRef.isBlank())
            throw new IllegalArgumentException("authorizedByRef e obrigatorio (quem autorizou)");
        if (authorizedByRef.equals(analystRef)) {
            throw new IllegalArgumentException("analista nao pode autorizar a si mesmo: "
                    + "authorizedByRef == analystRef (segregacao, ADR-0041)");
        }
    }

    /** Concessao da autorizacao: so com a norma ligada (porta da ativacao). */
    public static AnalystAuthorization grant(Lims17025Activation activation, String analystRef,
                                             String methodRef, LocalDate from, LocalDate to,
                                             String authorizedByRef) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        return new AnalystAuthorization(analystRef, methodRef, from, to, authorizedByRef);
    }

    /** Verdadeiro quando esta autorizacao cobre o analista, o metodo e a data (limites inclusivos). */
    public boolean covers(String analyst, String method, LocalDate date) {
        Objects.requireNonNull(date, "date e obrigatoria");
        return analystRef.equals(analyst) && methodRef.equals(method)
                && !date.isBefore(authorizedFrom)
                && (authorizedTo == null || !date.isAfter(authorizedTo));
    }
}
