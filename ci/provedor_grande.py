"""Provedor GRANDE de teste (padrão Xtream), com a internet limitada a 8 Mega, para as medições.

Imita um provedor de tamanho comum no Brasil: 8.000 canais, 30.000 filmes e 6.000 séries, com os campos
que os provedores de verdade mandam (por isso as respostas têm o mesmo tamanho das reais: dezenas de MB).
Tudo o que sai daqui (listas, logos e vídeos) passa por um limite único de 8 Mbit/s (1 MB por segundo),
como a internet da casa da Glaucia, e cada resposta demora 60 ms para começar (distância até o provedor).

Login: grande / 1234.  Vídeos: canais = HLS local (2 Mbit/s); filmes e episódios = MP4 local.
Uso: python3 provedor_grande.py [porta] [pasta_dos_videos]
"""
import http.server
import json
import os
import sys
import threading
import time
import urllib.parse

PORTA = int(sys.argv[1]) if len(sys.argv) > 1 else 8800
VIDEOS = sys.argv[2] if len(sys.argv) > 2 else "video"
BASE = f"http://10.0.2.2:{PORTA}"
BYTES_POR_SEGUNDO = 1_000_000  # 8 Mbit/s
LATENCIA = 0.06


class Limite:
    """Um só "cano" de 8 Mega para todas as conexões juntas (como a internet de casa)."""

    def __init__(self, bps):
        self.bps = bps
        self.trava = threading.Lock()
        self.livre = time.monotonic()

    def esperar(self, n):
        with self.trava:
            agora = time.monotonic()
            self.livre = max(agora, self.livre) + n / self.bps
            espera = self.livre - agora
        if espera > 0:
            time.sleep(espera)


cano = Limite(BYTES_POR_SEGUNDO)


def gerar():
    logos = [f"{BASE}/logo{i}.png" for i in (1, 2, 3)]
    temas_tv = ["Abertos", "Esportes", "Filmes e Séries", "Notícias", "Infantil", "Documentários", "Variedades", "Música"]
    live_cat = [{"category_id": str(i + 1), "category_name": f"{temas_tv[i % 8]} {i // 8 + 1}", "parent_id": 0} for i in range(60)]
    vod_cat = [{"category_id": str(100 + i), "category_name": f"Filmes {i + 1}", "parent_id": 0} for i in range(80)]
    ser_cat = [{"category_id": str(300 + i), "category_name": f"Séries {i + 1}", "parent_id": 0} for i in range(40)]
    live = [
        {
            "num": i, "name": f"Canal {i}", "stream_type": "live", "stream_id": i, "stream_icon": logos[i % 3],
            "epg_channel_id": f"canal{i}.br", "added": "1700000000", "is_adult": "0",
            "category_id": live_cat[(i - 1) * 60 // 8000]["category_id"], "category_ids": [int(live_cat[(i - 1) * 60 // 8000]["category_id"])],
            "custom_sid": "", "tv_archive": 0, "direct_source": "", "tv_archive_duration": 0,
        }
        for i in range(1, 8001)
    ]
    vod = [
        {
            "num": i, "name": f"Filme {i}", "title": f"Filme {i}", "year": str(1990 + i % 35), "stream_type": "movie",
            "stream_id": 100000 + i, "stream_icon": logos[i % 3], "rating": "7.1", "rating_5based": 3.6,
            "added": "1700000000", "is_adult": "0", "category_id": vod_cat[(i - 1) * 80 // 30000]["category_id"],
            "category_ids": [int(vod_cat[(i - 1) * 80 // 30000]["category_id"])], "container_extension": "mp4",
            "custom_sid": "", "direct_source": "",
        }
        for i in range(1, 30001)
    ]
    series = [
        {
            "num": i, "name": f"Série {i}", "title": f"Série {i}", "year": str(2000 + i % 25), "stream_type": "series",
            "series_id": 200000 + i, "cover": logos[i % 3],
            "plot": "Uma família muda para uma cidade nova e descobre segredos que mudam tudo o que achavam saber.",
            "cast": "Ator Um, Atriz Dois, Ator Três", "director": "Diretora Exemplo", "genre": "Drama / Suspense",
            "release_date": "2020-01-01", "releaseDate": "2020-01-01", "last_modified": "1700000000",
            "rating": "8", "rating_5based": 4, "backdrop_path": [logos[i % 3]], "youtube_trailer": "",
            "episode_run_time": "45", "category_id": ser_cat[(i - 1) * 40 // 6000]["category_id"],
            "category_ids": [int(ser_cat[(i - 1) * 40 // 6000]["category_id"])],
        }
        for i in range(1, 6001)
    ]
    dados = {
        "get_live_categories": live_cat, "get_live_streams": live,
        "get_vod_categories": vod_cat, "get_vod_streams": vod,
        "get_series_categories": ser_cat, "get_series": series,
    }
    return {acao: json.dumps(v, ensure_ascii=False).encode("utf-8") for acao, v in dados.items()}


RESPOSTAS = gerar()


def separar():
    """Respostas já separadas por categoria (para o pedido "só esta categoria")."""
    saida = {}
    for acao in ("get_live_streams", "get_vod_streams", "get_series"):
        grupos = {}
        for item in json.loads(RESPOSTAS[acao]):
            grupos.setdefault(str(item["category_id"]), []).append(item)
        saida[acao] = {cat: json.dumps(lista, ensure_ascii=False).encode("utf-8") for cat, lista in grupos.items()}
    return saida


POR_CATEGORIA = separar()
for acao, corpo in RESPOSTAS.items():
    print(f"{acao}: {len(corpo) / 1_000_000:.1f} MB")
print(f"total: {sum(len(c) for c in RESPOSTAS.values()) / 1_000_000:.1f} MB", flush=True)

LOGIN = json.dumps({
    "user_info": {"username": "grande", "auth": 1, "status": "Active", "exp_date": "4102444800", "is_trial": "0",
                  "active_cons": "0", "max_connections": "1", "allowed_output_formats": ["m3u8", "ts"]},
    "server_info": {"url": "10.0.2.2", "port": str(PORTA), "server_protocol": "http", "timezone": "America/Sao_Paulo"},
}).encode("utf-8")

EPISODIOS = json.dumps({"info": {"name": "Série"}, "episodes": {"1": [
    {"id": str(900000 + n), "episode_num": n, "title": f"Episódio {n}", "container_extension": "mp4", "season": 1}
    for n in range(1, 11)
]}}).encode("utf-8")


class Grande(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def enviar(self, corpo, tipo):
        time.sleep(LATENCIA)
        self.send_response(200)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers()
        try:
            for i in range(0, len(corpo), 16 * 1024):
                pedaco = corpo[i:i + 16 * 1024]
                cano.esperar(len(pedaco))
                self.wfile.write(pedaco)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def arquivo(self, nome, tipo):
        caminho = os.path.join(VIDEOS, nome) if not nome.startswith("logo") else os.path.join("listas", nome)
        if not os.path.isfile(caminho):
            self.send_error(404)
            return
        with open(caminho, "rb") as f:
            self.enviar(f.read(), tipo)

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        q = dict(urllib.parse.parse_qsl(url.query))
        partes = url.path.strip("/").split("/")
        if url.path == "/player_api.php":
            if q.get("username") != "grande" or q.get("password") != "1234":
                return self.enviar(b'{"user_info":{"auth":0}}', "application/json")
            acao = q.get("action")
            if acao is None:
                return self.enviar(LOGIN, "application/json")
            if acao == "get_series_info":
                return self.enviar(EPISODIOS, "application/json")
            # Como os provedores de verdade: dá para pedir só uma categoria.
            if "category_id" in q and acao in POR_CATEGORIA:
                return self.enviar(POR_CATEGORIA[acao].get(q["category_id"], b"[]"), "application/json")
            return self.enviar(RESPOSTAS.get(acao, b"[]"), "application/json")
        if len(partes) == 4 and partes[0] in ("live", "movie", "series") and partes[1:3] == ["grande", "1234"]:
            if partes[0] == "live":
                return self.arquivo("canal.m3u8", "application/vnd.apple.mpegurl")
            return self.arquivo("filme.mp4", "video/mp4")
        if len(partes) == 2 and partes[0] == "video":
            return self.arquivo(partes[1], "video/mp2t" if partes[1].endswith(".ts") else "application/octet-stream")
        if len(partes) == 1 and partes[0].startswith("logo"):
            return self.arquivo(partes[0], "image/png")
        self.send_error(404)


http.server.ThreadingHTTPServer(("0.0.0.0", PORTA), Grande).serve_forever()
