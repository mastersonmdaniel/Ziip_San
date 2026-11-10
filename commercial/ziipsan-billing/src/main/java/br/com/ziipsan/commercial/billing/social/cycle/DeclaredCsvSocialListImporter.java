package br.com.ziipsan.commercial.billing.social.cycle;

import br.com.ziipsan.commercial.billing.social.SocialEligibility;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * II-3: adaptador de prova da porta de importacao - le texto delimitado
 * no formato DECLARADO ({@link DeclaredSocialListFormat#proofFormat()}).
 *
 * <p>Validacoes de FORMA (nao de elegibilidade - elegibilidade e da
 * P1.5, delegada no cruzamento e na concessao):
 * <ul>
 *   <li>cabecalho exactamente igual ao declarado;</li>
 *   <li>indices cegos em 64 hex minusculo (a ED-57 registou, pendencia 7,
 *       que o validador generico da P1.40 nao tem tipo "hash"; este
 *       adaptador, dono do seu formato declarado, valida a forma);</li>
 *   <li>dominios fechados de papel (HOLDER/MEMBER) e de base
 *       (CADUNICO/BPC/LOCAL_PROGRAM), os da V4_2;</li>
 *   <li>combinacao base x data do CadUnico do CHECK da V4_2, nos dois
 *       sentidos - a mesma guarda de forma da ED-57 (achado A1 dela):
 *       CADUNICO sem data recusa; BPC/LOCAL_PROGRAM com data recusa;</li>
 *   <li>datas ISO e fim de vigencia nao anterior ao inicio.</li>
 * </ul>
 * Recusa e por linha, com numero da linha no ficheiro (cabecalho = 1).
 */
public final class DeclaredCsvSocialListImporter implements SocialListImportPort {

    private static final java.util.regex.Pattern HASH_64_HEX =
            java.util.regex.Pattern.compile("[0-9a-f]{64}");

    @Override
    public ImportOutcome importBatch(String content, DeclaredSocialListFormat format) {
        if (content == null || format == null) {
            throw new IllegalArgumentException("Conteudo da lista e formato declarado sao obrigatorios");
        }
        String[] lines = content.split("\\R", -1);
        List<ImportedBeneficiaryRow> accepted = new ArrayList<>();
        List<ImportedBeneficiaryRow> duplicates = new ArrayList<>();
        List<ImportRejection> rejections = new ArrayList<>();
        Map<String, ImportedBeneficiaryRow> firstByKey = new LinkedHashMap<>();

        int headerIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                headerIdx = i;
                break;
            }
        }
        if (headerIdx < 0) {
            rejections.add(new ImportRejection(1, "(ficheiro)", "EMPTY",
                    "Lista vazia: nem o cabecalho declarado foi encontrado."));
            return new ImportOutcome(accepted, duplicates, rejections);
        }
        if (!lines[headerIdx].trim().equals(format.headerLine())) {
            rejections.add(new ImportRejection(headerIdx + 1, "(cabecalho)", "INVALID_HEADER",
                    "Cabecalho diverge do formato declarado " + format.formatId() + " v" + format.version()
                            + ": esperado [" + format.headerLine() + "]."));
            return new ImportOutcome(accepted, duplicates, rejections);
        }

        String delim = java.util.regex.Pattern.quote(String.valueOf(format.delimiter()));
        for (int i = headerIdx + 1; i < lines.length; i++) {
            String raw = lines[i];
            if (raw.isBlank()) {
                continue;
            }
            int lineNumber = i + 1;
            String[] parts = raw.split(delim, -1);
            if (parts.length != format.columns().size()) {
                rejections.add(new ImportRejection(lineNumber, "(linha)", "INVALID_ARITY",
                        "Linha com " + parts.length + " campos; o formato declarado tem "
                                + format.columns().size() + "."));
                continue;
            }
            Map<String, String> v = new LinkedHashMap<>();
            for (int c = 0; c < format.columns().size(); c++) {
                v.put(format.columns().get(c), parts[c].trim());
            }
            ImportedBeneficiaryRow row = parseRow(v, lineNumber, rejections);
            if (row == null) {
                continue;
            }
            ImportedBeneficiaryRow first = firstByKey.putIfAbsent(row.dedupKey(), row);
            if (first == null) {
                accepted.add(row);
            } else {
                duplicates.add(row);
            }
        }
        return new ImportOutcome(accepted, duplicates, rejections);
    }

    /** Forma de uma linha; devolve null e regista a recusa quando invalida. */
    private static ImportedBeneficiaryRow parseRow(Map<String, String> v, int lineNumber,
                                                   List<ImportRejection> rejections) {
        String family = v.getOrDefault(DeclaredSocialListFormat.COL_FAMILY, "");
        String person = v.getOrDefault(DeclaredSocialListFormat.COL_PERSON, "");
        if (!HASH_64_HEX.matcher(family).matches()) {
            rejections.add(new ImportRejection(lineNumber, DeclaredSocialListFormat.COL_FAMILY,
                    "INVALID_HASH", "Indice cego da familia fora da forma 64 hex."));
            return null;
        }
        if (!HASH_64_HEX.matcher(person).matches()) {
            rejections.add(new ImportRejection(lineNumber, DeclaredSocialListFormat.COL_PERSON,
                    "INVALID_HASH", "Indice cego da pessoa fora da forma 64 hex."));
            return null;
        }
        String role = v.getOrDefault(DeclaredSocialListFormat.COL_ROLE, "");
        if (!role.equals("HOLDER") && !role.equals("MEMBER")) {
            rejections.add(new ImportRejection(lineNumber, DeclaredSocialListFormat.COL_ROLE,
                    "INVALID_DOMAIN", "Papel fora do dominio HOLDER/MEMBER da V4_2."));
            return null;
        }
        SocialEligibility.Basis basis;
        try {
            basis = SocialEligibility.Basis.valueOf(v.getOrDefault(DeclaredSocialListFormat.COL_BASIS, ""));
        } catch (IllegalArgumentException e) {
            rejections.add(new ImportRejection(lineNumber, DeclaredSocialListFormat.COL_BASIS,
                    "INVALID_DOMAIN", "Base de elegibilidade fora do dominio CADUNICO/BPC/LOCAL_PROGRAM da V4_2."));
            return null;
        }
        String cadunicoRaw = v.getOrDefault(DeclaredSocialListFormat.COL_CADUNICO_UPDATED, "");
        // Combinacao base x data do CHECK da V4_2 (forma, como na ED-57/A1).
        if (basis == SocialEligibility.Basis.CADUNICO && cadunicoRaw.isBlank()) {
            rejections.add(new ImportRejection(lineNumber, DeclaredSocialListFormat.COL_CADUNICO_UPDATED,
                    "REQUIRED", "Data de atualizacao do CadUnico obrigatoria para a base CADUNICO (V4_2)."));
            return null;
        }
        if (basis != SocialEligibility.Basis.CADUNICO && !cadunicoRaw.isBlank()) {
            rejections.add(new ImportRejection(lineNumber, DeclaredSocialListFormat.COL_CADUNICO_UPDATED,
                    "INVALID_DOMAIN", "Data de atualizacao do CadUnico nao se aplica a base declarada (V4_2)."));
            return null;
        }
        LocalDate cadunicoUpdatedOn;
        LocalDate validFrom;
        LocalDate validTo;
        try {
            cadunicoUpdatedOn = cadunicoRaw.isBlank() ? null : LocalDate.parse(cadunicoRaw);
            validFrom = LocalDate.parse(v.getOrDefault(DeclaredSocialListFormat.COL_VALID_FROM, ""));
            String validToRaw = v.getOrDefault(DeclaredSocialListFormat.COL_VALID_TO, "");
            validTo = validToRaw.isBlank() ? null : LocalDate.parse(validToRaw);
        } catch (DateTimeParseException e) {
            rejections.add(new ImportRejection(lineNumber, "(datas)", "INVALID_DATE",
                    "Data fora da forma ISO (AAAA-MM-DD) numa das colunas de data da linha."));
            return null;
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            rejections.add(new ImportRejection(lineNumber, DeclaredSocialListFormat.COL_VALID_TO,
                    "INVALID_RANGE", "Fim da vigencia do beneficio anterior ao inicio."));
            return null;
        }
        String municipality = v.getOrDefault(DeclaredSocialListFormat.COL_MUNICIPALITY, "");
        return new ImportedBeneficiaryRow(family, person, role, basis, cadunicoUpdatedOn,
                validFrom, validTo, municipality.isBlank() ? null : municipality);
    }
}
