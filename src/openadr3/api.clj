(ns openadr3.api
  "OpenADR 3 API client library.
  Spec-driven HTTP client built on Martian with the OpenAPI spec as the single source of truth.

  Functions return raw HTTP responses by default. Use the coerced helpers
  (programs, events, vens, etc.) to get namespaced Clojure entities."
  (:require [martian.core :as martian]
            [martian.hato :as mhhttp]
            [hato.client :as hc]
            [clojure.set :as set]
            [openadr3.entities :as entities]
            [clojure.java.io :as io]))

;; -----------------------------------------------------------------------------
;; Spec version resolution
;; -----------------------------------------------------------------------------

(def spec-versions
  "Map of OA3 spec version string to classpath resource path."
  {"3.0.0" "openadr3-specification/3.0.0/openadr3.yaml"
   "3.0.1" "openadr3-specification/3.0.1/openadr3.yaml"
   "3.1.0" "openadr3-specification/3.1.0/openadr3.yaml"})

(def default-spec-version "3.1.0")

(defn spec-path
  "Resolve a spec version string to a classpath resource name.
  Returns the resource name string (not a filesystem path) so that
  martian.file/local-resource can find it via clojure.java.io/resource.
  Throws if the version is unknown or the resource is not found."
  ([]
   (spec-path default-spec-version))
  ([version]
   (let [resource-path (or (get spec-versions version)
                           (throw (ex-info (str "Unknown OpenADR spec version: " version
                                                ". Known versions: " (keys spec-versions))
                                           {:version version
                                            :known (keys spec-versions)})))]
     (when-not (io/resource resource-path)
       (throw (ex-info (str "OpenAPI spec not found on classpath: " resource-path)
                       {:resource-path resource-path :version version})))
     resource-path)))

;; -----------------------------------------------------------------------------
;; Interceptors
;; -----------------------------------------------------------------------------

(defn create-authentication-header
  "Martian interceptor that adds a Bearer token to requests."
  [token]
  {:name ::add-oa3-authentication-header
   :enter (fn [ctx]
            (assoc-in ctx
                      [:request :headers "Authorization"]
                      (str "Bearer " token)))})

(defn turn-off-exception-throwing
  "Martian interceptor that prevents Hato from throwing on non-2xx responses."
  []
  {:name ::turn-off-exception-throwing
   :enter (fn [ctx]
            (assoc-in ctx
                      [:request :throw-exceptions?] false))})

(defn build-shared-http-client
  "Build a shared Java HttpClient with connection timeout.
  Reuse across requests to avoid per-request client creation."
  [{:keys [connect-timeout-ms] :or {connect-timeout-ms 5000}}]
  (hc/build-http-client {:connect-timeout connect-timeout-ms
                         :redirect-policy :normal
                         :version :http-1.1}))

(defn inject-http-client
  "Martian interceptor that injects a shared HttpClient into every request."
  [http-client]
  {:name ::inject-http-client
   :enter (fn [ctx]
            (assoc-in ctx [:request :http-client] http-client))})

;; -----------------------------------------------------------------------------
;; Client creation
;; -----------------------------------------------------------------------------

(def default-vtn-url "http://localhost:8080/openadr3/3.1.0")

(defn read-openapi-spec
  "Bootstrap a Martian client from an OpenAPI spec file.
  Returns a Martian instance with optional interceptors and API root URL.
  When http-client is provided, prepends an interceptor to inject it into
  every request, avoiding per-request HttpClient creation."
  ([filename]
   (read-openapi-spec filename [] default-vtn-url nil))
  ([filename interceptors]
   (read-openapi-spec filename interceptors default-vtn-url nil))
  ([filename interceptors url]
   (read-openapi-spec filename interceptors url nil))
  ([filename interceptors url http-client]
   (let [all-interceptors (cond-> interceptors
                            http-client (conj (inject-http-client http-client)))]
     (assoc (mhhttp/bootstrap-openapi
             filename
             {:use-defaults true
              :interceptors (concat
                             all-interceptors
                             mhhttp/default-interceptors)})
            :api-root url))))

(defn create-ven-client
  "Create an authenticated VEN (Virtual End Node) client.
  Uses the embedded spec (default version 3.1.0). Pass opts map to override
  :spec-version or provide :http-client.

  Examples:
    (create-ven-client token url)
    (create-ven-client token url {:spec-version \"3.0.1\"})
    (create-ven-client token url {:http-client hc})"
  ([auth-token url]
   (create-ven-client auth-token url {}))
  ([auth-token url opts]
   (let [specfile    (spec-path (:spec-version opts default-spec-version))
         http-client (:http-client opts)]
     (with-meta
       (read-openapi-spec specfile
                          [(create-authentication-header auth-token)
                           (turn-off-exception-throwing)]
                          url
                          http-client)
       {:openadr/client-type :ven
        :openadr/scopes #{"read_all" "read_targets" "read_ven_objects" "write_reports" "write_subscriptions" "write_vens"}}))))

(defn create-bl-client
  "Create an authenticated BL (Business Logic) client.
  Uses the embedded spec (default version 3.1.0). Pass opts map to override
  :spec-version or provide :http-client.

  Examples:
    (create-bl-client token url)
    (create-bl-client token url {:spec-version \"3.0.1\"})
    (create-bl-client token url {:http-client hc})"
  ([auth-token url]
   (create-bl-client auth-token url {}))
  ([auth-token url opts]
   (let [specfile    (spec-path (:spec-version opts default-spec-version))
         http-client (:http-client opts)]
     (with-meta
       (read-openapi-spec specfile
                          [(create-authentication-header auth-token)
                           (turn-off-exception-throwing)]
                          url
                          http-client)
       {:openadr/client-type :bl
        :openadr/scopes #{"read_all" "read_bl" "write_programs" "write_events" "write_subscriptions" "write_vens"}}))))

(defn client-type
  "Returns OpenADR3 client-type keyword, :ven or :bl"
  [client]
  (-> client meta :openadr/client-type))

(defn scopes
  "Returns set of OpenADR client scopes."
  [client]
  (-> client meta :openadr/scopes))

;; -----------------------------------------------------------------------------
;; Route introspection
;; -----------------------------------------------------------------------------

(defn all-routes
  "Returns vector of all route-name keywords."
  [client]
  (->> client :handlers (mapv :route-name)))

(defn get-handler
  "Returns the handler for the specified route-name keyword."
  [martian route-kw]
  (some (fn [handler]
          (when (= route-kw (:route-name handler))
            handler))
        (:handlers martian)))

(defn endpoint-scopes
  "Returns set of OAuth2 scopes required for the given endpoint."
  [client endpoint]
  (let [{:keys [openapi-definition]} (get-handler client endpoint)
        security (-> openapi-definition :security)]
    (->> security
         (some #(when (contains? % :oAuth2ClientCredentials) %))
         :oAuth2ClientCredentials
         set)))

(defn authorized?
  "Returns truthy if the client's scopes intersect with the endpoint's required scopes."
  [client-scopes endpoint-scopes]
  (not-empty (set/intersection client-scopes endpoint-scopes)))

(defn get-unauthenticated-routes
  "Returns vector of route-names that require no authentication."
  [martian]
  (into []
        (comp
         (filter #(empty? (-> % :openapi-definition :security)))
         (map :route-name))
        (-> martian :handlers)))

;; -----------------------------------------------------------------------------
;; Utilities
;; -----------------------------------------------------------------------------

(defn hash-map-by
  "Like group-by, but the mapping must be unique. Throws on duplicate keys."
  [f coll]
  (persistent!
   (reduce (fn [ret x]
             (let [k (f x)]
               (if (get ret k)
                 (throw (ex-info "Duplicate key" {:key k :value x}))
                 (assoc! ret k x))))
           (transient (hash-map))
           coll)))

;; -----------------------------------------------------------------------------
;; Authentication endpoints
;; -----------------------------------------------------------------------------

(defn get-auth-server
  "GET auth server info."
  [openapi-client]
  (martian/response-for openapi-client :get-auth-server-info))

(defn get-token
  "Fetch an OAuth2 token using client credentials."
  [openapi-client client-id client-secret]
  (martian/response-for openapi-client
                        :fetch-token
                        {:grant-type "client_credentials"
                         :client-id client-id
                         :client-secret client-secret}))

;; -----------------------------------------------------------------------------
;; Notifier endpoints
;; -----------------------------------------------------------------------------

(defn get-notifiers
  "List all notifiers."
  [openapi-client]
  (martian/response-for openapi-client :list-all-notifiers))

;; -----------------------------------------------------------------------------
;; MQTT Notifier Topics endpoints
;; -----------------------------------------------------------------------------

(defn get-mqtt-topics-programs [openapi-client]
  (martian/response-for openapi-client :list-all-mqtt-notifier-topics-programs))

(defn get-mqtt-topics-program
  "Get MQTT topic names for a specific program."
  [openapi-client program-id]
  (martian/response-for openapi-client
                        :list-all-mqtt-notifier-topics-program
                        {:program-id program-id}))

(defn get-mqtt-topics-program-events
  "Get MQTT topic names for events on a specific program."
  [openapi-client program-id]
  (martian/response-for openapi-client
                        :list-all-mqtt-notifier-topics-program-events
                        {:program-id program-id}))

(defn get-mqtt-topics-ven
  "Get MQTT topic names for a specific VEN."
  [openapi-client ven-id]
  (martian/response-for openapi-client
                        :list-all-mqtt-notifier-topics-ven
                        {:ven-id ven-id}))

(defn get-mqtt-topics-ven-programs
  "Get MQTT topic names for programs targeted at a VEN."
  [openapi-client ven-id]
  (martian/response-for openapi-client
                        :list-all-mqtt-notifier-topics-ven-programs
                        {:ven-id ven-id}))

(defn get-mqtt-topics-ven-events
  "Get MQTT topic names for events targeted at a VEN."
  [openapi-client ven-id]
  (martian/response-for openapi-client
                        :list-all-mqtt-notifier-topics-ven-events
                        {:ven-id ven-id}))

(defn get-mqtt-topics-ven-resources
  "Get MQTT topic names for resources of a VEN."
  [openapi-client ven-id]
  (martian/response-for openapi-client
                        :list-all-mqtt-notifier-topics-ven-resources
                        {:ven-id ven-id}))

(defn get-mqtt-topics-events [openapi-client]
  (martian/response-for openapi-client :list-all-mqtt-notifier-topics-events))

(defn get-mqtt-topics-reports [openapi-client]
  (martian/response-for openapi-client :list-all-mqtt-notifier-topics-reports))

(defn get-mqtt-topics-subscriptions [openapi-client]
  (martian/response-for openapi-client :list-all-mqtt-notifier-topics-subscriptions))

(defn get-mqtt-topics-vens [openapi-client]
  (martian/response-for openapi-client :list-all-mqtt-notifier-topics-vens))

(defn get-mqtt-topics-resources [openapi-client]
  (martian/response-for openapi-client :list-all-mqtt-notifier-topics-resources))

;; -----------------------------------------------------------------------------
;; Programs endpoints
;; -----------------------------------------------------------------------------

(defn get-programs
  "Search all programs."
  [openapi-client]
  (martian/response-for openapi-client :search-all-programs))

(defn get-program-by-id
  "Get a program by its ID."
  [openapi-client program-id]
  (martian/response-for openapi-client :search-program-by-program-id {:programID program-id}))

(defn search-programs
  "Search programs with query parameters (targets, skip, limit)."
  [openapi-client query-params]
  (martian/response-for openapi-client :search-all-programs query-params))

(defn create-program
  "Create a new program."
  [openapi-client program-request-body]
  (martian/response-for openapi-client :create-program program-request-body))

(defn update-program
  "Update an existing program."
  [openapi-client program-id program-request-body]
  (martian/response-for openapi-client
                        :update-program
                        (assoc program-request-body :programID program-id)))

(defn delete-program
  "Delete a program by ID."
  [openapi-client program-id]
  (martian/response-for openapi-client :delete-program {:program-id program-id}))

(defn find-program-by-name
  "Find a program by name. Returns the first match, or nil if not found."
  [client name]
  (some #(when (= name (:programName %)) %) (-> client get-programs :body)))

;; -----------------------------------------------------------------------------
;; Events endpoints
;; -----------------------------------------------------------------------------

(defn get-events
  "Search all events."
  [openapi-client]
  (martian/response-for openapi-client :search-all-events))

(defn search-events
  "Search events with query parameters."
  [openapi-client query-params]
  (martian/response-for openapi-client :search-all-events query-params))

(defn get-event-by-id
  "Get an event by its ID."
  [openapi-client event-id]
  (martian/response-for openapi-client :search-events-by-id {:eventID event-id}))

(defn create-event
  "Create a new event."
  [openapi-client event-request-body]
  (martian/response-for openapi-client :create-event event-request-body))

(defn update-event
  "Update an existing event."
  [openapi-client event-id event-request-body]
  (martian/response-for openapi-client
                        :update-event
                        (assoc event-request-body :eventID event-id)))

(defn delete-event
  "Delete an event by ID."
  [openapi-client event-id]
  (martian/response-for openapi-client :delete-event {:eventID event-id}))

;; -----------------------------------------------------------------------------
;; VENs endpoints
;; -----------------------------------------------------------------------------

(defn get-vens
  "Search all VENs."
  [openapi-client]
  (martian/response-for openapi-client :search-vens))

(defn search-vens
  "Search VENs with query parameters (targetType, targetValues, skip, limit, venName)."
  [openapi-client query-params]
  (martian/response-for openapi-client :search-vens query-params))

(defn get-ven-by-id
  "Get a VEN by its ID."
  [openapi-client ven-id]
  (martian/response-for openapi-client :search-ven-by-id {:ven-id ven-id}))

(defn create-ven
  "Create a new VEN."
  [openapi-client ven-request-body]
  (martian/response-for openapi-client :create-ven ven-request-body))

(defn update-ven
  "Update an existing VEN."
  [openapi-client ven-id ven-request-body]
  (martian/response-for openapi-client
                        :update-ven
                        (assoc ven-request-body :venID ven-id)))

(defn delete-ven
  "Delete a VEN by ID."
  [openapi-client ven-id]
  (martian/response-for openapi-client :delete-ven {:ven-id ven-id}))

(defn find-ven-by-name
  "Find a VEN by name. Returns the first match, or nil if not found."
  [client name]
  (some #(when (= name (:venName %)) %) (-> client get-vens :body)))

;; -----------------------------------------------------------------------------
;; Resource endpoints
;; -----------------------------------------------------------------------------

(defn search-ven-resources
  "Search VEN resources with query parameters."
  [openapi-client query-params]
  (martian/response-for openapi-client :search-ven-resources query-params))

(defn get-resource-by-id
  "Get a resource by its ID."
  [openapi-client resource-id]
  (martian/response-for openapi-client :search-ven-resource-by-id {:resourceID resource-id}))

(defn create-resource
  "Create a resource for a VEN. Body should include :venID, :resourceName,
  and :objectType (VEN_RESOURCE_REQUEST or BL_RESOURCE_REQUEST).
  For BL requests, also include :clientID."
  [openapi-client resource-body]
  (martian/response-for openapi-client :create-resource resource-body))

(defn update-resource
  "Update an existing resource."
  [openapi-client resource-id resource-request-body]
  (martian/response-for openapi-client
                        :update-ven-resource
                        (assoc resource-request-body :resourceID resource-id)))

(defn delete-resource
  "Delete a resource by ID."
  [openapi-client resource-id]
  (martian/response-for openapi-client :delete-ven-resource {:resourceID resource-id}))

;; -----------------------------------------------------------------------------
;; Report endpoints
;; -----------------------------------------------------------------------------

(defn get-reports
  "Search all reports."
  [openapi-client]
  (martian/response-for openapi-client :search-all-reports))

(defn search-reports
  "Search reports with query parameters."
  [openapi-client query-params]
  (martian/response-for openapi-client :search-all-reports query-params))

(defn get-report-by-id
  "Get a report by its ID."
  [openapi-client report-id]
  (martian/response-for openapi-client :search-reports-by-report-id {:reportID report-id}))

(defn create-report
  "Create a new report."
  [openapi-client report-request-body]
  (martian/response-for openapi-client :create-report report-request-body))

(defn update-report
  "Update an existing report."
  [openapi-client report-id report-request-body]
  (martian/response-for openapi-client
                        :update-report
                        (assoc report-request-body :reportID report-id)))

(defn delete-report
  "Delete a report by ID."
  [openapi-client report-id]
  (martian/response-for openapi-client :delete-report {:reportID report-id}))

;; -----------------------------------------------------------------------------
;; Subscription endpoints
;; -----------------------------------------------------------------------------

(defn get-subscriptions
  "Search all subscriptions."
  [openapi-client]
  (martian/response-for openapi-client :search-subscriptions))

(defn search-subscriptions
  "Search subscriptions with query parameters."
  [openapi-client query-params]
  (martian/response-for openapi-client :search-subscriptions query-params))

(defn get-subscription-by-id
  "Get a subscription by its ID."
  [openapi-client subscription-id]
  (martian/response-for openapi-client :search-subscription-by-id {:subscriptionID subscription-id}))

(defn create-subscription
  "Create a new subscription."
  [openapi-client subscription-request-body]
  (martian/response-for openapi-client :create-subscription subscription-request-body))

(defn update-subscription
  "Update an existing subscription."
  [openapi-client subscription-id subscription-request-body]
  (martian/response-for openapi-client
                        :update-subscription
                        (assoc subscription-request-body :subscriptionID subscription-id)))

(defn delete-subscription
  "Delete a subscription by ID."
  [openapi-client subscription-id]
  (martian/response-for openapi-client :delete-subscription {:subscriptionID subscription-id}))

;; -----------------------------------------------------------------------------
;; Response helpers
;; -----------------------------------------------------------------------------

(defn success?
  "True if the HTTP response has a 2xx status code."
  [response]
  (<= 200 (:status response) 299))

(defn body
  "Extract the :body from an HTTP response."
  [response]
  (:body response))

;; -----------------------------------------------------------------------------
;; Coerced entity helpers
;;
;; These return coerced namespaced entities (via openadr3.entities).
;; List endpoints return vectors; by-id endpoints return single entities.
;; All coerced entities carry :openadr/raw metadata with the original data.
;; -----------------------------------------------------------------------------

(defn programs
  "Fetch and coerce all programs. Returns a vector of Program entities."
  [client]
  (mapv entities/->program (body (get-programs client))))

(defn program
  "Fetch and coerce a program by ID. Returns a Program entity."
  [client program-id]
  (entities/->program (body (get-program-by-id client program-id))))

(defn events
  "Fetch and coerce all events. Returns a vector of Event entities."
  [client]
  (mapv entities/->event (body (get-events client))))

(defn event
  "Fetch and coerce an event by ID. Returns an Event entity."
  [client event-id]
  (entities/->event (body (get-event-by-id client event-id))))

(defn vens
  "Fetch and coerce all VENs. Returns a vector of Ven entities."
  [client]
  (mapv entities/->ven (body (get-vens client))))

(defn ven
  "Fetch and coerce a VEN by ID. Returns a Ven entity."
  [client ven-id]
  (entities/->ven (body (get-ven-by-id client ven-id))))

(defn reports
  "Fetch and coerce all reports. Returns a vector of Report entities."
  [client]
  (mapv entities/->report (body (get-reports client))))

(defn report
  "Fetch and coerce a report by ID. Returns a Report entity."
  [client report-id]
  (entities/->report (body (get-report-by-id client report-id))))

(defn subscriptions
  "Fetch and coerce all subscriptions. Returns a vector of Subscription entities."
  [client]
  (mapv entities/->subscription (body (get-subscriptions client))))

(defn subscription
  "Fetch and coerce a subscription by ID. Returns a Subscription entity."
  [client subscription-id]
  (entities/->subscription (body (get-subscription-by-id client subscription-id))))
