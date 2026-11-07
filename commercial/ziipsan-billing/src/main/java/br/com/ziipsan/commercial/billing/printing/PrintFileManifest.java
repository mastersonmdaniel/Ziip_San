package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.core.json.Json;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manifesto do lote da grafica (P2.8): o artefato INTERMEDIARIO que acompanha
 * o lote nos sistemas (fila, auditoria, tela de acompanhamento) — contagens,
 * versoes e a integridade do arquivo, SEM nenhum dado do arquivo em si.
 *
 * <p>Por construcao ele nao tem campo para o codigo de vinculacao, para o
 * nome do titular nem para o endereco: nao e redacao na saida, e ausencia de
 * dado na entrada. E essa ausencia que o aceite pede ("o codigo nao aparece
 * em arquivo intermediario fora do da grafica") e o
 * {@code LinkingCodeLeakTest} confere sobre o JSON e sobre o {@code toString}.
 *
 * @param batchId               lote gerado
 * @param layoutVersion         versao do leiaute ({@link PrintLayout})
 * @param profileKey            perfil regulatorio conferido (P2.31/P2.32)
 * @param generatedAt           instante da geracao
 * @param contentCatalogVersion versao do catalogo de conteudo minimo (P2.32)
 * @param messageCatalogVersion versao do catalogo de mensagens legais (P2.32)
 * @param recordCount           total de contas no arquivo
 * @param brailleCount          contas com indicador BRAILE
 * @param largePrintCount       contas com indicador FONTE_AMPLIADA
 * @param normalCount           contas sem formato acessivel
 * @param fileSha256            SHA-256 (hex) das linhas de registro do arquivo
 * @param billIds               contas do lote, na ordem do arquivo
 */
public record PrintFileManifest(
        UUID batchId,
        int layoutVersion,
        String profileKey,
        OffsetDateTime generatedAt,
        int contentCatalogVersion,
        int messageCatalogVersion,
        int recordCount,
        int brailleCount,
        int largePrintCount,
        int normalCount,
        String fileSha256,
        List<UUID> billIds) {

    public PrintFileManifest {
        if (batchId == null || profileKey == null || generatedAt == null || fileSha256 == null) {
            throw new IllegalArgumentException("manifesto incompleto: batchId, profileKey, generatedAt e fileSha256 sao obrigatorios");
        }
        billIds = billIds == null ? List.of() : List.copyOf(billIds);
        if (recordCount != billIds.size()) {
            throw new IllegalArgumentException("recordCount diverge da lista de contas do manifesto");
        }
        if (brailleCount + largePrintCount + normalCount != recordCount) {
            throw new IllegalArgumentException("contagens por formato nao fecham com o total do manifesto");
        }
    }

    /** JSON do manifesto, SO pela classe Json do core. Seguro para log e fila. */
    public String toJson() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("batchId", batchId.toString());
        fields.put("layoutVersion", layoutVersion);
        fields.put("profileKey", profileKey);
        fields.put("generatedAt", generatedAt.toString());
        fields.put("contentCatalogVersion", contentCatalogVersion);
        fields.put("messageCatalogVersion", messageCatalogVersion);
        fields.put("recordCount", recordCount);
        fields.put("brailleCount", brailleCount);
        fields.put("largePrintCount", largePrintCount);
        fields.put("normalCount", normalCount);
        fields.put("fileSha256", fileSha256);
        fields.put("billIds", billIds.stream().map(UUID::toString).toList());
        return Json.write(fields);
    }

    /** Resumo de uma linha para log: so o manifesto (que nao tem dado sensivel). */
    public String describeForLog() {
        return "lote grafica " + batchId + ": " + recordCount + " contas (braile=" + brailleCount
                + ", fonte ampliada=" + largePrintCount + ", comum=" + normalCount
                + "), sha256=" + fileSha256;
    }
}
