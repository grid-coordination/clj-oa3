(ns openadr3.entities
  "Two-layer data model for OpenADR 3 entities.

  Raw layer: camelCase keys, string values — direct from the API JSON.
  Coerced layer: namespaced keywords, ZonedDateTimes, Durations, tick intervals.

  Every coerced entity preserves the original raw data as :openadr/raw metadata.

  Coercion of ValuesMap payloads is extensible via the `coerce-payload` multimethod,
  dispatching on the payload :type string.

  Schemas live in dedicated namespaces:
    openadr3.entities.schema       — coerced entity schemas (the public contract)
    openadr3.entities.schema.raw   — raw API schemas (boundary validation)"
  (:require [malli.core :as m]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [openadr3.entities.schema.raw :as raw])
  (:import [java.time Duration OffsetDateTime ZoneId ZonedDateTime]))

;; ---------------------------------------------------------------------------
;; Parsing helpers
;; ---------------------------------------------------------------------------

(defn- parse-zoned-datetime
  "Parse an RFC 3339 datetime string to a ZonedDateTime.

  Accepts arbitrary offsets per RFC 3339: `Z`, `+00:00`, `-07:00`, etc.
  The returned ZonedDateTime is zoned to the wire offset (no IANA zone
  name is available from the wire), matching python-oa3's Pendulum
  behaviour.

  Also handles the VTN-RI's non-standard space-separated format
  (`2026-03-08 19:22:06`, no offset) by inserting `T` and assuming UTC."
  ^ZonedDateTime [^String s]
  (let [normalized (if (.contains s "T")
                     s
                     (str (.replace s " " "T") "Z"))]
    (.toZonedDateTime (OffsetDateTime/parse normalized))))

(defn- parse-duration
  "Parse an ISO 8601 duration string to a java.time.Duration."
  ^Duration [^String s]
  (Duration/parse s))

(defn- parse-zoned-datetime-maybe
  "Parse an RFC 3339 datetime string to ZonedDateTime, or nil if nil/blank."
  [s]
  (when (and s (not (.isBlank ^String s)))
    (parse-zoned-datetime s)))

(defn- parse-duration-maybe
  "Parse an ISO 8601 duration string to Duration, or nil if nil/blank."
  [s]
  (when (and s (not (.isBlank ^String s)))
    (parse-duration s)))

;; ---------------------------------------------------------------------------
;; ValuesMap coercion — multimethod dispatch on :type
;; ---------------------------------------------------------------------------

(defmulti coerce-payload
  "Coerce a raw ValuesMap payload based on its :type string.
  Dispatches on the :type value (e.g. \"PRICE\", \"USAGE\").
  Returns a coerced map with :openadr.payload/type and :openadr.payload/values.

  Extend for custom payload types:
    (defmethod coerce-payload \"MY_TYPE\" [raw]
      (-> {:openadr.payload/type :openadr.payload-type/my-type
           :openadr.payload/values (mapv my-coercion (:values raw))}
          (with-meta {:openadr/raw raw})))"
  (fn [raw] (:type raw)))

(defmethod coerce-payload "PRICE"
  [raw]
  (-> {:openadr.payload/type   :openadr.payload-type/price
       :openadr.payload/values (mapv bigdec (:values raw))}
      (with-meta {:openadr/raw raw})))

(defmethod coerce-payload "USAGE"
  [raw]
  (-> {:openadr.payload/type   :openadr.payload-type/usage
       :openadr.payload/values (mapv bigdec (:values raw))}
      (with-meta {:openadr/raw raw})))

(defmethod coerce-payload :default
  [raw]
  (-> {:openadr.payload/type   (keyword "openadr.payload-type" (.toLowerCase ^String (:type raw)))
       :openadr.payload/values (:values raw)}
      (with-meta {:openadr/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: IntervalPeriod → tick interval + duration
;; ---------------------------------------------------------------------------

(defn ->interval-period
  "Coerce a raw intervalPeriod map.

  Returns a map with:
    :openadr.interval-period/start    — ZonedDateTime (or nil)
    :openadr.interval-period/duration — Duration (or nil)
    :openadr.interval-period/randomize-start — Duration (or nil)
    :tick/beginning — ZonedDateTime (when both start and duration present)
    :tick/end       — ZonedDateTime (when both start and duration present)

  The entity is directly usable as a tick interval when both start and
  duration are present.  Attaches :openadr/raw metadata."
  [raw]
  (when raw
    (let [start    (parse-zoned-datetime-maybe (:start raw))
          duration (parse-duration-maybe (:duration raw))
          end      (when (and start duration)
                     (.plus ^ZonedDateTime start ^Duration duration))]
      (-> (cond-> {:openadr.interval-period/start    start
                   :openadr.interval-period/duration duration}
            (:randomizeStart raw)
            (assoc :openadr.interval-period/randomize-start
                   (parse-duration-maybe (:randomizeStart raw)))
            end
            (assoc :tick/beginning start :tick/end end))
          (with-meta {:openadr/raw raw})))))

;; ---------------------------------------------------------------------------
;; Coercion: Interval (event/report interval with payloads)
;; ---------------------------------------------------------------------------

(defn ->interval
  "Coerce a raw interval map (used in events and reports).

  Returns:
    :openadr.interval/id             — integer
    :openadr.interval/interval-period — coerced IntervalPeriod (when present)
    :openadr.interval/payloads       — vector of coerced payload maps

  Attaches :openadr/raw metadata."
  [raw]
  (-> (cond-> {:openadr.interval/id       (:id raw)
               :openadr.interval/payloads (mapv coerce-payload (:payloads raw))}
        (:intervalPeriod raw)
        (assoc :openadr.interval/interval-period (->interval-period (:intervalPeriod raw))))
      (with-meta {:openadr/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: shared object metadata
;; ---------------------------------------------------------------------------

(defn- coerce-object-metadata
  "Extract and coerce the common object metadata fields."
  [raw]
  {:openadr/id                    (:id raw)
   :openadr/created               (parse-zoned-datetime-maybe (:createdDateTime raw))
   :openadr/modified              (parse-zoned-datetime-maybe (:modificationDateTime raw))
   :openadr/object-type           (keyword "openadr.object-type"
                                           (.toLowerCase ^String (:objectType raw)))})

;; ---------------------------------------------------------------------------
;; Coercion: ValuesMap (used in attributes)
;; ---------------------------------------------------------------------------

(defn ->values-map
  "Coerce a raw ValuesMap (as used in attributes/targets).
  Same as coerce-payload but kept as a separate entry point for clarity."
  [raw]
  (coerce-payload raw))

;; ---------------------------------------------------------------------------
;; Coercion: Program
;; ---------------------------------------------------------------------------

(defn ->program
  "Coerce a raw Program map into a namespaced entity.

  Attaches :openadr/raw metadata."
  [raw]
  (-> (merge (coerce-object-metadata raw)
             {:openadr.program/name (:programName raw)}
             (when-let [ip (:intervalPeriod raw)]
               {:openadr.program/interval-period (->interval-period ip)})
             (when-let [descs (:programDescriptions raw)]
               {:openadr.program/descriptions (mapv :URL descs)})
             (when-let [pds (:payloadDescriptors raw)]
               {:openadr.program/payload-descriptors pds})
             (when-let [attrs (:attributes raw)]
               {:openadr.program/attributes (mapv ->values-map attrs)})
             (when-let [tgts (:targets raw)]
               {:openadr.program/targets tgts}))
      (with-meta {:openadr/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Event
;; ---------------------------------------------------------------------------

(defn ->event
  "Coerce a raw Event map into a namespaced entity.

  Attaches :openadr/raw metadata."
  [raw]
  (-> (merge (coerce-object-metadata raw)
             {:openadr.event/program-id (:programID raw)}
             (when-let [n (:eventName raw)]
               {:openadr.event/name n})
             (when-let [d (:duration raw)]
               {:openadr.event/duration (parse-duration d)})
             (when-let [p (:priority raw)]
               {:openadr.event/priority p})
             (when-let [tgts (:targets raw)]
               {:openadr.event/targets tgts})
             (when-let [rds (:reportDescriptors raw)]
               {:openadr.event/report-descriptors rds})
             (when-let [pds (:payloadDescriptors raw)]
               {:openadr.event/payload-descriptors pds})
             (when-let [ip (:intervalPeriod raw)]
               {:openadr.event/interval-period (->interval-period ip)})
             (when-let [intervals (:intervals raw)]
               {:openadr.event/intervals (mapv ->interval intervals)}))
      (with-meta {:openadr/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: VEN
;; ---------------------------------------------------------------------------

(defn ->ven
  "Coerce a raw VEN map into a namespaced entity.

  Attaches :openadr/raw metadata."
  [raw]
  (-> (merge (coerce-object-metadata raw)
             {:openadr.ven/name (:venName raw)}
             (when-let [cid (:clientID raw)]
               {:openadr.ven/client-id cid})
             (when-let [attrs (:attributes raw)]
               {:openadr.ven/attributes (mapv ->values-map attrs)})
             (when-let [tgts (:targets raw)]
               {:openadr.ven/targets tgts}))
      (with-meta {:openadr/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Resource
;; ---------------------------------------------------------------------------

(defn ->resource
  "Coerce a raw Resource map into a namespaced entity.

  Attaches :openadr/raw metadata."
  [raw]
  (-> (merge (coerce-object-metadata raw)
             {:openadr.resource/name   (:resourceName raw)
              :openadr.resource/ven-id (:venID raw)}
             (when-let [cid (:clientID raw)]
               {:openadr.resource/client-id cid})
             (when-let [attrs (:attributes raw)]
               {:openadr.resource/attributes (mapv ->values-map attrs)})
             (when-let [tgts (:targets raw)]
               {:openadr.resource/targets tgts}))
      (with-meta {:openadr/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Report
;; ---------------------------------------------------------------------------

(defn ->report-resource
  "Coerce a raw report resource (nested in a Report).

  Attaches :openadr/raw metadata."
  [raw]
  (-> (cond-> {:openadr.report-resource/name      (:resourceName raw)
               :openadr.report-resource/intervals  (mapv ->interval (:intervals raw))}
        (:intervalPeriod raw)
        (assoc :openadr.report-resource/interval-period
               (->interval-period (:intervalPeriod raw))))
      (with-meta {:openadr/raw raw})))

(defn ->report
  "Coerce a raw Report map into a namespaced entity.

  Attaches :openadr/raw metadata."
  [raw]
  (-> (merge (coerce-object-metadata raw)
             {:openadr.report/event-id    (:eventID raw)
              :openadr.report/client-name (:clientName raw)
              :openadr.report/client-id   (:clientID raw)
              :openadr.report/resources   (mapv ->report-resource (:resources raw))}
             (when-let [n (:reportName raw)]
               {:openadr.report/name n})
             (when-let [pds (:payloadDescriptors raw)]
               {:openadr.report/payload-descriptors pds}))
      (with-meta {:openadr/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Subscription
;; ---------------------------------------------------------------------------

(defn ->object-operation
  "Coerce a raw objectOperation map.

  Attaches :openadr/raw metadata."
  [raw]
  (-> {:openadr.object-operation/objects
       (mapv #(keyword "openadr.object-type" (.toLowerCase ^String %)) (:objects raw))

       :openadr.object-operation/operations
       (mapv #(keyword "openadr.operation" (.toLowerCase ^String %)) (:operations raw))

       :openadr.object-operation/callback-url (:callbackUrl raw)}
      (cond->
       (:bearerToken raw)
        (assoc :openadr.object-operation/bearer-token (:bearerToken raw)))
      (with-meta {:openadr/raw raw})))

(defn ->subscription
  "Coerce a raw Subscription map into a namespaced entity.

  Attaches :openadr/raw metadata."
  [raw]
  (-> (merge (coerce-object-metadata raw)
             {:openadr.subscription/client-name (:clientName raw)
              :openadr.subscription/client-id   (:clientID raw)
              :openadr.subscription/object-operations
              (mapv ->object-operation (:objectOperations raw))}
             (when-let [pid (:programID raw)]
               {:openadr.subscription/program-id pid})
             (when-let [tgts (:targets raw)]
               {:openadr.subscription/targets tgts}))
      (with-meta {:openadr/raw raw})))

;; ---------------------------------------------------------------------------
;; Validation helpers
;; ---------------------------------------------------------------------------

(defn validate-raw-program
  "Validate a raw program map. Returns nil on success, Malli explanation on failure."
  [raw]
  (m/explain raw/Program raw))

(defn validate-raw-event [raw] (m/explain raw/Event raw))
(defn validate-raw-ven [raw] (m/explain raw/Ven raw))
(defn validate-raw-resource [raw] (m/explain raw/Resource raw))
(defn validate-raw-report [raw] (m/explain raw/Report raw))
(defn validate-raw-subscription [raw] (m/explain raw/Subscription raw))

;; ---------------------------------------------------------------------------
;; Generic coercion dispatch (by objectType)
;; ---------------------------------------------------------------------------

(defmulti coerce
  "Coerce a raw API entity map based on its :objectType.
  Returns a namespaced entity with :openadr/raw metadata.

  Dispatches on the :objectType string value."
  (fn [raw] (:objectType raw)))

(defmethod coerce "PROGRAM" [raw] (->program raw))
(defmethod coerce "EVENT" [raw] (->event raw))
(defmethod coerce "VEN" [raw] (->ven raw))
(defmethod coerce "BL_VEN_REQUEST" [raw] (->ven raw))
(defmethod coerce "VEN_VEN_REQUEST" [raw] (->ven raw))
(defmethod coerce "RESOURCE" [raw] (->resource raw))
(defmethod coerce "BL_RESOURCE_REQUEST" [raw] (->resource raw))
(defmethod coerce "VEN_RESOURCE_REQUEST" [raw] (->resource raw))
(defmethod coerce "REPORT" [raw] (->report raw))
(defmethod coerce "SUBSCRIPTION" [raw] (->subscription raw))

(defmethod coerce :default
  [raw]
  (throw (ex-info (str "Unknown objectType: " (:objectType raw))
                  {:object-type (:objectType raw) :raw raw})))

;; ---------------------------------------------------------------------------
;; MQTT Notification coercion
;; ---------------------------------------------------------------------------

(def ^:private id-suffix-fixups
  "OpenADR uses uppercase ID in camelCase keys (programID, eventID, etc.)
  but camel-snake-kebab produces programId. Fix the known suffixes."
  {:programId  :programID
   :eventId    :eventID
   :venId      :venID
   :resourceId :resourceID
   :reportId   :reportID
   :clientId   :clientID
   :subscriptionId :subscriptionID})

(defn- snake->camel-keys
  "Recursively transform all keys from snake_case to camelCase keywords,
  then fix ID suffix convention (programId -> programID)."
  [m]
  (cske/transform-keys (comp #(get id-suffix-fixups % %) csk/->camelCaseKeyword) m))

(defn ->notification
  "Coerce a raw notification into a namespaced entity.

  Handles both formats:
    - Spec-compliant (camelCase): :objectType, :operation, :object with camelCase keys
    - VTN-RI (snake_case): :object_type, :operation, :object with snake_case keys

  The VTN-RI snake_case format is a known bug where MQTT notifications use
  snake_case instead of the camelCase defined in the OpenADR 3 specification.

  Optional extra-meta map is merged into the metadata. Use this to record
  the delivery channel, e.g. {:openadr/channel :mqtt :openadr/topic \"programs/create\"}.
  A future webhook handler would pass {:openadr/channel :webhook} instead.

  Attaches :openadr/raw metadata (plus any extra-meta)."
  ([raw]
   (->notification raw nil))
  ([raw extra-meta]
   (let [snake?    (and (contains? raw :object_type)
                        (not (contains? raw :objectType)))
         obj-type  (if snake? (:object_type raw) (:objectType raw))
         operation (:operation raw)
         obj       (:object raw)
         camel-obj (if snake? (snake->camel-keys obj) obj)
         entity    (coerce camel-obj)]
     (-> {:openadr.notification/object-type
          (keyword "openadr.object-type" (.toLowerCase ^String obj-type))

          :openadr.notification/operation
          (keyword "openadr.operation" (.toLowerCase ^String operation))

          :openadr.notification/object entity}
         (cond->
          (:targets raw)
           (assoc :openadr.notification/targets (:targets raw)))
         (with-meta (merge {:openadr/raw raw} extra-meta))))))

(defn notification?
  "Returns true if the map looks like a notification payload.
  Detects both spec-compliant camelCase and VTN-RI snake_case formats."
  [m]
  (and (map? m)
       (or (contains? m :object_type) (contains? m :objectType))
       (contains? m :operation)
       (contains? m :object)))

;; ---------------------------------------------------------------------------
;; Time helpers
;; ---------------------------------------------------------------------------

(defn ->zoned
  "Re-zone a coerced ZonedDateTime to the given IANA zone, preserving the
  same instant.  Useful when a value parsed from the wire (zoned to its
  numeric offset) needs to be presented in a named zone (e.g. your VTN's
  market zone) so subsequent arithmetic respects DST.

  Example:
    (->zoned (:openadr/created program) (java.time.ZoneId/of \"America/Los_Angeles\"))"
  [^ZonedDateTime zdt ^ZoneId zone-id]
  (.withZoneSameInstant zdt zone-id))
