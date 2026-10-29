"""Pairing (SPAKE2 over RFC 3526 group 14) and request/message authentication.

Everything here is stdlib only (hashlib, hmac, secrets, pow) so the TV side can
mirror it with java.math.BigInteger and javax.crypto.Mac on Android 8+.
See docs/PROTOCOL.md sections 4 and 5; docs/vectors.json has test vectors.
"""

from __future__ import annotations

import base64
import os
import hashlib
import hmac
import secrets
import struct

# RFC 3526, 2048-bit MODP group (id 14). p is a safe prime, q = (p - 1) / 2,
# and g = 2 generates the subgroup of order q.
P_HEX = (
    "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74"
    "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437"
    "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
    "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05"
    "98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB"
    "9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
    "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718"
    "3995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF"
)
P = int(P_HEX, 16)
Q = (P - 1) // 2
G = 2
ELEMENT_BYTES = 256  # every group element is sent as 256-byte big-endian


def b64u(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def unb64u(text: str) -> bytes:
    text = str(text)
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def sha256(data: bytes) -> bytes:
    return hashlib.sha256(data).digest()


def hmac256(key: bytes, data: bytes) -> bytes:
    return hmac.new(key, data, hashlib.sha256).digest()


def hkdf(ikm: bytes, salt: bytes, info: bytes, length: int = 32) -> bytes:
    """RFC 5869 HKDF-SHA256."""
    prk = hmac256(salt or b"\x00" * 32, ikm)
    out, block, counter = b"", b"", 1
    while len(out) < length:
        block = hmac256(prk, block + info + bytes([counter]))
        out += block
        counter += 1
    return out[:length]


def hash_to_group(label: bytes) -> int:
    """Nothing-up-my-sleeve element of the order-q subgroup.

    9 SHA-256 blocks (label || counter byte 0..8) give 2304 bits, reduced mod p
    and squared, so nobody knows its discrete log to base g.
    """
    expanded = b"".join(sha256(label + bytes([i])) for i in range(9))
    return pow(int.from_bytes(expanded, "big") % P, 2, P)


M = hash_to_group(b"OmarchyTV-Link SPAKE2 M v1")
N = hash_to_group(b"OmarchyTV-Link SPAKE2 N v1")


def element_bytes(x: int) -> bytes:
    return x.to_bytes(ELEMENT_BYTES, "big")


def parse_element(data: bytes) -> int:
    if len(data) != ELEMENT_BYTES:
        raise ValueError("group element must be 256 bytes")
    x = int.from_bytes(data, "big")
    if not 1 < x < P - 1 or pow(x, Q, P) != 1:
        raise ValueError("not an element of the prime-order subgroup")
    return x


def code_scalar(code: str) -> int:
    code = str(code).strip().replace(" ", "")
    if len(code) != 6 or not code.isdigit():
        raise ValueError("pairing code must be 6 digits")
    return int.from_bytes(sha256(b"OTV1 spake2 w\x00" + code.encode("ascii")), "big") % Q


def random_scalar() -> int:
    return secrets.randbelow(Q - 1) + 1


def _lp(data: bytes) -> bytes:
    return struct.pack("<Q", len(data)) + data


def transcript(pc_ident: str, tv_ident: str, t: int, s: int, k: int, w: int) -> bytes:
    """RFC 9382-style transcript: 8-byte little-endian length prefix per field."""
    return b"".join(
        _lp(x)
        for x in (
            pc_ident.encode("utf-8"),
            tv_ident.encode("utf-8"),
            element_bytes(t),
            element_bytes(s),
            element_bytes(k),
            element_bytes(w),
        )
    )


class PairingKeys:
    def __init__(self, tt: bytes):
        self.tt_hash = sha256(tt)
        ke, ka = self.tt_hash[:16], self.tt_hash[16:]
        kc_pc = hmac256(ka, b"OTV1 confirm pc")
        kc_tv = hmac256(ka, b"OTV1 confirm tv")
        self.confirm_pc = hmac256(kc_pc, self.tt_hash)
        self.confirm_tv = hmac256(kc_tv, self.tt_hash)
        self.token = hkdf(ke, self.tt_hash, b"OTV1 token", 32)


class PcPairing:
    """The PC (SPAKE2 party A)."""

    def __init__(self, code: str, pc_id: str, x: int | None = None):
        self.w = code_scalar(code)
        self.x = x if x is not None else random_scalar()
        self.pc_ident = "pc:" + pc_id
        self.t = pow(G, self.x, P) * pow(M, self.w, P) % P

    def message(self) -> bytes:
        return element_bytes(self.t)

    def finish(self, tv_id: str, s_bytes: bytes) -> PairingKeys:
        s = parse_element(s_bytes)
        k = pow(s * pow(N, Q - self.w, P) % P, self.x, P)
        return PairingKeys(transcript(self.pc_ident, "tv:" + tv_id, self.t, s, k, self.w))


class TvPairing:
    """The TV (SPAKE2 party B). The real one is Java; this is used by the mock and the tests."""

    def __init__(self, code: str, tv_id: str, pc_id: str, t_bytes: bytes, y: int | None = None):
        self.w = code_scalar(code)
        self.y = y if y is not None else random_scalar()
        t = parse_element(t_bytes)
        self.s = pow(G, self.y, P) * pow(N, self.w, P) % P
        k = pow(t * pow(M, Q - self.w, P) % P, self.y, P)
        self.keys = PairingKeys(transcript("pc:" + pc_id, "tv:" + tv_id, t, self.s, k, self.w))

    def message(self) -> bytes:
        return element_bytes(self.s)


# ---------------------------------------------------------------- requests

AUTH_SCHEME = "OTV1"


def request_string(method: str, target: str, ts: str, nonce: str, body: bytes) -> bytes:
    return "\n".join(
        [AUTH_SCHEME, method.upper(), target, ts, nonce, hashlib.sha256(body).hexdigest()]
    ).encode("utf-8")


def sign_request(token: bytes, pc_id: str, method: str, target: str, body: bytes,
                 ts: int, nonce: str | None = None) -> tuple[str, str]:
    """Returns (Authorization header value, nonce)."""
    nonce = nonce or b64u(secrets.token_bytes(16))
    sig = b64u(hmac256(token, request_string(method, target, str(ts), nonce, body)))
    return f"{AUTH_SCHEME} pc={pc_id}, ts={ts}, nonce={nonce}, sig={sig}", nonce


def parse_authorization(value: str) -> dict:
    value = (value or "").strip()
    if not value.startswith(AUTH_SCHEME + " "):
        raise ValueError("bad auth scheme")
    fields = {}
    for part in value[len(AUTH_SCHEME) + 1:].split(","):
        key, sep, val = part.strip().partition("=")
        if not sep:
            raise ValueError("bad auth field")
        fields[key] = val
    for key in ("pc", "ts", "nonce", "sig"):
        if not fields.get(key):
            raise ValueError("missing auth field " + key)
    return fields


def response_string(nonce: str, status: int, body: bytes) -> bytes:
    return "\n".join(
        [AUTH_SCHEME + "-R", nonce, str(status), hashlib.sha256(body).hexdigest()]
    ).encode("utf-8")


def sign_response(token: bytes, nonce: str, status: int, body: bytes) -> str:
    return b64u(hmac256(token, response_string(nonce, status, body)))


def ws_session_key(token: bytes, client_nonce: str, server_nonce: str) -> bytes:
    return hmac256(token, f"{AUTH_SCHEME}-WS\n{client_nonce}\n{server_nonce}".encode("utf-8"))


def seal_message(key: bytes, payload: bytes) -> str:
    """WebSocket text frame body: '<b64url mac> <payload json>'."""
    return b64u(hmac256(key, payload)) + " " + payload.decode("utf-8")


def open_message(key: bytes, text: str) -> bytes:
    mac, sep, payload = text.partition(" ")
    if not sep:
        raise ValueError("unsealed message")
    raw = payload.encode("utf-8")
    if not hmac.compare_digest(unb64u(mac), hmac256(key, raw)):
        raise ValueError("bad message mac")
    return raw


# ---- optional payload encryption (2.13): AES-256-GCM, negotiated per request with "X-OTV-Enc: A256GCM" ----
ENC_HEADER = "X-OTV-Enc"
ENC_ALG = "A256GCM"
ENC_CAP = "enc-a256gcm"
try:  # python-cryptography is optional; without it the link stays on signed plaintext (2.12 behaviour)
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM as _AESGCM
    HAVE_AES = True
except ImportError:  # pragma: no cover
    _AESGCM = None
    HAVE_AES = False


def enc_key(token: bytes) -> bytes:
    return hkdf(token, b"OTV1 enc salt", b"OTV1 enc", 32)


def seal(token: bytes, aad: str, plain: bytes) -> bytes:
    iv = os.urandom(12)
    return iv + _AESGCM(enc_key(token)).encrypt(iv, plain, aad.encode())


def unseal(token: bytes, aad: str, sealed: bytes) -> bytes:
    if len(sealed) < 28:
        raise ValueError("short ciphertext")
    return _AESGCM(enc_key(token)).decrypt(sealed[:12], sealed[12:], aad.encode())
