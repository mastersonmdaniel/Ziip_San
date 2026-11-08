package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.commercial.billing.content.LegalMessageEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Uma conta emitida, ja resolvida pelo chamador, pronta para o arquivo da
 * grafica (P2.8). O construtor do arquivo nao le banco: quem monta esta
 * origem le a conta da P2.5, o cadastro da P1.3 e o modelo da P2.30.
 *
 * @param billId              conta emitida (P2.5)
 * @param billVersionNo     versao da conta (a da prova de emissao da P2.32)
 * @param billTemplateVersionId versao do modelo da conta (motor da P2.30)
 * @param templateFieldKeys chaves dos campos/blocos SYSTEM dessa versao do
 *                            modelo — conferidas contra o conteudo minimo da
 *                            P2.32 para o perfil antes de gerar
 * @param lockedFieldKeys   subconjunto das chaves em bloco travado
 * @param unitId              unidade consumidora da conta
 * @param registrationNumber matricula da unidade (cadastro)
 * @param printedHolderName nome do titular COMO IMPRESSO: obtido pelo
 *                            chamador de {@code BillContentService.printedHolderName}
 *                            (regra {@code bill.holder_name_display} da P2.5) —
 *                            esta tarefa nao monta nome por conta propria
 * @param deliveryAddress   endereco de entrega do snapshot do titular
 *                            faturado (P2.5, DAD-15), ja decifrado pelo chamador
 * @param referenceMonth    competencia da conta (primeiro dia do mes)
 * @param dueDate           vencimento da conta
 * @param totalValue        total da conta
 * @param emissionContext   contexto de emissao para o motor de mensagens
 *                            legais da P2.32 (montado pelo chamador dos dados
 *                            reais da conta, como manda aquela tarefa)
 * @param accessibleFormat  formato acessivel marcado no cadastro da P1.3
 *                            para a unidade ({@link AccessiblePrintFormat})
 * @param linkingCode       codigo de vinculacao ao portal do imovel
 *                            (ADR-0022), vigente para a unidade
 * @param verificationCode  codigo publico de verificacao da fatura
 *                            (P3.35, PLT-11), vigente para ESTA conta —
 *                            distinto do codigo de vinculacao
 */
public record PrintBillSource(
        UUID billId,
        int billVersionNo,
        UUID billTemplateVersionId,
        Set<String> templateFieldKeys,
        Set<String> lockedFieldKeys,
        UUID unitId,
        String registrationNumber,
        String printedHolderName,
        String deliveryAddress,
        LocalDate referenceMonth,
        LocalDate dueDate,
        BigDecimal totalValue,
        LegalMessageEngine.EmissionContext emissionContext,
        AccessiblePrintFormat accessibleFormat,
        PortalLinkingCode linkingCode,
        VerificationCode verificationCode) {

    public PrintBillSource {
        if (billId == null) {
            throw new IllegalArgumentException("billId e obrigatorio");
        }
        if (billTemplateVersionId == null) {
            throw new IllegalArgumentException("billTemplateVersionId e obrigatorio para a conta " + billId);
        }
        if (templateFieldKeys == null) {
            throw new IllegalArgumentException("templateFieldKeys e obrigatorio para a conta " + billId);
        }
        templateFieldKeys = Set.copyOf(templateFieldKeys);
        lockedFieldKeys = lockedFieldKeys == null ? Set.of() : Set.copyOf(lockedFieldKeys);
        if (unitId == null) {
            throw new IllegalArgumentException("unitId e obrigatorio para a conta " + billId);
        }
        if (registrationNumber == null || registrationNumber.isBlank()) {
            throw new IllegalArgumentException("registrationNumber e obrigatorio para a conta " + billId);
        }
        if (printedHolderName == null || printedHolderName.isBlank()) {
            throw new IllegalArgumentException("printedHolderName e obrigatorio para a conta " + billId);
        }
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new IllegalArgumentException("deliveryAddress e obrigatorio para a conta " + billId);
        }
        if (referenceMonth == null || dueDate == null) {
            throw new IllegalArgumentException("referenceMonth e dueDate sao obrigatorios para a conta " + billId);
        }
        if (totalValue == null) {
            throw new IllegalArgumentException("totalValue e obrigatorio para a conta " + billId);
        }
        if (emissionContext == null) {
            throw new IllegalArgumentException("emissionContext e obrigatorio para a conta " + billId);
        }
        if (accessibleFormat == null) {
            throw new IllegalArgumentException("accessibleFormat e obrigatorio para a conta " + billId
                    + " (NONE explicito quando a unidade nao pede formato acessivel)");
        }
        if (linkingCode == null) {
            throw new IllegalArgumentException("linkingCode e obrigatorio para a conta " + billId
                    + ": conta sem codigo de vinculacao nao entra no arquivo da grafica (ADR-0022)");
        }
        if (verificationCode == null) {
            throw new IllegalArgumentException("verificationCode e obrigatorio para a conta " + billId
                    + ": conta sem codigo de verificacao nao entra no arquivo da grafica (P3.35)");
        }
    }

    /** Representacao segura para log/resumo: NUNCA inclui o codigo de vinculacao. */
    @Override
    public String toString() {
        return "PrintBillSource[billId=" + billId + ", billVersionNo=" + billVersionNo
                + ", unitId=" + unitId + ", registrationNumber=" + registrationNumber
                + ", referenceMonth=" + referenceMonth + ", accessibleFormat=" + accessibleFormat
                + ", linkingCode=REDACTED, verificationCode=REDACTED]";
    }
}
