"""Upload the exact Sea Crown FE menu photos; print SQL only after all succeed.

Run from the backend root: python scripts/upload-menu-images.py
Credentials are read from the ignored .env, never printed.
"""
import hashlib
import json
from pathlib import Path
import re
import time
import urllib.request
import uuid


def main():
    root = Path(__file__).resolve().parent.parent
    env = dict(line.split("=", 1) for line in (root / ".env").read_text().splitlines()
               if "=" in line and not line.startswith("#"))
    cloud = env["CLOUDINARY_CLOUD_NAME"]
    if not re.fullmatch(r"[a-z0-9-]+", cloud):
        raise ValueError("Invalid cloud name")
    frontend = root.parent / "restophere-fe-web"
    source = (frontend / "src/api/menu/mockData.ts").read_text(encoding="utf-8")
    names = re.findall(r"imageUrl: photo\('([a-z0-9-]+)'\)", source)
    if len(names) != 22 or len(set(names)) != 22:
        raise ValueError("Menu source changed; review photo mappings before uploading")
    photos = frontend / "public/sites/sea-crown/images/menu"
    for name in names:
        if not (photos / f"{name}.jpg").is_file():
            raise ValueError(f"Missing photo: {name}")
    mobile = root.parent / "restosphere-fe-mobile"
    # The web menu has no mushroom hotpot photo; use the mobile source asset.
    mobile_names = [("old-pot", "", "mushroom-hotpot")]
    uploads = [(name, photos / f"{name}.jpg", "web") for name in names]
    uploads += [(quoted or bare, mobile / f"assets/images/menu/{filename}.jpg", "mobile")
                for quoted, bare, filename in mobile_names if (quoted or bare) not in names]
    for _, path, _ in uploads:
        if not path.is_file():
            raise ValueError(f"Missing photo: {path.name}")
    updates = {}
    for name, path, collection in uploads:
        params = {"overwrite": "false", "public_id": f"restosphere/menu/{name}",
                  "timestamp": str(int(time.time()))}
        signed = "&".join(f"{key}={params[key]}" for key in sorted(params))
        params["signature"] = hashlib.sha1((signed + env["CLOUDINARY_API_SECRET"]).encode()).hexdigest()
        params["api_key"] = env["CLOUDINARY_API_KEY"]
        boundary = uuid.uuid4().hex
        body = bytearray()
        for key, value in params.items():
            body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}.jpg"\r\nContent-Type: image/jpeg\r\n\r\n'.encode())
        body.extend(path.read_bytes())
        body.extend(f"\r\n--{boundary}--\r\n".encode())
        request = urllib.request.Request(f"https://api.cloudinary.com/v1_1/{cloud}/image/upload",
                                         data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
        with urllib.request.urlopen(request, timeout=90) as response:
            result = json.load(response)
        url = result["secure_url"]
        if not url.startswith(f"https://res.cloudinary.com/{cloud}/image/upload/"):
            raise ValueError("Unexpected uploaded image URL")
        with urllib.request.urlopen(url, timeout=60) as response:
            if not response.headers.get("Content-Type", "").startswith("image/"):
                raise ValueError(f"Upload is not an image: {name}")
        print(f"-- Uploaded {collection}/{name}: {url}")
        updates[name] = f"UPDATE menu_items SET image_url = '{url.replace(chr(39), chr(39)*2)}', updated_at = now() WHERE id = md5('restosphere:menu:{name}')::uuid AND image_url IS NULL;"
    print("\n".join(updates.values()))


if __name__ == "__main__":
    main()
