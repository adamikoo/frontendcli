import urllib.request
import urllib.parse
import json
import time
import subprocess

client_id = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
client_secret = "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"
refresh_token = "1//03DYc17A25Tg6CgYIARAAGAMSNwF-L9IrVJdpoOcnr-M3COXnErrnu47vSUJUpsKm6Kz_J-4LTMEJikyJA-o41VgFXMtdQ69dqJg"

data = urllib.parse.urlencode({
    "client_id": client_id,
    "client_secret": client_secret,
    "refresh_token": refresh_token,
    "grant_type": "refresh_token"
}).encode()

req = urllib.request.Request("https://oauth2.googleapis.com/token", data=data)
res = json.loads(urllib.request.urlopen(req).read().decode())
print("Got fresh access token:", res.get("access_token", "")[:25] + "...")

stored = {
    "auth_method": "oauth",
    "user_tier": "FREE",
    "project_id": "default-cli-project",
    "access_token": res["access_token"],
    "refresh_token": refresh_token,
    "token_type": "Bearer",
    "expiry_date": int((time.time() + res.get("expires_in", 3600) - 300) * 1000)
}

stored_json = json.dumps(stored, indent=2)

with open("token_payload.json", "w") as f:
    f.write(stored_json)

print("Wrote token_payload.json locally.")
