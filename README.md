# clj-oa3

A Clojure client library for the [OpenADR 3](https://www.openadr.org/) API, providing spec-driven HTTP access to VTN (Virtual Top Node) servers.

## Features

- **Spec-driven HTTP client** built on [Martian](https://github.com/oliyh/martian) with the OpenAPI spec as the single source of truth
- **VEN and BL client types** with OAuth2 scope metadata
- **Full CRUD** for all OpenADR 3 resources: programs, events, VENs, resources, reports, subscriptions
- **MQTT topic discovery** for all notifier endpoints
- **Route introspection** and scope-based authorization checks

## Prerequisites

The OpenAPI spec is not bundled in this repo. It is symlinked from the sibling `specification` repository:

```
resources/openadr3-specification -> ../../specification
```

You must clone the [OpenADR 3 specification](https://github.com/OpenADRAlliance/specification) alongside this repo:

```bash
cd repo/
git clone https://github.com/OpenADRAlliance/specification.git
```

Expected layout:

```
repo/
  clj-oa3/                # this repo
  specification/           # OpenADR 3 OpenAPI specs
    3.0.0/
    3.0.1/
    3.1.0/
    3.1.1/
```

## Quick Start

```clojure
(require '[openadr3.api :as api])

;; Bootstrap from OpenAPI spec (unauthenticated)
(def spec (api/read-openapi-spec "resources/openadr3-specification/3.1.0/openadr3.yaml"))

;; Create authenticated clients
(def ven (api/create-ven-client "resources/openadr3-specification/3.1.0/openadr3.yaml"
                                 "my-ven-token"
                                 "http://localhost:8080/openadr3/3.1.0"))

(def bl (api/create-bl-client "resources/openadr3-specification/3.1.0/openadr3.yaml"
                               "my-bl-token"
                               "http://localhost:8080/openadr3/3.1.0"))

;; CRUD operations
(api/get-programs bl)
(api/create-program bl {:programName "MyProgram"})
(api/get-events ven)
(api/get-vens bl)
```

## Client Types

| Client | Type | Scopes |
|--------|------|--------|
| VEN | `:ven` | `read_all`, `read_targets`, `read_ven_objects`, `write_reports`, `write_subscriptions`, `write_vens` |
| BL | `:bl` | `read_all`, `read_bl`, `write_programs`, `write_events`, `write_subscriptions`, `write_vens` |

```clojure
(api/client-type ven)  ;=> :ven
(api/scopes ven)       ;=> #{"read_all" "read_targets" ...}
```

## API Reference

### Client Creation

| Function | Description |
|----------|-------------|
| `read-openapi-spec` | Bootstrap unauthenticated Martian client from spec file |
| `create-ven-client` | Create authenticated VEN client |
| `create-bl-client` | Create authenticated BL client |

### Introspection

| Function | Description |
|----------|-------------|
| `all-routes` | List all route-name keywords |
| `get-handler` | Get handler for a route-name |
| `endpoint-scopes` | Get required scopes for an endpoint |
| `authorized?` | Check if client scopes satisfy endpoint requirements |
| `get-unauthenticated-routes` | List routes requiring no auth |
| `client-type` | Get client type (`:ven` or `:bl`) |
| `scopes` | Get client's OAuth2 scopes |

### Programs

| Function | Description |
|----------|-------------|
| `get-programs` | Search all programs |
| `get-program-by-id` | Get program by ID |
| `search-programs` | Search with query params |
| `create-program` | Create a program |
| `update-program` | Update a program |
| `delete-program` | Delete a program |
| `find-program-by-name` | Find program by name |

### Events

| Function | Description |
|----------|-------------|
| `get-events` | Search all events |
| `get-event-by-id` | Get event by ID |
| `search-events` | Search with query params |
| `create-event` | Create an event |
| `update-event` | Update an event |
| `delete-event` | Delete an event |

### VENs

| Function | Description |
|----------|-------------|
| `get-vens` | Search all VENs |
| `get-ven-by-id` | Get VEN by ID |
| `create-ven` | Create a VEN |
| `update-ven` | Update a VEN |
| `delete-ven` | Delete a VEN |
| `find-ven-by-name` | Find VEN by name |

### Resources, Reports, Subscriptions

Full CRUD for each: `get-*`, `search-*`, `create-*`, `update-*`, `delete-*`.

### MQTT Topics

Topic discovery for programs, events, VENs, resources, reports, subscriptions. Functions follow the pattern `get-mqtt-topics-*`.

### Authentication

| Function | Description |
|----------|-------------|
| `get-auth-server` | Get auth server info |
| `get-token` | Fetch OAuth2 token via client credentials |

## Development

### Start nREPL

```bash
clojure -M:nrepl
# nREPL running on port 7889
```

## Related Repos

| Repo | Description |
|------|-------------|
| [clj-oa3-client](https://github.com/grid-coordination/clj-oa3-client) | Application layer: mDNS discovery, service management |
| [clj-oa3-test](https://github.com/grid-coordination/clj-oa3-test) | Integration tests against VTN-RI |

## License

Copyright (c) 2026. All rights reserved.
