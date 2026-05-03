# Contributing to clj-oa3

Thanks for your interest in contributing! This repo is a Clojure client library for the [OpenADR 3](https://www.openadr.org/) API, providing spec-driven HTTP access to VTN (Virtual Top Node) servers. It is built on the OpenADR 3 OpenAPI specs (versions 3.0.0, 3.0.1, 3.1.0) embedded under `resources/openadr3-specification/`, and exposes a two-layer raw/coerced data model with malli schemas, tick intervals, and ZonedDateTime time handling.

## How to contribute

### Discussions

Use [Discussions](https://github.com/grid-coordination/clj-oa3/discussions) for:

- Questions about how to use the library — clients, raw vs. coerced calls, entity coercion, schemas, time handling, MQTT topic discovery
- API and design judgment calls — "should clj-oa3 model X?" / "is this the right shape for Y?"
- OpenADR 3 spec interpretation that affects clj-oa3 — when the spec is ambiguous and you want to scope what the library should do
- Coordination with the upstream [openadr3-specification](https://github.com/grid-coordination/openadr3-specification) repo (the OpenAPI specs this client consumes)
- Cross-implementation parity questions with [python-oa3](https://github.com/grid-coordination/python-oa3) or the OpenADR Alliance VTN-RI
- Sharing what you're building on top of clj-oa3

Discussions are open-ended — a good place to think out loud or scope something before it becomes a concrete change. Aligned outcomes from a Discussion often turn into one or more Issues.

### Issues

Use [Issues](https://github.com/grid-coordination/clj-oa3/issues) for actionable changes:

- Bugs in client construction, request building, response parsing, or coercion against a real VTN
- Coercion or schema gaps surfaced by real API responses (a field the library doesn't handle, or a value that breaks the coerced shape)
- New endpoint coverage, new request parameters, or new spec versions when the upstream OpenAPI specs expose them
- MQTT notification handling bugs (camelCase / snake_case format issues, missing object types)
- Test failures or unexpected behavior with concrete repro steps
- Documentation errors, unclear explanations, or stale prose in `README.md` or namespace docstrings
- Discussion outcomes that have alignment and a clear scope

If you're not sure whether something is an Issue or a Discussion, start with a Discussion — we can convert it later.

### Pull requests

Pull requests are welcome.

- For small fixes (typos, broken links, single-test corrections, single-coercion bug fixes), open a PR directly.
- For substantive changes (new endpoint coverage, new spec version support, new schema fields, new coercion behavior, new namespaces), open a Discussion or Issue first so we can align on scope before you invest the effort.
- All changes pass `clojure -M:test` (Kaocha) and `clj-kondo --lint src test` cleanly.
- Match the existing tone and structure. The library composes spec-driven HTTP → raw response → coerced entities as roughly orthogonal layers; patches that fit cleanly into one layer without leaking concerns across them are the easiest to land.
- One commit per logical change is fine; we don't require squash or any particular branch naming.

## Development

```bash
clojure -M:test                 # run the Kaocha unit test suite (offline, sample data)
clojure -M:nrepl                # nREPL on the port written to .nrepl-port
clj-kondo --lint src test       # lint
```

The OpenAPI specs under `resources/openadr3-specification/` are vendored from the [openadr3-specification](https://github.com/grid-coordination/openadr3-specification) repo and serve as the single source of truth for routes, parameters, and request/response shapes — see [`resources/openadr3-specification/ORIGIN.md`](resources/openadr3-specification/ORIGIN.md) for provenance. When the upstream specs change, re-vendor the relevant files and re-run the test suite to confirm the wire format still matches. Integration testing against a live VTN happens in the sibling [clj-oa3-test](https://github.com/grid-coordination/clj-oa3-test) repo.

## Code of conduct

Be respectful and constructive. We're a small project and appreciate everyone who takes the time to file an issue or send a PR.

## Important notice

This library is provided on an "as-is" basis. Updates and maintenance, including responses to issues filed on GitHub, will take place on an "as time and resources permit" basis. Library output (raw API responses, coerced programs/events/vens/resources/reports/subscriptions) is best-effort against the OpenADR 3 specification as published by the [OpenADR Alliance](https://www.openadr.org/) and vendored in the [openadr3-specification](https://github.com/grid-coordination/openadr3-specification) repo. This library is not authoritative for compliance certification — independent verification against the source specification and a certified VTN is recommended for any consumer using this client in a production setting.
