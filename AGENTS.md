# Agent and CI budget policy

GitHub Actions minutes are a constrained resource on this account. Any agent or automation editing this repository must follow these rules unless the user explicitly asks otherwise.

1. **Do not restore APK builds on every push to `main`.** Heavy Android builds are opt-in: `workflow_dispatch`, a deliberate `v*` / `apk-*` tag, or a relevant pull request.
2. **Do not trigger a build just to check a documentation, naming, comment, README, or metadata-only change.**
3. **Complete a batch of related edits before requesting or triggering the APK build.** If the GitHub API creates one commit per edited file, finish all files first; build only the final state.
4. **One heavy validation per logical batch.** Do not create follow-up commits solely to retrigger a green build unless the previous run found a real defect.
5. Keep `concurrency.cancel-in-progress: true` so superseded runs are cancelled.
6. Prefer local/static reasoning and targeted checks during editing; reserve GitHub-hosted Android runners for the final batch validation.
7. Keep debug artifact retention at **7 days or less** unless the user requests a longer-lived release artifact.
8. Preserve support for `runner_labels_json` in `app-build-factory`; when the user's Kubuntu self-hosted runner is ready, switch Android builds to `["self-hosted","linux","x64","android"]` instead of redesigning the workflow.
9. Never weaken security, tests, signing, or release integrity merely to save Actions minutes. Reduce *redundant executions*, not meaningful validation.

Default working rule: **edit many → validate once → build once**.
