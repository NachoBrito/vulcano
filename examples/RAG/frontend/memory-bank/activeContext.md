# Active Context: RAG Frontend

## Current Focus
Initial project setup and implementation of real-time telemetry using Prometheus-style metrics.

## Recent Changes
- Updated telemetry module to poll from `http://localhost:9999/metrics` (Prometheus format).
- Implemented a custom Prometheus text parser in `src/telemetry.js`.
- Switched latency metrics to use P99 values.
- Updated chart titles in the UI to reflect P99 latency.
- Modified `doc-insert-chart` to show only P99 insert latency.
- Added a new indicator for "Insert Queue" size.
- Modified `vector-search-chart` title to "Queue Latency (P99)" and hid the count axis in all multi-axis charts.

## Next Steps
1. Add support for more metrics if needed.
2. Improve chart visualizations.
