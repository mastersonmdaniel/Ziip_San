# P2.9 -- Testes da LOGICA do aparato (gerador e percentis). Rodam em
# qualquer maquina com python3, sem banco nem rede:
#   python3 -m unittest discover -s scripts/carga/testes -v
# Eles NAO executam a carga nem provam o aceite: provam que a massa e
# deterministica e que a conta do p95 e a combinada.
import importlib.util
import os
import sys
import tempfile
import unittest

AQUI = os.path.dirname(os.path.abspath(__file__))
CARGA = os.path.dirname(AQUI)


def carregar(nome, arquivo):
    spec = importlib.util.spec_from_file_location(nome, os.path.join(CARGA, arquivo))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


gerar = carregar("gerar_massa", "gerar-massa.py")
medir = carregar("medir_api", "medir-api.py")


class TestGerador(unittest.TestCase):
    def test_deterministico_mesma_semente_mesmo_hash(self):
        with tempfile.TemporaryDirectory() as d1, tempfile.TemporaryDirectory() as d2:
            m1 = gerar.gerar(d1, 2000, 700, "semente-teste")
            m2 = gerar.gerar(d2, 2000, 700, "semente-teste")
            self.assertEqual(m1["sha256"], m2["sha256"])

    def test_semente_diferente_hash_diferente(self):
        with tempfile.TemporaryDirectory() as d1, tempfile.TemporaryDirectory() as d2:
            m1 = gerar.gerar(d1, 500, 100, "a")
            m2 = gerar.gerar(d2, 500, 100, "b")
            self.assertNotEqual(m1["sha256"], m2["sha256"])

    def test_volumes_e_mistura_do_aceite_em_escala(self):
        with tempfile.TemporaryDirectory() as d:
            m = gerar.gerar(d, 300_000 // 100, 100_000 // 100, gerar.SEMENTE_PADRAO)
            self.assertEqual(m["ligacoes"], 3000)
            self.assertEqual(m["faturaveis"], 1000)
            self.assertEqual(m["por_categoria"], {"RES": 2400, "COM": 600})

    def test_uuids_unicos_e_formulas(self):
        vistos = set()
        for seq in range(1, 5001):
            u = gerar.uuid_deterministico("s", "unit", seq)
            self.assertNotIn(u, vistos)
            vistos.add(u)
            self.assertEqual(u[14], "5")  # versao forcada
            self.assertIn(u[19], "89ab")  # variante
            c = gerar.consumo(seq)
            if gerar.categoria(seq) == "COM":
                self.assertTrue(30 <= c <= 149)
            else:
                self.assertTrue(12 <= c <= 36)
            ant = gerar.leitura_anterior(seq)
            self.assertTrue(0 <= ant < 90000)
            self.assertLess(ant + c, 100000)  # mostrador de 5 digitos, sem virada


    def test_rota_comeca_em_001_e_cicla_nos_grupos(self):
        # seq 1 = ROTA-001 (correcao do deslocamento: a formula antiga
        # (seq % 100) + 1 mandava a seq 1 para ROTA-002); a rota casa com o
        # route_code do grupo CARGA-NNN semeado no 20-seed-tarifas-ciclo.sql
        self.assertEqual(gerar.rota(1), "ROTA-001")
        self.assertEqual(gerar.rota(100), "ROTA-100")
        self.assertEqual(gerar.rota(101), "ROTA-001")
        self.assertEqual(gerar.rota(300_000), "ROTA-100")


class TestPercentil(unittest.TestCase):
    def test_percentis(self):
        s = sorted(range(1, 101))
        # DEFINICAO CORRIGIDA (correcao pos-revisao, achado 18): nearest-rank,
        # a regua do k6 -- indice ceil(p/100 * n), 1-based. Esperados
        # RECALCULADOS sob a nova regua (a antiga, round(p/100*(n-1)),
        # dava p50=51): n=100 -> p50 e o 50o valor, p95 o 95o, p99 o 99o.
        self.assertEqual(medir.percentil(s, 50), 50)
        self.assertEqual(medir.percentil(s, 95), 95)
        self.assertEqual(medir.percentil(s, 99), 99)
        self.assertEqual(medir.percentil([], 95), None)

    def test_percentil_nearest_rank_amostra_pequena(self):
        # n=4: p50 -> ceil(0,50*4)=2o valor; p95 -> ceil(0,95*4)=4o valor
        s = [10, 20, 30, 40]
        self.assertEqual(medir.percentil(s, 50), 20)
        self.assertEqual(medir.percentil(s, 95), 40)


if __name__ == "__main__":
    unittest.main()
