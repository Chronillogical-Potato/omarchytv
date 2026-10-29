"""2.13 optional AES-256-GCM payloads: negotiation via /v1/status and backward compatibility."""
import base64, http.server, json, os, sys, threading, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from omarchy_link import client, crypto

TOKEN = bytes(range(32))


def make_handler(advertise_enc, seen):
    class H(http.server.BaseHTTPRequestHandler):
        def log_message(self, *a): pass

        def _do(self):
            f = crypto.parse_authorization(self.headers.get("Authorization", ""))
            body = self.rfile.read(int(self.headers.get("Content-Length") or 0))
            enc = self.headers.get(crypto.ENC_HEADER) == crypto.ENC_ALG
            if enc and body:
                body = crypto.unseal(TOKEN, "OTV1-E\n" + f["nonce"], body)
            seen.append((self.command, self.path, enc, body))
            out = {"capabilities": ["cast"] + ([crypto.ENC_CAP] if advertise_enc else []), "echo": body.decode()}
            data = json.dumps(out).encode()
            self.send_response(200)
            if enc:
                data = crypto.seal(TOKEN, "OTV1-E-R\n" + f["nonce"], data)
                self.send_header(crypto.ENC_HEADER, crypto.ENC_ALG)
            self.send_header("X-OTV-Sig", crypto.sign_response(TOKEN, f["nonce"], 200, data))
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        do_GET = do_PUT = do_POST = _do
    return H


@unittest.skipUnless(crypto.HAVE_AES, "python-cryptography not installed")
class TestEnc(unittest.TestCase):
    def start(self, advertise):
        self.seen = []
        self.srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), make_handler(advertise, self.seen))
        threading.Thread(target=self.srv.serve_forever, daemon=True).start()
        self.addCleanup(self.srv.server_close)
        self.addCleanup(self.srv.shutdown)
        tv = {"address": "127.0.0.1", "port": self.srv.server_address[1], "token": base64.b64encode(TOKEN).decode()}
        return client.Client(tv, "pc1", strict_subnet=False)

    def test_negotiates_and_round_trips(self):
        c = self.start(True)
        c.status()
        self.assertTrue(c.encrypt)
        r = c.request("PUT", "/v1/x", {"secret": 1})
        self.assertEqual(json.loads(r["echo"]), {"secret": 1})
        self.assertTrue(self.seen[-1][2])

    def test_old_tv_stays_plaintext(self):
        c = self.start(False)
        c.status()
        self.assertFalse(c.encrypt)
        c.request("PUT", "/v1/x", {"a": 1})
        self.assertFalse(self.seen[-1][2])

    def test_wrong_aad_rejected(self):
        with self.assertRaises(Exception):
            crypto.unseal(TOKEN, "OTV1-E\nB", crypto.seal(TOKEN, "OTV1-E\nA", b"x"))


if __name__ == "__main__":
    unittest.main()
