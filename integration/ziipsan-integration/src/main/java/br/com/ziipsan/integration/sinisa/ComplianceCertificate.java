package br.com.ziipsan.integration.sinisa;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Registro do Certificado de Adimplencia com o Fornecimento de Dados ao SINISA
 * (aceite da P4.2: "Certificado de Adimplencia registrado com data").
 *
 * <p>Fundamento: a adimplencia com o fornecimento de dados ao SINISA e condicao
 * para o acesso a recursos publicos federais e financiamentos com recursos da
 * Uniao (Lei 11.445/2007, art. 50; Decreto 11.599/2023, art. 7o, V, comprovada por
 * certidao emitida pelo SINISA; Portaria MCID 1.069/2025, art. 7o, paragrafos 3o
 * e 4o: a adimplencia esta condicionada a finalizacao do preenchimento dos
 * formularios). A divulgacao do certificado ocorre ate 31 de dezembro do ano da
 * coleta (Portaria MCID 1.069/2025, calendario do ciclo).
 *
 * <p>A certidao oficial e <b>por tipo de servico</b> (agua, esgoto, residuos,
 * aguas pluviais) e vale ate o lancamento da base de dados do ano de referencia
 * seguinte: por isso o registro guarda o servico e a data de emissao, e a
 * validade e expressa pela regra (ano-base seguinte), nao por uma data final
 * inventada. Registro sem data e recusado: sem a data nao ha como provar a
 * adimplencia no tempo.
 *
 * @param baseYear           ano-base (ano de referencia) do ciclo certificado
 * @param serviceKind        tipo de servico da certidao (ex.: {@code AGUA}, {@code ESGOTO})
 * @param issuedOn           data de emissao/divulgacao do certificado (obrigatoria)
 * @param documentReference  referencia do documento oficial (ex.: numero da certidao
 *                           ou do processo), quando houver; nunca fabricada
 */
public record ComplianceCertificate(
        int baseYear,
        String serviceKind,
        LocalDate issuedOn,
        String documentReference) {

    public ComplianceCertificate {
        if (baseYear < 2000 || baseYear > 2200) {
            throw new IllegalArgumentException("Certificado SINISA: ano-base fora do intervalo: " + baseYear);
        }
        if (serviceKind == null || serviceKind.isBlank()) {
            throw new IllegalArgumentException(
                    "Certificado SINISA sem tipo de servico: a certidao e emitida por tipo de servico");
        }
        Objects.requireNonNull(issuedOn,
                "Certificado SINISA sem data: o registro exige a data do certificado (aceite da P4.2)");
        if (documentReference != null && documentReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Certificado SINISA: referencia do documento em branco; deixe nula se nao houver");
        }
    }
}
