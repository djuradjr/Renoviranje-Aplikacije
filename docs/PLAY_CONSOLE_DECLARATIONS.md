# Play Console — deklaracije (copy-paste artifact)

Ovaj fajl je **jedini izvor istine** za tekst koji se lepi u Play Console.
Kad se ponašanje app-a promeni, prvo izmeni ovde, pa u Konzoli — tako je
diffabilno po release-u (najčešći razlog odbijanja a11y aplikacija je
deklaracija koja se razišla sa stvarnim ponašanjem).

Provereno prema kodu na grani `placanje-billing` (verzija 1.1.0, versionCode 3):
- app: `com.djuki.blockbrainrot` (debug `.debug`)
- a11y klasa: `com.example.stayfree.service.StayFreeAccessibilityService`
- config: `flagReportViewIds|flagRetrieveInteractiveWindows`, `canRetrieveWindowContent=true`

---

## 1. Accessibility API — Prominent disclosure (Policy Declaration)

**Which accessibility capabilities does your app use?**
`flagReportViewIds`, `canRetrieveWindowContent` (retrieve window content /
active window info).

**Describe the functionality this enables (paste):**

> Block Doomscroll is a self-control tool that the device owner configures to
> block distracting apps and websites on their own device. The Accessibility
> Service is used only to detect what is currently on screen so the block
> screen can appear at the right moment:
>
> 1. The package name of the foreground app, to know which app is open.
> 2. `viewIdResourceName` and on-screen bounds of UI nodes, to detect specific
>    short-form-video surfaces the user chose to block (e.g. Reels, Shorts).
> 3. Only when the user has added one or more websites to block: the text of
>    the browser's address bar, to read the current URL and match it against
>    the user's blocklist.
>
> The service never reads, stores, or transmits general screen text, messages,
> or content beyond the URL described above. The user selects every blocked
> target, except strict mode: once the user enables the service (explained on the
> setup screen and in the disclosure), it keeps a fixed list of social-network apps (e.g. Instagram, TikTok,
> YouTube, Facebook) closed, each openable for 1–15 minutes at a time; the list is
> shown on the strict mode screen. Nothing is monitored silently. All processing is on-device; what the
> service reads is never transmitted, shared, or sold. The app's only network use is
> the Clean Instagram, Clean Facebook and Clean YouTube screens, WebViews that talk to
> instagram.com, facebook.com and youtube.com directly.
>
> Before the Accessibility settings are ever opened, an in-app disclosure
> screen shows this same explanation and the user must accept it.

**Is there an alternative to the Accessibility API?** No — detecting the
foreground app/URL the instant it opens, in order to draw our own block screen,
is not possible through a less-privileged API for third-party apps.

> ⚠️ Mora se poklopiti sa in-app disclosure tekstom
> (`R.string.disclosure_message`). Ako menjaš jedno, menjaj oba.

**Attach:** screenshot in-app disclosure dijaloga + kratak screen-recording
disclosure → Accessibility settings flow.

---

## 2. Foreground Service (specialUse)

**Type:** `specialUse`. Subtype (već u manifestu):

> Continuous on-device screen-time tracking that powers the user's app-blocking
> limits and statistics. No data leaves the device.

**Why a persistent FGS is needed (paste):**

> The service tracks on-screen time continuously so per-app daily limits and
> usage statistics stay accurate even when the app is in the background. While
> tracking, a persistent notification is shown (on Android 13+ with notifications
> off, Android lists the service in its Task Manager instead). This cannot be done
> with WorkManager/JobScheduler because enforcement must be immediate and
> continuous, not batched.

**Attach:** video koji pokazuje trajnu notifikaciju.

---

## 3. App access (instrukcije za reviewera, paste)

> No account or login exists — the app opens straight to onboarding.
>
> Blocking is a paid feature, except strict mode's social-app block, which is free.
> Onboarding asks for Usage access, shows a report, then the subscription screen
> (closing it skips it and Accessibility is asked for strict mode): tap "Have a promo code?", enter the code
> below and tap "Redeem in Google Play"; confirm in Google Play, then press Back
> to return. The app unlocks and asks for Accessibility — accept the disclosure
> and follow the on-screen guide in Settings. (The code also works in Play Store →
> profile → Payments & subscriptions → Redeem code; redeemed before onboarding,
> the subscription screen is skipped and Accessibility is asked right away.)
>
> Then add an app (e.g. Instagram) or a website to the block list and open it —
> the block screen appears. Screen-time tracking and statistics work without a
> subscription. Everything is on-device; there is no server.
>
> Promo code: <CODE> — it grants a one-time item that unlocks premium for free.
> It is not a trial: no payment method is needed and nothing is charged later.

Kod se pravi za jednokratni proizvod `premium_review`, NIKAKO za pretplatu:
promo kod pretplate je besplatna proba sa karticom i automatskom naplatom, a
recenzenti probe ne koriste (piše na samoj stranici App access).

---

## 4. Data safety

- Naš kod ništa ne šalje — nema servera, analitike ni JS interfejsa u WebView-ima.
  Ali Play podatke iz WebView-a koji aplikacija otvara na fiksnom sajtu (nije
  „otvoren web“) može da računa kao prikupljene, zato odgovor više nije „None“.
- INTERNET postoji od `cisti-instagram`, SAMO za WebView-e čistih feedova
  (instagram.com, facebook.com, youtube.com + njihovi CDN-ovi, npr. googlevideo.com,
  ytimg.com, fbcdn.net, cdninstagram.com). Korisnik tamo direktno koristi sajt
  Mete / Google-a; prijava ostaje kao kolačić tog sajta na uređaju.
- Kako popuniti formular (Djuki, u Play Console → App content → Data safety):
  1. „Does your app collect or share any of the required user data types?“ → **Yes**.
  2. Tipovi (sve *collected*, *not shared*, svrha **App functionality**, obavezno
     = **No** jer su čisti feedovi opcioni, „processed ephemerally“ = **No**):
     - Personal info → **Email address**, **User IDs** (prijava na sajt)
     - Messages → **Other in-app messages** (poruke na Instagramu/Facebooku)
     - Photos and videos → **Photos** (slika poslata u poruci/objavi)
     - App activity → **App interactions**, **Other user-generated content**
       (otvorene objave i videa, komentari)
  3. Šifrovanje u prenosu: **Yes** (samo HTTPS, `MIXED_CONTENT_NEVER_ALLOW`).
     Brisanje: korisnik briše podatke aplikacije / nalog na tom sajtu.
  4. Pre slanja pročitaj Play pomoć „Data safety“ → deo o WebView-u; ako Google
     izričito kaže da sadržaj sajta otvorenog u WebView-u ne treba prijaviti,
     odgovor može da ostane „No“.
  5. Play Billing `datatransport` telemetrija (Google): od kad INTERNET postoji,
     može da ide na mrežu. Google je navodi kao podatke koje Billing obrađuje —
     ne menja naš odgovor, ali to proveri u Billing „Data safety“ smernicama pre
     slanja.
- Account username/email (if set) is local-only (DataStore, backup-excluded).
- Play Billing ne menja ovaj odgovor — Google procesira te podatke. Provereno
  2026-09-23 na pravoj kupovini: u aplikaciji se čuva samo `premium_active`
  (boolean), bez tokena, broja porudžbine i mejla; u logovima nema ničega od toga.
- Posle svake nadogradnje Billing-a ili WebView-a proveri `aapt2 dump permissions`
  na release APK-u: INTERNET je očekivan, ništa drugo novo.

---

## 5. Store listing — kategorija i publika

- Category: **Tools** (ili Health & Fitness). **NIKAD Parenting/Families.**
- Target audience: **NOT children.** Self-blocking alat za vlasnika uređaja.
- Listing mora da kaže da je blokiranje deo pretplate (merenje vremena i
  statistika ostaju besplatni) — opis koji to prećuti je obmanjujuć.
- Screenshot-ovi moraju da prikažu i ekran sa pretplatom.

---

## 6. Pretplata (Play Billing)

- Proizvod: `premium`, bazni planovi `monthly` / `quarterly` / `semiannual`,
  ponude `trial-monthly` / `trial-quarterly` / `trial-semiannual` (7 dana,
  samo za one koji pretplatu nikad nisu imali).
- `premium_review`: jednokratni proizvod koji aplikacija nikad ne nudi — dobija
  se samo promo kodom, za recenzente. Opcija kupovine „Buy" (ne „Rent", ona
  ističe), cena najviše kao 6-mesečni plan da ne širi raspon cena na listingu.
- Paywall pre kupovine prikazuje: cenu iz Play-a, period, dužinu probe, kada
  počinje naplata i da se otkazuje u Google Play-u. Bez lažne hitnosti; mesečni
  plan je unapred izabran, nijedan skuplji nije.
- Otkazivanje ide kroz Play (Podešavanja → Premium → upravljanje pretplatom).
  U aplikaciji nema zasebnog toka otkazivanja.
- Kad pretplata istekne ili bude povučena, blokade se GASE i korisnik ih uvek
  može isključiti bez plaćanja — aplikacija nikad ne drži uređaj zaključan.
