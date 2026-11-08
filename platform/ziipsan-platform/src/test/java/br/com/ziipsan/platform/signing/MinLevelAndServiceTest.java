package br.com.ziipsan.platform.signing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Nivel minimo por tenant/tipo e os portoes do SignatureService. */
class MinLevelAndServiceTest {

    private SignatureService service(SigningParameters params, SignatureService.SignatureStore store) {
        var simple = new SimpleDocumentSigner(
                (t, u, c, v, h) -> Optional.of(SigningTestSupport.acceptance(h)),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
        return SigningTestSupport.service(params, List.of(simple), store);
    }

    @Test
    @DisplayName("nivel minimo: sem sign.min_level do tipo, recusa (a definicao e do ente, nao presumida)")
    void missingMinLevelRefuses() {
        var policy = new MinLevelPolicy(new SigningTestSupport.MapParameters());
        assertThatThrownBy(() -> policy.requiredLevel(
                SigningTestSupport.TENANT, SignableDocumentType.ADHESION_CONTRACT))
                .hasMessageContaining("sign.min_level.adhesion_contract");
    }

    @Test
    @DisplayName("nivel minimo: valor fora de SIMPLE/ADVANCED/QUALIFIED recusa; hierarquia da lei vale")
    void invalidMinLevelRefusesAndHierarchyHolds() {
        var params = new SigningTestSupport.MapParameters()
                .withMinLevel(SignableDocumentType.LAB_REPORT, "FORTE");
        var policy = new MinLevelPolicy(params);
        assertThatThrownBy(() -> policy.requiredLevel(SigningTestSupport.TENANT, SignableDocumentType.LAB_REPORT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(SignatureLevel.QUALIFIED.satisfies(SignatureLevel.ADVANCED)).isTrue();
        assertThat(SignatureLevel.SIMPLE.satisfies(SignatureLevel.QUALIFIED)).isFalse();
    }

    @Test
    @DisplayName("servico: pretendido abaixo do minimo do tenant recusa; no minimo, assina e persiste")
    void serviceEnforcesMinimum() {
        var params = new SigningTestSupport.MapParameters()
                .withMinLevel(SignableDocumentType.ADHESION_CONTRACT, "ADVANCED");
        var store = new SigningTestSupport.MapStore();
        var service = service(params, store);
        assertThatThrownBy(() -> service.sign(new SignatureService.SignCommand(
                SignatureLevel.SIMPLE, SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT))))
                .hasMessageContaining("abaixo do minimo");
        params.withMinLevel(SignableDocumentType.ADHESION_CONTRACT, "SIMPLE");
        DocumentSigner.SignatureRecord record = service.sign(new SignatureService.SignCommand(
                SignatureLevel.SIMPLE, SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT)));
        assertThat(store.byCode).containsKey(record.verificationCode());
    }

    @Test
    @DisplayName("servico: documento de entrega ao tenant sem decisao do dono recusa; com decisao (com proveniencia), segue")
    void tenantDeliveryNeedsOwnerDecision() {
        var params = new SigningTestSupport.MapParameters()
                .withMinLevel(SignableDocumentType.TENANT_DELIVERY_DOCUMENT, "SIMPLE");
        var service = service(params, new SigningTestSupport.MapStore());
        assertThatThrownBy(() -> service.sign(new SignatureService.SignCommand(
                SignatureLevel.SIMPLE, SigningTestSupport.contextForSimple(SignableDocumentType.TENANT_DELIVERY_DOCUMENT))))
                .hasMessageContaining("decisao do dono");
        params.withTenantDeliveryDecision();
        assertThat(service.sign(new SignatureService.SignCommand(
                SignatureLevel.SIMPLE, SigningTestSupport.contextForSimple(SignableDocumentType.TENANT_DELIVERY_DOCUMENT))))
                .isNotNull();
    }

    @Test
    @DisplayName("decisao do dono (D8): decisao registrada mas DESABILITADORA tambem recusa; e sem proveniencia nem se constroi")
    void tenantDeliveryDecisionProvenance() {
        var params = new SigningTestSupport.MapParameters()
                .withMinLevel(SignableDocumentType.TENANT_DELIVERY_DOCUMENT, "SIMPLE");
        params.tenantDeliveryDecision = Optional.of(new SigningParameters.TenantDeliveryDecision(
                false, "dono-da-plataforma (fixture)", SigningTestSupport.NOW, "registro-sintetico"));
        var service = service(params, new SigningTestSupport.MapStore());
        assertThatThrownBy(() -> service.sign(new SignatureService.SignCommand(
                SignatureLevel.SIMPLE, SigningTestSupport.contextForSimple(SignableDocumentType.TENANT_DELIVERY_DOCUMENT))))
                .hasMessageContaining("decisao do dono");
        // Proveniencia e parte do tipo: decisao sem quem/quando/referencia e impossivel de representar.
        assertThatThrownBy(() -> new SigningParameters.TenantDeliveryDecision(
                true, " ", SigningTestSupport.NOW, "ref"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SigningParameters.TenantDeliveryDecision(
                true, "dono", null, "ref"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SigningParameters.TenantDeliveryDecision(
                true, "dono", SigningTestSupport.NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
