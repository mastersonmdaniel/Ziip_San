#!/usr/bin/env python3
"""Validador do manifesto do tenant de pre-treinamento (ED-11).

Aplica, fora da JVM, a MESMA regua de `PreTrainingTenant` (produto, modulo
da plataforma): origem massa sintetica ou clone mascarado da ED-09 com
finalidade TRAINING, marca de sintetico, varredura da P0.45 em zero,
fiscal de producao desligado e, no clone, o portao da ED-09 (origem
producao, sem clone de clone, avaliacao do RIPD do tenant de origem,
com avaliador `assessedBy` — campo nao-nulo do record da ED-09 —,
viavel e anterior ao pedido). Massa sintetica com bloco de clone
associado e recusada: massa sintetica nao tem clone.

Uso:
    python3 validar-ambiente.py manifesto.json

Saida: um objeto JSON em stdout com `pronto` (bool) e `bloqueadores`
(lista de textos, os mesmos motivos da regua do produto).
Codigo de saida: 0 = pronto; 2 = bloqueado ou manifesto invalido.
"""
import json
import sys
from datetime import datetime


def _parse_instant(texto):
    return datetime.fromisoformat(texto.replace("Z", "+00:00"))


def validar(manifesto):
    bloqueadores = []
    if not isinstance(manifesto, dict):
        return ["Manifesto precisa ser um objeto JSON."]
    for campo in ("tenantId", "source", "synthetic", "sweepViolationCount",
                  "fiscalProductionAllowed"):
        if campo not in manifesto:
            bloqueadores.append(f"Campo obrigatorio ausente no manifesto: {campo}")
    if bloqueadores:
        return bloqueadores

    if manifesto["source"] not in ("SYNTHETIC_MASS", "MASKED_CLONE"):
        bloqueadores.append(f"Origem fora do dominio: {manifesto['source']}")
    if manifesto["synthetic"] is not True:
        bloqueadores.append(
            "O tenant de pre-treinamento precisa estar marcado como sintetico.")
    varredura = manifesto["sweepViolationCount"]
    if not isinstance(varredura, int) or varredura < 0:
        bloqueadores.append("A contagem da varredura precisa ser um inteiro >= 0.")
    elif varredura != 0:
        bloqueadores.append(
            f"Varredura da P0.45 com {varredura} violacao(oes): ambiente de "
            "treinamento nao recebe turma com residuo de dado pessoal.")
    if manifesto["fiscalProductionAllowed"] is not False:
        bloqueadores.append(
            "Emissao fiscal de producao ligada no tenant de pre-treinamento: "
            "o ambiente de treinamento roda com fiscal de producao desligado.")

    if manifesto["source"] == "SYNTHETIC_MASS" and manifesto.get("clone") is not None:
        bloqueadores.append(
            "Origem massa sintetica com bloco de clone associado: massa "
            "sintetica nao tem clone — clone so existe em MASKED_CLONE.")

    if manifesto["source"] == "MASKED_CLONE":
        clone = manifesto.get("clone")
        if not isinstance(clone, dict):
            bloqueadores.append("Origem clone mascarado sem o bloco do clone da ED-09.")
        else:
            if clone.get("purpose") != "TRAINING":
                bloqueadores.append(
                    "O clone precisa ter a finalidade TRAINING (PLT-39): clone de "
                    "homologacao nao e ambiente de treinamento.")
            if clone.get("sourceIsProduction") is not True:
                bloqueadores.append(
                    "O clone nasce da producao do tenant; a origem informada nao e producao.")
            if clone.get("sourceIsClone") is True:
                bloqueadores.append(
                    "Clone de clone e recusado: a origem precisa ser o tenant de producao.")
            avaliacao = clone.get("assessment")
            if not isinstance(avaliacao, dict):
                bloqueadores.append(
                    "Sem avaliacao do risco de reidentificacao registrada no RIPD "
                    "do tenant: nenhum clone e liberado (a avaliacao precede o "
                    "primeiro clone).")
            else:
                if avaliacao.get("tenantId") != clone.get("sourceTenantId"):
                    bloqueadores.append("A avaliacao do RIPD e de outro tenant.")
                if not avaliacao.get("assessedBy"):
                    bloqueadores.append(
                        "A avaliacao do RIPD nao informa o avaliador (assessedBy): "
                        "o registro da ED-09 exige tenant, instante e avaliador.")
                if avaliacao.get("viable") is not True:
                    bloqueadores.append(
                        "A avaliacao registrada no RIPD concluiu risco inaceitavel "
                        "de reidentificacao.")
                if not avaliacao.get("ripdReference"):
                    bloqueadores.append(
                        "A referencia do registro no RIPD e obrigatoria.")
                pedido = clone.get("requestedAt")
                avaliado = avaliacao.get("assessedAt")
                if pedido and avaliado:
                    try:
                        if _parse_instant(avaliado) > _parse_instant(pedido):
                            bloqueadores.append(
                                "A avaliacao do RIPD e posterior ao pedido: ela "
                                "precisa preceder o clone.")
                    except ValueError:
                        bloqueadores.append(
                            "Instantes do clone/RIPD fora do formato ISO-8601.")
    return bloqueadores


def main(argv):
    if len(argv) != 2:
        print(json.dumps({"pronto": False,
                          "bloqueadores": ["Uso: validar-ambiente.py <manifesto.json>"]},
                         ensure_ascii=False))
        return 2
    try:
        with open(argv[1], encoding="utf-8") as arq:
            manifesto = json.load(arq)
    except (OSError, json.JSONDecodeError) as exc:
        print(json.dumps({"pronto": False,
                          "bloqueadores": [f"Manifesto ilegivel: {exc.__class__.__name__}"]},
                         ensure_ascii=False))
        return 2
    bloqueadores = validar(manifesto)
    print(json.dumps({"pronto": not bloqueadores, "bloqueadores": bloqueadores},
                     ensure_ascii=False))
    return 0 if not bloqueadores else 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
