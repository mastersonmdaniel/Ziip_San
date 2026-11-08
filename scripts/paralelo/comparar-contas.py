#!/usr/bin/env python3
# GL.2: comparador conta a conta do paralelo local (ferramenta de operacao).
#
# Compara as contas de UM ciclo do ZiipSan com as do sistema do piloto,
# conta a conta, PELA CHAVE DO CODIGO SUBSTITUTO (a correspondencia com a
# matricula real fica no piloto -- ADR-0033/GL.1; nenhum dado pessoal entra
# aqui: a chave e o codigo substituto e os valores sao centavos/consumo).
#
# Uso:
#   comparar-contas.py --ziipsan <contas.json> --piloto <contas.json> \
#     --criterio <criterio.json> [--explicacoes <explicacoes.json>] \
#     --saida <relatorio.json>
#
# Formato das contas (os dois lados, o mesmo):
#   {"contas": [{"codigoSubstituto": "SUB-...", "totalCentavos": 12345,
#                "consumoM3": 12.5, "vencimento": "AAAA-MM-DD"}, ...]}
#   consumoM3 e vencimento sao opcionais; quando presentes nos DOIS lados e
#   diferentes, viram divergencia do tipo correspondente (ver abaixo).
# Criterio (extraido da config validada; fonte: P0.30):
#   {"toleranciaCentavosPorConta": int, "toleranciaCentavosTotalCiclo": int,
#    "toleranciaAusencias": int}
# Explicacoes: {"<codigoSubstituto>": "<motivo nao vazio>", ...}
#
# Veredito (acceptance: "divergencia dentro do criterio da P0.30, TODA
# explicada"): aprovado somente se (i) toda diferenca de valor esta dentro
# das tolerancias (por conta, total do ciclo e numero de ausencias) E
# (ii) toda divergencia -- valor, ausencia, consumo ou vencimento -- tem
# explicacao nao vazia. Divergencia de consumo/vencimento nao tem tolerancia
# monetaria definida na spec: ela e registrada, exige explicacao e NAO reprova
# sozinha o criterio monetario (interpretacao declarada no plano da GL.2;
# quem define o criterio e a P0.30 -- se ela tratar consumo, o criterio
# passado a esta ferramenta deve refleti-lo nos totais).
#
# Saidas: 0 aprovado (inclusive zero divergencias); 1 fora do criterio;
# 2 divergencia sem explicacao (dentro do criterio monetario);
# 3 entrada invalida (JSON ilegivel, codigo duplicado, campo obrigatorio
# ausente) -- entrada invalida NUNCA vira "sem divergencia".
# O relatorio JSON e gravado sempre que as entradas sao parseaveis.
import argparse
import json
import sys


def carregar(path, rotulo):
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except Exception as exc:
        print(f"ERRO: {rotulo} ilegivel ({path}): {exc}", file=sys.stderr)
        return None


def indice_contas(doc, rotulo):
    """Retorna (dict codigo->conta, erro|None). Duplicidade e erro (saida 3)."""
    if not isinstance(doc, dict) or not isinstance(doc.get("contas"), list):
        return None, f"{rotulo}: esperado objeto com lista 'contas'"
    idx = {}
    for i, conta in enumerate(doc["contas"]):
        if not isinstance(conta, dict):
            return None, f"{rotulo}: conta #{i} nao e objeto"
        cod = conta.get("codigoSubstituto")
        if not (isinstance(cod, str) and cod.strip()):
            return None, f"{rotulo}: conta #{i} sem codigoSubstituto"
        if cod in idx:
            return None, f"{rotulo}: codigoSubstituto duplicado: {cod}"
        total = conta.get("totalCentavos")
        if not isinstance(total, int) or isinstance(total, bool):
            return None, f"{rotulo}: conta {cod} sem totalCentavos inteiro"
        idx[cod] = conta
    return idx, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ziipsan", required=True)
    ap.add_argument("--piloto", required=True)
    ap.add_argument("--criterio", required=True)
    ap.add_argument("--explicacoes")
    ap.add_argument("--saida", required=True)
    args = ap.parse_args()

    zip_doc = carregar(args.ziipsan, "ziipsan")
    pil_doc = carregar(args.piloto, "piloto")
    criterio = carregar(args.criterio, "criterio")
    if zip_doc is None or pil_doc is None or criterio is None:
        return 3
    for campo in ("toleranciaCentavosPorConta", "toleranciaCentavosTotalCiclo",
                  "toleranciaAusencias"):
        v = criterio.get(campo) if isinstance(criterio, dict) else None
        if not isinstance(v, int) or isinstance(v, bool) or v < 0:
            print(f"ERRO: criterio sem {campo} inteiro >= 0", file=sys.stderr)
            return 3

    explic = {}
    if args.explicacoes:
        doc = carregar(args.explicacoes, "explicacoes")
        if doc is None:
            return 3
        if not isinstance(doc, dict):
            print("ERRO: explicacoes: esperado objeto codigo->motivo", file=sys.stderr)
            return 3
        explic = {k: v for k, v in doc.items() if isinstance(v, str) and v.strip()}

    zidx, err = indice_contas(zip_doc, "ziipsan")
    if err:
        print(f"ERRO: {err}", file=sys.stderr)
        return 3
    pidx, err = indice_contas(pil_doc, "piloto")
    if err:
        print(f"ERRO: {err}", file=sys.stderr)
        return 3

    divergencias = []
    soma_diff = 0
    ausencias = 0
    iguais = 0
    for cod in sorted(set(zidx) | set(pidx)):
        z = zidx.get(cod)
        p = pidx.get(cod)
        if z is None or p is None:
            ausencias += 1
            divergencias.append({
                "codigoSubstituto": cod,
                "tipo": "AUSENTE_NO_ZIIPSAN" if z is None else "AUSENTE_NO_PILOTO",
                "diffCentavos": None,
                "explicada": cod in explic,
            })
            continue
        diff = abs(z["totalCentavos"] - p["totalCentavos"])
        soma_diff += diff
        tipos = []
        if diff > 0:
            tipos.append("VALOR")
        for campo, tipo in (("consumoM3", "CONSUMO"), ("vencimento", "VENCIMENTO")):
            if campo in z and campo in p and z[campo] != p[campo]:
                tipos.append(tipo)
        if not tipos:
            iguais += 1
        for tipo in tipos:
            divergencias.append({
                "codigoSubstituto": cod,
                "tipo": tipo,
                "diffCentavos": diff if tipo == "VALOR" else None,
                "explicada": cod in explic,
            })

    dentro = (
        all((d["diffCentavos"] or 0) <= criterio["toleranciaCentavosPorConta"]
            for d in divergencias)
        and soma_diff <= criterio["toleranciaCentavosTotalCiclo"]
        and ausencias <= criterio["toleranciaAusencias"]
    )
    toda_explicada = all(d["explicada"] for d in divergencias)
    aprovado = dentro and toda_explicada

    relatorio = {
        "contasZiipsan": len(zidx),
        "contasPiloto": len(pidx),
        "contasComparadas": len(set(zidx) | set(pidx)),
        "iguais": iguais,
        "divergencias": divergencias,
        "totalDivergencias": len(divergencias),
        "somaDiffCentavos": soma_diff,
        "ausencias": ausencias,
        "dentroCriterioP030": dentro,
        "todaDivergenciaExplicada": toda_explicada,
        "aprovado": aprovado,
    }
    with open(args.saida, "w", encoding="utf-8") as fh:
        json.dump(relatorio, fh, ensure_ascii=False, indent=1)

    if not dentro:
        return 1
    if not toda_explicada:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
