package br.com.ziipsan.commercial.fiscal.arquivo;

import java.util.Map;
import java.util.Optional;

/**
 * Costura fina do arquivamento com retenção (P2.18) sobre a interface de
 * armazenamento de objetos da P0.45:
 * {@code br.com.ziipsan.platform.infra.storage.ObjectStorage} (módulo
 * {@code platform/ziipsan-platform}), cuja implementação local é o
 * {@code S3ObjectStorage} (HTTP + SigV4, sem SDK) contra o emulador de S3
 * SeaweedFS do perfil {@code storage} (e o {@code LocalFileObjectStorage}).
 *
 * <p><b>Não é uma segunda abstração de armazenamento, e NÃO é um adaptador
 * 1:1.</b> É a porta do módulo fiscal no mesmo desenho das portas da P2.6
 * ({@code NfageSignerGateway}, {@code NfageDocumentoStore}): o fiscal não
 * depende do módulo de plataforma no {@code pom.xml}. A revisão cruzada da
 * P2.18 leu a {@code ObjectStorage} real (HEAD {@code 0fbcd22}) e a sua
 * assinatura exata é apenas:
 *
 * <pre>{@code
 * void put(String bucket, String key, InputStream content, String contentType);
 * Optional<InputStream> get(String bucket, String key);
 * boolean exists(String bucket, String key);
 * void delete(String bucket, String key); // idempotente
 * }</pre>
 *
 * <p>Contra esta porta, o adaptador da integração é uma <b>composição com
 * perdas e decisões próprias</b>, em quatro pontos verificados:
 *
 * <ul>
 *   <li><b>Não há gravação condicional nem atômica na interface real</b> —
 *       nenhum put condicional, nenhum {@code If-None-Match} declarado.
 *       {@link #gravarSeAusente} terá de ser composto pelo adaptador como
 *       {@code exists} + {@code put}: uma sequência <b>check-then-put com
 *       janela de corrida real</b> entre a conferência e a gravação. Essa
 *       corrida é um fato conhecido da interface, não uma hipótese, e a forma
 *       de tratá-la é <b>decisão pendente nomeada da integração</b> (ex.:
 *       serializar por documento/chave, ou compor e tratar o conflito
 *       detectado). Esta porta <b>não promete atomicidade</b>: a garantia
 *       anti-sobrescrita desta camada é a do serviço (registro prévio +
 *       {@code existe} + tradução da {@link ObjetoJaGravadoException} quando
 *       o adaptador detecta a ocupação); entre processos, ela só vale na
 *       medida da decisão da integração acima;</li>
 *   <li><b>Não há metadados no {@code put} real</b> — só {@code contentType}.
 *       O mapa de metadados desta porta (SHA-256, {@code retido-ate},
 *       origem etc.) <b>não tem para onde ir</b> pela interface real: quem os
 *       guarda é o {@link RegistroRetencaoXml} (retrato/retenção desta
 *       camada), não o objeto; se a integração quiser metadados no objeto,
 *       precisará de caminho fora desta interface;</li>
 *   <li><b>Não há bucket nesta porta</b>: a chave única
 *       {@code retencao/nfage/<tenant>/…} terá de ser dividida em
 *       bucket/chave pelo adaptador;</li>
 *   <li><b>O {@code put} real devolve {@code void}</b>: a {@code uriRetencao}
 *       que {@link #gravarSeAusente} devolve terá de ser <b>sintetizada</b>
 *       pelo adaptador, não vem do armazenamento.</li>
 * </ul>
 *
 * <p>Mapeamento restante: {@link #ler} → {@code get} (lendo o
 * {@code InputStream} até os bytes); {@link #existe} → {@code exists};
 * {@link #apagar} → {@code delete}. <b>O serviço de retenção nunca chama
 * {@code apagar} para objeto retido</b> — ele existe na porta porque existe
 * na interface da P0.45, e os testes provam (contador no duplo) que a recusa
 * acontece antes de qualquer delegação. Como o {@code delete} real é livre e
 * idempotente, <b>nada nesta camada impede um {@code delete}/{@code put}
 * direto por outro chamador da {@code ObjectStorage}</b>: a garantia é só
 * desta camada de serviço.
 *
 * <p>A retenção (Object Lock) de verdade em produção é do S3 gerenciado na
 * LC.5; no emulador local, quem garante a imutabilidade é a camada de
 * arquivamento ({@link XmlAutorizadoRetencaoService}): gravação só se
 * ausente nesta porta, nenhuma remoção delegada e leitura com conferência
 * de SHA-256 contra o registro.
 */
public interface ArmazenamentoObjetosPort {

    /**
     * Objeto já gravado na chave: o adaptador detectou a chave ocupada e o
     * serviço traduz a ocupação em recusa de sobrescrita. Lançar esta
     * exceção é o meio de o adaptador sinalizar a corrida check-then-put
     * (a interface real não oferece sinal equivalente).
     */
    class ObjetoJaGravadoException extends RuntimeException {
        public ObjetoJaGravadoException(String chave) {
            super("Objeto já gravado na chave (ocupação detectada; sobrescrita recusada): " + chave);
        }
    }

    /**
     * Grava o objeto somente se a chave estiver livre, na semântica desta
     * porta. Sobre a interface real da P0.45 isto é uma composição
     * {@code exists} + {@code put} (check-then-put), <b>sem atomicidade
     * prometida</b> — ver o javadoc da interface.
     *
     * @param chave     chave do objeto no armazenamento (determinística, do serviço)
     * @param conteudo  bytes do objeto (o XML autorizado, sem transformação)
     * @param metadados metadados pretendidos (SHA-256, chave de acesso, retenção);
     *                  a interface real não os recebe — ficam no registro de
     *                  retenção desta camada
     * @return URI do objeto gravado, sintetizada pelo adaptador
     *         (o {@code put} real devolve {@code void})
     * @throws ObjetoJaGravadoException se a chave já estiver ocupada, inclusive
     *         quando a ocupação é detectada na corrida check-then-put
     */
    String gravarSeAusente(String chave, byte[] conteudo, Map<String, String> metadados);

    /** Bytes do objeto da chave, se existir. */
    Optional<byte[]> ler(String chave);

    /** Verdadeiro se a chave estiver ocupada. */
    boolean existe(String chave);

    /**
     * Remove o objeto da chave. Existe porque a interface da P0.45 o expõe;
     * o arquivamento com retenção <b>nunca</b> o invoca sobre objeto retido.
     */
    void apagar(String chave);
}
