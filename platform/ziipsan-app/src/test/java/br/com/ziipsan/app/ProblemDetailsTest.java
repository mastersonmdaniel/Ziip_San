package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Formato de erro da API (problem+json com errors[]/warnings[], como o frontend lê) e ausência de
 * detalhe interno nas respostas.
 *
 * <p>O controller de teste entra por {@code @Import}: o Spring Boot exclui do scan as classes internas
 * de classes de teste (TestTypeExcludeFilter).
 */
@Import(ProblemDetailsTest.ProblemTestController.class)
class ProblemDetailsTest extends AppIntegrationTestBase {

    /** Só existe nos testes: dispara cada tipo de erro. */
    @RestController
    static class ProblemTestController {

        record Body(@NotBlank(message = "Informe o nome.") String name) {
        }

        @GetMapping("/api/__test/validation")
        void validation() {
            throw ApiException.Validation.of(new ApiException.FieldError("lot", "RANGE", "Lote fora da faixa."));
        }

        @GetMapping("/api/__test/warning")
        void warning() {
            throw new ApiException.Validation("Confirme para continuar.", List.of(),
                    List.of(new ApiException.Warning("SEQ", "Sequência ocupada.", "tok-1")));
        }

        @GetMapping("/api/__test/not-found")
        void notFound() {
            throw new ApiException.NotFound("Imóvel não encontrado.");
        }

        @GetMapping("/api/__test/stale")
        void stale() {
            throw new OptimisticLockingFailureException("versão 3 esperada, encontrada 4");
        }

        @GetMapping("/api/__test/boom")
        void boom() {
            throw new IllegalStateException("SELECT * FROM customers WHERE document = '52998224725'");
        }

        @PostMapping("/api/__test/bean-validation")
        void beanValidation(@Valid @RequestBody Body body) {
        }
    }

    private String token() {
        return bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"));
    }

    @Test
    @DisplayName("validação: 422 com errors[] {field, code, message}")
    void validationProblem() throws Exception {
        mvc.perform(get("/api/__test/validation").header("Authorization", token()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("lot"))
                .andExpect(jsonPath("$.errors[0].code").value("RANGE"))
                .andExpect(jsonPath("$.errors[0].message").value("Lote fora da faixa."));
    }

    @Test
    @DisplayName("aviso confirmável: 422 com warnings[] e confirmToken")
    void warningProblem() throws Exception {
        mvc.perform(get("/api/__test/warning").header("Authorization", token()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:confirmation-required"))
                .andExpect(jsonPath("$.warnings[0].confirmToken").value("tok-1"));
    }

    @Test
    @DisplayName("404 e 409 (versão desatualizada) em problem+json")
    void notFoundAndConflict() throws Exception {
        mvc.perform(get("/api/__test/not-found").header("Authorization", token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Imóvel não encontrado."));
        mvc.perform(get("/api/__test/stale").header("Authorization", token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:stale-version"))
                .andExpect(content().string(not(containsString("versão 3"))));
    }

    @Test
    @DisplayName("erro inesperado: 500 genérico, sem SQL, dado ou stack trace na resposta")
    void unexpectedErrorHidesInternals() throws Exception {
        mvc.perform(get("/api/__test/boom").header("Authorization", token()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:internal-error"))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("52998224725"))))
                .andExpect(content().string(not(containsString("at br.com"))));
    }

    @Test
    @DisplayName("Bean Validation do corpo: 422 com o campo em errors[]")
    void beanValidationProblem() throws Exception {
        mvc.perform(post("/api/__test/bean-validation").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Informe o nome."));
    }
}
