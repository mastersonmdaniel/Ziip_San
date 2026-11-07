# P3.23 -- Testes da LOGICA do aparato de demonstracao e de ensaio.
# Rodam em qualquer maquina com python3, sem banco nem rede:
#   python3 -m unittest discover -s scripts/demo/testes -v
# Eles NAO sobem o ambiente nem executam a POC: provam que a massa e
# sintetica marcada e deterministica, que o piso de 10 mil unidades e
# imposto, e que o criterio de ensaio (50/162 VISAN; SAMAE 2.4/2.5 sem
# falha; tempo de preparo 5/7 dias uteis; cobertura completa) e a regua
# combinada -- com os esperados calculados a mao, nao copiados da saida.
import csv
import importlib.util
import os
import tempfile
import unittest
from datetime import date, datetime

AQUI = os.path.dirname(os.path.abspath(__file__))
DEMO = os.path.dirname(AQUI)


def carregar(nome, arquivo):
    spec = importlib.util.spec_from_file_location(nome, os.path.join(DEMO, arquivo))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


massa = carregar("gerar_massa_demo", "gerar-massa-demo.py")
ensaio = carregar("avaliar_ensaio", "avaliar-ensaio.py")


def dv_cpf_ok(numero: str) -> bool:
    """Conferencia INDEPENDENTE do DV (reimplementada no teste)."""
    n = [int(c) for c in numero if c.isdigit()]
    if len(n) != 11 or len(set(n)) == 1:
        return False
    for corte in (9, 10):
        soma = sum(n[i] * (corte + 1 - i) for i in range(corte))
        dv = (soma * 10) % 11
        if dv == 10:
            dv = 0
        if dv != n[corte]:
            return False
    return True


def dv_cnpj_ok(numero: str) -> bool:
    n = [int(c) for c in numero if c.isdigit()]
    if len(n) != 14 or len(set(n)) == 1:
        return False
    for corte, pesos in ((12, [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]),
                         (13, [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])):
        resto = sum(d * p for d, p in zip(n[:corte], pesos)) % 11
        dv = 0 if resto < 2 else 11 - resto
        if dv != n[corte]:
            return False
    return True


class TestGeradorDemo(unittest.TestCase):
    def test_recusa_abaixo_do_piso_do_aceite(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(ValueError):
                massa.gerar(d, 9_999, "semente-teste")

    def test_massa_no_piso_toda_marcada_sintetica(self):
        with tempfile.TemporaryDirectory() as d:
            m = massa.gerar(d, 10_000, "semente-teste")
            self.assertEqual(m["unidades"], 10_000)
            self.assertEqual(m["por_categoria"], {"RES": 8000, "COM": 2000})
            with open(os.path.join(d, "unidades.csv"), encoding="utf-8") as f:
                linhas = list(csv.DictReader(f))
            self.assertEqual(len(linhas), 10_000)
            documentos = set()
            for r in linhas:
                # A MARCA VIAJA COM O DADO (aceite: documento marcado).
                self.assertEqual(r["documento_sintetico"], "SIM")
                self.assertEqual(r["marca"], "SINTETICO")
                self.assertTrue(r["titular"].startswith("TITULAR SINTETICO "))
                self.assertIn("RUA SINTETICA", r["endereco"])
                self.assertIn("MUNICIPIO SINTETICO", r["endereco"])
                self.assertNotIn(r["documento"], documentos)
                documentos.add(r["documento"])
                if r["tipo_documento"] == "CPF":
                    self.assertEqual(r["categoria"], "RES")
                    self.assertTrue(dv_cpf_ok(r["documento"]), r["documento"])
                else:
                    self.assertEqual(r["categoria"], "COM")
                    self.assertTrue(dv_cnpj_ok(r["documento"]), r["documento"])

    def test_deterministico_mesma_semente_mesmo_hash(self):
        with tempfile.TemporaryDirectory() as d1, tempfile.TemporaryDirectory() as d2:
            m1 = massa.gerar(d1, 10_000, "semente-teste")
            m2 = massa.gerar(d2, 10_000, "semente-teste")
            self.assertEqual(m1["sha256"], m2["sha256"])
            m3 = massa.gerar(d2, 10_000, "outra-semente")
            self.assertNotEqual(m1["sha256"], m3["sha256"])


def linha(item, roteiro, secao, maximo, obtidos, resultado,
          requisito="REQ-1", tarefa="P3.23", teste="TesteX"):
    return {"item": item, "roteiro": roteiro, "secao": secao,
            "pontos_maximos": maximo, "pontos_obtidos": obtidos,
            "resultado": resultado, "requisito": requisito,
            "tarefa": tarefa, "teste": teste, "evidencia": "registro"}


def cenario(aprovados_visan=25, samae=("APROVADO",) * 4):
    """81 itens VISAN de 2 pontos (162) + 4 itens SAMAE 2.4/2.5 de 1 ponto."""
    linhas = []
    for i in range(81):
        ok = i < aprovados_visan
        linhas.append(linha(f"VISAN-{i + 1:02d}", "VISAN", "", 2,
                            2 if ok else 0, "APROVADO" if ok else "FALHOU"))
    for j, res in enumerate(samae):
        linhas.append(linha(f"SAMAE-2.{4 + (j % 2)}.{j + 1}", "SAMAE",
                            "2.4" if j % 2 == 0 else "2.5", 1,
                            1 if res == "APROVADO" else 0, res))
    return linhas


# Semana de referencia (conferida: 02/11/2026 e segunda, 06/11 sexta,
# 09/11 a segunda seguinte).
INI = datetime(2026, 11, 2, 0, 0)
FIM_SEMANA = datetime(2026, 11, 9, 0, 0)  # 5 dias uteis depois, a meia-noite


class TestDiasUteis(unittest.TestCase):
    def test_semana_inteira_a_meia_noite_sao_5(self):
        self.assertAlmostEqual(
            ensaio.dias_uteis_decorridos(INI, FIM_SEMANA, set()), 5.0, places=3)

    def test_mesmo_dia_util_so_a_fracao(self):
        # quarta 04/11, 09h -> 17h = 8/24 = 0,3333...
        v = ensaio.dias_uteis_decorridos(
            datetime(2026, 11, 4, 9), datetime(2026, 11, 4, 17), set())
        self.assertAlmostEqual(v, 8 / 24, places=3)

    def test_fim_de_semana_nao_conta(self):
        v = ensaio.dias_uteis_decorridos(
            datetime(2026, 11, 7, 9), datetime(2026, 11, 8, 17), set())
        self.assertEqual(v, 0.0)

    def test_feriado_explicito_nao_conta(self):
        # segunda 02/11 como feriado declarado: seg 00h -> ter 00h = 0
        v = ensaio.dias_uteis_decorridos(
            INI, datetime(2026, 11, 3, 0), {date(2026, 11, 2)})
        self.assertAlmostEqual(v, 0.0, places=3)


class TestCriterioEnsaio(unittest.TestCase):
    def test_exatos_50_pontos_aprova(self):
        r = ensaio.avaliar(cenario(25), INI, FIM_SEMANA, set())
        self.assertEqual(r["visan"]["pontos_obtidos"], 50)
        self.assertEqual(r["visan"]["veredito"], "APROVADO")
        self.assertEqual(r["samae_secoes_2_4_e_2_5"]["veredito"], "APROVADO")
        self.assertEqual(r["veredito_global"], "APROVADO")

    def test_48_pontos_reprova(self):
        r = ensaio.avaliar(cenario(24), INI, FIM_SEMANA, set())
        self.assertEqual(r["visan"]["pontos_obtidos"], 48)
        self.assertEqual(r["visan"]["veredito"], "REPROVADO")
        self.assertEqual(r["veredito_global"], "REPROVADO_OU_INCOMPLETO")

    def test_falha_no_samae_reprova_mesmo_com_visan_alto(self):
        r = ensaio.avaliar(cenario(81, ("APROVADO", "FALHOU", "APROVADO", "APROVADO")),
                           INI, FIM_SEMANA, set())
        self.assertEqual(r["visan"]["veredito"], "APROVADO")
        self.assertEqual(r["samae_secoes_2_4_e_2_5"]["veredito"], "REPROVADO")
        self.assertEqual(r["veredito_global"], "REPROVADO_OU_INCOMPLETO")

    def test_samae_nao_executado_nao_comprova(self):
        r = ensaio.avaliar(cenario(81, ("APROVADO", "NAO_EXECUTADO", "APROVADO", "APROVADO")),
                           INI, FIM_SEMANA, set())
        self.assertEqual(r["samae_secoes_2_4_e_2_5"]["veredito"], "NAO_COMPROVADO")
        self.assertEqual(r["veredito_global"], "REPROVADO_OU_INCOMPLETO")

    def test_estrutura_visan_divergente_e_inconclusiva(self):
        linhas = [linha("VISAN-01", "VISAN", "", 100, 100, "APROVADO"),
                  linha("SAMAE-2.4.1", "SAMAE", "2.4", 1, 1, "APROVADO")]
        r = ensaio.avaliar(linhas, INI, FIM_SEMANA, set())
        self.assertEqual(r["visan"]["veredito"], "INCONCLUSIVO_ESTRUTURA")
        self.assertEqual(r["veredito_global"], "REPROVADO_OU_INCOMPLETO")

    def test_estouro_do_tempo_de_preparo_reprova(self):
        # seg 02/11 00h -> seg 16/11 00h = 10 dias uteis > 7
        r = ensaio.avaliar(cenario(81), INI, datetime(2026, 11, 16, 0), set())
        self.assertEqual(r["tempo_de_preparo"]["orcamentos_dias_uteis"],
                         {"5": "FORA", "7": "FORA"})
        self.assertEqual(r["veredito_global"], "REPROVADO_OU_INCOMPLETO")

    def test_cobertura_incompleta_reprova(self):
        linhas = cenario(81)
        linhas[0]["teste"] = ""
        r = ensaio.avaliar(linhas, INI, FIM_SEMANA, set())
        self.assertEqual(r["cobertura"]["veredito"], "INCOMPLETA")
        self.assertEqual(r["veredito_global"], "REPROVADO_OU_INCOMPLETO")

    def test_samae_bloqueado_nao_comprova(self):
        # Revisao cruzada P3.23: BLOQUEADO, como NAO_EXECUTADO, nao comprova.
        r = ensaio.avaliar(cenario(81, ("APROVADO", "BLOQUEADO", "APROVADO", "APROVADO")),
                           INI, FIM_SEMANA, set())
        self.assertEqual(r["samae_secoes_2_4_e_2_5"]["veredito"], "NAO_COMPROVADO")
        self.assertEqual(r["veredito_global"], "REPROVADO_OU_INCOMPLETO")

    def test_exatos_7_dias_uteis_dentro_do_maior_orcamento(self):
        # Revisao cruzada P3.23: seg 02/11 00h -> qua 11/11 00h = 7 dias
        # uteis em (inicio, fim]: 3,4,5,6,9,10,11. Fronteira exata do
        # orcamento maior: DENTRO de 7, FORA de 5, e o global aprova.
        r = ensaio.avaliar(cenario(81), INI, datetime(2026, 11, 11, 0), set())
        self.assertAlmostEqual(r["tempo_de_preparo"]["dias_uteis_decorridos"], 7.0, places=3)
        self.assertEqual(r["tempo_de_preparo"]["orcamentos_dias_uteis"],
                         {"5": "FORA", "7": "DENTRO"})
        self.assertEqual(r["veredito_global"], "APROVADO")

    def test_pontuacao_parcial_em_aprovado_e_erro_de_formato(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "r.csv")
            with open(p, "w", newline="", encoding="utf-8") as f:
                w = csv.writer(f, lineterminator="\n")
                w.writerow(["item", "roteiro", "secao", "pontos_maximos",
                            "pontos_obtidos", "resultado", "requisito",
                            "tarefa", "teste", "evidencia", "duracao_seg"])
                w.writerow(["VISAN-01", "VISAN", "", 2, 1, "APROVADO",
                            "REQ-1", "P3.23", "T", "e", 60])
            with self.assertRaises(ValueError):
                ensaio.carregar_resultados(p)


if __name__ == "__main__":
    unittest.main()
