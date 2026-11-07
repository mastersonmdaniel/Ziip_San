package br.com.ziipsan.platform.poc;

import br.com.ziipsan.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P3.23: critério de ensaio de POC do P3, como regra pura e testável.
 *
 * <p>Os números são os do aceite da P3.23 (nenhum é parâmetro nem foi
 * escolhido aqui): o roteiro da VISAN da P1.25 soma
 * {@value #VISAN_PONTOS_TOTAIS} pontos e o ensaio do P3 exige pelo menos
 * {@value #VISAN_MINIMO_P3}; as seções 2.4 e 2.5 do SAMAE precisam passar
 * <em>sem falha</em>; e o tempo de preparo é comparado com os dois tempos
 * que os editais de referência concedem, 5 e 7 dias úteis.
 *
 * <p>Regras de honestidade, espelhadas no avaliador de script
 * ({@code scripts/demo/avaliar-ensaio.py}, com os mesmos casos conferidos
 * à mão nos dois conjuntos de testes):
 * <ul>
 *   <li>se os itens VISAN apresentados não somam 162 pontos máximos, o
 *       veredito VISAN é {@code INCONCLUSIVO_ESTRUTURA} — o arquivo não é
 *       o roteiro da P1.25 e nenhuma aprovação sai por aproximação;</li>
 *   <li>item das seções 2.4/2.5 do SAMAE não executado ou bloqueado deixa
 *       o critério {@code NAO_COMPROVADO}: não se declara "sem falha" o
 *       que não foi executado;</li>
 *   <li>item executado sem a ligação requisito–tarefa–teste torna a
 *       cobertura {@code INCOMPLETA} e o ensaio não aprova: o relatório
 *       de cobertura é parte do aceite, não um anexo opcional.</li>
 * </ul>
 *
 * <p>Exemplo calculado à mão (repetido no teste): 81 itens VISAN de 2
 * pontos, 25 aprovados → 50 pontos → VISAN aprovada no limite exato;
 * 24 aprovados → 48 → reprovada.
 */
public final class PocEnsaioCriterion {

    public static final int VISAN_PONTOS_TOTAIS = 162;
    public static final int VISAN_MINIMO_P3 = 50;
    public static final List<String> SAMAE_SECOES_SEM_FALHA = List.of("2.4", "2.5");
    public static final List<Integer> ORCAMENTOS_DIAS_UTEIS = List.of(5, 7);

    public enum ItemResultado {APROVADO, FALHOU, NAO_EXECUTADO, BLOQUEADO}

    public enum Veredito {APROVADO, REPROVADO, NAO_COMPROVADO, INCONCLUSIVO_ESTRUTURA}

    /**
     * Um item de roteiro como registrado na matriz viva: a ligação
     * requisito–tarefa–teste é o que alimenta o relatório de cobertura.
     */
    public record Item(String item, String roteiro, String secao,
                       int pontosMaximos, int pontosObtidos,
                       ItemResultado resultado,
                       String requisito, String tarefa, String teste) {
        public Item {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("Item de roteiro é obrigatório");
            }
            // Revisão cruzada P3.23: roteiro nulo/em branco era aceito e a
            // seção nula explodia em NullPointerException dentro de
            // SAMAE_SECOES_SEM_FALHA.contains(...) (List.of rejeita null).
            // Roteiro é obrigatório; seção nula vira "" (sem seção), como
            // no avaliador de script, em que "" simplesmente não é 2.4/2.5.
            if (roteiro == null || roteiro.isBlank()) {
                throw new IllegalArgumentException(
                        "Roteiro é obrigatório no item " + item);
            }
            if (secao == null) {
                secao = "";
            }
            if (resultado == null) {
                throw new IllegalArgumentException(
                        "Resultado é obrigatório no item " + item);
            }
            if (pontosMaximos < 0 || pontosObtidos < 0 || pontosObtidos > pontosMaximos) {
                throw new IllegalArgumentException(
                        "Pontos fora do intervalo [0, máximos] no item " + item);
            }
            if (resultado == ItemResultado.APROVADO && pontosObtidos != pontosMaximos) {
                throw new IllegalArgumentException(
                        "Item APROVADO exige pontosObtidos == pontosMaximos (item " + item
                                + "): pontuação parcial não é aprovação");
            }
            if (resultado == ItemResultado.FALHOU && pontosObtidos != 0) {
                throw new IllegalArgumentException(
                        "Item FALHOU exige pontosObtidos == 0 (item " + item + ")");
            }
        }

        boolean executado() {
            return resultado == ItemResultado.APROVADO || resultado == ItemResultado.FALHOU;
        }

        boolean comCobertura() {
            return requisito != null && !requisito.isBlank()
                    && tarefa != null && !tarefa.isBlank()
                    && teste != null && !teste.isBlank();
        }
    }

    public record Avaliacao(int visanPontosMaximos, int visanPontosObtidos,
                            Veredito visan, Veredito samae,
                            double diasUteisDecorridos, boolean dentroDoMaiorOrcamento,
                            boolean coberturaCompleta, boolean aprovado) {

        /** JSON do veredito, sempre pela classe {@code Json} do core. */
        public String toJson() {
            Map<String, Object> mapa = new LinkedHashMap<>();
            mapa.put("visanPontosMaximos", visanPontosMaximos);
            mapa.put("visanPontosObtidos", visanPontosObtidos);
            mapa.put("visanMinimoP3", VISAN_MINIMO_P3);
            mapa.put("visan", visan.name());
            mapa.put("samaeSecoes24e25", samae.name());
            mapa.put("diasUteisDecorridos", diasUteisDecorridos);
            mapa.put("dentroDoMaiorOrcamento", dentroDoMaiorOrcamento);
            mapa.put("coberturaCompleta", coberturaCompleta);
            mapa.put("aprovado", aprovado);
            return Json.write(mapa);
        }
    }

    private PocEnsaioCriterion() {
    }

    public static Avaliacao avaliar(List<Item> itens, double diasUteisDecorridos) {
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Ensaio sem itens não é avaliável");
        }
        if (!Double.isFinite(diasUteisDecorridos) || diasUteisDecorridos < 0) {
            throw new IllegalArgumentException("Dias úteis decorridos inválidos: "
                    + diasUteisDecorridos);
        }
        int visanMax = 0;
        int visanObt = 0;
        List<Item> samaeCriterio = new ArrayList<>();
        boolean algumExecutado = false;
        for (Item i : itens) {
            if ("VISAN".equals(i.roteiro())) {
                visanMax += i.pontosMaximos();
                if (i.resultado() == ItemResultado.APROVADO) {
                    visanObt += i.pontosObtidos();
                }
            }
            if ("SAMAE".equals(i.roteiro()) && SAMAE_SECOES_SEM_FALHA.contains(i.secao())) {
                samaeCriterio.add(i);
            }
            if (i.executado()) {
                algumExecutado = true;
            }
        }
        boolean coberturaCompleta = algumExecutado && itens.stream()
                .filter(Item::executado).allMatch(Item::comCobertura);

        Veredito visan;
        if (visanMax != VISAN_PONTOS_TOTAIS) {
            visan = Veredito.INCONCLUSIVO_ESTRUTURA;
        } else {
            visan = visanObt >= VISAN_MINIMO_P3 ? Veredito.APROVADO : Veredito.REPROVADO;
        }

        Veredito samae;
        if (samaeCriterio.stream().anyMatch(i -> i.resultado() == ItemResultado.FALHOU)) {
            samae = Veredito.REPROVADO;
        } else if (samaeCriterio.isEmpty() || samaeCriterio.stream()
                .anyMatch(i -> i.resultado() != ItemResultado.APROVADO)) {
            samae = Veredito.NAO_COMPROVADO;
        } else {
            samae = Veredito.APROVADO;
        }

        boolean dentroDoMaior = diasUteisDecorridos <= ORCAMENTOS_DIAS_UTEIS.getLast();
        boolean aprovado = visan == Veredito.APROVADO && samae == Veredito.APROVADO
                && dentroDoMaior && coberturaCompleta;
        return new Avaliacao(visanMax, visanObt, visan, samae, diasUteisDecorridos,
                dentroDoMaior, coberturaCompleta, aprovado);
    }
}
