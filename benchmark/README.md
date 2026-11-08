# Performance benchmark

## `scripts/bench-open.ps1`

Black-box open-latency benchmark. It does not depend on app internals.

```powershell
.\scripts\bench-open.cmd -Runs 5
.\scripts\bench-open.cmd -Runs 10 -Cold -CsvPath .\artifacts\bench-open.csv
```

What it measures:

- from tapping Home “继续阅读”
- until the reader top bar (`返回` / `目录`) is visible
- p50/p95/min/max
- optional cold app launch

Limitations:

- UI Automator dump adds overhead; one `uiautomator dump` on the test device takes about 2.2 s, so the script cannot resolve sub-second opens. Use screenshots or an instrumentation build for first-page timing.
- it needs a saved reading record
- it does not measure the first painted frame separately; use Perfetto/atrace for frame timing

## Baseline

Current large EPUB (12.5 MB, 4145 pages), OnePlus Ace 5:

| Version | Open (script, median) | First readable page |
|---|---|---|
| 0.7.2 | ~8.4 s | ~8.4 s |
| 0.8.0 phase 1 | ~2.5 s warm; ~5.0 s first run | ~2.2 s |
| 0.8.3 | 2.5 s (tool floor) | 0.21 s first page; 0.75 s far resume |

The 0.8.3 column separates the script number from the in-app first-page
measurement. The script number is dominated by the UI dump and should not be
read as a regression. Details: [M8b.1](../docs/quality/2026-11-08-m8b1-open.md).

## Next measurements

- add fixtures for 1 MB EPUB, 100 MB EPUB, TXT, HTML/MD, PDF
- add instrumentation trace sections for read/unzip/parse/text/layout
- record memory and frame timing
