package br.com.ziipsan.platform.trustcenter.web;

import br.com.ziipsan.platform.trustcenter.TrustCenterService;
import br.com.ziipsan.platform.trustcenter.TrustCenterSource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * P3.38: endpoint público do Trust Center —
 * {@code GET /api/v1/public/trust-center} (mesma família pública da
 * página de status da P3.18).
 *
 * <p>Público de verdade: sem autenticação e sem nenhum dado de tenant,
 * de relato ou de pesquisador — a visão montada por
 * {@link TrustCenterService} só contém suboperadores publicáveis,
 * certificações do provedor com a fonte, e as demais seções com o estado
 * honesto (fato ou pendência declarada). O corpo é o JSON da
 * {@code Json} do core via {@code TrustCenterService.toJson}; a página
 * estática em {@code docs/trust-center/} consome este endpoint e, na
 * LC.7, é publicada com a página de status em hospedagem separada da
 * produção apontando para ele.
 *
 * <p>O {@code /.well-known/security.txt} NÃO é servido por este
 * controller: ele é arquivo da hospedagem estática (RFC 9116 o quer no
 * domínio, gerado por {@code SecurityTxt} a partir da política
 * configurada no ato da publicação — ver {@code docs/trust-center/}).
 *
 * <p>A segurança da aplicação precisa liberar esta rota sem
 * autenticação na integração (padrão das rotas {@code /api/v1/public/*});
 * isso é fiação declarada no relatório, não uma anotação inventada aqui.
 */
@RestController
public class PublicTrustCenterController {

    private final TrustCenterSource source;

    public PublicTrustCenterController(TrustCenterSource source) {
        this.source = source;
    }

    @GetMapping(value = "/api/v1/public/trust-center", produces = MediaType.APPLICATION_JSON_VALUE)
    public String trustCenter() {
        TrustCenterService.TrustCenterView view = TrustCenterService.buildView(
                source.subprocessors(), source.providerCertifications(), source.pentestSummary(),
                source.dpoName(), source.dpoContact(), source.disclosurePolicy(),
                source.asvsEvidenceAvailable(), Instant.now());
        return TrustCenterService.toJson(view);
    }
}
