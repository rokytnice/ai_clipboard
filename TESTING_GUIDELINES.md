# Testing Guidelines for YouTube Audio Player App

This document outlines test cases for the YouTube Audio Player Android application.

## I. Core Functionality

1.  **Valid URL Playback:**
    *   Paste a standard YouTube video URL (e.g., \`https://www.youtube.com/watch?v=dQw4w9WgXcQ\`).
    *   Click "Fetch Audio".
    *   **Expected:** Status updates to "Fetching...", then "Audio ready...". Playback controls become visible.
    *   Click Play.
    *   **Expected:** Audio starts playing. Status updates to "Playing". Play/Pause button shows "Pause" icon. Notification appears with playback controls.
2.  **Pause Functionality:**
    *   While audio is playing, click the Pause button.
    *   **Expected:** Audio pauses. Status updates to "Paused". Play/Pause button shows "Play" icon. Notification updates to show "Play" icon.
3.  **Resume Functionality:**
    *   While audio is paused, click the Play button.
    *   **Expected:** Audio resumes playing from where it left off. Status updates to "Playing". Play/Pause button shows "Pause" icon. Notification updates.
4.  **Stop Functionality:**
    *   While audio is playing or paused, click the Stop button.
    *   **Expected:** Audio stops. Status updates to "Stopped". Play/Pause button shows "Play" icon. MediaPlayer resources are released. Notification might update or be dismissed depending on implementation.
5.  **Background Playback (App Minimized):**
    *   Start playing audio.
    *   Press the Home button to minimize the app.
    *   **Expected:** Audio continues to play in the background.
6.  **Background Playback (Screen Off):**
    *   Start playing audio.
    *   Turn off the screen.
    *   **Expected:** Audio continues to play.
7.  **Notification Controls:**
    *   While audio is playing/paused from background:
        *   Use the Pause button in the notification. **Expected:** Audio pauses, UI in notification updates.
        *   Use the Play button in the notification. **Expected:** Audio plays, UI in notification updates.
        *   Use the Stop button in the notification. **Expected:** Audio stops, notification might be dismissed or updated.
8.  **Open App from Notification:**
    *   While audio is playing (and app is in background), tap the notification body (not the action buttons).
    *   **Expected:** The MainActivity of the app opens and reflects the current playback state.

## II. URL Variations

1.  **Short YouTube URL (\`youtu.be\`)**:
    *   Test with a URL like \`https://youtu.be/dQw4w9WgXcQ\`.
    *   **Expected:** Works the same as standard URLs.
2.  **URL with Timestamp**:
    *   Test with a URL like \`https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=60s\`.
    *   **Expected:** Audio playback starts from the beginning of the video (timestamp in URL is typically for video player start time, audio extraction might not honor it by default). Note actual behavior.
3.  **YouTube Live Stream URL**:
    *   Find a currently live YouTube stream.
    *   Test with its URL.
    *   **Expected:** Behavior might vary. It might play the live audio, show an error if the format is incompatible, or continuously buffer. Document the outcome. (NewPipeExtractor might support some live streams).
4.  **YouTube Mix/Playlist URL**:
    *   Test with a URL for a YouTube mix or playlist.
    *   **Expected:** Likely an error, as the app is designed for single video URLs. NewPipeExtractor might only fetch info for the list, not individual stream URLs directly in this context. Document behavior.

## III. Error Handling & Edge Cases

1.  **Invalid YouTube-like URL (Incorrect Video ID):**
    *   E.g., \`https://www.youtube.com/watch?v=invalidID123\`
    *   **Expected:** Status shows an error like "Could not extract audio..." or "Invalid URL...". `btnFetchAudio` re-enabled.
2.  **Completely Non-YouTube URL:**
    *   E.g., \`https://www.google.com\`
    *   **Expected:** Status shows "Please enter a valid YouTube URL." due to basic validation, or an extraction error if basic validation is bypassed.
3.  **Empty URL Input:**
    *   Click "Fetch Audio" without entering any URL.
    *   **Expected:** Status shows "Please enter a YouTube URL."
4.  **Video With No Audio Stream (e.g., some specific art tracks or silent videos):**
    *   Find such a video if possible.
    *   **Expected:** Status shows "No audio streams found..." or similar.
5.  **Age-Restricted Video (requiring login on YouTube):**
    *   Test with an age-restricted video URL.
    *   **Expected:** Likely an error message such as "Video may be protected (ReCaptcha)..." or "Could not extract audio...".
6.  **Private Video:**
    *   Test with a URL of a private video you don't have access to.
    *   **Expected:** Error message indicating failure to extract.
7.  **Members-Only Video:**
    *   Test with a URL of a members-only video if you are not a member.
    *   **Expected:** Error message indicating failure to extract.
8.  **Network Interruption (During Fetch):**
    *   Start fetching audio, then turn off Wi-Fi/data.
    *   **Expected:** Status shows a network-related error message (e.g., "Network Error. Check connection..."). `btnFetchAudio` re-enabled.
9.  **Network Interruption (During Playback):**
    *   Start playing audio, then turn off Wi-Fi/data.
    *   **Expected:** Playback stops or enters buffering state, then eventually errors out. Status message updates accordingly. Notification reflects this.
10. **Rapid Button Clicks:**
    *   Quickly click "Fetch Audio" multiple times.
    *   Quickly click Play/Pause multiple times.
    *   **Expected:** App remains stable, does not crash, and handles state correctly (e.g., subsequent clicks are ignored or queued appropriately). `btnFetchAudio` should be disabled during fetching.

## IV. Audio Focus Handling

1.  **Interruption by Another App:**
    *   Start playing audio in this app.
    *   Open another app (e.g., Spotify, another YouTube app, a game) and start playing audio/video there.
    *   **Expected:** This app's audio should pause (due to `AUDIOFOCUS_LOSS_TRANSIENT` or `AUDIOFOCUS_LOSS`). Notification should update.
2.  **Resume After Interruption (Transient Loss):**
    *   While this app's audio is paused due to transient focus loss (e.g., a short notification sound from another app if that causes transient loss).
    *   When the other app releases audio focus.
    *   **Expected:** This app should ideally resume playback automatically if focus loss was transient and playback was active. If not, it should remain paused and allow manual resume. (Test current implementation).
3.  **Resume After Interruption (Permanent Focus Loss):**
    *   After audio pauses due to `AUDIOFOCUS_LOSS` (e.g., another music app started).
    *   Stop audio in the other app.
    *   Return to this app.
    *   **Expected:** User should be able to manually resume playback by pressing Play.
4.  **Phone Call Interruption:**
    *   Start playing audio.
    *   Simulate an incoming phone call (or make a real one to the test device).
    *   **Expected:** Audio pauses or stops.
    *   End the call.
    *   **Expected:** App should allow manual resume (or auto-resume if it was only a transient loss, though calls are usually permanent loss).

## V. Lifecycle & State Management

1.  **Screen Rotation:**
    *   Start playing audio.
    *   Rotate the screen.
    *   **Expected:** Audio continues to play without interruption. UI state (buttons, status text) is correctly restored.
2.  **Minimize and Restore App:**
    *   Fetch a URL, but don't press play. Minimize and restore. **Expected:** URL and "Audio ready" state persist.
    *   Start playing, pause, minimize, restore. **Expected:** Paused state persists.
3.  **App Closed by System (while in background - harder to test reliably):**
    *   Start playback, send app to background.
    *   If possible (e.g. via developer options "Don't keep activities" or by memory pressure), have the system destroy the activity and then the service.
    *   Re-launch app.
    *   **Expected:** App starts fresh. If service was properly managed with `START_STICKY` (not current) or if user explicitly restarts, state might be different. Current is `START_NOT_STICKY`. Test behavior on re-launch after service has been stopped.
4.  **Clearing App from Recents:**
    *   Start playback.
    *   Clear the app from the "Recent Apps" list.
    *   **Expected:** Service stops, playback terminates, notification is removed.

## VI. UI/UX

1.  **Clarity of Status Messages:**
    *   Verify all messages in `tvStatus` are clear, concise, and accurately reflect the app's current state or errors.
2.  **Responsiveness of Controls:**
    *   Ensure all buttons (`Fetch Audio`, Play/Pause, Stop) are responsive.
3.  **Play/Pause Icon States:**
    *   Verify the Play/Pause button icon correctly toggles between "Play" and "Pause" states.
4.  **Notification Appearance:**
    *   Check notification on different Android versions (if possible).
    *   Ensure icons, text, and actions are displayed correctly.
5.  **Initial App State:**
    *   When app is launched for the first time.
    *   **Expected:** `tvStatus` shows "Enter a YouTube video URL to start.". Playback controls are hidden or disabled.

This list is not exhaustive but covers the main functionalities and potential issues.
