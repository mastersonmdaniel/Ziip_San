package br.com.ziipsan.platform.signing.web;

import br.com.ziipsan.platform.signing.SignatureService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Pagina de validacao publica (aceite da P3.34): o destino do QR impresso no
 * documento. Devolve o JSON publico da verificacao (resultado, nivel,
 * signatario MASCARADO, instante, hash e o link do VALIDAR do ITI) — nunca o
 * conteudo do documento, a evidencia interna ou o signatario integral
 * (correcao D10/LGPD). Rota publica por desenho: o codigo de verificacao e a
 * credencial (80 bits de entropia, {@code VerificationCodes}), e o servico
 * limita as tentativas por codigo e por origem (correcao D7).
 *
 * <p><b>Origem do limite (requisito de fiacao, D7):</b> a chave de origem e o
 * {@code X-Forwarded-For} — que so e confiavel atras de proxy reverso
 * confiavel que o sobrescreva. A implantacao NAO pode expor este endpoint
 * direto ao cliente confiando neste header (ele e falsificavel e a origem
 * real deve vir do proxy/{@code remoteAddr} na fiacao); sem origem
 * confiavel, vale apenas o limite por codigo.
 */
@RestController
@RequestMapping(path = "/public/signature-validations", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicSignatureValidationController {

    private final SignatureService.SignatureFacade facade;

    public PublicSignatureValidationController(SignatureService.SignatureFacade facade) {
        this.facade = Objects.requireNonNull(facade, "facade e obrigatoria");
    }

    @GetMapping("/{verificationCode}")
    public String validate(@PathVariable String verificationCode,
                           @RequestHeader(value = "X-Forwarded-For", required = false) String sourceIp) {
        return facade.publicValidationJson(verificationCode, sourceIp);
    }
}
