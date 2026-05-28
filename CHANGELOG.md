# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). While the library is in early development (0.x), breaking changes may appear between minor versions when needed to fix correctness issues or align with the OpenADR 3.1.0 specification.

## [0.4.0] — 2026-05-28

### Added

- **`entities/->resolved-intervals`** ([OA3L-fq2](https://github.com/grid-coordination/clj-oa3/issues)). Applies the OpenADR 3.1.0 `intervalPeriod` inheritance rules and returns a new vector of intervals with the resolved values. Per the spec, `event.intervalPeriod` *"sets default start time and duration of intervals"* and a per-interval `intervalPeriod` *"may set temporal aspects of interval or override event.intervalPeriod"* — but `:openadr.event/interval-period` and `:openadr.interval/interval-period` are each coerced as raw passthrough fields, so without this helper every downstream consumer had to reimplement the inheritance and risked getting it wrong. The gap was first observed in the [`openadr3-ven-hass`](https://github.com/grid-coordination/openadr3-ven-hass) integration, where missing per-interval `intervalPeriod` was silently filled with a hardcoded 60-minute default instead of falling back to event-level. Resolution rules: per-interval `duration` falls back to `event.interval-period.duration`; per-interval `start` falls back to `event.interval-period.start` advanced by the sum of all prior intervals' resolved durations; `randomize-start` is per-interval only (no event-level fallback); `:tick/beginning` and `:tick/end` are recomputed when both resolved fields are present. Original `:openadr.event/intervals` is never mutated. Parallel to python-oa3 0.5.0's `Event.resolved_intervals()`.

## [0.3.0] — 2026-05-03

### Changed

- **Breaking: datetime fields are now `java.time.ZonedDateTime`** zoned to the offset present on the wire, instead of `java.time.Instant` (UTC). Affects `:openadr/created`, `:openadr/modified`, `:openadr.interval-period/start`, `:tick/beginning`, and `:tick/end` on every coerced entity. Spec-compliant VTNs that publish non-`Z` RFC 3339 offsets (e.g. `-07:00`, `+09:00`) no longer crash the parser — the old `Instant/parse` path was Z-only and would throw on any wire value with a numeric offset. Brings cross-implementation parity with [`python-oa3`](https://github.com/grid-coordination/python-oa3)'s Pendulum-based behaviour. Callers that want an `Instant` call `.toInstant` on the value. The VTN-RI space-separated workaround is preserved. Closes [grid-coordination/clj-oa3#1](https://github.com/grid-coordination/clj-oa3/issues/1).

### Added

- README "Time and timezones" section pinning the wire-offset preservation contract.
- `CONTRIBUTING.md` workflow doc (Discussions vs. Issues, dev commands).

## [0.2.4] — 2026-04-27

### Added

- **Build provenance embedded in the JAR.** Each release now writes `META-INF/<group>/<artifact>/build-provenance.{edn,json}` so a downstream consumer can answer "exactly which build of this lib is running?" via `(build-provenance.read/read 'energy.grid-coordination/clj-oa3)` or the `bp inspect` CLI. Adopts `com.dcj/build-provenance 0.2.0`.
- README badges for the `md-docs` and `build-provenance` conventions; bump to `com.dcj/codox-md 0.2.0`.

## [0.2.3] — 2026-04-16

### Changed

- **Breaking:** `:tick/beginning` and `:tick/end` are now assoc'd directly on the IntervalPeriod entity (when both start and duration are present), replacing the nested `:openadr.interval-period/period` sub-map. The IntervalPeriod entity is itself usable as a [tick](https://github.com/juxt/tick) interval without unwrapping.

## [0.2.2] — 2026-04-13

### Fixed

- Non-JSON error responses from the VTN no longer break the Martian interceptor chain — a `text/plain` 4xx body previously threw during JSON deserialization, masking the actual HTTP error and surfacing a confusing stack trace instead.

## [0.2.1] — 2026-04-12

### Changed

- Removed unused logging dependencies (`tools.logging`, `log4j`, `slf4j`) from the public deps. The library itself does not log; consumers wire their own backend.

## [0.2.0] — 2026-04-12

### Added

- `:user-agent` option on `create-ven-client` and `create-bl-client` for VTN server-side log identification.
- Codox-generated API docs embedded in the JAR via `com.dcj/codox-md`. Pinned `jackson-core` / `jackson-databind` 2.18.3 in the `:build` alias to fix `streamReadConstraints` missing on the 2.12.4 that deps-deploy transitively brought.

## [0.1.0] — 2026-04-04

Initial published release. Two-layer raw/coerced data model with namespaced keywords, `java.time.Instant` and `java.time.Duration` types, Martian-based Hato HTTP client with bearer auth, payload-type dispatch for PRICE / USAGE → `BigDecimal`, OpenAPI spec validation, MQTT notification coercion for both spec-compliant camelCase and VTN-RI snake_case shapes, Malli schemas for both raw and coerced layers.

[0.4.0]: https://github.com/grid-coordination/clj-oa3/releases/tag/v0.4.0
[0.3.0]: https://github.com/grid-coordination/clj-oa3/releases/tag/v0.3.0
