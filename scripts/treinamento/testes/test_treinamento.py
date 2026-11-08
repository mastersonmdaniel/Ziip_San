#!/usr/bin/env python3
"""Testes do validador de ambiente da ED-11 (mesma regua de PreTrainingTenant)."""
import importlib.util
import pathlib
import unittest

AQUI = pathlib.Path(__file__).resolve()
MOD = AQUI.parent.parent / "validar-ambiente.py"
spec = importlib.util.spec_from_file_location("validar_ambiente", MOD)
validar_ambiente = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validar_ambiente)
validar = validar_ambiente.validar

TENANT = "00000000-0000-0000-0000-000000000301"
PROD = "00000000-0000-0000-0000-000000000302"


def massa():
    return {"tenantId": TENANT, "source": "SYNTHETIC_MASS", "synthetic": True,
            "sweepViolationCount": 0, "fiscalProductionAllowed": False, "clone": None}


def clone(purpose="TRAINING", viable=True, assessed="2026-11-01T10:00:00Z",
          requested="2026-11-10T10:00:00Z", assessment_tenant=PROD):
    return {"tenantId": TENANT, "source": "MASKED_CLONE", "synthetic": True,
            "sweepViolationCount": 0, "fiscalProductionAllowed": False,
            "clone": {"sourceTenantId": PROD, "sourceIsProduction": True,
                      "sourceIsClone": False, "purpose": purpose,
                      "requestedAt": requested,
                      "assessment": {"tenantId": assessment_tenant,
                                     "assessedAt": assessed,
                                     "assessedBy": "00000000-0000-0000-0000-000000000303",
                                     "viable": viable,
                                     "ripdReference": "RIPD-EXEMPLO-SINTETICO"}}}


class TestValidarAmbiente(unittest.TestCase):
    def test_massa_sintetica_pronta(self):
        self.assertEqual(validar(massa()), [])

    def test_clone_treinamento_pronto(self):
        self.assertEqual(validar(clone()), [])

    def test_clone_sem_ripd_bloqueia(self):
        m = clone()
        m["clone"]["assessment"] = None
        self.assertTrue(any("RIPD" in b for b in validar(m)))

    def test_clone_homologacao_bloqueia(self):
        self.assertTrue(any("TRAINING" in b for b in validar(clone(purpose="HOMOLOGATION"))))

    def test_ripd_inviavel_e_posterior_bloqueia(self):
        bloqueadores = validar(clone(viable=False, assessed="2026-11-12T10:00:00Z"))
        self.assertTrue(any("inaceitavel" in b for b in bloqueadores))
        self.assertTrue(any("posterior" in b for b in bloqueadores))

    def test_ripd_de_outro_tenant_bloqueia(self):
        self.assertTrue(any("outro tenant" in b
                            for b in validar(clone(assessment_tenant=TENANT))))

    def test_varredura_suja_sem_marca_e_fiscal_ligado_bloqueiam(self):
        m = massa()
        m["synthetic"] = False
        m["sweepViolationCount"] = 3
        m["fiscalProductionAllowed"] = True
        self.assertEqual(len(validar(m)), 3)

    def test_massa_com_clone_bloqueia(self):
        m = massa()
        m["clone"] = clone()["clone"]
        self.assertTrue(any("nao tem clone" in b for b in validar(m)))

    def test_clone_sem_avaliador_bloqueia(self):
        m = clone()
        del m["clone"]["assessment"]["assessedBy"]
        self.assertTrue(any("assessedBy" in b for b in validar(m)))
        m2 = clone()
        m2["clone"]["assessment"]["assessedBy"] = ""
        self.assertTrue(any("assessedBy" in b for b in validar(m2)))

    def test_campo_ausente_bloqueia(self):
        m = massa()
        del m["synthetic"]
        self.assertTrue(any("synthetic" in b for b in validar(m)))


if __name__ == "__main__":
    unittest.main()
