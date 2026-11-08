package br.com.ziipsan.commercial.billing.servicecatalog;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCatalogParametersTest {

    @Test @DisplayName("required_documents via Json do core, com validade")
    void requiredDocuments() {
        var docs = ServiceCatalogParameters.requiredDocuments(
                "[{\"code\":\"RG\",\"name\":\"Documento de identidade\",\"validityMonths\":null},"
                        + "{\"code\":\"MATRICULA\",\"name\":\"Matricula do imovel\",\"validityMonths\":12}]");
        assertThat(docs).hasSize(2);
        assertThat(docs.get(1).validityMonths()).isEqualTo(12);
    }

    @Test @DisplayName("workflow e signature_policy via Json; esgoto simultaneo booleano")
    void workflowAndPolicy() {
        var wf = ServiceCatalogParameters.workflow(
                "[{\"code\":\"A\",\"name\":\"a\",\"dependsOn\":[]},{\"code\":\"B\",\"name\":\"b\",\"dependsOn\":[\"A\"],\"deadlineHours\":72}]");
        assertThat(wf.step("B").deadlineHours()).isEqualTo(72);
        var pol = ServiceCatalogParameters.signaturePolicy("{\"CERTIDAO\":\"QUALIFIED\"}");
        assertThat(pol.levelFor("CERTIDAO")).isEqualTo(SignaturePolicy.Level.QUALIFIED);
        assertThat(ServiceCatalogParameters.newRequiresSewerWhenAvailable("true")).isTrue();
    }

    @Test @DisplayName("ausencia e erro explicito (MissingParameterException), nunca padrao assumido")
    void missingIsExplicit() {
        assertThatThrownBy(() -> ServiceCatalogParameters.requiredDocuments(null)).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> ServiceCatalogParameters.signaturePolicy("")).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> ServiceCatalogParameters.requiredDocuments(
                "[{\"code\":\"X\",\"name\":\"x\",\"requiresNotarizedSignature\":true}]"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
