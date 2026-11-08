#!/usr/bin/env python3
# GL.2: validador da configuracao do paralelo local (ferramenta de operacao).
#
# Uso: validar-config-paralelo.py <config-paralelo.json>
# Sai 0 e imprime CONFIG_OK se a configuracao satisfaz TODOS os portoes de
# forma do acceptance da GL.2; sai 1 listando cada erro no stderr.
#
# Portoes (fail-closed; nenhum valor e presumido):
#   - tenantId: string em formato UUID;
#   - ciclos: INTEIRO 2 ou 3 (o acceptance diz "dois ou tres ciclos").
#     Correcao D1 da revisao: o tipo e exigido (isinstance int, sem bool),
#     nao so a igualdade numerica -- em Python, 2.0 in (2, 3) e True e
#     "2"/2.0 NAO sao o inteiro 2; float/string/bool sao recusados;
#   - fiscal: tpAmb INTEIRO == 2 (mesmo rigor de tipo de D1: 2.0 e "2"
#     recusados) E ambienteFiscal em {SIMULADOR, HOMOLOGACAO_SEFAZ}
#     -- NFAg-e no paralelo SO no simulador ou na homologacao da SEFAZ (P3.7);
#     tpAmb=1 (producao) e recusado sempre;
#   - perfilRegulatorio (P2.31, RAS-01): agencia e perfilId nao vazios,
#     ativo == true, vigencia cobrindo a data do primeiro ciclo e
#     conferidoEm ESTRITAMENTE ANTES do primeiro ciclo
#     (conferidoEm < dataPrimeiroCiclo; correcao R1 da revisao: a spec
#     diz "vigencia conferida ANTES do primeiro ciclo" -- conferencia no
#     MESMO dia do 1o ciclo e recusada, sem interpretacao silenciosa);
#   - criterioDivergencia: fonte literal "P0.30" e as tres tolerancias
#     (por conta, total do ciclo, ausencias) inteiras >= 0. O CRITERIO em si
#     e decisao registrada na P0.30 -- este validador NAO inventa numero:
#     sem o bloco, ou com fonte diversa, a configuracao e recusada;
#   - conteudoMinimo: fonte literal "P2.32" e perfilConteudo nao vazio;
#   - operacao (P3.30): unidadeOrganizacional e escopoArea nao vazios;
#   - cargaGl1.registroCarga: caminho nao vazio do registro CONCLUIDO da
#     GL.1 (a existencia e o conteudo sao conferidos pelo orquestrador).
import json
import re
import sys
from datetime import date

UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)


def parse_data(valor):
    if not isinstance(valor, str):
        return None
    try:
        return date.fromisoformat(valor)
    except ValueError:
        return None


def nao_vazio(valor):
    return isinstance(valor, str) and valor.strip() != ""


def tol_ok(valor):
    return isinstance(valor, int) and not isinstance(valor, bool) and valor >= 0


def int_puro(valor):
    # Correcao D1: inteiro de verdade -- bool e subclasse de int em
    # Python e float iguala o inteiro (2.0 == 2); nenhum dos dois passa.
    return isinstance(valor, int) and not isinstance(valor, bool)


def main():
    if len(sys.argv) != 2:
        print("uso: validar-config-paralelo.py <config-paralelo.json>", file=sys.stderr)
        return 1
    try:
        cfg = json.load(open(sys.argv[1], encoding="utf-8"))
    except Exception as exc:  # JSON ilegivel = configuracao invalida
        print(f"ERRO: config ilegivel: {exc}", file=sys.stderr)
        return 1
    if not isinstance(cfg, dict):
        print("ERRO: config nao e um objeto JSON", file=sys.stderr)
        return 1

    erros = []

    tid = cfg.get("tenantId")
    if not (isinstance(tid, str) and UUID_RE.match(tid)):
        erros.append("tenantId ausente ou nao e string em formato UUID")

    ciclos = cfg.get("ciclos")
    if not int_puro(ciclos) or ciclos not in (2, 3):
        erros.append("ciclos deve ser o INTEIRO 2 ou 3 (acceptance: dois ou tres "
                     "ciclos); float (2.0), string (\"2\") e bool sao recusados")

    primeiro = parse_data(cfg.get("dataPrimeiroCiclo"))
    if primeiro is None:
        erros.append("dataPrimeiroCiclo ausente ou nao e data ISO (AAAA-MM-DD)")

    tpamb = cfg.get("tpAmb")
    if not int_puro(tpamb) or tpamb != 2:
        erros.append("tpAmb deve ser o INTEIRO 2: NFAg-e no paralelo so no simulador ou na "
                     "homologacao da SEFAZ (P3.7); producao (tpAmb=1) e recusada; "
                     "float (2.0) e string (\"2\") tambem sao recusados")
    if cfg.get("ambienteFiscal") not in ("SIMULADOR", "HOMOLOGACAO_SEFAZ"):
        erros.append("ambienteFiscal deve ser SIMULADOR ou HOMOLOGACAO_SEFAZ")

    perfil = cfg.get("perfilRegulatorio")
    if not isinstance(perfil, dict):
        erros.append("perfilRegulatorio (P2.31) ausente")
    else:
        if not nao_vazio(perfil.get("agencia")):
            erros.append("perfilRegulatorio.agencia vazio (agencia do piloto, P2.31)")
        if not nao_vazio(perfil.get("perfilId")):
            erros.append("perfilRegulatorio.perfilId vazio")
        if perfil.get("ativo") is not True:
            erros.append("perfilRegulatorio.ativo deve ser true (perfil ativo antes do 1o ciclo)")
        ini = parse_data(perfil.get("vigenciaInicio"))
        fim = parse_data(perfil.get("vigenciaFim")) if perfil.get("vigenciaFim") else None
        conf = parse_data(perfil.get("conferidoEm"))
        if ini is None:
            erros.append("perfilRegulatorio.vigenciaInicio ausente ou invalida")
        if perfil.get("vigenciaFim") and fim is None:
            erros.append("perfilRegulatorio.vigenciaFim invalida")
        if conf is None:
            erros.append("perfilRegulatorio.conferidoEm ausente ou invalido "
                         "(a vigencia tem de ser conferida antes do primeiro ciclo)")
        if primeiro is not None and ini is not None and ini > primeiro:
            erros.append("perfilRegulatorio fora de vigencia no 1o ciclo (inicio depois dele)")
        if primeiro is not None and fim is not None and fim < primeiro:
            erros.append("perfilRegulatorio fora de vigencia no 1o ciclo (fim antes dele)")
        if primeiro is not None and conf is not None and conf >= primeiro:
            erros.append("perfilRegulatorio conferido NO DIA ou DEPOIS do 1o ciclo "
                         "(correcao R1: a conferencia e ESTRITAMENTE antes do "
                         "primeiro ciclo — conferidoEm < dataPrimeiroCiclo)")

    crit = cfg.get("criterioDivergencia")
    if not isinstance(crit, dict):
        erros.append("criterioDivergencia ausente: o criterio e o da P0.30 e nao "
                     "pode ser presumido por esta ferramenta")
    else:
        if crit.get("fonte") != "P0.30":
            erros.append("criterioDivergencia.fonte deve ser 'P0.30'")
        for campo in ("toleranciaCentavosPorConta", "toleranciaCentavosTotalCiclo",
                      "toleranciaAusencias"):
            if not tol_ok(crit.get(campo)):
                erros.append(f"criterioDivergencia.{campo} deve ser inteiro >= 0")

    conteudo = cfg.get("conteudoMinimo")
    if not isinstance(conteudo, dict):
        erros.append("conteudoMinimo (P2.32) ausente")
    else:
        if conteudo.get("fonte") != "P2.32":
            erros.append("conteudoMinimo.fonte deve ser 'P2.32'")
        if not nao_vazio(conteudo.get("perfilConteudo")):
            erros.append("conteudoMinimo.perfilConteudo vazio")

    operacao = cfg.get("operacao")
    if not isinstance(operacao, dict):
        erros.append("operacao (P3.30) ausente")
    else:
        if not nao_vazio(operacao.get("unidadeOrganizacional")):
            erros.append("operacao.unidadeOrganizacional vazio (P3.30)")
        if not nao_vazio(operacao.get("escopoArea")):
            erros.append("operacao.escopoArea vazio (P3.30)")

    carga = cfg.get("cargaGl1")
    if not isinstance(carga, dict) or not nao_vazio(carga.get("registroCarga")):
        erros.append("cargaGl1.registroCarga vazio: o paralelo parte da carga da GL.1")

    if erros:
        for e in erros:
            print(f"ERRO: {e}", file=sys.stderr)
        return 1
    print("CONFIG_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
