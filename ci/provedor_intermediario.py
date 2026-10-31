"""Imita o provedor real da Glaucia, com as MESMAS respostas que ele deu em 31/10/2026 (testado sem usuário/senha reais):

  player_api.php sem nada            -> 422 {"error":"Bad Request"}
  player_api.php com dados errados   -> 400 {"error":"Bad Request"}   (o padrão comum seria 200 com auth=0)
  get.php com dados errados          -> 404 (página vazia)
  /                                  -> 307 para https://www.google.com/
  servidor "nginx/1.27.1"

Como ainda não sabemos o que ele responde com os dados CERTOS, há dois cenários, um para cada possibilidade:
  padrao / Ab12cd     -> o jeito padrão funciona (player_api 200), inclusive pedir canais de uma categoria só
  sololink / Ab12cd   -> o jeito padrão continua recusando (400), mas a lista completa pelo get.php funciona
  qualquer outro      -> 400 no player_api e 404 no get.php (igual ao real com dados errados)

Vídeos: /live|movie/<usuário>/<senha>/<id>.<ext> -> HLS local (pasta de vídeos).
Uso: python3 provedor_intermediario.py [porta] [pasta_dos_videos]
"""
import http.server
import json
import os
import sys
import urllib.parse

PORTA = int(sys.argv[1]) if len(sys.argv) > 1 else 8900
VIDEOS = sys.argv[2] if len(sys.argv) > 2 else "video"
BASE = f"http://10.0.2.2:{PORTA}"
SENHA = "Ab12cd"
USUARIOS = {"padrao", "sololink"}

CATEGORIAS = [{"category_id": str(i), "category_name": nome, "parent_id": 0} for i, nome in
              enumerate(["Abertos", "Esportes", "Notícias", "Infantil"], start=1)]
CANAIS = [{"num": i, "name": f"Canal Intermediário {i}", "stream_type": "live", "stream_id": 500 + i,
           "stream_icon": f"{BASE}/logo{i % 3 + 1}.png", "category_id": str((i - 1) // 5 + 1)} for i in range(1, 21)]
FILMES = [{"num": i, "name": f"Filme Intermediário {i}", "stream_id": 700 + i, "category_id": "10",
           "container_extension": "mp4"} for i in range(1, 6)]


def lista_m3u(usuario):
    linhas = ["#EXTM3U"]
    for c in CANAIS:
        grupo = CATEGORIAS[int(c["category_id"]) - 1]["category_name"]
        linhas.append(f'#EXTINF:-1 tvg-id="" tvg-name="{c["name"]}" tvg-logo="{c["stream_icon"]}" group-title="{grupo}",{c["name"]}')
        linhas.append(f'{BASE}/live/{usuario}/{SENHA}/{c["stream_id"]}.m3u8')
    for f in FILMES:
        linhas.append(f'#EXTINF:-1 tvg-name="{f["name"]}" group-title="Filmes",{f["name"]}')
        linhas.append(f'{BASE}/movie/{usuario}/{SENHA}/{f["stream_id"]}.mp4')
    return ("\n".join(linhas) + "\n").encode("utf-8")


class Intermediario(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "nginx/1.27.1"
    sys_version = ""

    def log_message(self, formato, *args):
        # Anota só o caminho e o código (sem usuário e senha) para conferir os pedidos do app.
        caminho = urllib.parse.urlparse(self.path)
        acao = dict(urllib.parse.parse_qsl(caminho.query)).get("action", "")
        print(f"{self.command} {caminho.path} action={acao} -> {args[1] if len(args) > 1 else ''}", flush=True)

    def responder(self, codigo, corpo=b"", tipo="application/json; charset=utf-8", extra=None):
        self.send_response(codigo)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(corpo)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(corpo)

    def bad(self, codigo=400):
        self.responder(codigo, b'{"error":"Bad Request"}')

    def dados(self):
        url = urllib.parse.urlparse(self.path)
        q = dict(urllib.parse.parse_qsl(url.query))
        if self.command == "POST":
            tamanho = int(self.headers.get("Content-Length") or 0)
            q.update(dict(urllib.parse.parse_qsl(self.rfile.read(tamanho).decode("utf-8"))))
        return url, q

    def do_POST(self):
        url, q = self.dados()
        if url.path == "/player_api.php":
            return self.player_api(q)
        self.bad()

    def do_GET(self):
        url, q = self.dados()
        partes = url.path.strip("/").split("/")
        if url.path == "/":
            return self.responder(307, tipo="text/html", extra={"Location": "https://www.google.com/"})
        if url.path == "/player_api.php":
            return self.player_api(q)
        if url.path == "/get.php":
            usuario, senha = q.get("username"), q.get("password")
            if usuario in USUARIOS and senha == SENHA and q.get("type", "").startswith("m3u"):
                return self.responder(200, lista_m3u(usuario), "audio/x-mpegurl")
            return self.responder(404, tipo="text/html; charset=UTF-8")
        if len(partes) == 4 and partes[0] in ("live", "movie") and partes[1] in USUARIOS and partes[2] == SENHA:
            return self.arquivo("canal.m3u8" if partes[0] == "live" else "filme.mp4")
        if len(partes) == 2 and partes[0] == "video":
            return self.arquivo(partes[1])
        if len(partes) == 1 and partes[0].startswith("logo"):
            return self.arquivo(partes[0], pasta="listas")
        self.bad()

    def player_api(self, q):
        usuario, senha = q.get("username"), q.get("password")
        if usuario is None and senha is None:
            return self.bad(422)
        if usuario != "padrao" or senha != SENHA:
            return self.bad(400)
        acao = q.get("action")
        if acao is None:
            return self.json({
                "user_info": {"username": usuario, "password": senha, "auth": 1, "status": "Active",
                              "exp_date": "4102444800", "max_connections": "1", "active_cons": "0",
                              "allowed_output_formats": ["m3u8", "ts"]},
                "server_info": {"url": "10.0.2.2", "port": str(PORTA), "server_protocol": "http"},
            })
        categoria = q.get("category_id")
        if acao == "get_live_categories":
            return self.json(CATEGORIAS)
        if acao == "get_live_streams":
            return self.json([c for c in CANAIS if categoria is None or c["category_id"] == categoria])
        if acao == "get_vod_categories":
            return self.json([{"category_id": "10", "category_name": "Filmes"}])
        if acao == "get_vod_streams":
            return self.json(FILMES)
        return self.json([])

    def json(self, dados):
        self.responder(200, json.dumps(dados, ensure_ascii=False).encode("utf-8"))

    def arquivo(self, nome, pasta=None):
        caminho = os.path.join(pasta or VIDEOS, nome)
        if not os.path.isfile(caminho):
            return self.responder(404, tipo="text/html")
        tipo = "application/vnd.apple.mpegurl" if nome.endswith(".m3u8") else "video/mp2t" if nome.endswith(".ts") \
            else "video/mp4" if nome.endswith(".mp4") else "image/png"
        with open(caminho, "rb") as f:
            self.responder(200, f.read(), tipo)


http.server.ThreadingHTTPServer(("0.0.0.0", PORTA), Intermediario).serve_forever()
