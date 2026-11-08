package br.com.ziipsan.compliance.lgpd.privacy.web;

import br.com.ziipsan.compliance.lgpd.privacy.PrivacyFacade;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Telas do encarregado no backoffice — item de menu "Privacidade" (aceite da P3.40),
 * visível ao perfil de fábrica de encarregado de dados da P1.37: relatório (pedidos por
 * tipo/situação/prazo, consentimentos por finalidade, operações, RIPD, alertas de acesso
 * anômalo), pendências de cobertura do registro de operações e os registros em si, sobre
 * o que o módulo já modela ({@code lgpd_data_subject_request}, {@code lgpd_consent},
 * {@code lgpd_dpia}, {@code lgpd_retention_schedule}).
 *
 * <p>A fila em si é a caixa "Minhas tarefas" da P2.28 (tipo {@code HOLDER_REQUEST});
 * este controller não duplica a caixa — expõe o relatório e as pendências que a
 * alimentam de contexto. A restrição de visibilidade ao perfil é da camada de
 * autorização da aplicação (catálogo da P0.13), ligada na integração.
 *
 * <p>Autorização (correção RG-LOTE2/S3, revisão geral de 08/11/2026): a
 * checagem na {@code SecurityConfig} do app composto (main, HEAD 19f5890)
 * mostrou que nenhuma das três cadeias tem matcher para
 * {@code /privacidade/**} — a rota só cairia no {@code anyRequest().denyAll()}
 * da cadeia resource server quando o módulo entrar no scan, sem regra
 * desenhada para ela. A proteção, então, é declarada aqui, no padrão
 * dominante do projeto ({@code @PreAuthorize} no controller, como nos demais
 * controllers protegidos e na correção análoga da P3.34): toda a classe
 * exige usuário autenticado. O catálogo de permissões da P0.13 conferido na
 * main NÃO tem permissão de privacidade/LGPD, e o perfil de fábrica
 * {@code ENCARREGADO_DADOS} só carrega {@code registry:read} e
 * {@code user:read} — não existe autoridade real de encarregado/DPO para
 * referenciar sem inventar nome. Limitação registrada para a integração:
 * enquanto o catálogo não ganhar a permissão do encarregado, qualquer
 * usuário autenticado do tenant passa neste portão; a troca por
 * {@code hasAuthority(...)} é de uma linha quando a permissão existir.
 * A anotação só tem efeito com o módulo no scan da aplicação e o
 * {@code @EnableMethodSecurity} da {@code SecurityConfig} (que já existe).
 */
@RestController
@RequestMapping(path = "/privacidade", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("isAuthenticated()")
public class DpoBackofficeController {

    private final PrivacyFacade facade;

    public DpoBackofficeController(PrivacyFacade facade) {
        this.facade = Objects.requireNonNull(facade, "facade é obrigatória");
    }

    /** Relatório do encarregado completo. */
    @GetMapping("/relatorio")
    public String report() {
        return facade.dpoReportJson();
    }

    /** Colunas classificadas como dado pessoal sem operação de tratamento associada. */
    @GetMapping("/pendencias-cobertura")
    public String coverageGaps() {
        return facade.coverageGapsJson();
    }

    /** Registro de operações de tratamento (art. 37) do tenant, com versões. */
    @GetMapping("/operacoes")
    public String processingRecords() {
        return facade.processingRecordsJson();
    }
}
