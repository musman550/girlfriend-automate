<div align="center">

# Girlfriend Automate

**The open-source AI SMS auto-reply assistant for Android. Busy at work, studying or driving? Your AI keeps the chat warm, and always says it is an AI.**

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84.svg)](#requirements)
[![Open source AI](https://img.shields.io/badge/AI-Groq%20%7C%20Gemini-blue.svg)](#which-ai-models-does-it-use)
[![Latest release](https://img.shields.io/github/v/release/musman550/girlfriend-automate)](../../releases/latest)

[Download the APK](../../releases/latest) · [Website](https://musman550.github.io/girlfriend-automate/) · [Report a bug](../../issues)

</div>

## What is Girlfriend Automate?

Girlfriend Automate is a free, MIT-licensed Android app that reads incoming SMS from the contacts **you choose**, writes a short, natural reply in **your** chat style using an AI model (Groq or Gemini), and sends it back by SMS. It works for a partner, friends, family or clients.

It is built on one rule: **the other person is never tricked.** Every reply starts with an intro you write yourself (for example, "Main Asad ka AI assistant hun, Asad abhi busy hain."). If your intro does not contain the word "AI", the app adds "(AI)" automatically.

> Why this exists: people get busy, and silence hurts. A clearly labelled AI assistant that says "I'm here, they'll reply soon" is kinder than leaving a message unanswered for hours.

## Features

- **Choose who gets replies**: type a name or number, or pick from your contacts. Each contact is a removable chip.
- **Your style**: the AI reads recent chat history and matches your tone, in Roman Urdu, Hinglish, English or any language you write in.
- **Custom intro and persona**: write any friendly intro you like. Your name is filled in with `{NAME}`.
- **Approve mode**: get a notification with the draft and tap **Send** or **Ignore**.
- **Urgent words**: messages containing words like "hospital" or "emergency" are never auto-answered. You get a notification instead.
- **Stop words**: if the other person sends "stop", they are muted automatically.
- **Natural timing**: random reply delay, cooldown between replies, hourly cap and optional active hours.
- **Pause, Resume, Stop, Remove** controls on the main screen.
- **Two AI providers with fallback**: if one fails or retires a model, the app switches model or provider on its own.
- **Live model picker and API test**: no more "model not found" surprises.
- **History screen** of every reply, skip and error.
- **Private by design**: no analytics, no server of ours. Messages go only to the AI provider you pick.
- **Dark and light mode**, Material 3 design.

## Install

1. Open the [latest release](../../releases/latest) on your phone and download `GirlfriendAutomate.apk`.
2. Allow "Install unknown apps" for your browser when Android asks (this is normal for apps outside the Play Store).
3. Open the app, allow the SMS, Contacts and Notification permissions.
4. Paste a free API key from [Groq](https://console.groq.com/keys) or [Google AI Studio](https://aistudio.google.com/apikey).
5. Add a contact, write your name and intro, tap **Save**, then switch **Auto-reply ON**.
6. In Android battery settings set the app to **Unrestricted** so it can reply in the background.

### Requirements

- Android 9.0 (API 28) or newer, with a SIM that can send SMS.
- Internet connection and a free API key.
- Normal SMS only. WhatsApp, Instagram and RCS "chat features" are not supported.
- Standard SMS charges from your carrier apply to every reply.

## Build from source

```bash
git clone https://github.com/musman550/girlfriend-automate.git
cd girlfriend-automate
# open in Android Studio, or:
gradle :app:assembleDebug
```

Every push to `main` is built and signed by GitHub Actions and published under Releases.

## Which AI models does it use?

Defaults are `openai/gpt-oss-20b` on Groq (an open-weight model, very fast) and `gemini-3.5-flash` on Gemini. Providers retire models often, so the app can list live models, pick a working one automatically and fall back to the other provider.

## Privacy and honesty

- Your API key and settings stay on your phone.
- Chat context is sent only to the AI provider you choose, to write the reply.
- No ads, no tracking, no accounts. See [PRIVACY.md](PRIVACY.md).
- **Please do not use this app to impersonate or deceive anyone.** The AI label is built in on purpose and the project will not add a way to remove it.

## Self-maintaining project

A weekly GitHub Action builds the app, checks that the default AI models still exist and opens a pull request with a fix if not, opens an issue if the build breaks, and posts AI-written improvement ideas. Dependabot keeps libraries current. Nothing is merged without a human review, because this app sends real SMS.

## FAQ

**Is Girlfriend Automate free?**
Yes. The app is free and MIT licensed. You only pay your carrier's SMS charges and, if you exceed free limits, your AI provider.

**Does it tell the other person that an AI is replying?**
Yes. Every reply starts with your AI intro. The app forces the word "AI" to be present.

**Can I use it for friends, family or customers instead of a partner?**
Yes. It works for any contact you add.

**Does it work with WhatsApp?**
No. It only handles normal SMS.

**Which Android versions are supported?**
Android 9 (API 28) and above.

**Is my chat private?**
The app has no server. Recent messages are sent to Groq or Gemini only when a reply is generated, under their terms.

**Why does Android warn me when I install it?**
The APK is signed but not distributed through the Play Store, which restricts SMS apps. Android shows the unknown-source prompt for every app installed this way.

**An AI model stopped working. What now?**
Open the app, tap **Live models dekhein** and pick a model, or tap **API test**. The app also tries to pick a working model automatically.

**How can I contribute?**
See [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports, translations and UI ideas are welcome.

## Keywords

AI SMS auto reply Android, automatic text message reply app, AI chat assistant for texting, auto responder for busy people, open source SMS bot, Groq Gemini Android app, Roman Urdu AI replies, relationship communication assistant, GPT-OSS Android.

## License

[MIT](LICENSE). Use it, fork it, improve it.
