(ns user
  (:require [openadr3.api :as api]))

;; OpenAPI spec file (symlinked from ../specification)
(def specfile "resources/openadr3-specification/3.1.0/openadr3.yaml")
(def vtn-url api/default-vtn-url)

(comment
  ;; Create clients
  (def spec (api/read-openapi-spec specfile))
  (def ven (api/create-ven-client specfile "ven_token" vtn-url))
  (def bl (api/create-bl-client specfile "bl_token" vtn-url))

  ;; List routes
  (sort (api/all-routes spec))

  ;; Programs
  (api/get-programs bl)
  (api/find-program-by-name bl "MyProgram")

  ;; VENs
  (api/get-vens bl)

  ;; Events
  (api/get-events ven))
