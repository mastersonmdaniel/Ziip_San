package br.com.ziipsan.commercial.billing.catalog;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Porta (gancho) de beneficio/isencao POR COBRANCA do catalogo unico
 * (desenho da Camada 0, secao 1; proposta de arquitetura tarifaria,
 * secao 3). Esta camada define SO o contrato: nenhum comportamento de
 * beneficio/isencao vive aqui e nenhuma implementacao nasce nesta
 * camada.
 *
 * <p>Consumidores futuros nomeados no desenho: a ED-22 (isencao de
 * residuos) e a ED-57 (enquadramento social) — as calculadoras das
 * camadas seguintes consultam esta porta para saber se a cobranca
 * resolvida tem beneficio/isencao aplicavel no contexto dado, sem que
 * a regra de enquadramento entre no catalogo.
 *
 * <p>O contexto e minimo e opaco para o catalogo: uma referencia de
 * unidade e a competencia; o catalogo nao interpreta nenhum dos dois
 * (quem implementa a porta e quem conhece o enquadramento).
 */
public interface ChargeBenefitHook {

    /**
     * Indica se ha beneficio/isencao aplicavel a cobranca resolvida
     * (definicao + versao vigente) no contexto dado.
     *
     * @return a indicacao; nunca {@code null} — sem beneficio, a
     *         implementacao devolve uma indicacao com os dois sinais
     *         desligados
     */
    BenefitIndication assess(ChargeDefinition definition, ChargeVersion version, BenefitContext context);

    /**
     * Contexto minimo e opaco da consulta: referencia da unidade
     * (nao interpretada pelo catalogo) e competencia da conta.
     *
     * @param unitRef    referencia opaca da unidade consumidora
     * @param competence competencia (data de referencia da conta)
     * @param reference  referencia adicional opaca do chamador;
     *                   anulavel
     */
    record BenefitContext(String unitRef, LocalDate competence, String reference) {

        public BenefitContext {
            Objects.requireNonNull(unitRef, "referencia da unidade e obrigatoria (opaca)");
            Objects.requireNonNull(competence, "competencia e obrigatoria");
        }

        /** Contexto so com unidade e competencia (sem referencia adicional). */
        public BenefitContext(String unitRef, LocalDate competence) {
            this(unitRef, competence, null);
        }
    }

    /**
     * Indicacao devolvida pela porta: se um beneficio se aplica, se
     * uma isencao se aplica, e o codigo da base invocada (opaco para
     * o catalogo; o significado e de quem implementa — ED-22/ED-57).
     *
     * @param benefitApplies   beneficio aplicavel a cobranca
     * @param exemptionApplies isencao aplicavel a cobranca
     * @param basisCode        codigo opaco da base do beneficio/isencao;
     *                         anulavel quando nenhum se aplica
     */
    record BenefitIndication(boolean benefitApplies, boolean exemptionApplies, String basisCode) {

        /** Indicacao vazia: nenhum beneficio nem isencao. */
        public static BenefitIndication none() {
            return new BenefitIndication(false, false, null);
        }
    }
}
