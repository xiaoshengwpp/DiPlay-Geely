# Host-independent audio fixes (partial upstream port)

Source: upstream DiPlay `e06a250219b2c2288948d8b4ad7203b417db0de1`, selected audio changes only. Base: Geely main `99f3d29ace7d13ffb6a628c98d3ac76294f88602`.

- Opus still decodes at 48 kHz. Negotiated 16/24/48 kHz RTP clocks now drive feedback sample rate, sampleTime and drift diagnostics; PCM/AAC retain their existing clocks.
- Audio decoder input retries drain output with a 100 ms retry budget rather than the unchanged video default of 500 ms. This is a pump retry budget, not a hard real-time guarantee for platform calls or an end-to-end latency promise.
- Call playback diagnostics use `CALL`; private iPhone `PHONE` logs remain filtered. No captured audio is added.

Only AudioStream, CarPlayMediaEngine and three local AndroidMediaSink hunks change production code. Existing Geely navigation routing, inactive OEM logic and all other product boundaries remain unchanged. No Host, microphone recovery, permission/grant, USB, manifest, language, signing, version, release or installation changes are included. This is neither a full upstream version sync nor a Geely capability restoration.

## Verification

Added JVM coverage for negotiated clocks, PCM/AAC compatibility, unsigned feedback wrap and pre-playout clamping, engine-owned drift monitor, stalled audio versus video retry budgets, recovery/shutdown and diagnostic filtering. Added Python source-wiring contracts; existing navigation and OEM-negative suites remain intact.

Local public-tree check and all 88 Python contracts passed. The required aggregate Gradle command was attempted but could not download Gradle in the local restricted network. The unchanged GitHub Android checks workflow is the authoritative full unit/lint/source-only debug validation; its exact-SHA result belongs on the review PR. No APK is a deliverable. Calls, Siri and background music still require parked real-car acceptance; no physical vehicle behavior is established by this patch.
