package br.com.ziipsan.commercial.fiscal.arquivo;

import java.util.Optional;

/**
 * Costura fina do arquivamento com retenção (P2.18) sobre a {@code HashChain}
 * do projeto — a cadeia de hashes encadeados citada no aceite da tarefa
 * ("hash na {@code HashChain}").
 *
 * <p><b>Localização honesta:</b> a classe {@code HashChain} original é a dos
 * MTRs puros do diário de bordo (12/10/2026: "{@code MtrStateMachine},
 * {@code HashChain} e {@code MtrCanonical} puros"). Nas árvores do repositório
 * atual que esta tarefa conseguiu espelhar antes do HTTP 429 do conector
 * ({@code shared/ziipsan-core}, {@code shared/ziipsan-security} e
 * {@code platform/ziipsan-platform} — onde existe o vizinho
 * {@code platform/audit/AuditTrailIntegrityService}), <b>nenhuma classe com
 * esse nome foi encontrada</b>; as árvores de {@code operations/} e
 * {@code integration/} não puderam ser conferidas (ver RELATORIO-P2.18.md).
 * Por isso, no mesmo desenho das portas da P2.6, esta porta declara o contrato
 * mínimo de que o arquivamento precisa e a integração a liga à implementação
 * real da cadeia, onde ela estiver:
 *
 * <ul>
 *   <li>{@link #registrar} acrescenta um elo cujo conteúdo é o SHA-256 do XML
 *       arquivado, ligado ao elo anterior, e devolve o hash do elo gravado;</li>
 *   <li>{@link #consultar} devolve o hash do elo de uma referência, para a
 *       leitura conferir que o elo continua igual ao gravado no registro de
 *       retenção.</li>
 * </ul>
 *
 * <p><b>O que esta porta prova — e o que não prova:</b> {@link #consultar}
 * recebe só a referência, <b>não recebe o SHA-256</b>. Por isso, na leitura,
 * a conferência possível com esta porta é a coerência <b>registro × elo</b>
 * (o elo consultado é igual ao elo do registro), somada à conferência
 * <b>bytes × SHA-256 do registro</b> que o serviço faz por conta própria.
 * Esta porta <b>não</b> permite conferir elo × conteúdo (elo × SHA-256
 * recalculado dos bytes): se registro e cadeia forem adulterados de forma
 * consistente, esta camada não detecta — a barreira de conteúdo é o SHA-256
 * contra o registro. Ampliar {@link #consultar} para receber o hash (ou
 * expor uma verificação elo × conteúdo) é <b>pendência nomeada da
 * integração</b>, a decidir com a implementação real da cadeia.
 *
 * <p>Nenhuma segunda cadeia de hash é criada aqui: sem uma implementação desta
 * porta ligada, o serviço não arquiva (a porta é obrigatória no construtor).
 */
public interface HashChainPort {

    /**
     * Acrescenta à cadeia um elo com o SHA-256 do conteúdo arquivado.
     *
     * @param referencia  referência estável do objeto (a chave de retenção)
     * @param sha256Conteudo SHA-256 do XML arquivado (64 hex minúsculos)
     * @return hash do elo gravado na cadeia (64 hex minúsculos)
     */
    String registrar(String referencia, String sha256Conteudo);

    /** Hash do elo gravado para a referência, se houver. */
    Optional<String> consultar(String referencia);
}
