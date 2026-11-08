package br.com.ziipsan.core.certification.quality9001;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma linha do mapa de processos do sistema de gestão da qualidade
 * (ED-69): processo com responsável, entradas, saídas, indicador e risco
 * — os cinco elementos que o aceite exige por processo, nenhum opcional.
 * Processo sem responsável não tem dono; sem entradas/saídas não se liga
 * à cadeia; sem indicador não se mede; sem risco declarado não conversa
 * com o registro de riscos da P3.31.
 *
 * <p>O indicador é referenciado pelo CÓDIGO do registro único de
 * indicadores da P3.41 (JOR-19) — nunca uma fórmula copiada aqui: a
 * medição tem uma só fonte, o registro. A existência do código no
 * registro é validada na fiação de integração (porta equivalente à
 * {@code IndicatorCatalog} da P3.31); esta régua garante o formato.
 *
 * <p>O risco é a descrição declarada pelo tenant para o processo; o
 * registro formal de riscos/oportunidades (critério, escore, apetite)
 * continua sendo o da P3.31 ({@code cert_risks} da V21_3), ao qual esta
 * descrição se refere por texto — não há segunda fonte de risco.
 *
 * <p>Só com a norma ligada: o nascimento passa pela porta da
 * {@link Iso9001Activation}.
 */
public record ProcessMapEntry(String processCode, String processName, UUID ownerId,
                              List<String> inputs, List<String> outputs,
                              String indicatorCode, String riskDescription) {

    /** Teto do código do processo no SQL pendente da ED-69 (VARCHAR(60)). */
    public static final int PROCESS_CODE_MAX = 60;
    /** Teto do código do indicador ({@code V15_2}/P3.41: VARCHAR(60), como na V21_3). */
    public static final int INDICATOR_CODE_MAX = 60;

    public ProcessMapEntry {
        if (processCode == null || processCode.isBlank()
                || processCode.trim().length() > PROCESS_CODE_MAX) {
            throw new IllegalArgumentException("processCode é obrigatório e tem no máximo "
                    + PROCESS_CODE_MAX + " caracteres");
        }
        processCode = processCode.trim();
        if (processName == null || processName.isBlank()) {
            throw new IllegalArgumentException("processName é obrigatório (nome do processo "
                    + "no mapa do tenant)");
        }
        processName = processName.trim();
        Objects.requireNonNull(ownerId, "ownerId é obrigatório (responsável pelo processo)");
        inputs = requireEntries(inputs, "inputs (entradas do processo)");
        outputs = requireEntries(outputs, "outputs (saídas do processo)");
        if (indicatorCode == null || indicatorCode.isBlank()
                || indicatorCode.trim().length() > INDICATOR_CODE_MAX) {
            throw new IllegalArgumentException("indicatorCode é obrigatório (código do registro "
                    + "de indicadores da P3.41) e tem no máximo " + INDICATOR_CODE_MAX
                    + " caracteres");
        }
        indicatorCode = indicatorCode.trim();
        if (riskDescription == null || riskDescription.isBlank()) {
            throw new IllegalArgumentException("riskDescription é obrigatória (risco do processo, "
                    + "declarado pelo tenant; o registro formal é o da P3.31)");
        }
        riskDescription = riskDescription.trim();
    }

    private static List<String> requireEntries(List<String> entries, String field) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException(field + ": ao menos uma entrada é obrigatória");
        }
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                throw new IllegalArgumentException(field + ": item em branco não é declaração");
            }
        }
        return List.copyOf(entries);
    }

    /** Nascimento da linha do mapa: só com a norma ligada (porta da ativação). */
    public static ProcessMapEntry define(Iso9001Activation activation, String processCode,
                                         String processName, UUID ownerId, List<String> inputs,
                                         List<String> outputs, String indicatorCode,
                                         String riskDescription) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new ProcessMapEntry(processCode, processName, ownerId, inputs, outputs,
                indicatorCode, riskDescription);
    }
}
