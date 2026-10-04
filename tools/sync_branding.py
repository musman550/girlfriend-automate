#!/usr/bin/env python3
"""Keeps the owner's brand/social links at the bottom of EVERY public repo README.

Runs daily from .github/workflows/brand-sync.yml.
- With BRAND_PAT (a token that can write to all your repos) it updates every repo.
- With only GITHUB_TOKEN it can update just this repo; other repos are skipped quietly.
Opt out for one repo by giving it the GitHub topic `no-branding`.
Set DRY_RUN=1 to only print what would change.
"""
import base64
import json
import os
import re
import urllib.error
import urllib.request

TOKEN = os.environ.get("BRAND_PAT") or os.environ.get("GITHUB_TOKEN", "")
OWNER = os.environ.get("GITHUB_REPOSITORY_OWNER", "musman550")
DRY = os.environ.get("DRY_RUN") == "1"
START, END = "<!-- BRANDING:START -->", "<!-- BRANDING:END -->"

BLOCK = f"""{START}

---

🌐 Website: [musfiraai.com](https://musfiraai.com/)

* ▶️ YouTube: [Automate With Musfira AI](https://www.youtube.com/@automatewithmusfiraai)
* 💼 LinkedIn: [Musfira AI](https://www.linkedin.com/in/musfira-ai-b3218b39b)
* 📸 Instagram: [@musma_n55](https://instagram.com/musma_n55)

{END}"""


def api(method, path, data=None):
    req = urllib.request.Request(
        "https://api.github.com" + path,
        data=json.dumps(data).encode() if data is not None else None,
        method=method,
        headers={"Authorization": f"Bearer {TOKEN}", "Accept": "application/vnd.github+json",
                 "User-Agent": "brand-sync", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        txt = r.read().decode()
        return json.loads(txt) if txt else {}


def all_repos():
    page, out = 1, []
    while True:
        chunk = api("GET", f"/users/{OWNER}/repos?type=owner&per_page=100&page={page}")
        out += chunk
        if len(chunk) < 100:
            return out
        page += 1


def new_text(text):
    if START in text and END in text:
        return re.sub(re.escape(START) + r".*?" + re.escape(END), lambda m: BLOCK, text, flags=re.S)
    return text.rstrip() + "\n\n" + BLOCK + "\n"


def sync(repo):
    name, branch = repo["full_name"], repo["default_branch"]
    try:
        rd = api("GET", f"/repos/{name}/readme")
    except urllib.error.HTTPError as e:
        print(f"{name}: no README ({e.code}), skipped")
        return
    old = base64.b64decode(rd["content"]).decode("utf-8")
    new = new_text(old)
    if new == old:
        print(f"{name}: already up to date")
        return
    if DRY:
        print(f"{name}: WOULD update")
        return
    try:
        api("PUT", f"/repos/{name}/contents/{rd['path']}", {
            "message": "docs: sync brand links [skip ci]",
            "content": base64.b64encode(new.encode("utf-8")).decode(),
            "sha": rd["sha"], "branch": branch})
        print(f"{name}: updated")
    except urllib.error.HTTPError as e:
        print(f"{name}: cannot write ({e.code}), skipped")


if __name__ == "__main__":
    for r in all_repos():
        if r["fork"] or r["archived"] or r["private"] or "no-branding" in r.get("topics", []):
            continue
        if r["name"].lower() == OWNER.lower():  # profile README is hand-made, leave it alone
            continue
        sync(r)
