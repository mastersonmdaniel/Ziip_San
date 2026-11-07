package br.com.ziipsan.commercial.billing.content;

import br.com.ziipsan.commercial.billing.bill.HolderNameDisplay;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API publica do conteudo minimo e das mensagens legais (P2.32) para os
 * consumidores declarados no aceite — P2.8 (arquivo para grafica), P2.17
 * (marca e metadados nos PDFs) e P2.24 (declaracao anual e bloco de
 * qualidade da agua). Esta tarefa NAO implementa os consumidores: expoe o
 * conteudo, a validacao e a prova; quem renderiza e o motor da P2.30.
 */
public interface BillContentService {

    /** Campos obrigatorios vigentes do perfil (dado versionado). */
    List<BillFieldRequirement> requiredFields(String profileKey, LocalDate date);

    /**
     * Aprova o {@code bill.invoice_template} pelo conteudo: lanca excecao
     * nomeando os campos faltantes se o modelo nao cobrir o perfil vigente.
     */
    void assertTemplateApprovable(Set<String> templateFieldKeys, Set<String> lockedFieldKeys,
                                  String profileKey, LocalDate date);

    /** Mensagens selecionadas e ordenadas (legais primeiro, travado). */
    List<LegalMessageEngine.SelectedMessage> messagesFor(LegalMessageEngine.EmissionContext context);

    /** Prova de emissao da conta (qual mensagem, em qual versao da conta). */
    LegalMessageEngine.EmissionProof emissionProof(UUID billId, int billVersionNo,
                                                   UUID billTemplateVersionId,
                                                   LegalMessageEngine.EmissionContext context);

    /**
     * Nome do titular como a fatura imprime: SEMPRE pela regra
     * {@code bill.holder_name_display} da P2.5 (item 6 do aceite) — nenhum
     * consumidor monta o nome por conta propria.
     */
    default String printedHolderName(String holderNameDisplayParameterValue, String userHolderName,
                                     String ownerName) {
        return HolderNameDisplay.fromParameterValue(holderNameDisplayParameterValue)
                .printedName(userHolderName, ownerName);
    }
}
