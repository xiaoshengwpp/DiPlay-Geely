# Official v0.2.22 synchronization review — paused before integration

Reviewed 2026-10-28. This branch preserves a review checkpoint, NOT an integrated 0.2.22 candidate.

- Target main: `7ef61dfa0f3ebca35af949ce03829e72c5fee507` (accepted 0.2.21).
- Official v0.2.22: `28d6002ba00320f0ba745212160b08759e4f60b9`.
- Connected account has push/admin permission. Main was not changed.
- Upstream v0.2.21 to v0.2.22 changes 303 files (+15110/-911 lines).
- Ordinary historical three-way merge produced 189 conflicted paths; prior source integrations do not make v0.2.21 a merge ancestor. The merge was aborted. A delta-based three-way application was also rejected atomically because several upstream paths do not exist in the fork. No partial code is committed.

## Decision required: new instrument-panel control

Official 0.2.22 introduces GeelyClusterDisplay and GeelyClusterProjection. The hardware gate is msmnile_gvmq / geely_fx11 / CS11_A4; this is not evidence of compatibility with the user's 2025 Xingyue L. Projection uses hidden ServiceManager lookup and DimNaviService binder transactions 9/10 to mutate/read a head-unit-wide switch shared with the stock map, including stale-state handback and watchdog behavior. This is a material behavior change, not just a passive public-display adaptation. It must not silently replace the fork's per-capability opt-ins and temporary display consent. Preserve existing behavior unless the user explicitly chooses a reviewed subset or approves investigating this new path.

## Language and safety boundaries

Current fork still defaults an unset app-language choice to Simplified Chinese and preserves explicit SYSTEM, saved language and Android 13+ platform locale choices. Upstream adds Korean and Turkish; those additions must be merged without replacing fork defaults/migration behavior. Existing inert ADB, hotspot credential read/write, BYD mutation and upstream APK-update boundaries must remain. No reference-fork bulk merge is involved.

## Validation evidence and limits

- Existing unchanged fork baseline: public-tree credential-container scan passes; 155 Python source/policy tests pass (121 scripts, 10 common UI boundary, 24 shared).
- Required Gradle aggregate was attempted against the unchanged baseline using a writable isolated GRADLE_USER_HOME. It stopped before configuration while downloading Gradle 9.5.0 (Network is unreachable). JVM tests, lint and assemblies did not run here. No integrated candidate exists and no 0.2.22 acceptance is claimed.
- Official failed run https://github.com/shihabal3amri/DiPlay/actions/runs/40659785858 checked out PR1169 merge-test commit 2520919341c0ff55423c66ba90a1962cfb277962, not a direct tag checkout. Its log reports 1798 shared tests, one failure: AirPlayEventPeerTest.aStrangerCannotTakeTheEventSlot, java.net.SocketException at line 62. Do not assume flakiness or treat a different successful run as proof this failure is resolved.

No user computer, private authentication material, credential configuration, signed release, APK installation/execution, maintainer contact, force push or main merge was used. Review branch only; ask for the scope decision before proceeding.
