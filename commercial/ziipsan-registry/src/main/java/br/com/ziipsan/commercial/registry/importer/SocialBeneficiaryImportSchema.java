package br.com.ziipsan.commercial.registry.importer;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.DATE;
import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.TEXT;

/**
 * ED-57: esquema {@code social.beneficiaries} - lista de beneficiarios de
 * programa social importada pelo importador generico da P1.40 (JOR-26), sem
 * API publica: a lista chega em arquivo, pela area de preparacao, com
 * validacao por linha, relatorio de recusas e aplicacao por OS de lote do
 * framework. Este esquema NAO e um importador novo: e a declaracao que o
 * {@link ImportSchemaRegistry} pede ("esquemas proprios das suas frentes",
 * como o de beneficiarios, citado no registro).
 *
 * <p>Destino: a base da lista oficial da tarifa social da P1.5 (V4_2) -
 * {@code social_base_imports} (o lote) e {@code social_base_records} (uma
 * linha por pessoa, titular ou membro). A classificacao automatica, os
 * avisos e o ciclo de vida do beneficio sao da P1.5 e NAO sao duplicados
 * aqui: aplicada a lista, quem cruza com as unidades e cria a inscricao e a
 * classificacao da P1.5 ({@code SocialClassification}, no billing), que le
 * exatamente estas duas tabelas. Este esquema so recebe, valida a forma e
 * grava a base.
 *
 * <p>So indices cegos (privacidade por desenho da P1.5, V4_2): a planilha
 * carrega {@code family_code_hash} e {@code person_document_hash} (HMAC, 64
 * hex), nunca CPF, NIS, codigo familiar ou renda em claro - assim a area de
 * preparacao ({@code import_rows}) tambem nao guarda dado pessoal em claro
 * deste esquema. A geracao do indice cego a partir do documento e passo
 * anterior a montagem da planilha, com a chave do tenant
 * ({@code BlindIndex}, ziipsan-security); o contrato do aplicador da P1.40
 * nao recebe a chave, entao documento em claro NAO e aceito aqui.
 *
 * <p>Colunas de lote repetidas por linha: {@code list_source},
 * {@code legal_basis}, {@code layout_version} e {@code received_on}
 * identificam a lista em todas as linhas (o aplicador e por linha e o lote
 * da V4_2 e resolvido por esta chave). {@code list_source} e a origem da
 * lista conforme a natureza juridica do prestador (PLT-36; dominios da
 * V4_2: REGULATOR_LIST, MUNICIPAL_BASE, CONECTA_API); a trava da combinacao
 * com {@code reg.legal_nature} (cessao direta do CadUnico so para ente de
 * direito publico, Decreto 11.016/2022) e validacao de parametros da
 * plataforma e regra pura do billing (ED-57), nao deste esquema.
 * {@code legal_basis} e a base legal registrada da lista: fica gravada na
 * area de preparacao e e a base que a inscricao criada a partir da lista
 * carrega em {@code social_tariff_enrollments.legal_basis} (NOT NULL na
 * V4_2). A V4_2 nao tem coluna de base legal no lote
 * ({@code social_base_imports}) - ver relatorio da ED-57.
 *
 * <p>Vigencia de cada beneficio: {@code benefit_valid_from} /
 * {@code benefit_valid_to} sao as datas declaradas na lista para o
 * beneficio daquela pessoa. A base da V4_2 nao tem coluna de vigencia por
 * registro; as datas ficam na area de preparacao e sao consumidas na
 * conferencia e na inscricao (vigencia da inscricao, no billing), sem
 * migration nova nesta frente.
 *
 * <p>Chave do esquema (duplicidade no arquivo e no banco): a composicao
 * (lote, familia, pessoa, papel) - a mesma do UNIQUE da V4_2 em
 * {@code social_base_records}. Uma lista nova (outra data de recebimento)
 * nao e duplicidade da anterior: e a recertificacao da P1.5. Premissa
 * registrada (achado A3 da revisao): a duplicidade NO ARQUIVO do
 * validador generico usa so as tres colunas {@code uniqueKey} (familia,
 * pessoa, papel), sem as colunas de lote - ela parte da premissa de que
 * um arquivo e UMA lista so (mesma origem, leiaute e data de
 * recebimento em todas as linhas, como no modelo). Um arquivo que
 * misture duas listas com a mesma pessoa no mesmo papel e recusado como
 * {@code DUPLICATE_IN_FILE}, embora o {@code existsByKey} (banco) as
 * tratasse como lotes distintos; listas distintas vao em arquivos
 * distintos.
 *
 * <p>Combinacao base x data do CadUnico (achado A1 da revisao): a V4_2
 * impoe em {@code social_base_records} o CHECK
 * {@code (eligibility_basis = 'CADUNICO') = (cadunico_updated_on IS NOT NULL)}
 * - a data existe exatamente quando a base e CADUNICO. O validador
 * generico da P1.40 nao expressa obrigatoriedade condicional entre
 * colunas, entao esta combinacao NAO fica a cargo dele nem do banco:
 * {@link #validate} (a validacao completa deste esquema) a recusa LINHA
 * A LINHA, nos dois sentidos - CADUNICO sem data e recusado
 * ({@code REQUIRED} em {@code cadunico_updated_on}) e BPC/LOCAL_PROGRAM
 * com data e recusado ({@code INVALID_DOMAIN} em
 * {@code cadunico_updated_on}) - e o aplicador repete a guarda antes de
 * qualquer INSERT ({@link #requireBasisDateConsistent}), como ultima
 * defesa de quem bypassar a validacao do esquema: sem isso, a linha
 * passaria na preparacao e quebraria no INSERT pelo CHECK, e o servico
 * do framework marcaria o JOB INTEIRO como FAILED no meio do lote, em
 * vez de a linha constar do relatorio de recusas. A regra de
 * elegibilidade da P1.5 nao e tocada: isto e forma da gravacao (o que a
 * V4_2 aceita gravar), nao elegibilidade - CADUNICO sem data continua
 * inelegivel pela P1.5 onde quer que ele apareca.
 *
 * <p>Inverso (OS de correcao do lote): apaga o registro da base inserido
 * por esta linha. Registro ja cruzado com uma inscricao
 * ({@code matched_enrollment_id} preenchido) NAO e apagado: a correcao do
 * lote nao desfaz inscricao - o fim do beneficio segue o ciclo de vida da
 * P1.5 (aviso de perda e graca), e a reversao recusa com erro explicito.
 * Registro apenas casado com uma unidade ({@code match_status} MATCHED,
 * {@code matched_unit_id} preenchido, mas {@code matched_enrollment_id}
 * ainda nulo) E apagado pelo inverso (achado A5 da revisao): nenhuma
 * inscricao existe para desfazer, e o cruzamento e refeito pela
 * classificacao da P1.5 a partir do lote corrigido.
 *
 * <p>Cruzamento de edificacoes com unidades para perdas aparentes: fora
 * desta frente - e da ED-95 (OPE-37), que consome o cadastro e a GIS; o
 * unico ponto de contato aqui e que esta base nao cria unidade nem
 * edificacao, so pessoas da lista.
 */
@Component
public class SocialBeneficiaryImportSchema implements ImportSchema {

    public static final String CODE = "social.beneficiaries";

    /** Coluna da origem da lista, usada na resolucao do lote da V4_2. */
    static final String SOURCE_COLUMN = "list_source";

    private static final List<ImportColumn> COLUMNS = List.of(
            new ImportColumn("list_source", TEXT, true,
                    List.of("REGULATOR_LIST", "MUNICIPAL_BASE", "CONECTA_API"),
                    "REGULATOR_LIST", null, false),
            ImportColumn.text("legal_basis", true, "Lei 14.898/2024"),
            ImportColumn.text("layout_version", true, "v1"),
            new ImportColumn("received_on", DATE, true, List.of(), "2026-01-15", null, false),
            new ImportColumn("family_code_hash", TEXT, true, List.of(),
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", null, true),
            new ImportColumn("person_document_hash", TEXT, true, List.of(),
                    "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210", null, true),
            new ImportColumn("person_role", TEXT, true, List.of("HOLDER", "MEMBER"),
                    "HOLDER", null, true),
            new ImportColumn("eligibility_basis", TEXT, true,
                    List.of("CADUNICO", "BPC", "LOCAL_PROGRAM"), "CADUNICO", null, false),
            new ImportColumn("cadunico_updated_on", DATE, false, List.of(), "2025-06-01", null, false),
            new ImportColumn("benefit_valid_from", DATE, true, List.of(), "2026-01-01", null, false),
            new ImportColumn("benefit_valid_to", DATE, false, List.of(), "", null, false));

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Beneficiarios de programas sociais";
    }

    @Override
    public List<ImportColumn> columns() {
        return COLUMNS;
    }

    /**
     * A validacao completa deste esquema: a validacao generica da P1.40
     * ({@link ImportValidator}) MAIS a recusa linha a linha da combinacao
     * base x data do CadUnico do CHECK da V4_2, que o validador generico
     * nao expressa (ver o Javadoc da classe, achado A1). Linha recusada
     * aqui sai das aceitas e entra no relatorio de recusas (linha, campo,
     * motivo) - ela nao chega ao aplicador, nao quebra INSERT nenhum e
     * nao derruba o lote: as demais linhas seguem aceitas.
     *
     * <p>Limite conhecido, registrado no relatorio da ED-57: o
     * {@code ImportService} da P1.40 chama apenas o validador generico -
     * o framework nao tem gancho de validacao cruzada por esquema. Ate a
     * P1.40 ganhar esse gancho (decisao do dono / frente de integracao,
     * fora do pacote da ED-57), a guarda do aplicador
     * ({@link #requireBasisDateConsistent}) e a barreira efetiva no
     * caminho de aplicacao; esta validacao e a que os testes exercem e a
     * que qualquer chamador da validacao deste esquema deve usar.
     */
    public ImportValidator.ValidationResult validate(ImportCsvParser.ParsedCsv csv,
                                                     ImportValidator.DbChecks db) {
        ImportValidator.ValidationResult generic = ImportValidator.validate(this, csv, db);
        List<ImportCsvParser.ParsedRow> accepted = new ArrayList<>();
        List<ImportValidator.Rejection> rejections = new ArrayList<>(generic.rejections());
        for (ImportCsvParser.ParsedRow row : generic.accepted()) {
            Optional<ImportValidator.Rejection> rejection = basisDateRejection(row);
            if (rejection.isPresent()) {
                rejections.add(rejection.get());
            } else {
                accepted.add(row);
            }
        }
        rejections.sort(Comparator.comparingInt(ImportValidator.Rejection::rowNumber));
        return new ImportValidator.ValidationResult(List.copyOf(accepted), List.copyOf(rejections));
    }

    /**
     * A combinacao base x data de uma linha ja aceita pela validacao
     * generica viola o CHECK da V4_2? CADUNICO sem data: a data e
     * obrigatoria para esta base ({@code REQUIRED}). BPC ou LOCAL_PROGRAM
     * com data: a data nao se aplica a esta base ({@code INVALID_DOMAIN}).
     * Base fora do dominio nao chega aqui (o validador generico ja a
     * recusou) e nao gera segunda recusa.
     */
    static Optional<ImportValidator.Rejection> basisDateRejection(ImportCsvParser.ParsedRow row) {
        String basis = row.values().getOrDefault("eligibility_basis", "");
        String cadunicoUpdatedOn = row.values().getOrDefault("cadunico_updated_on", "");
        if ("CADUNICO".equals(basis) && cadunicoUpdatedOn.isBlank()) {
            return Optional.of(new ImportValidator.Rejection(row.number(), "cadunico_updated_on",
                    "REQUIRED", "Data de atualizacao do CadUnico obrigatoria para a base CADUNICO"
                    + " (V4_2: a data existe exatamente quando a base e CADUNICO)."));
        }
        if (("BPC".equals(basis) || "LOCAL_PROGRAM".equals(basis)) && !cadunicoUpdatedOn.isBlank()) {
            return Optional.of(new ImportValidator.Rejection(row.number(), "cadunico_updated_on",
                    "INVALID_DOMAIN", "Data de atualizacao do CadUnico nao se aplica a base"
                    + " declarada: so CADUNICO tem data de atualizacao (V4_2: a data existe"
                    + " exatamente quando a base e CADUNICO)."));
        }
        return Optional.empty();
    }

    /**
     * Guarda do aplicador (ultima defesa do achado A1): a combinacao
     * base x data que a validacao do esquema recusa nao pode ser gravada
     * nem por um caminho que bypassou a validacao - a recusa aqui e
     * ANTES de resolver/criar o lote e de qualquer INSERT, com mensagem
     * explicita, nunca o erro cru do CHECK da V4_2.
     */
    static void requireBasisDateConsistent(Map<String, String> values) throws SQLException {
        String basis = values.getOrDefault("eligibility_basis", "");
        String cadunicoUpdatedOn = values.get("cadunico_updated_on");
        boolean hasDate = cadunicoUpdatedOn != null && !cadunicoUpdatedOn.isBlank();
        if ("CADUNICO".equals(basis) && !hasDate) {
            throw new SQLException("Base CADUNICO sem data de atualizacao do CadUnico: a linha"
                    + " devia ter sido recusada na validacao do esquema (V4_2 exige a data"
                    + " exatamente quando a base e CADUNICO) e nao pode ser aplicada.");
        }
        if (("BPC".equals(basis) || "LOCAL_PROGRAM".equals(basis)) && hasDate) {
            throw new SQLException("Data de atualizacao do CadUnico presente para base que nao"
                    + " e CADUNICO: a linha devia ter sido recusada na validacao do esquema"
                    + " (V4_2 exige a data exatamente quando a base e CADUNICO) e nao pode"
                    + " ser aplicada.");
        }
    }

    @Override
    public ImportApplier applier() {
        return new ImportApplier() {
            @Override
            public boolean existsByKey(Connection connection, String schema, UUID tenantId,
                                       Map<String, String> values) throws SQLException {
                UUID lotId = findLot(connection, schema, tenantId, values);
                if (lotId == null) {
                    return false;
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM " + schema + ".social_base_records"
                                + " WHERE tenant_id = ? AND import_id = ? AND family_code_hash = ?"
                                + " AND person_document_hash = ? AND person_role = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, lotId);
                    ps.setString(3, values.get("family_code_hash"));
                    ps.setString(4, values.get("person_document_hash"));
                    ps.setString(5, values.get("person_role"));
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            }

            @Override
            public Applied apply(Connection connection, String schema, UUID tenantId, UUID actorId,
                                 Map<String, String> values) throws SQLException {
                requireBasisDateConsistent(values);
                UUID lotId = findLot(connection, schema, tenantId, values);
                if (lotId == null) {
                    lotId = insertLot(connection, schema, tenantId, actorId, values);
                }
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + schema + ".social_base_records"
                                + " (id, tenant_id, import_id, family_code_hash, person_document_hash,"
                                + " person_role, eligibility_basis, cadunico_updated_on, match_status,"
                                + " created_by, updated_by)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)")) {
                    int i = 0;
                    ps.setObject(++i, id);
                    ps.setObject(++i, tenantId);
                    ps.setObject(++i, lotId);
                    ps.setString(++i, values.get("family_code_hash"));
                    ps.setString(++i, values.get("person_document_hash"));
                    ps.setString(++i, values.get("person_role"));
                    ps.setString(++i, values.get("eligibility_basis"));
                    setDateOrNull(ps, ++i, values.get("cadunico_updated_on"));
                    setUuid(ps, ++i, actorId);
                    setUuid(ps, ++i, actorId);
                    ps.executeUpdate();
                }
                refreshLotCount(connection, schema, tenantId, lotId);
                // A chave do alvo expoe so o lote e os indices cegos ja presentes na planilha.
                return new Applied("social_base_records", id,
                        values.get("family_code_hash") + "/" + values.get("person_document_hash")
                                + "/" + values.get("person_role"));
            }

            @Override
            public void reverse(Connection connection, String schema, UUID tenantId, UUID actorId,
                                Applied applied) throws SQLException {
                UUID lotId;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT import_id, matched_enrollment_id FROM " + schema + ".social_base_records"
                                + " WHERE tenant_id = ? AND id = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, applied.targetId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            // Ja revertido ou nunca existiu: o inverso e idempotente.
                            return;
                        }
                        if (rs.getObject(2) != null) {
                            throw new SQLException("Registro da lista ja gerou inscricao de tarifa"
                                    + " social: a correcao do lote nao desfaz inscricao; o fim do"
                                    + " beneficio segue o ciclo de vida da P1.5 (aviso e graca).");
                        }
                        lotId = (UUID) rs.getObject(1);
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM " + schema + ".social_base_records"
                                + " WHERE tenant_id = ? AND id = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, applied.targetId());
                    ps.executeUpdate();
                }
                if (lotId != null) {
                    refreshLotCount(connection, schema, tenantId, lotId);
                }
            }
        };
    }

    /** O lote da V4_2 desta lista: mesma origem, leiaute e data de recebimento. */
    private static UUID findLot(Connection connection, String schema, UUID tenantId,
                                Map<String, String> values) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM " + schema + ".social_base_imports"
                        + " WHERE tenant_id = ? AND list_source = ? AND layout_version = ?"
                        + " AND received_on = ? ORDER BY created_at, id LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setString(2, values.get(SOURCE_COLUMN));
            ps.setString(3, values.get("layout_version"));
            ps.setObject(4, LocalDate.parse(values.get("received_on")));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? (UUID) rs.getObject(1) : null;
            }
        }
    }

    private static UUID insertLot(Connection connection, String schema, UUID tenantId, UUID actorId,
                                  Map<String, String> values) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + schema + ".social_base_imports"
                        + " (id, tenant_id, list_source, layout_version, received_on, record_count,"
                        + " status, created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, ?, 0, 'RECEIVED', ?, ?)")) {
            int i = 0;
            ps.setObject(++i, id);
            ps.setObject(++i, tenantId);
            ps.setString(++i, values.get(SOURCE_COLUMN));
            ps.setString(++i, values.get("layout_version"));
            ps.setObject(++i, LocalDate.parse(values.get("received_on")));
            setUuid(ps, ++i, actorId);
            setUuid(ps, ++i, actorId);
            ps.executeUpdate();
        }
        return id;
    }

    /** record_count do lote = registros gravados ate aqui (o aplicador e por linha). */
    private static void refreshLotCount(Connection connection, String schema, UUID tenantId,
                                        UUID lotId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE " + schema + ".social_base_imports SET record_count ="
                        + " (SELECT count(*) FROM " + schema + ".social_base_records"
                        + " WHERE tenant_id = ? AND import_id = ?)"
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, lotId);
            ps.setObject(3, tenantId);
            ps.setObject(4, lotId);
            ps.executeUpdate();
        }
    }

    static void setDateOrNull(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setObject(index, LocalDate.parse(value));
        }
    }

    static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }
}
