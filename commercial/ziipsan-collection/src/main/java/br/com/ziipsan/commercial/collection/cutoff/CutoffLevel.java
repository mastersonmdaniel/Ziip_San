package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.core.cutoff.CutoffEnums;

/**
 * Niveis de corte da ED-29 (18e-E9; 18f-ENT-04, TR-539/SG-501): a
 * execucao do corte se registra POR NIVEL — cavalete, ramal e
 * supressao — em niveis SUCESSIVOS (TR-417/SG-400: ao menos cavalete e
 * ramal, com calendario por nivel; o aceite da ED-29 acrescenta a
 * supressao como terceiro nivel).
 *
 * <p>Os dois primeiros niveis sao os do modelo da P3.4
 * ({@code cut.level_model}: VALVE = cavalete, BRANCH = ramal). A
 * SUPRESSAO nao tem nivel correspondente no enum da P3.4: o efeito dela
 * e a situacao SUPRIMIDA da ligacao ({@link CutoffEnums.ConnectionStatus#SUPPRESSED});
 * o registro do tipo de OS da supressao no nucleo (P1.31) e trabalho de
 * integracao declarado no relatorio — esta classe nao finge um codigo
 * de tipo que o nucleo nao declarou.
 */
public enum CutoffLevel {

    /** 1o nivel: cavalete (VALVE da P3.4). */
    VALVE(1),
    /** 2o nivel: ramal (BRANCH da P3.4). */
    BRANCH(2),
    /** 3o nivel: supressao da ligacao (sem nivel no enum da P3.4). */
    SUPPRESSION(3);

    private final int order;

    CutoffLevel(int order) {
        this.order = order;
    }

    /** Posicao na sucessao (1, 2, 3): nivel so executa depois do anterior. */
    public int order() {
        return order;
    }

    /** Nivel correspondente na P3.4, quando existe (supressao: vazio). */
    public java.util.Optional<CutoffEnums.CutLevel> coreLevel() {
        return switch (this) {
            case VALVE -> java.util.Optional.of(CutoffEnums.CutLevel.VALVE);
            case BRANCH -> java.util.Optional.of(CutoffEnums.CutLevel.BRANCH);
            case SUPPRESSION -> java.util.Optional.empty();
        };
    }

    /** Situacao da ligacao apos a execucao deste nivel. */
    public CutoffEnums.ConnectionStatus statusAfterExecution() {
        return this == SUPPRESSION
                ? CutoffEnums.ConnectionStatus.SUPPRESSED
                : CutoffEnums.ConnectionStatus.CUT;
    }

    /** Proximo nivel da sucessao, quando ha (supressao e o ultimo). */
    public java.util.Optional<CutoffLevel> next() {
        return switch (this) {
            case VALVE -> java.util.Optional.of(BRANCH);
            case BRANCH -> java.util.Optional.of(SUPPRESSION);
            case SUPPRESSION -> java.util.Optional.empty();
        };
    }
}
