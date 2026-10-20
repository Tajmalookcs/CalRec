# CallRec — learning project

A minimal Android call recorder, no root. Personal/educational use only.

## How it works
- `CallRecorderService.kt` is an **Accessibility service**. Android only lets
  apps like this use the mic during a call.
- It watches the call state: **OFFHOOK → start recording**, **IDLE → stop**.
- Audio comes from the **microphone** (not the call line), so the other
  person is clearest on **speakerphone**.
- Files are saved as `.m4a` in the app's private folder:
  `Android/data/com.example.callrec/files/Music/`

## Build the APK
**With a computer:** open the `CallRec` folder in Android Studio → Build → Build APK.

**Phone only:** upload the folder to a GitHub repo. Open the **Actions** tab,
run **Build APK**, and download the `CallRec-apk` artifact when it's done.

## Install on the Galaxy A15
1. Open the APK and allow "Install unknown apps" for your file manager.
2. Open CallRec → **Grant permissions**.
3. Tap **Turn on in Accessibility** → Installed apps → CallRec recorder → On.
   If it's greyed out ("Restricted setting"): Settings → Apps → CallRec →
   ⋮ (top right) → **Allow restricted settings**, then try again.
4. Make a test call. Come back to the app to play the recording.

## Ideas to extend (good practice)
- Save the phone number in the filename (needs `READ_CALL_LOG`).
- Add a share button (needs a `FileProvider`).
- Show a notification while recording.
- Auto-delete recordings older than 30 days.

Remember: tell people when you record them where the law requires it.
