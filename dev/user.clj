(ns user
  (:require [openadr3.api :as api]
            [openadr3.entities :as entities]))

(def vtn-url api/default-vtn-url)

(comment
  ;; -------------------------------------------------------------------------
  ;; Client setup (spec resolved from classpath automatically)
  ;; -------------------------------------------------------------------------
  (def spec (api/read-openapi-spec (api/spec-path)))
  (def ven (api/create-ven-client "ven_token" vtn-url))
  (def bl (api/create-bl-client "bl_token" vtn-url))

  ;; Use a specific spec version
  (def ven-301 (api/create-ven-client "ven_token" vtn-url {:spec-version "3.0.1"}))

  ;; List routes
  (sort (api/all-routes spec))

  ;; -------------------------------------------------------------------------
  ;; Raw API (returns HTTP responses with :status and :body)
  ;; -------------------------------------------------------------------------
  (api/get-programs bl)
  (api/get-events ven)
  (api/get-vens bl)

  ;; -------------------------------------------------------------------------
  ;; Coerced entities (namespaced keywords, Instants, tick intervals)
  ;; -------------------------------------------------------------------------

  ;; Programs
  (api/programs bl)
  ;; => [#:openadr{:id "abc123"
  ;;              :created #inst "2023-06-15T09:30:00Z"
  ;;              :modified #inst "2023-06-15T09:30:00Z"
  ;;              :object-type :openadr.object-type/program}
  ;;     #:openadr.program{:name "MyProgram"}]

  ;; Single program by ID
  (api/program bl "abc123")

  ;; Events with intervals and payloads
  (api/events ven)

  ;; VENs
  (api/vens bl)

  ;; Access raw data from any coerced entity
  (-> (first (api/programs bl)) meta :openadr/raw)

  ;; Convert timestamps to local time
  (entities/->zoned (:openadr/created (first (api/programs bl)))
                    (java.time.ZoneId/of "America/Los_Angeles"))

  ;; -------------------------------------------------------------------------
  ;; Manual coercion (when you already have raw data)
  ;; -------------------------------------------------------------------------
  (entities/->program {:id "test"
                       :createdDateTime "2023-06-15T09:30:00Z"
                       :modificationDateTime "2023-06-15T09:30:00Z"
                       :objectType "PROGRAM"
                       :programName "TestProgram"})

  ;; Generic coercion (dispatches on objectType)
  (entities/coerce {:objectType "PROGRAM"
                    :id "test"
                    :createdDateTime "2023-06-15T09:30:00Z"
                    :modificationDateTime "2023-06-15T09:30:00Z"
                    :programName "TestProgram"})

  ;; Payload multimethod
  (entities/coerce-payload {:type "PRICE" :values [42.5 43.1]})
  ;; => #:openadr.payload{:type :openadr.payload-type/price
  ;;                      :values [42.5M 43.1M]}
  )
