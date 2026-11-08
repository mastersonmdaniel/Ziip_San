package br.com.ziipsan.compliance.lgpd.privacy.web;

import br.com.ziipsan.compliance.lgpd.privacy.PrivacyFacade;
import org.springframework.http.MediaType;
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
 */
@RestController
@RequestMapping(path = "/privacidade", produces = MediaType.APPLICATION_JSON_VALUE)
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
