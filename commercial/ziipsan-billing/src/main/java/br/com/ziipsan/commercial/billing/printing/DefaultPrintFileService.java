package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.commercial.billing.content.BillContentService;
import br.com.ziipsan.commercial.billing.content.LegalMessageCatalog;
import br.com.ziipsan.commercial.billing.content.LegalMessageEngine;
import br.com.ziipsan.commercial.billing.content.RequiredBillContent;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementacao do {@link PrintFileService} (P2.8): monta o arquivo no
 * leiaute {@link PrintLayout} a partir das contas resolvidas, conferindo por
 * conta, nesta ordem:
 *
 * <ol>
 *   <li>o modelo da conta cobre o conteudo minimo do perfil vigente —
 *       delega a {@code BillContentService.assertTemplateApprovable} da
 *       P2.32, que nomeia o campo faltante e a norma;</li>
 *   <li>braile marcado no cadastro so sai com {@code bill.braille_enabled}
 *       ligado no tenant;</li>
 *   <li>todo valor cabe no leiaute (sem separador nem quebra de linha).</li>
 * </ol>
 *
 * <p>As mensagens de cada registro sao as selecionadas pelo motor da P2.32
 * para o contexto da conta (legais primeiro, ordem travada la), gravadas
 * como chave@posicao; os campos obrigatorios vao como a lista ordenada de
 * chaves do catalogo da P2.32. O codigo de vinculacao so e lido aqui, uma
 * vez por registro, direto para a linha do arquivo — nao passa pelo
 * manifesto, pela prova nem por nenhuma representacao textual (ver
 * {@code LinkingCodeLeakTest}).
 */
@Service
public class DefaultPrintFileService implements PrintFileService {

    private final BillContentService billContentService;

    public DefaultPrintFileService(BillContentService billContentService) {
        if (billContentService == null) {
            throw new IllegalArgumentException("billContentService e obrigatorio");
        }
        this.billContentService = billContentService;
    }

    @Override
    public PrintFile generate(PrintFileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request e obrigatorio");
        }
        List<String> recordLines = new ArrayList<>();
        List<LegalMessageEngine.EmissionProof> proofs = new ArrayList<>();
        int braille = 0;
        int largePrint = 0;
        int normal = 0;
        int seq = 0;
        for (PrintBillSource bill : request.bills()) {
            seq++;
            billContentService.assertTemplateApprovable(bill.templateFieldKeys(), bill.lockedFieldKeys(),
                    request.profileKey(), bill.emissionContext().issueDate());
            if (bill.accessibleFormat() == AccessiblePrintFormat.BRAILLE && !request.brailleEnabled()) {
                throw new IllegalArgumentException("Conta " + bill.billId() + " marcada em braile no cadastro,"
                        + " mas o parametro " + AccessiblePrintFormat.BRAILLE_ENABLED_PARAMETER_KEY
                        + " esta desligado no tenant: conflito explicito, sem rebaixar para impressao comum");
            }
            switch (bill.accessibleFormat()) {
                case BRAILLE -> braille++;
                case LARGE_PRINT -> largePrint++;
                case NONE -> normal++;
            }
            List<LegalMessageEngine.SelectedMessage> messages =
                    billContentService.messagesFor(bill.emissionContext());
            proofs.add(billContentService.emissionProof(bill.billId(), bill.billVersionNo(),
                    bill.billTemplateVersionId(), bill.emissionContext()));
            recordLines.add(recordLine(seq, bill, messages, request.profileKey()));
        }
        String sha256 = sha256Hex(String.join("\n", recordLines));
        PrintFileManifest manifest = new PrintFileManifest(request.batchId(), PrintLayout.LAYOUT_VERSION,
                request.profileKey(), request.generatedAt(), RequiredBillContent.CATALOG_VERSION,
                LegalMessageCatalog.CATALOG_VERSION, recordLines.size(), braille, largePrint, normal,
                sha256, request.bills().stream().map(PrintBillSource::billId).toList());
        String header = String.join(PrintLayout.DELIMITER,
                PrintLayout.HEADER_TAG,
                "LAYOUT=" + PrintLayout.LAYOUT_VERSION,
                "BATCH=" + request.batchId(),
                "GERADO_EM=" + request.generatedAt(),
                "PERFIL=" + request.profileKey(),
                "CONTEUDO_V=" + RequiredBillContent.CATALOG_VERSION,
                "MENSAGENS_V=" + LegalMessageCatalog.CATALOG_VERSION);
        String trailer = String.join(PrintLayout.DELIMITER,
                PrintLayout.TRAILER_TAG,
                "REGISTROS=" + recordLines.size(),
                "SHA256=" + sha256);
        String content = header + "\n" + String.join("\n", recordLines) + "\n" + trailer + "\n";
        return new PrintFile(content, manifest, proofs);
    }

    private String recordLine(int seq, PrintBillSource bill,
                              List<LegalMessageEngine.SelectedMessage> messages, String profileKey) {
        // A lista ordenada de chaves e a do catalogo da P2.32 para o perfil
        // do lote — a mesma lista cuja cobertura a aprovacao acima conferiu.
        List<String> requiredKeys = billContentService.requiredFields(
                        profileKey, bill.emissionContext().issueDate())
                .stream().map(f -> f.fieldKey()).toList();
        String fields = String.join(",", requiredKeys);
        String messageKeys = messages.stream()
                .map(m -> m.definition().messageKey() + "@" + m.printPosition())
                .collect(Collectors.joining(","));
        return String.join(PrintLayout.DELIMITER,
                PrintLayout.RECORD_TAG,
                "SEQ=" + seq,
                "CONTA=" + bill.billId(),
                "VERSAO=" + bill.billVersionNo(),
                "UNIDADE=" + bill.unitId(),
                "MATRICULA=" + field(bill.registrationNumber(), "registrationNumber", bill),
                "TITULAR=" + field(bill.printedHolderName(), "printedHolderName", bill),
                "ENDERECO_ENTREGA=" + field(bill.deliveryAddress(), "deliveryAddress", bill),
                "COMPETENCIA=" + bill.referenceMonth().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                "VENCIMENTO=" + bill.dueDate(),
                "TOTAL=" + total(bill),
                "MODELO_VERSAO=" + bill.billTemplateVersionId(),
                "CAMPOS=" + fields,
                "MENSAGENS=" + messageKeys,
                // UNICO ponto da tarefa em que o codigo de vinculacao e lido:
                // direto para a linha do arquivo final da grafica.
                "VINCULO_PORTAL=" + bill.linkingCode().value(),
                "ACESSIVEL=" + bill.accessibleFormat().fileToken(),
                // Idem para o codigo de verificacao da P3.35: leitura unica,
                // direto para a linha do arquivo final da grafica.
                "VERIFICACAO=" + bill.verificationCode().value());
    }

    private static String total(PrintBillSource bill) {
        try {
            return bill.totalValue().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Total da conta " + bill.billId()
                    + " com mais de 2 casas decimais: o calculo da P2.5 entrega o total fechado em centavos", e);
        }
    }

    private static String field(String value, String name, PrintBillSource bill) {
        if (value.contains(PrintLayout.DELIMITER) || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("Valor de " + name + " da conta " + bill.billId()
                    + " contem separador ou quebra de linha: incompativel com o leiaute da grafica");
        }
        return value;
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }
}
