package br.com.ziipsan.commercial.billing.printing;

/**
 * Gerador do arquivo para a grafica (P2.8). Consome o conteudo minimo e as
 * mensagens legais EXCLUSIVAMENTE pela API da P2.32
 * ({@code BillContentService}): esta tarefa nao tem texto legal proprio, nao
 * rele o catalogo por conta propria e nao aprova modelo — quem aprova o
 * conteudo e a regra da P2.32, chamada para cada conta antes de gerar.
 */
public interface PrintFileService {

    /**
     * Gera o lote. Recusa o lote inteiro (sem arquivo parcial) quando
     * qualquer conta falha numa trava: modelo sem o conteudo minimo do
     * perfil, codigo de vinculacao ausente, braile marcado com o parametro
     * do tenant desligado ou valor incompativel com o leiaute. Lote pela
     * metade na grafica imprimiria contas fora do padrao legal — a falha e
     * do lote, nao da conta.
     */
    PrintFile generate(PrintFileRequest request);
}
