# clj-oa3

A Clojure client library for the [OpenADR 3](https://www.openadr.org/) API, providing spec-driven HTTP access to VTN (Virtual Top Node) servers.

## Features

- **Spec-driven HTTP client** built on [Martian](https://github.com/oliyh/martian) — the OpenAPI spec is the single source of truth
- **Two-layer data model** — raw API responses (camelCase JSON) and coerced Clojure entities (namespaced keywords, Instants, Durations, tick intervals)
- **VEN and BL client types** with OAuth2 scope metadata
- **Full CRUD** for all OpenADR 3 resources: programs, events, VENs, resources, reports, subscriptions
- **MQTT topic discovery** for all notifier endpoints
- **Extensible payload coercion** via `coerce-payload` multimethod (dispatches on `:type`)
- **Malli schemas** for both raw and coerced entity validation
- **Route introspection** and scope-based authorization checks

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    openadr3.api                          │
│                                                         │
│  Raw functions: get-programs, create-event, ...         │
│  → return {:status 200 :body {...camelCase...}}         │
│                                                         │
│  Coerced functions: programs, events, vens, ...         │
│  → return [#:openadr{:id "..." :created #inst ...}]    │
│       └── each entity carries :openadr/raw metadata     │
├─────────────────────────────────────────────────────────┤
│                  openadr3.entities                       │
│                                                         │
│  ->program, ->event, ->ven, ->resource, ->report,       │
│  ->subscription, ->interval, ->interval-period          │
│                                                         │
│  Multimethods: coerce (by objectType),                  │
│                coerce-payload (by payload type)          │
│                                                         │
│  Malli schemas: RawProgram, Program, RawEvent, Event... │
├─────────────────────────────────────────────────────────┤
│              Martian + Hato (HTTP)                       │
│              OpenADR 3 OpenAPI spec (YAML)               │
└─────────────────────────────────────────────────────────┘
```

## Prerequisites

The OpenAPI spec is symlinked from the sibling `specification` repository:

```
resources/openadr3-specification -> ../../specification
```

Clone the [OpenADR 3 specification](https://github.com/OpenADRAlliance/specification) alongside this repo:

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

;; Create authenticated clients
(def ven (api/create-ven-client "resources/openadr3-specification/3.1.0/openadr3.yaml"
                                 "my-ven-token"
                                 "http://localhost:8080/openadr3/3.1.0"))

(def bl (api/create-bl-client "resources/openadr3-specification/3.1.0/openadr3.yaml"
                               "my-bl-token"
                               "http://localhost:8080/openadr3/3.1.0"))
```

### Raw API (HTTP responses)

```clojure
(api/get-programs bl)
;; => {:status 200 :body [{:id "abc123" :programName "MyProgram" ...}]}

(api/create-program bl {:programName "MyProgram"})
(api/get-events ven)
(api/get-vens bl)
(api/success? (api/get-programs bl))  ;=> true
(api/body (api/get-programs bl))       ;=> [{:id "abc123" ...}]
```

### Coerced Entities (namespaced Clojure maps)

```clojure
(api/programs bl)
;; => [#:openadr{:id "abc123"
;;              :created #inst "2023-06-15T09:30:00Z"
;;              :modified #inst "2023-06-15T09:30:00Z"
;;              :object-type :openadr.object-type/program}
;;     #:openadr.program{:name "MyProgram"}]

(api/program bl "abc123")     ;; single entity
(api/events ven)
(api/vens bl)
(api/reports bl)
(api/subscriptions bl)

;; Access the original raw data from any coerced entity
(-> (first (api/programs bl)) meta :openadr/raw)
;; => {:id "abc123" :programName "MyProgram" :objectType "PROGRAM" ...}
```

### Entity Coercion Details

All timestamps become `java.time.Instant` (UTC — the OA3 spec mandates Zulu time). Durations become `java.time.Duration`. When an IntervalPeriod has both start and duration, a [tick](https://github.com/juxt/tick) interval is computed:

```clojure
;; IntervalPeriod with tick interval
(:openadr.event/interval-period event)
;; => #:openadr.interval-period{:start    #inst "2023-06-15T09:30:00Z"
;;                              :duration #object[Duration "PT1H"]
;;                              :period   {:tick/beginning #inst "..." :tick/end #inst "..."}}

;; Convert to local time when you know the timezone
(require '[openadr3.entities :as entities])
(entities/->zoned (:openadr/created program)
                  (java.time.ZoneId/of "America/Los_Angeles"))
```

### Payload Coercion (extensible)

ValuesMap payloads are coerced via the `coerce-payload` multimethod, dispatching on the `:type` string:

```clojure
;; Built-in: PRICE and USAGE convert values to BigDecimal
(entities/coerce-payload {:type "PRICE" :values [42.5 43.1]})
;; => #:openadr.payload{:type :openadr.payload-type/price
;;                      :values [42.5M 43.1M]}

;; Extend for custom payload types
(defmethod entities/coerce-payload "GRID_CARBON"
  [raw]
  (-> {:openadr.payload/type   :openadr.payload-type/grid-carbon
       :openadr.payload/values (mapv double (:values raw))}
      (with-meta {:openadr/raw raw})))
```

### Generic Coercion (by objectType)

```clojure
;; Dispatches on :objectType string
(entities/coerce {:objectType "PROGRAM" :id "test" :programName "Foo" ...})
;; => namespaced Program entity

(entities/coerce {:objectType "EVENT" :id "test" :programID "p1" ...})
;; => namespaced Event entity
```

## Client Types

| Client | Type | Scopes |
|--------|------|--------|
| VEN | `:ven` | `read_all`, `read_targets`, `read_ven_objects`, `write_reports`, `write_subscriptions`, `write_vens` |
| BL | `:bl` | `read_all`, `read_bl`, `write_programs`, `write_events`, `write_subscriptions`, `write_vens` |

```clojure
(api/client-type ven)  ;=> :ven
(api/scopes ven)       ;=> #{"read_all" "read_targets" ...}

;; Check if a client can call an endpoint
(api/authorized? (api/scopes ven) (api/endpoint-scopes ven :search-all-events))
```

## API Reference

### Client Creation

| Function | Description |
|----------|-------------|
| `read-openapi-spec` | Bootstrap Martian client from spec file |
| `create-ven-client` | Create authenticated VEN client |
| `create-bl-client` | Create authenticated BL client |

### Raw CRUD (returns `{:status :body}`)

| Resource | List | Get by ID | Search | Create | Update | Delete | Find by Name |
|----------|------|-----------|--------|--------|--------|--------|--------------|
| Programs | `get-programs` | `get-program-by-id` | `search-programs` | `create-program` | `update-program` | `delete-program` | `find-program-by-name` |
| Events | `get-events` | `get-event-by-id` | `search-events` | `create-event` | `update-event` | `delete-event` | — |
| VENs | `get-vens` | `get-ven-by-id` | — | `create-ven` | `update-ven` | `delete-ven` | `find-ven-by-name` |
| Resources | — | `get-resource-by-id` | `search-ven-resources` | `create-resource` | `update-resource` | `delete-resource` | — |
| Reports | `get-reports` | `get-report-by-id` | `search-reports` | `create-report` | `update-report` | `delete-report` | — |
| Subscriptions | `get-subscriptions` | `get-subscription-by-id` | `search-subscriptions` | `create-subscription` | `update-subscription` | `delete-subscription` | — |

### Coerced Entities (returns namespaced maps with `:openadr/raw` metadata)

| Function | Returns |
|----------|---------|
| `programs` / `program` | Program entities |
| `events` / `event` | Event entities (with intervals + payloads) |
| `vens` / `ven` | VEN entities |
| `reports` / `report` | Report entities (with resources + intervals) |
| `subscriptions` / `subscription` | Subscription entities |

### Response Helpers

| Function | Description |
|----------|-------------|
| `success?` | True if 2xx status |
| `body` | Extract `:body` from response |

### MQTT Topics

Topic discovery for all notifier endpoints. Functions follow the pattern `get-mqtt-topics-*`:

`get-mqtt-topics-programs`, `get-mqtt-topics-program`, `get-mqtt-topics-program-events`, `get-mqtt-topics-ven`, `get-mqtt-topics-ven-programs`, `get-mqtt-topics-ven-events`, `get-mqtt-topics-ven-resources`, `get-mqtt-topics-events`, `get-mqtt-topics-reports`, `get-mqtt-topics-subscriptions`, `get-mqtt-topics-vens`, `get-mqtt-topics-resources`

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

### Authentication

| Function | Description |
|----------|-------------|
| `get-auth-server` | Get auth server info |
| `get-token` | Fetch OAuth2 token via client credentials |

### Entity Coercion (openadr3.entities)

| Function | Description |
|----------|-------------|
| `->program`, `->event`, `->ven`, `->resource`, `->report`, `->subscription` | Coerce raw API map to namespaced entity |
| `->interval`, `->interval-period` | Coerce nested structures |
| `coerce` | Generic multimethod (dispatches on `:objectType`) |
| `coerce-payload` | Extensible multimethod (dispatches on payload `:type`) |
| `->zoned` | Convert Instant to ZonedDateTime |
| `validate-raw-*` | Malli validation for raw API maps |

### Malli Schemas

Both raw (`RawProgram`, `RawEvent`, ...) and coerced (`Program`, `Event`, ...) schemas are available in `openadr3.entities` for boundary validation and testing.

## Development

### Start nREPL

```bash
clojure -M:nrepl
# nREPL running on port 7889
```

### Dev REPL

```clojure
(def ven (api/create-ven-client specfile "ven_token" vtn-url))
(def bl  (api/create-bl-client specfile "bl_token" vtn-url))

(api/programs bl)                          ;; coerced entities
(api/get-programs bl)                      ;; raw HTTP response
(-> (first (api/programs bl)) meta :openadr/raw)  ;; round-trip
(sort (api/all-routes ven))                ;; 45 routes
```

## Dependencies

| Library | Purpose |
|---------|---------|
| [Martian](https://github.com/oliyh/martian) | OpenAPI spec-driven HTTP client |
| [Hato](https://github.com/gnarroway/hato) | HTTP client (Java 11+ HttpClient) |
| [Malli](https://github.com/metosin/malli) | Schema validation |
| [tick](https://github.com/juxt/tick) | Time intervals (Allen's interval algebra) |
| [medley](https://github.com/weavejester/medley) | Utility functions |
| [camel-snake-kebab](https://github.com/clj-commons/camel-snake-kebab) | Case conversion |

## Related Repos

| Repo | Description |
|------|-------------|
| [clj-oa3-client](https://github.com/grid-coordination/clj-oa3-client) | Component lifecycle wrapper for constructing and managing OA3 clients |
| [clj-oa3-test](https://github.com/grid-coordination/clj-oa3-test) | Integration tests against VTN-RI |

## License

Copyright (c) 2026. All rights reserved.
