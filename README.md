# Nihongo Steps: Japanese at your own pace (Android app + home-screen widget)

1000 sessions of 5 words or phrases (5,002 items), with listening, speaking practice and a progress tracker.

## What's inside
- **Sessions 1-84:** starter course: greetings, polite basics, numbers, all of hiragana, everyday sentences, katakana and real-life conversations.
- **Sessions 85-1000:** JLPT vocabulary from N5 (beginner) through N4 and N3 to N2, each word with kana reading, romaji and meaning.
- **Reviews:** every 7th session reviews earlier items.
- **Your own pace:** do one session a week or ten a day. Jump to any session with "Go to session…".

## How a session works
1. **Read** each card: Japanese, kana reading, romaji (can be hidden) and English meaning.
2. **Listen:** 🔊 plays the word in a Japanese voice, at a slow, relaxed or normal speed. "▶ Play all" reads all 5.
3. **Speak:** 🎤 opens Google speech recognition in Japanese and checks what you said. A matching answer shows ✓ Great!
   - Recognition often writes kanji for a kana phrase. If it heard you correctly but didn't match, tap "It was right ✓".
4. **Finish ✓:** shows your progress tracker:
   - sessions done out of 1000 and the percentage;
   - words and phrases practised;
   - daily streak and best streak;
   - sessions today.

## The widget
- **Card:** shows the current session's words one at a time (◀ ▶), with romaji and meaning.
- **🔊 Listen:** speaks the word right from the home screen.
- **Start ▸:** opens the full session, for speaking practice and finishing.
- **Progress bar:** your progress across the 1000 sessions, plus your streak.

## Build the APK (free, no computer setup)
1. **Create an account:** go to https://github.com and make a free account.
2. **Create a repository:** tap **+ → New repository**, name it `NihongoSteps`, choose **Public**, then tap **Create**.
3. **Upload the project:** tap **uploading an existing file**, and upload everything inside this folder, including the hidden `.github` folder. Then tap **Commit**.
4. **Wait for the build:** open **Actions**. "Build APK" runs in about 3-5 minutes.
5. **Download:** open the finished run and download **NihongoSteps-apk**.
6. **Install:** on the phone, open `app-debug.apk` and allow "Install unknown apps". If Samsung Auto Blocker blocks it, turn it off temporarily.
7. **Add the widget:** long-press the home screen, tap **Widgets**, and add **Nihongo Steps**.

Or open the folder in Android Studio and choose **Build → Build APK(s)**.

## Phone setup for sound and speech (Samsung Galaxy)
- **Listening:** needs a Japanese text-to-speech voice. The app offers to install one if it's missing. You can also add it under Settings → General management → Text-to-speech, using Samsung or Google TTS.
- **Speaking:** uses the Google app's speech recognition. Make sure the Google app is installed and up to date. For better results, download Japanese for offline speech recognition in Google voice settings.

## Credits
- **Vocabulary:** [open-anki-jlpt-decks](https://github.com/jamsinclair/open-anki-jlpt-decks) by Jamie Sinclair (MIT licence), based on JLPT word lists and the JMdict dictionary.
- **Data build:** romaji was generated from the kana readings by `tools/gen_data.py` (Hepburn, with macrons for long vowels).

## Technical
- **Code:** Java, Android framework only, with no libraries. minSdk 26, targetSdk 35.
- **Data:** `app/src/main/assets/sessions.tsv` (session, topic, japanese, reading, romaji, meaning).
- **Progress:** saved on the phone, in SharedPreferences.
