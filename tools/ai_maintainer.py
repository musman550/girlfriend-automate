#!/usr/bin/env python3
"""Weekly maintainer for Girlfriend Automate.

1. Checks that the default AI models in Prefs.kt still exist at Groq/Gemini.
   If not, opens a pull request with a working default (deterministic, no AI guess).
2. If the build failed, opens an issue with the log and, when GROQ_API_KEY is set,
   an AI-written diagnosis.
3. When GROQ_API_KEY is set, opens a weekly issue with AI-written UX/feature ideas.

Nothing is merged automatically: this app sends real SMS, so a human reviews every change.
Missing API keys are fine; those steps are skipped.
"""
import datetime
import json
import os
import re
import subprocess
import urllib.error
import urllib.request

REPO = os.environ.get("GITHUB_REPOSITORY", "")
GH_TOKEN = os.environ.get("GITHUB_TOKEN", "")
GROQ = os.environ.get("GROQ_API_KEY", "")
GEMINI = os.environ.get("GEMINI_API_KEY", "")
BUILD = os.environ.get("BUILD_OUTCOME", "success")
PREFS = "app/src/main/java/com/musfira/smsai/Prefs.kt"
AI_MODEL = os.environ.get("AI_MODEL", "openai/gpt-oss-120b")
GROQ_PREF = ["openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.6-27b"]
GEMINI_PREF = ["gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-3.6-flash"]


def http(method, url, data=None, headers=None, timeout=60):
    h = {"User-Agent": "girlfriend-automate-maintainer"}
    h.update(headers or {})
    body = json.dumps(data).encode() if data is not None else None
    if body:
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=h, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        txt = r.read().decode()
        return json.loads(txt) if txt else {}


def gh(method, path, data=None):
    return http(method, f"https://api.github.com/repos/{REPO}{path}", data,
                {"Authorization": f"Bearer {GH_TOKEN}", "Accept": "application/vnd.github+json"})


def run(*cmd):
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout


def llm(prompt, max_tokens=1500):
    resp = http("POST", "https://api.groq.com/openai/v1/chat/completions", {
        "model": AI_MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "reasoning_effort": "low",
        "max_completion_tokens": max_tokens,
    }, {"Authorization": f"Bearer {GROQ}"}, timeout=120)
    return resp["choices"][0]["message"].get("content", "").strip()


def groq_models():
    d = http("GET", "https://api.groq.com/openai/v1/models", headers={"Authorization": f"Bearer {GROQ}"})
    bad = ("whisper", "tts", "orpheus", "guard", "embed", "compound")
    return sorted(m["id"] for m in d.get("data", []) if not any(b in m["id"] for b in bad))


def gemini_models():
    d = http("GET", "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
             headers={"x-goog-api-key": GEMINI})
    bad = ("image", "tts", "live", "embed", "robotics", "audio", "computer", "veo", "imagen")
    out = []
    for m in d.get("models", []):
        mid = m.get("name", "").removeprefix("models/")
        if "generateContent" in m.get("supportedGenerationMethods", []) and mid.startswith("gemini") \
                and not any(b in mid for b in bad):
            out.append(mid)
    return sorted(out)


def open_pr_exists(prefix):
    for pr in gh("GET", "/pulls?state=open&per_page=50"):
        if pr["title"].startswith(prefix):
            return True
    return False


def issue_exists(prefix):
    for it in gh("GET", "/issues?state=open&per_page=50"):
        if "pull_request" not in it and it["title"].startswith(prefix):
            return True
    return False


def check_models():
    src = open(PREFS, encoding="utf-8").read()
    changes = []
    for const, key, lister, pref in (
        ("DEFAULT_GROQ_MODEL", GROQ, groq_models, GROQ_PREF),
        ("DEFAULT_GEMINI_MODEL", GEMINI, gemini_models, GEMINI_PREF),
    ):
        if not key:
            print(f"{const}: no API key, skipped")
            continue
        try:
            available = lister()
        except Exception as e:  # network or key problem: never fail the workflow
            print(f"{const}: could not list models: {e}")
            continue
        m = re.search(rf'{const} = "([^"]+)"', src)
        current = m.group(1) if m else ""
        if current in available:
            print(f"{const}: {current} is still available")
            continue
        new = next((p for p in pref if p in available), available[0] if available else None)
        if new and new != current:
            src = src.replace(f'{const} = "{current}"', f'{const} = "{new}"')
            changes.append(f"{const}: {current} -> {new}")
    if not changes:
        return
    title = "chore(models): update default AI models"
    if open_pr_exists(title):
        print("model PR already open")
        return
    branch = "ai/model-update-" + datetime.date.today().isoformat()
    open(PREFS, "w", encoding="utf-8").write(src)
    run("git", "config", "user.name", "maintainer-bot")
    run("git", "config", "user.email", "maintainer-bot@users.noreply.github.com")
    run("git", "checkout", "-b", branch)
    run("git", "add", PREFS)
    run("git", "commit", "-m", title)
    run("git", "push", "origin", branch)
    gh("POST", "/pulls", {"title": title, "head": branch, "base": "main",
                           "body": "Automatic weekly check found retired default models:\n\n- " + "\n- ".join(changes)
                           + "\n\nPlease review, test a reply on a phone, then merge."})
    print("opened PR:", changes)


def report_build_failure():
    if BUILD == "success":
        return
    title = "Weekly build failed"
    if issue_exists(title):
        return
    log = ""
    try:
        log = "".join(open("build.log", encoding="utf-8", errors="ignore").readlines()[-80:])
    except Exception:
        pass
    body = f"The weekly build finished with outcome `{BUILD}`.\n\n```\n{log}\n```\n"
    if GROQ and log:
        try:
            body += "\n### AI diagnosis (suggestion only)\n\n" + llm(
                "You are an Android/Kotlin expert. Explain the likely cause and the smallest fix for this Gradle build failure:\n" + log)
        except Exception as e:
            body += f"\n(AI diagnosis unavailable: {e})"
    gh("POST", "/issues", {"title": title, "body": body})


def weekly_ideas():
    if not GROQ:
        print("weekly ideas: no GROQ_API_KEY, skipped")
        return
    year, week, _ = datetime.date.today().isocalendar()
    title = f"Weekly AI suggestions {year}-W{week:02d}"
    if issue_exists(title):
        return
    readme = open("README.md", encoding="utf-8").read()[:6000]
    try:
        text = llm(
            "You review an open-source Android SMS auto-reply app that always labels replies as AI. "
            "Based on this README, suggest 5 concrete improvements for UI/UX, reliability, accessibility, "
            "safety and discoverability (SEO/AEO). Keep each to 2 lines. Never suggest hiding the AI label.\n\n" + readme)
    except Exception as e:
        print("weekly ideas failed:", e)
        return
    gh("POST", "/issues", {"title": title, "body": text + "\n\n_Generated automatically. Review before acting._"})


if __name__ == "__main__":
    for step in (check_models, report_build_failure, weekly_ideas):
        try:
            step()
        except Exception as e:
            print(f"{step.__name__} failed: {e}")
