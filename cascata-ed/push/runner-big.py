#!/usr/bin/env python3
"""Runner de push da Cascata ED (padrão Push 2/3): rota `github push-files`,
HEAD conferido após cada lote, HTTP 429 = parada, timeout nunca presume
falha (confere o HEAD e o assunto do commit antes de repetir; máx. 2 tentativas).

Uso: runner.py <dir-da-operacao>   (lê plan.json e state.json do diretório)
plan.json: [{idx, message, files: [{src, path}]}]  — um chunk = um commit.
Chunks > ~400 KB devem vir pré-divididos no plano (partes numeradas).
"""
import json, os, shutil, subprocess, sys, time

GH = "/opt/hatch/bin/github"
OWNER, REPO, BRANCH = "mastersonmdaniel", "Ziip_San", "main"
WORK = os.path.abspath(sys.argv[1])
PLAN = json.load(open(os.path.join(WORK, "plan.json")))
STATE_F = os.path.join(WORK, "state.json")
LOG_F = os.path.join(WORK, "exec.log")
STAGE = os.path.join(WORK, "stage")

def log(msg):
    line = f"{time.strftime('%H:%M:%S')} {msg}"
    with open(LOG_F, "a", encoding="utf-8") as f:
        f.write(line + "\n")
    print(line, flush=True)

def head():
    r = subprocess.run([GH, "call-read-tool", "--name", "list_commits",
                        "--arguments-json", json.dumps({"owner": OWNER, "repo": REPO, "sha": BRANCH, "perPage": 1, "page": 1})],
                       capture_output=True, text=True, timeout=180)
    d = json.loads(r.stdout)
    arr = json.loads(d["result"]["content"][0]["text"])
    return arr[0]["sha"], arr[0]["commit"]["message"].split("\n")[0]

def do_push(chunk):
    sdir = os.path.join(STAGE, str(chunk["idx"]))
    if os.path.exists(sdir):
        shutil.rmtree(sdir)
    paths = []
    for f in chunk["files"]:
        dest = os.path.join(sdir, f["path"])
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        shutil.copyfile(f["src"], dest)
        paths.append(f["path"])
    os.makedirs(STAGE, exist_ok=True)
    pfile = os.path.join(STAGE, f"{chunk['idx']}.paths")
    with open(pfile, "wb") as fh:
        fh.write(b"\0".join(p.encode() for p in paths) + b"\0")
    r = subprocess.run([GH, "push-files", "--owner", OWNER, "--repo", REPO, "--branch", BRANCH,
                        "--message", chunk["message"], "--source-dir", sdir, "--paths-file", pfile],
                       capture_output=True, text=True, timeout=1200)
    return (r.stdout or ""), (r.stderr or ""), r.returncode

def main():
    state = {"done": [], "head": None}
    if os.path.exists(STATE_F):
        state = json.load(open(STATE_F))
    prev_head, prev_subj = head()
    log(f"HEAD atual: {prev_head[:7]} :: {prev_subj[:80]}")
    if state.get("head") and state["head"] != prev_head:
        log(f"AVISO: HEAD difere do estado salvo ({state['head'][:7]}); seguindo do HEAD real.")
    state["head"] = prev_head
    for chunk in PLAN:
        idx = chunk["idx"]
        if idx in state["done"]:
            continue
        size = sum(os.path.getsize(f["src"]) for f in chunk["files"])
        log(f"LOTE {idx}/{len(PLAN)}: {chunk['message'][:100]} ({len(chunk['files'])} arq, {size//1024} KB)")
        if size > 400_000:
            log(f"PARADA lote {idx}: {size} bytes excede o teto de 400.000 bytes (~390 KB) — dividir no plano.")
            sys.exit(4)
        landed = False
        for attempt in (1, 2):
            err_out = ""
            try:
                out, err, rc = do_push(chunk)
                err_out = (out[-400:] + " || " + err[-200:]).replace("\n", " ")
                if "429" in out or "429" in err:
                    log(f"PARADA lote {idx}: HTTP 429. Saida: {err_out}")
                    sys.exit(3)
            except subprocess.TimeoutExpired:
                err_out = "TIMEOUT 1200s na chamada de escrita"
            except Exception as e:
                err_out = f"EXCECAO local: {e!r}"
            try:
                new_head, new_subj = head()
            except Exception as e:
                log(f"PARADA lote {idx}: nao consegui verificar HEAD ({e}); saida push: {err_out}")
                sys.exit(2)
            if new_head != prev_head and new_subj == chunk["message"]:
                state["done"].append(idx); state["head"] = new_head
                json.dump(state, open(STATE_F, "w"))
                log(f"OK lote {idx} (tentativa {attempt}) -> HEAD {new_head[:7]}")
                prev_head = new_head
                landed = True
                break
            log(f"lote {idx} tentativa {attempt}: HEAD {prev_head[:7]}->{new_head[:7]} subj='{new_subj[:70]}' saida={err_out[-260:]}")
        if not landed:
            log(f"PARADA lote {idx}: nao pousou apos 2 tentativas.")
            sys.exit(1)
    log(f"FIM: {len(state['done'])}/{len(PLAN)} lotes; HEAD final {state['head']}")

if __name__ == "__main__":
    main()
