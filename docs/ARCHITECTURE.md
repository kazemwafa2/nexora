# Architecture (summary; the full plan is in the shared planning document)

Ten independent layers: Android compatibility, desktop shell, window manager, internal apps,
file/document services, Windows compatibility engine, graphics backend, capability detection,
automated diagnostics, security/update management.

Rules enforced by code and tests in `core/`:
1. The desktop only knows `IEngine`; it never imports an engine implementation.
2. Every engine call goes through `EngineSupervisor`: exceptions, OOM and hangs become errors; a crash
   loop disables the engine until the user resets it. The desktop stays alive.
3. Tier is a pure function of measured facts and real test evidence. `DeviceFacts` has no brand or
   model field. UNKNOWN never qualifies. Advanced needs every engine diagnostic to PASS.
4. A user can lower the tier but never raise it above what tests allow.
5. Engine packs are installed from a folder (USB/SD/download) with hash verification, staging and
   atomic rename; an existing install is never overwritten.
6. Claim levels (claimed / reviewed / verified-on-real-device) stay separate; only real device
   reports can produce "verified".
