(ns openadr3.entities
  "Two-layer data model for OpenADR 3 entities.

  Raw layer: camelCase keys, string values — direct from the API JSON.
  Coerced layer: namespaced keywords, Instants, Durations, tick intervals.

  Every coerced entity preserves the original raw data as :openadr/raw metadata.

  Coercion of ValuesMap payloads is extensible via the `coerce-payload` multimethod,
  dispatching on the payload :type string."
  (:require [tick.core :as t]
            [tick.alpha.interval :as t.i]
            [malli.core :as m])
  (:import [java.time Duration Instant]))

;; ---------------------------------------------------------------------------
;; Raw API schemas (camelCase, strings — mirrors JSON)
;; ---------------------------------------------------------------------------

(def RawIntervalPeriod
  [:map
   [:start {:optional true} :string]
   [:duration {:optional true} :string]
   [:randomizeStart {:optional true} :string]])

(def RawValuesMap
  [:map
   [:type :string]
   [:values [:vector :any]]])

(def RawInterval
  [:map
   [:id :int]
   [:intervalPeriod {:optional true} RawIntervalPeriod]
   [:payloads [:vector RawValuesMap]]])

(def RawEventPayloadDescriptor
  [:map
   [:objectType [:= "EVENT_PAYLOAD_DESCRIPTOR"]]
   [:payloadType :string]
   [:units {:optional true} [:maybe :string]]
   [:currency {:optional true} [:maybe :string]]])

(def RawReportPayloadDescriptor
  [:map
   [:objectType [:= "REPORT_PAYLOAD_DESCRIPTOR"]]
   [:payloadType :string]
   [:readingType {:optional true} [:maybe :string]]
   [:units {:optional true} [:maybe :string]]
   [:accuracy {:optional true} [:maybe number?]]
   [:confidence {:optional true} [:maybe :int]]])

(def RawProgram
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType [:= "PROGRAM"]]
   [:programName :string]
   [:intervalPeriod {:optional true} RawIntervalPeriod]
   [:programDescriptions {:optional true} [:maybe [:vector [:map [:URL :string]]]]]
   [:payloadDescriptors {:optional true} [:maybe [:vector :map]]]
   [:attributes {:optional true} [:maybe [:vector RawValuesMap]]]
   [:targets {:optional true} [:maybe [:vector :string]]]])

(def RawEvent
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType [:= "EVENT"]]
   [:programID :string]
   [:eventName {:optional true} [:maybe :string]]
   [:duration {:optional true} [:maybe :string]]
   [:priority {:optional true} [:maybe :int]]
   [:targets {:optional true} [:maybe [:vector :string]]]
   [:reportDescriptors {:optional true} [:maybe [:vector :map]]]
   [:payloadDescriptors {:optional true} [:maybe [:vector :map]]]
   [:intervalPeriod {:optional true} RawIntervalPeriod]
   [:intervals {:optional true} [:vector RawInterval]]])

(def RawVen
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType :string]
   [:venName :string]
   [:clientID {:optional true} :string]
   [:attributes {:optional true} [:maybe [:vector RawValuesMap]]]
   [:targets {:optional true} [:maybe [:vector :string]]]])

(def RawResource
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType :string]
   [:resourceName :string]
   [:venID :string]
   [:clientID {:optional true} :string]
   [:attributes {:optional true} [:maybe [:vector RawValuesMap]]]
   [:targets {:optional true} [:maybe [:vector :string]]]])

(def RawReportResource
  [:map
   [:resourceName :string]
   [:intervalPeriod {:optional true} RawIntervalPeriod]
   [:intervals [:vector RawInterval]]])

(def RawReport
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType [:= "REPORT"]]
   [:eventID :string]
   [:clientName :string]
   [:clientID :string]
   [:reportName {:optional true} [:maybe :string]]
   [:payloadDescriptors {:optional true} [:maybe [:vector :map]]]
   [:resources [:vector RawReportResource]]])

(def RawObjectOperation
  [:map
   [:objects [:vector :string]]
   [:operations [:vector :string]]
   [:callbackUrl :string]
   [:bearerToken {:optional true} [:maybe :string]]])

(def RawSubscription
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType [:= "SUBSCRIPTION"]]
   [:clientName :string]
   [:clientID :string]
   [:programID {:optional true} :string]
   [:objectOperations [:vector RawObjectOperation]]
   [:targets {:optional true} [:maybe [:vector :string]]]])

;; ---------------------------------------------------------------------------
;; Parsing helpers
;; ---------------------------------------------------------------------------

(defn- parse-instant
  "Parse an RFC 3339 datetime string to a UTC Instant."
  ^Instant [^String s]
  (Instant/parse s))

(defn- parse-duration
  "Parse an ISO 8601 duration string to a java.time.Duration."
  ^Duration [^String s]
  (Duration/parse s))

(defn- parse-instant-maybe
  "Parse an RFC 3339 datetime string to Instant, or nil if nil/blank."
  [s]
  (when (and s (not (.isBlank ^String s)))
    (parse-instant s)))

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
    :openadr.interval-period/start    — Instant (or nil)
    :openadr.interval-period/duration — Duration (or nil)
    :openadr.interval-period/randomize-start — Duration (or nil)
    :openadr.interval-period/period   — tick interval [start, start+duration) (when both present)

  Attaches :openadr/raw metadata."
  [raw]
  (when raw
    (let [start    (parse-instant-maybe (:start raw))
          duration (parse-duration-maybe (:duration raw))
          period   (when (and start duration)
                     (t.i/new-interval start (.plus ^Instant start ^Duration duration)))]
      (-> (cond-> {:openadr.interval-period/start    start
                   :openadr.interval-period/duration duration}
            (:randomizeStart raw)
            (assoc :openadr.interval-period/randomize-start
                   (parse-duration-maybe (:randomizeStart raw)))
            period
            (assoc :openadr.interval-period/period period))
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
   :openadr/created               (parse-instant (:createdDateTime raw))
   :openadr/modified              (parse-instant (:modificationDateTime raw))
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
;; Coerced entity schemas (namespaced keywords, native types)
;; ---------------------------------------------------------------------------

(def IntervalPeriod
  [:map
   [:openadr.interval-period/start [:maybe inst?]]
   [:openadr.interval-period/duration [:maybe [:fn #(instance? Duration %)]]]
   [:openadr.interval-period/randomize-start {:optional true} [:maybe [:fn #(instance? Duration %)]]]
   [:openadr.interval-period/period {:optional true} [:map [:tick/beginning inst?] [:tick/end inst?]]]])

(def Payload
  [:map
   [:openadr.payload/type :keyword]
   [:openadr.payload/values [:vector :any]]])

(def Interval
  [:map
   [:openadr.interval/id :int]
   [:openadr.interval/interval-period {:optional true} IntervalPeriod]
   [:openadr.interval/payloads [:vector Payload]]])

(def Program
  [:map
   [:openadr/id :string]
   [:openadr/created inst?]
   [:openadr/modified inst?]
   [:openadr/object-type [:= :openadr.object-type/program]]
   [:openadr.program/name :string]
   [:openadr.program/interval-period {:optional true} IntervalPeriod]
   [:openadr.program/descriptions {:optional true} [:vector :string]]
   [:openadr.program/payload-descriptors {:optional true} [:vector :map]]
   [:openadr.program/attributes {:optional true} [:vector Payload]]
   [:openadr.program/targets {:optional true} [:vector :string]]])

(def Event
  [:map
   [:openadr/id :string]
   [:openadr/created inst?]
   [:openadr/modified inst?]
   [:openadr/object-type [:= :openadr.object-type/event]]
   [:openadr.event/program-id :string]
   [:openadr.event/name {:optional true} :string]
   [:openadr.event/duration {:optional true} [:fn #(instance? Duration %)]]
   [:openadr.event/priority {:optional true} :int]
   [:openadr.event/targets {:optional true} [:vector :string]]
   [:openadr.event/report-descriptors {:optional true} [:vector :map]]
   [:openadr.event/payload-descriptors {:optional true} [:vector :map]]
   [:openadr.event/interval-period {:optional true} IntervalPeriod]
   [:openadr.event/intervals {:optional true} [:vector Interval]]])

(def Ven
  [:map
   [:openadr/id :string]
   [:openadr/created inst?]
   [:openadr/modified inst?]
   [:openadr/object-type :keyword]
   [:openadr.ven/name :string]
   [:openadr.ven/client-id {:optional true} :string]
   [:openadr.ven/attributes {:optional true} [:vector Payload]]
   [:openadr.ven/targets {:optional true} [:vector :string]]])

(def Resource
  [:map
   [:openadr/id :string]
   [:openadr/created inst?]
   [:openadr/modified inst?]
   [:openadr/object-type :keyword]
   [:openadr.resource/name :string]
   [:openadr.resource/ven-id :string]
   [:openadr.resource/client-id {:optional true} :string]
   [:openadr.resource/attributes {:optional true} [:vector Payload]]
   [:openadr.resource/targets {:optional true} [:vector :string]]])

(def ReportResource
  [:map
   [:openadr.report-resource/name :string]
   [:openadr.report-resource/intervals [:vector Interval]]
   [:openadr.report-resource/interval-period {:optional true} IntervalPeriod]])

(def Report
  [:map
   [:openadr/id :string]
   [:openadr/created inst?]
   [:openadr/modified inst?]
   [:openadr/object-type [:= :openadr.object-type/report]]
   [:openadr.report/event-id :string]
   [:openadr.report/client-name :string]
   [:openadr.report/client-id :string]
   [:openadr.report/resources [:vector ReportResource]]
   [:openadr.report/name {:optional true} :string]
   [:openadr.report/payload-descriptors {:optional true} [:vector :map]]])

(def ObjectOperation
  [:map
   [:openadr.object-operation/objects [:vector :keyword]]
   [:openadr.object-operation/operations [:vector :keyword]]
   [:openadr.object-operation/callback-url :string]
   [:openadr.object-operation/bearer-token {:optional true} :string]])

(def Subscription
  [:map
   [:openadr/id :string]
   [:openadr/created inst?]
   [:openadr/modified inst?]
   [:openadr/object-type [:= :openadr.object-type/subscription]]
   [:openadr.subscription/client-name :string]
   [:openadr.subscription/client-id :string]
   [:openadr.subscription/object-operations [:vector ObjectOperation]]
   [:openadr.subscription/program-id {:optional true} :string]
   [:openadr.subscription/targets {:optional true} [:vector :string]]])

;; ---------------------------------------------------------------------------
;; Validation helpers
;; ---------------------------------------------------------------------------

(defn validate-raw-program
  "Validate a raw program map. Returns nil on success, Malli explanation on failure."
  [raw]
  (m/explain RawProgram raw))

(defn validate-raw-event [raw] (m/explain RawEvent raw))
(defn validate-raw-ven [raw] (m/explain RawVen raw))
(defn validate-raw-resource [raw] (m/explain RawResource raw))
(defn validate-raw-report [raw] (m/explain RawReport raw))
(defn validate-raw-subscription [raw] (m/explain RawSubscription raw))

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
(defmethod coerce "BL_VEN_REQUEST" [raw] (->ven raw))
(defmethod coerce "VEN_VEN_REQUEST" [raw] (->ven raw))
(defmethod coerce "BL_RESOURCE_REQUEST" [raw] (->resource raw))
(defmethod coerce "VEN_RESOURCE_REQUEST" [raw] (->resource raw))
(defmethod coerce "REPORT" [raw] (->report raw))
(defmethod coerce "SUBSCRIPTION" [raw] (->subscription raw))

(defmethod coerce :default
  [raw]
  (throw (ex-info (str "Unknown objectType: " (:objectType raw))
                  {:object-type (:objectType raw) :raw raw})))

;; ---------------------------------------------------------------------------
;; Time helpers
;; ---------------------------------------------------------------------------

(defn ->zoned
  "Convert an Instant to a ZonedDateTime in the given zone.

  Use when you know the timezone context (e.g. your VTN's market zone).
  The library stores Instants (UTC) by default to stay timezone-neutral.

  Example:
    (->zoned (:openadr/created program) (java.time.ZoneId/of \"America/Los_Angeles\"))"
  [^Instant instant ^java.time.ZoneId zone-id]
  (.atZone instant zone-id))
