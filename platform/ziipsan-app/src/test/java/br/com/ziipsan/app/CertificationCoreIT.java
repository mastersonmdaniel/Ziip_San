package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.certification.CertificationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.32 (core V9_8 + tenant V21_1) on the real PostgreSQL, over the
 * provisioned tenant.
 *
 * <p>Live proof: the global catalog starts empty for the test's codes and
 * only the synthetic rows the test itself inserts are visible (the P1.34
 * load is a declared pendency, not faked here); the full activation cycle
 * runs (propose, approve by another person, formal acceptance with hash,
 * clause map copied from the accepted edition); the many-to-many evidence
 * link serves one evidence to several clauses and one clause to several
 * evidences, with the exact duplicate refused; deactivation freezes the
 * map and keeps activation, map and links queryable; reactivation reuses
 * the same row and unfreezes the same map rows without duplicating them;
 * a REGULATORY standard refuses deactivation; a never-activated standard
 * leaves the active-gate closed; unknown code is a 404, unknown edition
 * and blank scope are validation errors.
 *
 * <p>Synthetic catalog rows only (codes with a SYN- prefix and a random
 * suffix, own-words titles, no regulatory content): they are deleted at
 * the end of each test, and their uniqueness also protects the shared
 * platform catalog across the surefire/failsafe JVMs of the full CI.
 */
class CertificationCoreIT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    private static CertificationService service;

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
        service = new CertificationService(ProvisionedTenant.appDs, SCHEMA, TENANT);
    }

    private static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static void insertStandard(String code, String standardClass) throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.cert_standard (catalog_key, code, title, standard_class,"
                            + " verification_seal, status) VALUES (?, ?, ?, ?, 'NAO_CONFIRMADA', 'VIGENTE')")) {
                ps.setString(1, "syn-" + code.toLowerCase());
                ps.setString(2, code);
                ps.setString(3, "Norma sintética de teste — sistema de gestão genérico (" + code + ")");
                ps.setString(4, standardClass);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.cert_standard_edition (standard_id, edition)"
                            + " SELECT id, 'ED-TESTE' FROM platform.cert_standard WHERE code = ?")) {
                ps.setString(1, code);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.cert_clause (edition_id, clause_number, title, summary, sort_order)"
                            + " SELECT e.id, ?, ?, ?, ? FROM platform.cert_standard_edition e"
                            + " JOIN platform.cert_standard s ON s.id = e.standard_id WHERE s.code = ?")) {
                ps.setString(1, "4.1");
                ps.setString(2, "Contexto em palavras próprias de teste");
                ps.setString(3, "Resumo próprio de teste para a cláusula de contexto.");
                ps.setInt(4, 1);
                ps.setString(5, code);
                ps.executeUpdate();
                ps.setString(1, "5.1");
                ps.setString(2, "Liderança em palavras próprias de teste");
                ps.setString(3, "Resumo próprio de teste para a cláusula de liderança.");
                ps.setInt(4, 2);
                ps.setString(5, code);
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    private static void deleteStandard(String code) throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            st.execute("DELETE FROM platform.cert_standard WHERE code = '" + code + "'");
            c.commit();
        }
    }

    private static CertificationService.ActivationView activateCycle(
            String code, UUID proposer, UUID approver, UUID accepter) {
        CertificationService.ActivationView proposed =
                service.activate(new CertificationService.ActivateInput(code, "Escopo de teste — unidade sintética", "ED-TESTE"), proposer);
        CertificationService.ActivationView approved = service.approve(proposed.id(), approver);
        return service.accept(approved.id(), accepter);
    }

    @Test
    @DisplayName("ciclo completo: propõe, aprova por outra pessoa, aceita, mapa copiado, evidência M:N, desliga sem apagar, reativa sem duplicar")
    void voluntaryLifecycle() throws SQLException {
        String code = uniq("SYN-VOL");
        UUID proposer = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        UUID accepter = UUID.randomUUID();
        UUID linker = UUID.randomUUID();
        try {
            insertStandard(code, "VOLUNTARY");
            assertThat(service.isStandardActive(code)).isFalse();

            CertificationService.ActivationView proposed = service.activate(
                    new CertificationService.ActivateInput(code, "Escopo de teste — unidade sintética", "ED-TESTE"), proposer);
            assertThat(proposed.status()).isEqualTo(CertificationService.ActivationStatus.PROPOSED);
            // Mapa vazio antes do aceite: só o aceite copia as cláusulas.
            assertThat(service.listClauseMap(proposed.id())).isEmpty();
            // Quem propôs não aprova.
            assertThatThrownBy(() -> service.approve(proposed.id(), proposer))
                    .isInstanceOf(ApiException.Forbidden.class);

            CertificationService.ActivationView active =
                    service.accept(service.approve(proposed.id(), approver).id(), accepter);
            assertThat(active.status()).isEqualTo(CertificationService.ActivationStatus.ACTIVE);
            assertThat(active.acceptanceHash()).isNotBlank();
            assertThat(active.acceptanceHash()).isEqualTo(CertificationService.acceptanceHash(
                    TENANT, active.id(), code, "ED-TESTE", "Escopo de teste — unidade sintética", accepter));
            assertThat(service.isStandardActive(code)).isTrue();
            assertThat(service.activeStandards()).contains(code);

            List<CertificationService.ClauseMapView> map = service.listClauseMap(active.id());
            assertThat(map).hasSize(2);
            assertThat(map).extracting(CertificationService.ClauseMapView::clauseNumber)
                    .containsExactly("4.1", "5.1");

            // Muitos-para-muitos: a mesma evidência serve às duas cláusulas.
            UUID evidenceId = UUID.randomUUID();
            service.linkEvidence(map.get(0).id(),
                    new CertificationService.EvidenceInput(CertificationService.EvidenceKind.DOCUMENT, evidenceId, "ref própria 1"), linker);
            service.linkEvidence(map.get(1).id(),
                    new CertificationService.EvidenceInput(CertificationService.EvidenceKind.DOCUMENT, evidenceId, "ref própria 1"), linker);
            assertThat(service.listEvidenceClauses(evidenceId)).hasSize(2);
            assertThat(service.listEvidence(map.get(0).id())).hasSize(1);
            // Duplicata exata é recusada.
            assertThatThrownBy(() -> service.linkEvidence(map.get(0).id(),
                    new CertificationService.EvidenceInput(CertificationService.EvidenceKind.DOCUMENT, evidenceId, "ref própria 1"), linker))
                    .isInstanceOf(ApiException.Conflict.class);

            // Desligar não apaga registro: tudo continua consultável, congelado.
            CertificationService.ActivationView inactive =
                    service.deactivate(active.id(), linker, "motivo de teste");
            assertThat(inactive.status()).isEqualTo(CertificationService.ActivationStatus.INACTIVE);
            assertThat(service.isStandardActive(code)).isFalse();
            assertThat(service.activeStandards()).doesNotContain(code);
            List<CertificationService.ClauseMapView> frozen = service.listClauseMap(active.id());
            assertThat(frozen).hasSize(2);
            assertThat(frozen).extracting(CertificationService.ClauseMapView::mapStatus)
                    .containsOnly("FROZEN");
            assertThat(service.listEvidence(map.get(0).id())).hasSize(1);
            assertThat(service.listEvidenceClauses(evidenceId)).hasSize(2);
            // Mapa congelado não recebe nova evidência.
            assertThatThrownBy(() -> service.linkEvidence(map.get(0).id(),
                    new CertificationService.EvidenceInput(CertificationService.EvidenceKind.RECORD, UUID.randomUUID(), null), linker))
                    .isInstanceOf(ApiException.Conflict.class);

            // Reativação: mesma linha, novo ciclo, mesmo mapa descongelado sem duplicar.
            CertificationService.ActivationView reactivated =
                    activateCycle(code, proposer, approver, accepter);
            assertThat(reactivated.id()).isEqualTo(active.id());
            assertThat(reactivated.status()).isEqualTo(CertificationService.ActivationStatus.ACTIVE);
            List<CertificationService.ClauseMapView> unfrozen = service.listClauseMap(active.id());
            assertThat(unfrozen).hasSize(2);
            assertThat(unfrozen).extracting(CertificationService.ClauseMapView::mapStatus)
                    .containsOnly("ACTIVE");
        } finally {
            deleteStandard(code);
        }
    }

    @Test
    @DisplayName("norma REGULATORY ativa recusa desligamento e continua ativa")
    void regulatoryCannotDeactivate() throws SQLException {
        String code = uniq("SYN-REG");
        try {
            insertStandard(code, "REGULATORY");
            CertificationService.ActivationView active =
                    activateCycle(code, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            assertThatThrownBy(() -> service.deactivate(active.id(), UUID.randomUUID(), "tentativa"))
                    .isInstanceOf(ApiException.Forbidden.class);
            assertThat(service.getActivation(active.id()).status())
                    .isEqualTo(CertificationService.ActivationStatus.ACTIVE);
            assertThat(service.isStandardActive(code)).isTrue();
        } finally {
            deleteStandard(code);
        }
    }

    @Test
    @DisplayName("código desconhecido é 404; edição desconhecida e escopo em branco são validação; norma nunca ligada deixa o portão fechado")
    void validationAndDisabledGate() throws SQLException {
        String code = uniq("SYN-GATE");
        try {
            insertStandard(code, "VOLUNTARY");
            assertThatThrownBy(() -> service.activate(
                    new CertificationService.ActivateInput("SYN-INEXISTENTE-" + UUID.randomUUID().toString().substring(0, 8),
                            "Escopo", "ED-TESTE"), UUID.randomUUID()))
                    .isInstanceOf(ApiException.NotFound.class);
            assertThatThrownBy(() -> service.activate(
                    new CertificationService.ActivateInput(code, "Escopo", "ED-INEXISTENTE"), UUID.randomUUID()))
                    .isInstanceOf(ApiException.Validation.class);
            assertThatThrownBy(() -> service.activate(
                    new CertificationService.ActivateInput(code, "   ", "ED-TESTE"), UUID.randomUUID()))
                    .isInstanceOf(ApiException.Validation.class);
            assertThatThrownBy(() -> service.getStandard("SYN-INEXISTENTE"))
                    .isInstanceOf(ApiException.NotFound.class);
            assertThatThrownBy(() -> service.linkEvidence(UUID.randomUUID(),
                    new CertificationService.EvidenceInput(CertificationService.EvidenceKind.DOCUMENT, UUID.randomUUID(), null),
                    UUID.randomUUID()))
                    .isInstanceOf(ApiException.NotFound.class);
            // Norma nunca ligada: portão fechado, fora da lista de ativas.
            assertThat(service.isStandardActive(code)).isFalse();
            assertThat(service.activeStandards()).doesNotContain(code);
            assertThat(service.listStandardsByClass(CertificationService.StandardClass.VOLUNTARY))
                    .extracting(CertificationService.StandardView::code).contains(code);
            // O aviso fixo acompanha o serviço.
            assertThat(CertificationService.DISCLAIMER).contains("não certifica");
        } finally {
            deleteStandard(code);
        }
    }
}
