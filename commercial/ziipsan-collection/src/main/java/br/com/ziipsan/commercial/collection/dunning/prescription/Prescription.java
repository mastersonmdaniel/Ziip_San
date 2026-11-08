package br.com.ziipsan.commercial.collection.dunning.prescription;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Prescricao por natureza juridica do prestador e por pretensao (RAS-15):
 * tabela VERSIONADA com a FONTE de cada linha, consultada pela ED-30
 * (negativacao e protesto) e pela ED-31 (divida ativa) para bloquear
 * negativacao, protesto e ajuizamento de divida prescrita.
 *
 * <p>Fontes nomeadas pelo aceite: Codigo Civil, arts. 205 e 206; Temas
 * 251 a 254 do STJ para a execucao de tarifa; Sumula 412 do STJ para a
 * repeticao de indebito; Decreto 20.910/1932 SO com analise juridica.
 *
 * <p>Honestidade das linhas semeadas (SQL pendente desta entrega): so
 * entram com prazo as linhas cujo prazo decorre diretamente da fonte
 * citada — art. 205 do CC (regra geral, 10 anos) e a repeticao de
 * indebito (Sumula 412 remete ao prazo do Codigo Civil). As linhas da
 * execucao de tarifa (Temas 251 a 254) e do Decreto 20.910/1932 nascem
 * SEM prazo e marcadas como dependentes de confirmacao/analise juridica:
 * enquanto nao houver prazo registrado por versao nova da tabela, o
 * desfecho e {@link Outcome#UNDETERMINED} e os portoes (ED-30/ED-31)
 * bloqueiam por cautela — nunca se chuta um prazo.
 *
 * <p>LACUNA DECLARADA do art. 206 (correcao D7 da revisao cruzada): o
 * aceite nomeia "arts. 205 e 206" como fontes, mas nenhuma spec
 * disponivel fixa QUAL pretensao do art. 206 (o artigo tem prazos
 * diversos por inciso, de 1 a 5 anos) se aplica a qual cobranca desta
 * tabela. A linha {@link ClaimType#ARTICLE_206_UNSPECIFIED} existe
 * justamente para nao silenciar a lacuna: nasce SEM prazo
 * ({@code period_years NULL}), com a fonte "Codigo Civil, art. 206" e a
 * declaracao da lacuna — a consulta devolve UNDETERMINED e os portoes
 * bloqueiam, ate o juridico fixar inciso e prazo em versao nova da
 * tabela. Nenhum prazo de inciso foi inventado.
 */
public final class Prescription {

    private Prescription() {
    }

    /** Natureza juridica do prestador (a tabela e por natureza E por pretensao). */
    public enum ProviderNature {
        PRIVATE_LAW,
        PUBLIC_LAW
    }

    /** Pretensao a que o prazo se aplica. */
    public enum ClaimType {
        /** Cobranca da tarifa/debito em geral (regra residual do art. 205). */
        GENERAL,
        /** Execucao de tarifa (Temas 251 a 254 do STJ). */
        TARIFF_EXECUTION,
        /** Repeticao de indebito (Sumula 412 do STJ). */
        UNDUE_PAYMENT_REPETITION,
        /**
         * Pretensao sob o art. 206 do CC sem inciso/prazo fixados nas
         * specs (LACUNA DECLARADA, D7): linha sem prazo, UNDETERMINED.
         */
        ARTICLE_206_UNSPECIFIED,
        /** Pretensao contra ente publico (Decreto 20.910/1932, so com analise juridica). */
        AGAINST_PUBLIC_ENTITY
    }

    /**
     * Uma linha da tabela versionada. {@code periodYears == null} significa
     * "prazo ainda nao confirmado para esta linha": com
     * {@code requiresLegalAnalysis}, falta a analise juridica; sem ela,
     * falta a confirmacao do prazo na fonte citada. Linha sem prazo nunca
     * produz "prescrita" nem "nao prescrita": produz UNDETERMINED.
     */
    public record RuleRow(
            ProviderNature providerNature,
            ClaimType claimType,
            Integer periodYears,
            String legalSource,
            boolean requiresLegalAnalysis,
            int tableVersion,
            LocalDate validFrom) {
        public RuleRow {
            if (providerNature == null || claimType == null
                    || legalSource == null || legalSource.isBlank()
                    || tableVersion < 1 || validFrom == null) {
                throw new IllegalArgumentException(
                        "linha de prescricao exige natureza, pretensao, FONTE, versao e vigencia");
            }
            if (periodYears != null && periodYears < 1) {
                throw new IllegalArgumentException("prazo de prescricao deve ser positivo");
            }
        }
    }

    /** A tabela versionada (linhas vigentes na data de referencia). */
    public record Table(List<RuleRow> rows) {
        public Table {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        /** Linha aplicavel; ausencia e erro explicito de quem consulta (tabela incompleta). */
        public Optional<RuleRow> find(ProviderNature nature, ClaimType claim, LocalDate at) {
            return rows.stream()
                    .filter(r -> r.providerNature() == nature && r.claimType() == claim
                            && !r.validFrom().isAfter(at))
                    .reduce((a, b) -> b.tableVersion() >= a.tableVersion() ? b : a);
        }
    }

    public enum Outcome {
        NOT_PRESCRIBED,
        PRESCRIBED,
        /** Sem prazo confirmado ou dependente de analise juridica: os portoes bloqueiam. */
        UNDETERMINED
    }

    /** Resultado da consulta, com a linha e o termo final que o sustentam. */
    public record Evaluation(Outcome outcome, RuleRow row, LocalDate deadline,
                             LocalDate clockStart, String reason) {
    }

    /**
     * O relogio da prescricao. O termo inicial e o vencimento do debito;
     * o reconhecimento inequívoco da divida pelo devedor — a promessa de
     * pagamento registrada na regua e o caso desta tarefa — interrompe a
     * prescricao e reinicia o relogio (modelo declarado: CC, art. 202,
     * VI). A prescricao se consuma quando a data de avaliacao alcanca o
     * termo final (inicio + prazo); no dia anterior, ainda nao.
     */
    public static Evaluation evaluate(Table table, ProviderNature nature, ClaimType claim,
                                      LocalDate dueDate, LocalDate lastAcknowledgment,
                                      boolean legalAnalysisRecorded, LocalDate evaluationDate) {
        if (table == null || dueDate == null || evaluationDate == null) {
            throw new IllegalArgumentException("tabela, vencimento e data de avaliacao sao obrigatorios");
        }
        RuleRow row = table.find(nature, claim, evaluationDate).orElseThrow(() ->
                new IllegalStateException("tabela de prescricao sem linha para " + nature + "/" + claim
                        + ": completar a tabela versionada (RAS-15) antes de consultar"));
        LocalDate clockStart = lastAcknowledgment != null && lastAcknowledgment.isAfter(dueDate)
                ? lastAcknowledgment : dueDate;
        if (row.periodYears() == null || (row.requiresLegalAnalysis() && !legalAnalysisRecorded)) {
            String reason = row.periodYears() == null
                    ? "linha sem prazo confirmado na fonte citada (" + row.legalSource() + ")"
                    : "linha dependente de analise juridica nao registrada (" + row.legalSource() + ")";
            return new Evaluation(Outcome.UNDETERMINED, row, null, clockStart, reason);
        }
        LocalDate deadline = clockStart.plusYears(row.periodYears());
        boolean prescribed = !evaluationDate.isBefore(deadline);
        return new Evaluation(prescribed ? Outcome.PRESCRIBED : Outcome.NOT_PRESCRIBED,
                row, deadline, clockStart,
                prescribed ? "termo final " + deadline + " alcancado" : "termo final " + deadline + " nao alcancado");
    }
}
