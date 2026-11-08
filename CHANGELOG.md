# Changelog

Version numbers follow the version 5.0 line used during development. Older notes only describe behavior that landed in the repository.

## 5.0.0 — 2026-04-20

First GitHub release. This version has the people library, suspect alerts, and unit tests that do not need outside services.

- People library: enroll from a photo, mark a suspect, merge duplicates, replace the representative face, delete
- Live alerts: a hit on `alert_embeddings` is pushed to the dashboard over WebSocket and kept on the page for a while
- A capture is written to MinIO before Milvus. Search, person match, and alert each have their own threshold
- Unit tests cover JWT, threshold fallback, and MinIO metadata parsing. `mvn test` does not call outside services
- README and technical note match the running system, including figures from the project report
- Photo search and people-library upload accept both Windows `.venv/Scripts/python.exe` and macOS / Linux `.venv/bin/python`
- `pika` is listed in the Python requirements because the capture process imports it
- `node_modules`, Maven `target/`, runtime logs, and editor settings are no longer tracked

## 4.x — 2026-04-18 to 2026-04-19

- 4.2 adjusted alerts
- 4.1 finished the people library and alerts, and adjusted the database layout
- 4.0 added alerts and the people library, reworked capture handling, and split MinIO and Milvus responsibilities

## 3.0 — 2026-04-17

- Adjusted the database layout

## 2.0 — 2026-04-06

- Tightened comparison during detection

## 1.0 — 2026-03

- Connected YOLOv8 capture, Facenet embeddings, RabbitMQ, Spring Boot, Milvus, and MinIO
- Console: overview, trajectory search, dataset, access control, and threshold settings
