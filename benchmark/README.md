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

- UI Automator dump adds overhead; treat absolute values as conservative upper bounds
- it needs a saved reading record
- it does not measure the first painted frame separately; use Perfetto/atrace for frame timing

## Baseline

Current large EPUB (12.5 MB, 4145 pages), OnePlus Ace 5:

| Version | Open (script, median) |
|---|---|
| 0.7.2 | ~8.4 s |
| 0.8.0 phase 1 | ~2.5 s warm; ~5.0 s first run |

The first run includes a cold parse; subsequent runs use the in-memory
`BookDocument` cache. A real first-content measurement is being added in
phase 2.

## Next measurements

- add fixtures for 1 MB EPUB, 100 MB EPUB, TXT, HTML/MD, PDF
- add instrumentation trace sections for read/unzip/parse/text/layout
- record memory and frame timing
