(ns openadr3.entities-test
  (:require [clojure.test :refer [deftest is testing]]
            [openadr3.entities :as entities])
  (:import [java.time Duration OffsetDateTime ZoneId ZonedDateTime]))

;; ---------------------------------------------------------------------------
;; Test fixtures — raw API response maps
;; ---------------------------------------------------------------------------

(def raw-program
  {:id "prog-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "PROGRAM"
   :programName "Summer DR"
   :programDescriptions [{:URL "https://example.com/desc"}]
   :payloadDescriptors [{:objectType "EVENT_PAYLOAD_DESCRIPTOR"
                         :payloadType "PRICE"}]
   :targets ["target-1"]
   :attributes [{:type "PRICE" :values [1.5 2.0]}]})

(def raw-event
  {:id "evt-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "EVENT"
   :programID "prog-001"
   :eventName "Peak Event"
   :duration "PT1H"
   :priority 1
   :targets ["target-1"]
   :intervalPeriod {:start "2024-06-15T12:00:00Z"
                    :duration "PT2H"}
   :intervals [{:id 0
                :payloads [{:type "PRICE" :values [42.5 43.1]}]
                :intervalPeriod {:start "2024-06-15T12:00:00Z"
                                 :duration "PT1H"}}]})

(def raw-ven
  {:id "ven-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "VEN"
   :venName "My VEN"
   :clientID "client-xyz"
   :attributes [{:type "USAGE" :values [100]}]
   :targets ["target-1"]})

(def raw-resource
  {:id "res-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "RESOURCE"
   :resourceName "Battery"
   :venID "ven-001"
   :clientID "client-xyz"})

(def raw-report
  {:id "rpt-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "REPORT"
   :eventID "evt-001"
   :clientName "my-client"
   :clientID "client-xyz"
   :reportName "Usage Report"
   :resources [{:resourceName "Battery"
                :intervals [{:id 0
                             :payloads [{:type "USAGE" :values [99.5]}]}]}]})

(def raw-subscription
  {:id "sub-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "SUBSCRIPTION"
   :clientName "my-client"
   :clientID "client-xyz"
   :programID "prog-001"
   :objectOperations [{:objects ["PROGRAM" "EVENT"]
                       :operations ["POST" "PUT"]
                       :callbackUrl "https://example.com/webhook"
                       :bearerToken "secret"}]})

;; ---------------------------------------------------------------------------
;; Payload coercion
;; ---------------------------------------------------------------------------

(deftest coerce-payload-price-test
  (let [result (entities/coerce-payload {:type "PRICE" :values [42.5 43.1]})]
    (is (= :openadr.payload-type/price (:openadr.payload/type result)))
    (is (= [42.5M 43.1M] (:openadr.payload/values result)))
    (is (every? #(instance? BigDecimal %) (:openadr.payload/values result)))
    (testing "preserves raw metadata"
      (is (= {:type "PRICE" :values [42.5 43.1]} (:openadr/raw (meta result)))))))

(deftest coerce-payload-usage-test
  (let [result (entities/coerce-payload {:type "USAGE" :values [100 200]})]
    (is (= :openadr.payload-type/usage (:openadr.payload/type result)))
    (is (= [100M 200M] (:openadr.payload/values result)))))

(deftest coerce-payload-default-test
  (let [result (entities/coerce-payload {:type "GRID_CARBON" :values [0.5]})]
    (is (= :openadr.payload-type/grid_carbon (:openadr.payload/type result)))
    (is (= [0.5] (:openadr.payload/values result)))))

;; ---------------------------------------------------------------------------
;; IntervalPeriod coercion
;; ---------------------------------------------------------------------------

(deftest interval-period-test
  (let [result (entities/->interval-period {:start "2024-06-15T12:00:00Z"
                                            :duration "PT2H"})]
    (is (instance? ZonedDateTime (:openadr.interval-period/start result)))
    (is (instance? Duration (:openadr.interval-period/duration result)))
    (is (= (Duration/parse "PT2H") (:openadr.interval-period/duration result)))
    (testing "assocs tick interval keys directly when both start and duration present"
      (is (instance? ZonedDateTime (:tick/beginning result)))
      (is (instance? ZonedDateTime (:tick/end result))))
    (testing "preserves raw metadata"
      (is (some? (:openadr/raw (meta result)))))))

(deftest interval-period-nil-test
  (is (nil? (entities/->interval-period nil))))

(deftest interval-period-partial-test
  (testing "start only — no tick interval keys"
    (let [result (entities/->interval-period {:start "2024-06-15T12:00:00Z"})]
      (is (some? (:openadr.interval-period/start result)))
      (is (nil? (:openadr.interval-period/duration result)))
      (is (nil? (:tick/beginning result)))
      (is (nil? (:tick/end result)))))

  (testing "duration only — no tick interval keys"
    (let [result (entities/->interval-period {:duration "PT1H"})]
      (is (nil? (:openadr.interval-period/start result)))
      (is (some? (:openadr.interval-period/duration result)))
      (is (nil? (:tick/beginning result)))
      (is (nil? (:tick/end result))))))

(deftest interval-period-randomize-start-test
  (let [result (entities/->interval-period {:start "2024-06-15T12:00:00Z"
                                            :duration "PT1H"
                                            :randomizeStart "PT5M"})]
    (is (= (Duration/parse "PT5M")
           (:openadr.interval-period/randomize-start result)))))

;; ---------------------------------------------------------------------------
;; Interval coercion
;; ---------------------------------------------------------------------------

(deftest interval-test
  (let [result (entities/->interval {:id 0
                                     :payloads [{:type "PRICE" :values [42.5]}]
                                     :intervalPeriod {:start "2024-06-15T12:00:00Z"
                                                      :duration "PT1H"}})]
    (is (= 0 (:openadr.interval/id result)))
    (is (= 1 (count (:openadr.interval/payloads result))))
    (is (some? (:openadr.interval/interval-period result)))
    (is (some? (:openadr/raw (meta result))))))

;; ---------------------------------------------------------------------------
;; ->resolved-intervals — spec-mandated intervalPeriod inheritance
;; ---------------------------------------------------------------------------

(defn- event-with
  "Build a minimal coerced event from event-level interval-period args and
  a seq of per-interval intervalPeriod maps (raw, or nil for absent)."
  [{:keys [event-start event-duration interval-ips]}]
  (let [evt-ip (when (or event-start event-duration)
                 (entities/->interval-period
                  (cond-> {}
                    event-start    (assoc :start event-start)
                    event-duration (assoc :duration event-duration))))]
    (cond-> {:openadr.event/intervals
             (vec (map-indexed
                   (fn [i ip]
                     (entities/->interval
                      (cond-> {:id i :payloads []}
                        ip (assoc :intervalPeriod ip))))
                   interval-ips))}
      evt-ip (assoc :openadr.event/interval-period evt-ip))))

(deftest resolved-intervals-passthrough-test
  (testing "all per-interval intervalPeriod present — unchanged passthrough"
    (let [evt (event-with {:event-start "2024-06-15T12:00:00Z"
                           :event-duration "PT1H"
                           :interval-ips [{:start "2024-06-15T13:00:00Z" :duration "PT30M"}
                                          {:start "2024-06-15T14:00:00Z" :duration "PT45M"}]})
          result (entities/->resolved-intervals evt)]
      (is (= (:openadr.event/intervals evt) result)))))

(deftest resolved-intervals-full-inheritance-test
  (testing "per-interval absent — inherits both start and duration"
    (let [evt (event-with {:event-start "2024-06-15T12:00:00Z"
                           :event-duration "PT1H"
                           :interval-ips [nil nil]})
          result (entities/->resolved-intervals evt)
          [iv0 iv1] result]
      (is (= (Duration/parse "PT1H")
             (get-in iv0 [:openadr.interval/interval-period :openadr.interval-period/duration])))
      (is (= (.toInstant (OffsetDateTime/parse "2024-06-15T12:00:00Z"))
             (.toInstant ^ZonedDateTime
              (get-in iv0 [:openadr.interval/interval-period :openadr.interval-period/start]))))
      (is (= (Duration/parse "PT1H")
             (get-in iv1 [:openadr.interval/interval-period :openadr.interval-period/duration])))
      (is (= (.toInstant (OffsetDateTime/parse "2024-06-15T13:00:00Z"))
             (.toInstant ^ZonedDateTime
              (get-in iv1 [:openadr.interval/interval-period :openadr.interval-period/start]))))
      (testing "tick/beginning + tick/end populated"
        (is (some? (get-in iv0 [:openadr.interval/interval-period :tick/beginning])))
        (is (some? (get-in iv0 [:openadr.interval/interval-period :tick/end])))))))

(deftest resolved-intervals-duration-only-test
  (testing "per-interval has only duration — inherits start"
    (let [evt (event-with {:event-start "2024-06-15T12:00:00Z"
                           :event-duration "PT1H"
                           :interval-ips [{:duration "PT15M"}
                                          {:duration "PT45M"}]})
          [iv0 iv1] (entities/->resolved-intervals evt)]
      (is (= (Duration/parse "PT15M")
             (get-in iv0 [:openadr.interval/interval-period :openadr.interval-period/duration])))
      (is (= (.toInstant (OffsetDateTime/parse "2024-06-15T12:00:00Z"))
             (.toInstant ^ZonedDateTime
              (get-in iv0 [:openadr.interval/interval-period :openadr.interval-period/start]))))
      (testing "interval[1] inherited start = event-start + interval[0] resolved duration"
        (is (= (.toInstant (OffsetDateTime/parse "2024-06-15T12:15:00Z"))
               (.toInstant ^ZonedDateTime
                (get-in iv1 [:openadr.interval/interval-period :openadr.interval-period/start])))))
      (is (= (Duration/parse "PT45M")
             (get-in iv1 [:openadr.interval/interval-period :openadr.interval-period/duration]))))))

(deftest resolved-intervals-start-only-test
  (testing "per-interval has only start — inherits duration"
    (let [evt (event-with {:event-start "2024-06-15T12:00:00Z"
                           :event-duration "PT1H"
                           :interval-ips [{:start "2024-06-15T15:30:00Z"}]})
          [iv0] (entities/->resolved-intervals evt)]
      (is (= (.toInstant (OffsetDateTime/parse "2024-06-15T15:30:00Z"))
             (.toInstant ^ZonedDateTime
              (get-in iv0 [:openadr.interval/interval-period :openadr.interval-period/start]))))
      (is (= (Duration/parse "PT1H")
             (get-in iv0 [:openadr.interval/interval-period :openadr.interval-period/duration])))
      (testing "tick keys recomputed against resolved start + duration"
        (is (= (.toInstant (OffsetDateTime/parse "2024-06-15T16:30:00Z"))
               (.toInstant ^ZonedDateTime
                (get-in iv0 [:openadr.interval/interval-period :tick/end]))))))))

(deftest resolved-intervals-no-event-ip-test
  (testing "per-interval absent AND event-level absent — no synthesis"
    (let [evt (event-with {:interval-ips [nil nil]})
          result (entities/->resolved-intervals evt)]
      (is (every? #(nil? (:openadr.interval/interval-period %)) result))
      (is (= 2 (count result))))))

(deftest resolved-intervals-sequential-test
  (testing "N intervals all inheriting — sequential consecutive starts"
    (let [evt (event-with {:event-start "2024-06-15T12:00:00Z"
                           :event-duration "PT30M"
                           :interval-ips [nil nil nil nil]})
          result (entities/->resolved-intervals evt)
          starts (map #(get-in % [:openadr.interval/interval-period :openadr.interval-period/start])
                      result)]
      (is (= 4 (count result)))
      (is (= [(.toInstant (OffsetDateTime/parse "2024-06-15T12:00:00Z"))
              (.toInstant (OffsetDateTime/parse "2024-06-15T12:30:00Z"))
              (.toInstant (OffsetDateTime/parse "2024-06-15T13:00:00Z"))
              (.toInstant (OffsetDateTime/parse "2024-06-15T13:30:00Z"))]
             (map #(.toInstant ^ZonedDateTime %) starts))))))

(deftest resolved-intervals-preserves-payloads-test
  (testing "synthesized intervals retain their payloads and id"
    (let [evt {:openadr.event/interval-period
               (entities/->interval-period {:start "2024-06-15T12:00:00Z" :duration "PT1H"})
               :openadr.event/intervals
               [(entities/->interval {:id 7
                                      :payloads [{:type "PRICE" :values [42.5]}]})]}
          [iv0] (entities/->resolved-intervals evt)]
      (is (= 7 (:openadr.interval/id iv0)))
      (is (= 1 (count (:openadr.interval/payloads iv0))))
      (is (= :openadr.payload-type/price
             (-> iv0 :openadr.interval/payloads first :openadr.payload/type))))))

;; ---------------------------------------------------------------------------
;; Entity coercion: Program
;; ---------------------------------------------------------------------------

(deftest program-coercion-test
  (let [p (entities/->program raw-program)]
    (is (= "prog-001" (:openadr/id p)))
    (is (instance? ZonedDateTime (:openadr/created p)))
    (is (instance? ZonedDateTime (:openadr/modified p)))
    (is (= :openadr.object-type/program (:openadr/object-type p)))
    (is (= "Summer DR" (:openadr.program/name p)))
    (is (= ["https://example.com/desc"] (:openadr.program/descriptions p)))
    (is (= 1 (count (:openadr.program/payload-descriptors p))))
    (is (= 1 (count (:openadr.program/targets p))))
    (is (= 1 (count (:openadr.program/attributes p))))
    (testing "raw metadata roundtrip"
      (is (= raw-program (:openadr/raw (meta p)))))))

(deftest program-minimal-test
  (let [p (entities/->program {:id "p"
                               :createdDateTime "2024-01-01T00:00:00Z"
                               :modificationDateTime "2024-01-01T00:00:00Z"
                               :objectType "PROGRAM"
                               :programName "Minimal"})]
    (is (= "Minimal" (:openadr.program/name p)))
    (is (nil? (:openadr.program/descriptions p)))
    (is (nil? (:openadr.program/interval-period p)))))

;; ---------------------------------------------------------------------------
;; Entity coercion: Event
;; ---------------------------------------------------------------------------

(deftest event-coercion-test
  (let [e (entities/->event raw-event)]
    (is (= "evt-001" (:openadr/id e)))
    (is (= :openadr.object-type/event (:openadr/object-type e)))
    (is (= "prog-001" (:openadr.event/program-id e)))
    (is (= "Peak Event" (:openadr.event/name e)))
    (is (= (Duration/parse "PT1H") (:openadr.event/duration e)))
    (is (= 1 (:openadr.event/priority e)))
    (is (some? (:openadr.event/interval-period e)))
    (is (= 1 (count (:openadr.event/intervals e))))
    (testing "nested interval has coerced payloads"
      (let [interval (first (:openadr.event/intervals e))
            payload (first (:openadr.interval/payloads interval))]
        (is (= :openadr.payload-type/price (:openadr.payload/type payload)))
        (is (= [42.5M 43.1M] (:openadr.payload/values payload)))))))

;; ---------------------------------------------------------------------------
;; Entity coercion: VEN
;; ---------------------------------------------------------------------------

(deftest ven-coercion-test
  (let [v (entities/->ven raw-ven)]
    (is (= "ven-001" (:openadr/id v)))
    (is (= :openadr.object-type/ven (:openadr/object-type v)))
    (is (= "My VEN" (:openadr.ven/name v)))
    (is (= "client-xyz" (:openadr.ven/client-id v)))
    (is (= 1 (count (:openadr.ven/attributes v))))
    (is (= raw-ven (:openadr/raw (meta v))))))

;; ---------------------------------------------------------------------------
;; Entity coercion: Resource
;; ---------------------------------------------------------------------------

(deftest resource-coercion-test
  (let [r (entities/->resource raw-resource)]
    (is (= "res-001" (:openadr/id r)))
    (is (= "Battery" (:openadr.resource/name r)))
    (is (= "ven-001" (:openadr.resource/ven-id r)))
    (is (= "client-xyz" (:openadr.resource/client-id r)))))

;; ---------------------------------------------------------------------------
;; Entity coercion: Report
;; ---------------------------------------------------------------------------

(deftest report-coercion-test
  (let [r (entities/->report raw-report)]
    (is (= "rpt-001" (:openadr/id r)))
    (is (= :openadr.object-type/report (:openadr/object-type r)))
    (is (= "evt-001" (:openadr.report/event-id r)))
    (is (= "my-client" (:openadr.report/client-name r)))
    (is (= "Usage Report" (:openadr.report/name r)))
    (is (= 1 (count (:openadr.report/resources r))))
    (testing "nested report-resource"
      (let [res (first (:openadr.report/resources r))]
        (is (= "Battery" (:openadr.report-resource/name res)))
        (is (= 1 (count (:openadr.report-resource/intervals res))))))))

;; ---------------------------------------------------------------------------
;; Entity coercion: Subscription
;; ---------------------------------------------------------------------------

(deftest subscription-coercion-test
  (let [s (entities/->subscription raw-subscription)]
    (is (= "sub-001" (:openadr/id s)))
    (is (= :openadr.object-type/subscription (:openadr/object-type s)))
    (is (= "my-client" (:openadr.subscription/client-name s)))
    (is (= "prog-001" (:openadr.subscription/program-id s)))
    (is (= 1 (count (:openadr.subscription/object-operations s))))
    (testing "nested object-operation"
      (let [op (first (:openadr.subscription/object-operations s))]
        (is (= [:openadr.object-type/program :openadr.object-type/event]
               (:openadr.object-operation/objects op)))
        (is (= [:openadr.operation/post :openadr.operation/put]
               (:openadr.object-operation/operations op)))
        (is (= "https://example.com/webhook"
               (:openadr.object-operation/callback-url op)))
        (is (= "secret"
               (:openadr.object-operation/bearer-token op)))))))

;; ---------------------------------------------------------------------------
;; Generic coerce multimethod
;; ---------------------------------------------------------------------------

(deftest coerce-dispatch-test
  (testing "dispatches to correct coercion by objectType"
    (is (= :openadr.object-type/program (:openadr/object-type (entities/coerce raw-program))))
    (is (= :openadr.object-type/event (:openadr/object-type (entities/coerce raw-event))))
    (is (= :openadr.object-type/ven (:openadr/object-type (entities/coerce raw-ven))))
    (is (= :openadr.object-type/resource (:openadr/object-type (entities/coerce raw-resource))))
    (is (= :openadr.object-type/report (:openadr/object-type (entities/coerce raw-report))))
    (is (= :openadr.object-type/subscription (:openadr/object-type (entities/coerce raw-subscription)))))

  (testing "VEN request types coerce as VEN"
    (is (= :openadr.object-type/bl_ven_request
           (:openadr/object-type (entities/coerce (assoc raw-ven :objectType "BL_VEN_REQUEST")))))
    (is (= :openadr.object-type/ven_ven_request
           (:openadr/object-type (entities/coerce (assoc raw-ven :objectType "VEN_VEN_REQUEST"))))))

  (testing "Resource request types coerce as Resource"
    (is (some? (entities/coerce (assoc raw-resource :objectType "BL_RESOURCE_REQUEST"))))
    (is (some? (entities/coerce (assoc raw-resource :objectType "VEN_RESOURCE_REQUEST")))))

  (testing "unknown objectType throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unknown objectType"
                          (entities/coerce {:objectType "BOGUS"})))))

;; ---------------------------------------------------------------------------
;; Notification coercion
;; ---------------------------------------------------------------------------

(deftest notification-camelcase-test
  (let [raw {:objectType "PROGRAM"
             :operation "POST"
             :object raw-program}
        n (entities/->notification raw)]
    (is (= :openadr.object-type/program (:openadr.notification/object-type n)))
    (is (= :openadr.operation/post (:openadr.notification/operation n)))
    (is (= "prog-001" (:openadr/id (:openadr.notification/object n))))
    (is (= raw (:openadr/raw (meta n))))))

(deftest notification-snake-case-test
  (testing "VTN-RI snake_case format is handled"
    (let [raw {:object_type "PROGRAM"
               :operation "POST"
               :object {:id "prog-002"
                        :created_date_time "2024-06-15T09:30:00Z"
                        :modification_date_time "2024-06-15T10:00:00Z"
                        :object_type "PROGRAM"
                        :program_name "Snake Program"}}
          n (entities/->notification raw)]
      (is (= :openadr.object-type/program (:openadr.notification/object-type n)))
      (is (= :openadr.operation/post (:openadr.notification/operation n)))
      (is (= "prog-002" (:openadr/id (:openadr.notification/object n)))))))

(deftest notification-extra-meta-test
  (let [n (entities/->notification
           {:objectType "PROGRAM" :operation "POST" :object raw-program}
           {:openadr/channel :mqtt :openadr/topic "programs/create"})]
    (is (= :mqtt (:openadr/channel (meta n))))
    (is (= "programs/create" (:openadr/topic (meta n))))))

(deftest notification?-test
  (testing "detects camelCase notifications"
    (is (true? (entities/notification? {:objectType "PROGRAM" :operation "POST" :object {}}))))

  (testing "detects snake_case notifications"
    (is (true? (entities/notification? {:object_type "PROGRAM" :operation "POST" :object {}}))))

  (testing "rejects non-notifications"
    (is (false? (entities/notification? {:operation "POST" :object {}})))
    (is (false? (entities/notification? {:objectType "PROGRAM" :object {}})))
    (is (false? (entities/notification? "not a map")))
    (is (false? (entities/notification? nil)))))

;; ---------------------------------------------------------------------------
;; Time parsing edge cases
;; ---------------------------------------------------------------------------

(deftest vtn-ri-datetime-format-test
  (testing "handles VTN-RI non-standard datetime (space instead of T, no Z)"
    (let [p (entities/->program {:id "p"
                                 :createdDateTime "2024-06-15 09:30:00"
                                 :modificationDateTime "2024-06-15 10:00:00"
                                 :objectType "PROGRAM"
                                 :programName "VTN-RI Format"})]
      (is (instance? ZonedDateTime (:openadr/created p)))
      (is (= (.toInstant (OffsetDateTime/parse "2024-06-15T09:30:00Z"))
             (.toInstant ^ZonedDateTime (:openadr/created p)))))))

(deftest rfc-3339-offset-test
  (testing "accepts arbitrary RFC 3339 offsets, preserving the wire offset"
    (let [p (entities/->program {:id "p"
                                 :createdDateTime "2026-05-03T00:00:00-07:00"
                                 :modificationDateTime "2026-05-03T00:00:00+00:00"
                                 :objectType "PROGRAM"
                                 :programName "Offset"})
          ^ZonedDateTime created  (:openadr/created p)
          ^ZonedDateTime modified (:openadr/modified p)]
      (is (instance? ZonedDateTime created))
      (is (= (.getOffset (OffsetDateTime/parse "2026-05-03T00:00:00-07:00"))
             (.getOffset created)))
      (is (= (.toInstant (OffsetDateTime/parse "2026-05-03T07:00:00Z"))
             (.toInstant created)))
      (is (= (.toInstant (OffsetDateTime/parse "2026-05-03T00:00:00Z"))
             (.toInstant modified))))))

;; ---------------------------------------------------------------------------
;; Timezone conversion
;; ---------------------------------------------------------------------------

(deftest zoned-test
  (let [zdt (.atZone (.toInstant (OffsetDateTime/parse "2024-06-15T12:00:00Z"))
                     (ZoneId/of "UTC"))
        zone (ZoneId/of "America/Los_Angeles")
        rezoned (entities/->zoned zdt zone)]
    (is (instance? ZonedDateTime rezoned))
    (is (= 5 (.getHour rezoned)))
    (is (= zone (.getZone rezoned)))))

;; ---------------------------------------------------------------------------
;; Raw validation
;; ---------------------------------------------------------------------------

(deftest validate-raw-program-test
  (testing "valid program passes"
    (is (nil? (entities/validate-raw-program raw-program))))
  (testing "missing required field fails"
    (is (some? (entities/validate-raw-program (dissoc raw-program :programName))))))

(deftest validate-raw-event-test
  (is (nil? (entities/validate-raw-event raw-event)))
  (is (some? (entities/validate-raw-event (dissoc raw-event :programID)))))

(deftest validate-raw-ven-test
  (is (nil? (entities/validate-raw-ven raw-ven)))
  (is (some? (entities/validate-raw-ven (dissoc raw-ven :venName)))))

(deftest validate-raw-resource-test
  (is (nil? (entities/validate-raw-resource raw-resource)))
  (is (some? (entities/validate-raw-resource (dissoc raw-resource :venID)))))

(deftest validate-raw-report-test
  (is (nil? (entities/validate-raw-report raw-report)))
  (is (some? (entities/validate-raw-report (dissoc raw-report :eventID)))))

(deftest validate-raw-subscription-test
  (is (nil? (entities/validate-raw-subscription raw-subscription)))
  (is (some? (entities/validate-raw-subscription (dissoc raw-subscription :clientName)))))
