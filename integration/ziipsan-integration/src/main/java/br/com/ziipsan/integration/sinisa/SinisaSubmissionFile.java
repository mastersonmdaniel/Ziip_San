package br.com.ziipsan.integration.sinisa;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Arquivo de envio assistido do SINISA (P4.2, ADR-0004).
 *
 * <p><b>Meio de envio confirmado na fonte oficial antes da construcao</b> (exigencia
 * do aceite): a coleta do SINISA e feita por <b>sistema web de coleta de dados</b>
 * disponibilizado pela Secretaria Nacional de Saneamento Ambiental (Portaria MCID
 * 1.069/2025, art. 8o; pagina "Area do Prestador", Ministerio das Cidades), com
 * preenchimento dos formularios na propria pagina. Nao ha API publica de envio nem
 * formato oficial de arquivo de importacao publicado: presumir qualquer um dos dois
 * seria inventar formato.
 *
 * <p>Por isso, no modo assistido do ADR-0004 ("arquivo oficial, o cliente envia"),
 * este gerador produz o <b>pacote de transcricao</b> do ciclo registrado: um
 * documento JSON (so pela classe {@code Json} do core) com cada variavel, valor,
 * origem e justificativa, na ordem do catalogo, acompanhado do resumo SHA-256 do
 * conteudo canonico — a evidencia do que foi preparado para o responsavel digitar
 * e conferir no sistema web oficial. O documento declara expressamente o canal
 * ({@code channel = WEB_SYSTEM_TRANSCRIPTION}) para ninguem o confundir com um
 * arquivo de importacao oficial.
 *
 * <p>Geracao so a partir da versao preliminar registrada: rascunho em aberto nao
 * gera pacote de envio.
 */
public final class SinisaSubmissionFile {

    /** Canal confirmado na fonte oficial: sistema web, por transcricao do responsavel. */
    public static final String CHANNEL = "WEB_SYSTEM_TRANSCRIPTION";

    private SinisaSubmissionFile() {
    }

    /** Pacote gerado: documento JSON e seu resumo SHA-256 (hex, minusculas). */
    public record GeneratedFile(String json, String sha256) {
    }

    public static GeneratedFile generate(SinisaCycle cycle, OffsetDateTime generatedAt) {
        Objects.requireNonNull(cycle, "Ciclo SINISA e obrigatorio");
        Objects.requireNonNull(generatedAt, "Instante de geracao e obrigatorio");
        if (cycle.stage() == SinisaCycle.Stage.RASCUNHO) {
            throw new ApiException.Conflict("sinisa-file-before-preliminary",
                    "Arquivo de envio so e gerado a partir da versao preliminar registrada; "
                            + "o ciclo esta em RASCUNHO.");
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("system", "SINISA");
        document.put("channel", CHANNEL);
        document.put("channelNote", "Coleta por sistema web oficial (Portaria MCID 1.069/2025, art. 8o); "
                + "pacote assistido para transcricao e conferencia pelo responsavel. Sem API nem "
                + "formato de importacao oficial publicado.");
        document.put("baseYear", cycle.baseYear());
        document.put("providerScope", cycle.scope().name());
        document.put("municipalitiesServed", cycle.municipalitiesServed());
        document.put("responsibleName", cycle.responsibleName());
        document.put("stage", cycle.stage().name());
        document.put("generatedAt", generatedAt.toString());

        List<Map<String, Object>> entries = new ArrayList<>();
        for (SinisaVariable variable : cycle.catalog().variables()) {
            SinisaValue value = cycle.value(variable.key()).orElseThrow(() ->
                    new ApiException.Conflict("sinisa-file-incomplete",
                            "Variavel sem valor na geracao do arquivo: " + variable.key() + "."));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", variable.key());
            entry.put("officialCode", variable.officialCode());
            entry.put("form", variable.form().name());
            entry.put("name", variable.name());
            entry.put("unit", variable.unit());
            entry.put("value", value.value().toPlainString());
            entry.put("origin", value.origin().name());
            entry.put("sourceKind", variable.sourceKind().name());
            entry.put("sourceExpression", variable.sourceExpression());
            entry.put("justification", value.justification());
            entries.add(entry);
        }
        document.put("values", entries);
        cycle.certificate().ifPresent(cert -> {
            Map<String, Object> certMap = new LinkedHashMap<>();
            certMap.put("serviceKind", cert.serviceKind());
            certMap.put("issuedOn", cert.issuedOn().toString());
            certMap.put("documentReference", cert.documentReference());
            document.put("complianceCertificate", certMap);
        });

        String json = Json.write(document);
        return new GeneratedFile(json, sha256(json));
    }

    /** Resumo SHA-256 do conteudo, para evidencia e conferencia de integridade. */
    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}
