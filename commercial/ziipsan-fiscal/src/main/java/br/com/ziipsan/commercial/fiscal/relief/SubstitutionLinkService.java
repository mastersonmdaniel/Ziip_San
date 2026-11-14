package br.com.ziipsan.commercial.fiscal.relief;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Vinculos tipo de substituicao x motivos (DESENHO-PA02 secao 7.1; F-17 a
 * F-20): o tipo da NFAg substituta resolve-se PELO VINCULO do motivo usado —
 * nunca por escolha livre do operador nem por default. Motivo sem vinculo =>
 * emissao substituta recusada com erro explicito.
 *
 * <p>Guarda de completude: a activacao de motivo de situacao "Cancelada"
 * (F-19) e de motivo de alteracao de fatura (F-20) sem vinculo a tipo activo
 * e RECUSADA; para os quatro sub-tipos de cancelamento (F-18), a activacao
 * sem vinculo gera AVISO FORTE (se "todos vinculados" e invariante ou meta
 * operacional continua [A DEFINIR] — P-02-10). O relatorio de cobertura lista
 * os motivos activos sem vinculo: e a lista de trabalho da implantacao.
 */
public final class SubstitutionLinkService {

    public enum MotiveKind {
        CANCELLATION_DIVERSE_CHARGE,
        CANCELLATION_DEBIT_RESEND,
        CANCELLATION_TRANSFER,
        CANCELLATION_PAYMENT_REVERSAL,
        BILL_STATUS,
        BILL_ALTERATION;

        /** Recusa dura de activacao sem vinculo (F-19/F-20 universais). */
        public boolean activationRequiresLink() {
            return this == BILL_STATUS || this == BILL_ALTERATION;
        }
    }

    /** Motivo operacional conhecido pelo servico. */
    public record Motive(MotiveKind kind, String motiveRef, boolean cancelledStatusOnly) {
        public Motive {
            Objects.requireNonNull(kind, "tipo do motivo e obrigatorio");
            if (motiveRef == null || motiveRef.isBlank()) {
                throw new IllegalArgumentException("referencia do motivo e obrigatoria");
            }
        }

        String key() {
            return kind + "|" + motiveRef;
        }
    }

    private final SubstitutionTypes types;
    private final Map<String, Motive> motives = new LinkedHashMap<>();
    private final Map<String, String> links = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public SubstitutionLinkService(SubstitutionTypes types) {
        this.types = Objects.requireNonNull(types, "cadastro de tipos e obrigatorio");
    }

    public void link(Motive motive, String substitutionTypeCode) {
        types.resolveActive(substitutionTypeCode);
        motives.putIfAbsent(motive.key(), motive);
        links.put(motive.key(), substitutionTypeCode);
    }

    /**
     * Activacao de um motivo: sem vinculo, recusa (Cancelada/alteracao) ou
     * aviso forte (cancelamentos), conforme o tipo.
     */
    public void activateMotive(Motive motive) {
        motives.putIfAbsent(motive.key(), motive);
        boolean applies = motive.kind() != MotiveKind.BILL_STATUS || motive.cancelledStatusOnly();
        if (!applies) {
            return; // F-19: vinculo de situacao so para motivos de situacao "Cancelada"
        }
        if (links.containsKey(motive.key())) {
            return;
        }
        if (motive.kind().activationRequiresLink()) {
            throw new IllegalStateException(
                    "Activacao recusada: motivo " + motive.kind() + " " + motive.motiveRef()
                            + " sem vinculo a tipo de substituicao (F-19/F-20)");
        }
        warnings.add("AVISO FORTE: motivo de cancelamento " + motive.motiveRef()
                + " activado sem vinculo a tipo de substituicao (F-18; P-02-10 em aberto)");
    }

    /** Resolucao da substituicao pelo vinculo do motivo. Sem vinculo = recusa. */
    public SubstitutionTypes.SubstitutionType resolveFor(Motive motive) {
        String code = links.get(motive.key());
        if (code == null) {
            throw new IllegalStateException(
                    "Motivo " + motive.kind() + " " + motive.motiveRef()
                            + " sem vinculo: emissao substituta recusada");
        }
        return types.resolveActive(code);
    }

    /** Relatorio de cobertura: motivos conhecidos sem vinculo aplicavel. */
    public List<Motive> coverageGaps() {
        List<Motive> gaps = new ArrayList<>();
        for (Motive motive : motives.values()) {
            boolean applies = motive.kind() != MotiveKind.BILL_STATUS || motive.cancelledStatusOnly();
            if (applies && !links.containsKey(motive.key())) {
                gaps.add(motive);
            }
        }
        return List.copyOf(gaps);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
