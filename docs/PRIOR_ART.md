# Prior art: study these two open-source apps and borrow from them

The owner explicitly wants you to **read through the full source of both apps below and take what is useful from them**. Do not be shy about it: clone them, read them properly, and reuse code and structure where it helps. The only rules are licenses (below), so that the owner is never surprised.

## The two repos
1. **Myspoti**: https://github.com/kota-zo/myspoti
   Native Android Spotify client (Jetpack Compose). License: **GPL-3.0**.
   Worth studying: Spotify sign-in flow (user logs in on Spotify's own page, app never sees the password); how it drives Spotify's player from its own screens; library/search/playlist calls; Home, Search, Your Library and player UI structure; lyrics (LRCLIB, Genius); module layout (`:api`, `:api-spotify`, `:host-android`); recap screen built from top tracks/artists; Bluetooth device and codec line in the player.
2. **SpatialFlow**: https://github.com/MythicalSHUB/SpatialFlow
   Android music player (Kotlin, Jetpack Compose, Media3/ExoPlayer). License: **Apache-2.0**.
   Worth studying: the InnerTube (WebRemix) client for YouTube Music search/browse/streaming (it credits OuterTune for the call logic); NewPipe Extractor use; Ktor networking; one queue shared by local and online tracks; crossfade, normalization, equalizer; lyrics providers (LRCLIB and others); Material 3 theming with dynamic color from album art.

## How to work
1. `git clone` both into a scratch folder **outside** the Duet repo (e.g. `~/prior-art/`). Read the READMEs, the Gradle module structure, then the actual code paths for: auth, search, playback, queue, lyrics, now-playing UI.
2. Write `docs/PRIOR_ART_NOTES.md` in the Duet repo: for each feature, what each app does, which file(s), what you will reuse, what you will do differently. Do this before writing the matching Duet code.
3. Reuse freely. Port classes, copy algorithms, adapt UI structure. Prefer the better of the two for each feature; both are fair game.
4. Keep a ledger in `docs/THIRD_PARTY.md`: every file or chunk taken, from which repo and path, its license, and what was changed.

## License rules (the only guardrails)
- **SpatialFlow (Apache-2.0):** copy and adapt freely. Keep the copyright/license headers on copied files, add a `NOTICE` entry, and list it in `THIRD_PARTY.md`. This is the easy one.
- **Myspoti (GPL-3.0):** copying its code is allowed, but then **Duet as a whole must be released under GPL-3.0 if Duet is ever distributed**. For purely personal, sideloaded use there is no practical obligation. **Ask the owner once, at the start, which they want:**
  - (a) Duet is GPL-3.0 (then copy from Myspoti freely, same as SpatialFlow), or
  - (b) keep Duet's license open (then study Myspoti's approach and re-implement from understanding, no code copied).
  Record the owner's answer in `docs/THIRD_PARTY.md` and follow it.
- **Do not port Myspoti's ad-blocking** (it disables ad breaks in Spotify's player; its own README says this breaks Spotify's Terms and may get accounts suspended). Everything else is fair.
- Also check each repo's dependencies' licenses (NewPipe Extractor is GPL-3.0; the same GPL question applies if Duet uses it directly).
- Ideas, architecture, API usage and UI patterns are not copyrightable; only the code text is. Look at the actual code, do not guess from the READMEs.
