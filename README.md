# Janken Mind

An Android rock-paper-scissors (janken) game where the AI learns your habits and tries to read your next move. Each hand is played by an animal character (fox, turtle, eagle) with its own animations.

## Features

- **Mind-reading AI**: the AI counts which hand you play next after each combination of your last hand and the last result (9 situations), so it learns habits like "keep the hand after a win, switch after a loss". With little data it falls back to your most common hand, and it plays randomly when it is not confident. What it learns is saved between sessions.
- **Animal animations**: each animal has idle, pose, crying and surprised images, with keyframe animations for each hand (`AnimalMotion.kt`).
- **Stats**: win rate, results and a summary of your favourite hands.
- **5 languages**: Vietnamese, Japanese, English, Korean and Chinese, switchable inside the app. The choice is remembered.
- **Tablet layout** for screens 600dp and wider.

## Build

Open the project in Android Studio, or build from the command line:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Requires Android 7.0 (API 24) or newer.

## Code

- `app/src/main/java/com/example/kadai7/Janken.kt`: hands, judging, the `MindReader` AI and stats
- `AnimalMotion.kt`: animation keyframes for each animal
- `Languages.kt`: in-app language picker
- `MainActivity.kt`: screen, input and saving
- `animal-animation-updated.html`: a browser prototype of the animal animations
