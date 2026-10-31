"""Servidor de teste para o emulador: serve a pasta "listas" e imita provedores "difíceis".

/lento.m3u            demora 45 s para começar a responder (provedor lento)
/exige-navegador.m3u  recusa (403) quem não se apresenta como navegador
/redireciona.m3u      manda para /teste.m3u (redirecionamento)
/erro-456             responde 456 (código usado por provedores para limite de conexões)
/corta.m3u            nas 2 primeiras vezes corta a conexão no meio da lista gigante; na 3ª entrega inteira

/player_api.php       imita um provedor "Provedor/Usuário/Senha" (padrão Xtream):
                      teste/1234 = ativo; vencido/1234 = assinatura vencida; qualquer outro = senha errada
/live|movie|series/teste/1234/<id>.<ext>  redireciona para os vídeos públicos de teste

Uso: python3 servidor.py [porta] [certificado.pem chave.pem]  (com certificado, vira https)
"""
import http.server
import json
import os
import urllib.parse
import ssl
import sys
import time

PASTA = "listas"
cortes = {"vezes": 0}
BASE = "http://10.0.2.2:8000"
HLS = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
DASH = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd"
MP4 = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"
VIDEOS = {"101": HLS, "102": HLS, "201": MP4, "202": DASH, "401": MP4, "402": MP4}
XTREAM = {
    "get_live_categories": [{"category_id": "1", "category_name": "Canais de teste"}],
    "get_live_streams": [
        {"num": 1, "name": "Canal HLS de teste", "stream_id": 101, "stream_icon": BASE + "/logo1.png", "category_id": "1"},
        {"num": 2, "name": "Canal 2 de teste", "stream_id": "102", "stream_icon": None, "category_id": 1},
    ],
    "get_vod_categories": [{"category_id": "2", "category_name": "Filmes de teste"}],
    "get_vod_streams": [
        {"name": "Filme MP4 de teste", "stream_id": 201, "stream_icon": BASE + "/logo3.png", "category_id": "2", "container_extension": "mp4"},
        {"name": "Filme DASH de teste", "stream_id": 202, "stream_icon": "", "category_id": "2", "container_extension": "mpd"},
    ],
    "get_series_categories": [{"category_id": "3", "category_name": "Séries de teste"}],
    "get_series": [{"name": "Série de teste", "series_id": 301, "cover": BASE + "/logo2.png", "category_id": "3"}],
}


class Provedor(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=PASTA, **kwargs)

    def json(self, dados):
        corpo = json.dumps(dados).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers()
        self.wfile.write(corpo)

    def do_GET(self):
        caminho = self.path.split("?")[0]
        q = dict(urllib.parse.parse_qsl(urllib.parse.urlparse(self.path).query))
        if caminho == "/player_api.php":
            usuario, senha = q.get("username"), q.get("password")
            if senha != "1234" or usuario not in ("teste", "vencido"):
                return self.json({"user_info": {"auth": 0}})
            if usuario == "vencido":
                return self.json({"user_info": {"auth": 1, "status": "Expired", "exp_date": "1600000000"}})
            acao = q.get("action")
            if acao is None:
                return self.json({"user_info": {"auth": 1, "status": "Active", "exp_date": "4102444800", "allowed_output_formats": ["m3u8", "ts"]}, "server_info": {}})
            if acao == "get_series_info":
                return self.json({"info": {}, "episodes": {"1": [
                    {"id": "401", "episode_num": 1, "title": "Episódio 1", "container_extension": "mp4", "season": 1},
                    {"id": "402", "episode_num": 2, "title": "Episódio 2", "container_extension": "mp4", "season": 1},
                ]}})
            return self.json(XTREAM.get(acao, []))
        partes = caminho.strip("/").split("/")
        if len(partes) == 4 and partes[0] in ("live", "movie", "series") and partes[1] == "teste" and partes[2] == "1234":
            destino = VIDEOS.get(partes[3].split(".")[0])
            if destino:
                self.send_response(302)
                self.send_header("Location", destino)
                self.end_headers()
                return
        if caminho == "/lento.m3u":
            time.sleep(45)
            return self.enviar_lista()
        if caminho == "/exige-navegador.m3u":
            if "Mozilla" not in self.headers.get("User-Agent", ""):
                self.send_error(403)
                return
            return self.enviar_lista()
        if caminho == "/redireciona.m3u":
            self.send_response(302)
            self.send_header("Location", "/teste.m3u")
            self.end_headers()
            return
        if caminho == "/corta.m3u":
            dados = open(os.path.join(PASTA, "gigante.m3u"), "rb").read()
            self.send_response(200)
            self.send_header("Content-Type", "audio/x-mpegurl")
            self.send_header("Content-Length", str(len(dados)))
            self.end_headers()
            if cortes["vezes"] < 2:
                cortes["vezes"] += 1
                self.wfile.write(dados[: len(dados) // 2])
                self.wfile.flush()
                self.close_connection = True
                self.connection.close()
                return
            self.wfile.write(dados)
            return
        if caminho == "/erro-456":
            self.send_response(456)
            self.end_headers()
            return
        return super().do_GET()

    def enviar_lista(self):
        dados = open(os.path.join(PASTA, "teste.m3u"), "rb").read()
        self.send_response(200)
        self.send_header("Content-Type", "audio/x-mpegurl")
        self.send_header("Content-Length", str(len(dados)))
        self.end_headers()
        self.wfile.write(dados)


porta = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
servidor = http.server.ThreadingHTTPServer(("0.0.0.0", porta), Provedor)
if len(sys.argv) > 3:
    contexto = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    contexto.load_cert_chain(sys.argv[2], sys.argv[3])
    servidor.socket = contexto.wrap_socket(servidor.socket, server_side=True)
servidor.serve_forever()
