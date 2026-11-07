package br.com.ziipsan.commercial.registry.legacy;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

/**
 * P2.10: {@link InstallmentDebitGateway} sobre JDBC, no padrão dos
 * aplicadores da P1.40/ED-57 (SQL direto, sem JSON escrito à mão). Grava
 * em {@code debit_receivables} (V4_1 + proveniência da V4_4, P2.5):
 *
 * <ul>
 *   <li>prestação: {@code installments} = o campo {@code installments}
 *       do comando, validado como 1 no record (uma a uma),
 *       {@code installments_done = 0}, {@code source_type =
 *       'INSTALLMENT_AGREEMENT'}, {@code source_id} = acordo migrado
 *       (V14_2), {@code legacy_reference} = referência do acordo do
 *       legado passada pelo teto de 100 caracteres de
 *       {@link LegacyReferences#installmentReference} (a coluna é
 *       {@code VARCHAR(100)} na V4_4),
 *       {@code financing_term} = total de prestações do acordo na unidade,
 *       meses contábil e de arrecadação = mês do vencimento;</li>
 *   <li>resíduo: {@code source_type = 'LEGACY_IMPORT'} (que exige
 *       {@code legacy_reference} pelo CHECK {@code chk_drec_legacy_reference}),
 *       {@code legacy_reference} = referência da carga montada por
 *       {@link LegacyReferences#residualReference} (≤100 caracteres,
 *       correção A4 — a forma natural {@code <batchId>:<legacyKey>}
 *       chegaria a 137).</li>
 * </ul>
 *
 * <p>O {@code debit_type_id} é resolvido pelo código no catálogo
 * {@code debit_types} do tenant: código inexistente ou inativo aborta com
 * erro explícito ANTES de qualquer INSERT (o catálogo é dado do tenant; a
 * importação não inventa tipo de débito). {@code generates_bill} não é
 * gravado aqui: desde a V4_4 ele vive só no catálogo.
 *
 * <p>Sem {@code @Component}: a fiação (DataSource do tenant, schema) é da
 * integração, como nos demais adaptadores JDBC do módulo.
 */
public final class JdbcInstallmentDebitGateway implements InstallmentDebitGateway {

    private final DataSource dataSource;
    private final String schema;

    public JdbcInstallmentDebitGateway(DataSource dataSource, String schema) {
        if (dataSource == null || schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("dataSource e schema são obrigatórios.");
        }
        this.dataSource = dataSource;
        this.schema = schema;
    }

    @Override
    public UUID createInstallmentDebit(UUID tenantId, UUID actorId, InstallmentDebit debit) {
        try (Connection connection = dataSource.getConnection()) {
            UUID debitTypeId = requireDebitType(connection, tenantId, debit.debitTypeCode());
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + schema + ".debit_receivables"
                            + " (id, tenant_id, unit_id, debit_type_id, description, total_amount,"
                            + " installments, installments_done, status, under_review,"
                            + " source_type, source_id, legacy_reference, service_order_id,"
                            + " accounting_month, collection_month, financing_term, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'NORMAL', FALSE,"
                            + " 'INSTALLMENT_AGREEMENT', ?, ?, NULL, ?, ?, ?, ?, ?)")) {
                int i = 0;
                ps.setObject(++i, id);
                ps.setObject(++i, tenantId);
                ps.setObject(++i, debit.unitId());
                ps.setObject(++i, debitTypeId);
                ps.setString(++i, "Prestação " + debit.installmentNo()
                        + " do parcelamento migrado " + debit.legacyAgreementRef());
                ps.setBigDecimal(++i, BigDecimal.valueOf(debit.amountCents(), 2));
                // = 1, garantido pela validação do record InstallmentDebit.
                ps.setInt(++i, debit.installments());
                ps.setObject(++i, debit.agreementId());
                ps.setString(++i, LegacyReferences.installmentReference(debit.legacyAgreementRef()));
                ps.setObject(++i, debit.accountingMonth());
                ps.setObject(++i, debit.collectionMonth());
                ps.setInt(++i, debit.financingTerm());
                setUuid(ps, ++i, actorId);
                setUuid(ps, ++i, actorId);
                ps.executeUpdate();
            }
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar o débito da prestação "
                    + debit.installmentNo() + " do acordo " + debit.legacyAgreementRef(), e);
        }
    }

    @Override
    public UUID createResidualDebit(UUID tenantId, UUID actorId, ResidualDebit debit) {
        try (Connection connection = dataSource.getConnection()) {
            UUID debitTypeId = requireDebitType(connection, tenantId, debit.debitTypeCode());
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + schema + ".debit_receivables"
                            + " (id, tenant_id, unit_id, debit_type_id, description, total_amount,"
                            + " installments, installments_done, status, under_review,"
                            + " source_type, source_id, legacy_reference, service_order_id,"
                            + " accounting_month, collection_month, financing_term, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, 1, 0, 'NORMAL', FALSE,"
                            + " 'LEGACY_IMPORT', NULL, ?, NULL, ?, ?, NULL, ?, ?)")) {
                int i = 0;
                ps.setObject(++i, id);
                ps.setObject(++i, tenantId);
                ps.setObject(++i, debit.unitId());
                ps.setObject(++i, debitTypeId);
                ps.setString(++i, "Saldo migrado do legado (resíduo após as prestações a vencer)");
                ps.setBigDecimal(++i, BigDecimal.valueOf(debit.amountCents(), 2));
                ps.setString(++i, debit.legacyReference());
                ps.setObject(++i, debit.accountingMonth());
                ps.setObject(++i, debit.collectionMonth());
                setUuid(ps, ++i, actorId);
                setUuid(ps, ++i, actorId);
                ps.executeUpdate();
            }
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar o débito de resíduo de saldo ("
                    + debit.legacyReference() + ")", e);
        }
    }

    private UUID requireDebitType(Connection connection, UUID tenantId, String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM " + schema + ".debit_types"
                        + " WHERE tenant_id = ? AND code = ? AND active")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Tipo de débito '" + code + "' não existe (ou está"
                            + " inativo) no catálogo debit_types do tenant: a importação da P2.10"
                            + " não inventa tipo — cadastre o item no catálogo (P2.5) e reaplique.");
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }
}
