#!/usr/bin/env python3
"""GL.1 — validador do manifesto de carga da homologação local.

Ferramenta de operação (não é código de produto): lê o manifesto JSON da
carga com o parser padrão da linguagem e confere a FORMA e as REGRAS que
não dependem do banco:

  * campos obrigatórios presentes (tenant, código substituto declarado,
    totais do piloto, origem dos XML, camadas do cadastro técnico);
  * `tenantId` tem de ser string em formato UUID (número é recusado);
  * XML: na homologação local só `SINTETICO_CERTIFICADO_TESTE` ou `NENHUM`
    — XML real é recusado aqui (só entra na carga de produção, LC.10);
  * camadas do GIS: o `tipo` é normalizado (maiúsculas, sem espaços das
    pontas) e tem de pertencer ao conjunto CONHECIDO de tipos de camada —
    tipo desconhecido é recusado (fail-closed: grafia mista/minúscula ou
    tipo inventado NÃO escapa dos portões, correção D1 da revisão);
  * camadas pessoais do GIS (ligação, ramal, cavalete, imóvel) exigem
    método de anonimização registrado: SHIFTED_WITHIN_BLOCK,
    AGGREGATED_BY_BLOCK ou SYNTHETIC (ADR-0033);
  * rede real (redes, peças e estruturas) exige acesso restrito declarado
    com grupo E responsáveis nomeados (lista não vazia) — sem isso, a
    carga da rede real é recusada (correção D7 da revisão).

Saída: uma linha `ERRO: ...` por problema (stderr) e código 1; ou a linha
`MANIFESTO_OK` e código 0. Não imprime nenhum valor do manifesto além de
contagens — o manifesto não deve conter dado pessoal (a correspondência
código substituto ↔ matrícula fica só no piloto, roteiro da P0.45).
"""
import json
import re
import sys

METODOS_ANONIMIZACAO = {"SHIFTED_WITHIN_BLOCK", "AGGREGATED_BY_BLOCK", "SYNTHETIC"}
TIPOS_PESSOAIS_GIS = {"LIGACAO", "RAMAL", "CAVALETE", "IMOVEL"}
TIPOS_CONHECIDOS_GIS = TIPOS_PESSOAIS_GIS | {"REDE_REAL"}
ORIGENS_XML_LOCAL = {"SINTETICO_CERTIFICADO_TESTE", "NENHUM"}
UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)


def main() -> int:
    if len(sys.argv) != 2:
        print("ERRO: uso: validar-manifesto.py <manifesto.json>", file=sys.stderr)
        return 1
    try:
        with open(sys.argv[1], encoding="utf-8") as fh:
            m = json.load(fh)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ERRO: manifesto ilegível ou JSON inválido: {exc}", file=sys.stderr)
        return 1
    if not isinstance(m, dict):
        print("ERRO: manifesto deve ser um objeto JSON", file=sys.stderr)
        return 1

    erros = []

    tenant_id = m.get("tenantId")
    if not tenant_id:
        erros.append("tenantId ausente")
    elif not isinstance(tenant_id, str) or not UUID_RE.match(tenant_id.strip()):
        erros.append(
            "tenantId deve ser string em formato UUID "
            "(valor numérico ou fora do formato é recusado)"
        )
    if m.get("codigoSubstituto") is not True:
        erros.append(
            "codigoSubstituto deve ser true: a matrícula do piloto entra só "
            "como código substituto e a correspondência fica no piloto (P0.45)"
        )
    totais = m.get("totaisPiloto")
    if not isinstance(totais, dict):
        erros.append("totaisPiloto ausente (totais do sistema do piloto para conciliação)")
    else:
        for campo in ("unidades", "saldoTotalCentavos", "registrosHistorico", "xmlAceitos"):
            valor = totais.get(campo)
            if not isinstance(valor, int) or isinstance(valor, bool) or valor < 0:
                erros.append(f"totaisPiloto.{campo} deve ser inteiro >= 0")

    origem_xml = m.get("xmlOrigem")
    if origem_xml not in ORIGENS_XML_LOCAL:
        erros.append(
            "xmlOrigem deve ser SINTETICO_CERTIFICADO_TESTE ou NENHUM na "
            "homologação local; XML real só na carga de produção (LC.10)"
        )

    camadas = m.get("camadasGis")
    if not isinstance(camadas, list) or not camadas:
        erros.append("camadasGis ausente ou vazio (cadastro técnico da P3.26)")
    else:
        for i, camada in enumerate(camadas):
            if not isinstance(camada, dict) or not camada.get("nome") or not camada.get("tipo"):
                erros.append(f"camadasGis[{i}] sem nome/tipo")
                continue
            tipo_bruto = camada["tipo"]
            if not isinstance(tipo_bruto, str):
                erros.append(f"camadasGis[{i}] com tipo não textual — recusado (fail-closed)")
                continue
            # D1: normaliza a grafia antes de comparar; sem isso, "ramal"
            # escapava da exigência de método de anonimização.
            tipo = tipo_bruto.strip().upper()
            if tipo not in TIPOS_CONHECIDOS_GIS:
                erros.append(
                    f"camadasGis[{i}] com tipo desconhecido '{tipo_bruto}' — recusado "
                    "(fail-closed): tipos conhecidos são "
                    + ", ".join(sorted(TIPOS_CONHECIDOS_GIS))
                )
                continue
            if tipo in TIPOS_PESSOAIS_GIS and camada.get("metodoAnonimizacao") not in METODOS_ANONIMIZACAO:
                erros.append(
                    f"camadasGis[{i}] ({tipo}) sem método de anonimização válido "
                    "(SHIFTED_WITHIN_BLOCK | AGGREGATED_BY_BLOCK | SYNTHETIC) — ADR-0033"
                )
            if tipo == "REDE_REAL":
                acesso = camada.get("acessoRestrito")
                if not isinstance(acesso, dict) or not acesso.get("grupo"):
                    erros.append(
                        f"camadasGis[{i}] (REDE_REAL) sem acessoRestrito.grupo declarado — "
                        "a rede real do piloto entra com acesso restrito (ADR-0033)"
                    )
                else:
                    responsaveis = acesso.get("responsaveis")
                    if (
                        not isinstance(responsaveis, list)
                        or not responsaveis
                        or any(not isinstance(r, str) or not r.strip() for r in responsaveis)
                    ):
                        erros.append(
                            f"camadasGis[{i}] (REDE_REAL) sem acessoRestrito.responsaveis "
                            "nomeados (lista não vazia) — acesso restrito exige grupo E "
                            "responsáveis declarados (ADR-0033)"
                        )

    if erros:
        for erro in erros:
            print(f"ERRO: {erro}", file=sys.stderr)
        return 1
    print("MANIFESTO_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
