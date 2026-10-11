# Reader's Attention

A companion for the phone that counts not the screen time but the use to no purpose: the apps
opened by reflex. Each day, in words: how many times the reflex apps were opened and for how
long, what came first after unlocking, the longest stretch, the evening, the quick checks. One of
the [Reader's](https://github.com/funkypitt/readers-launcher) apps: text only, white on black.

## Key points

* Two classes of apps. **Reflex**: the ones opened without meaning to: social networks, games,
  short video. **Tools**: everything else. The system's categories and a short list of well-known
  apps set the defaults; a tap on any app moves it to the other class, and your choice wins.
* The first page is today, in words: "23 openings · 1 h 12" for the reflex apps, the screen time
  and the unlocks under it, then the sentences that name the habit, each only when it applies:
  "after unlocking, a reflex app came first 9 times out of 41", "longest stretch: instagram,
  34 min, from 22:10", "after 22:00: 48 min of reflex apps", "checked instagram 14 times for less
  than a minute". Then one row per reflex app, and the tools folded under one row.
* A week page: one row per day, with last week's average.
* Two interventions, both off until you turn them on, neither of them blocking:
  **a pause before a reflex app**: the app's name, today's count, "what for?", and "go on" after
  a count. Light: four reasons to tap and six seconds (twenty in the evening). Firm: the reason
  typed in a few words and twelve seconds, because the pause works in proportion to what it
  costs. The reason stays with the opening, so you see the gap between it and what followed.
  **A word after a while**: one line at the top of the screen, "20 minutes in instagram", for a
  few seconds. Both need the accessibility service, which hears only which app comes to the front.
* The question and the sentences each have a few phrasings and change with the day, so they keep
  being read; there are never more of them than this.
* A home-screen widget: "reflex 23 · 1 h 12" and "tools 2 h 28".
* Everything is read on the phone from Android's own usage events, with the "usage access"
  permission. No network permission: nothing leaves the phone. Six languages.

## Install

- **F-Droid** (recommended, updates arrive by themselves): add the repository from [gallaz.ch/apps](https://gallaz.ch/apps/#fdroid), or the address `https://funkypitt.github.io/fdroid-repo/repo` in F-Droid.
- **Obtainium**: add `https://github.com/funkypitt/readers-attention` in Obtainium.
- **APK**: attached to the [latest release](../../releases/latest). No automatic updates.

All three deliver the same file, with the same signature.

## Build

```
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleDebug
```

minSdk 28, targetSdk 34.

## Crédits / Credits

© 2026 Pierre Gallaz. Développé avec [Claude Code](https://claude.com/claude-code) (Anthropic).
Licence MIT, voir `LICENSE`.

© 2026 Pierre Gallaz. Developed with [Claude Code](https://claude.com/claude-code) (Anthropic).
MIT licence, see `LICENSE`.
