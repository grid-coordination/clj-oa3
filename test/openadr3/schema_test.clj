(ns openadr3.schema-test
  "Tests that coerced entities conform to their Malli schemas,
  and that raw fixtures conform to raw schemas."
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [openadr3.entities :as entities]
            [openadr3.entities.schema :as schema]
            [openadr3.entities.schema.raw :as raw]))

;; ---------------------------------------------------------------------------
;; Raw fixtures (same as entities_test)
;; ---------------------------------------------------------------------------

(def raw-program
  {:id "prog-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "PROGRAM"
   :programName "Summer DR"})

(def raw-event
  {:id "evt-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "EVENT"
   :programID "prog-001"
   :intervals [{:id 0 :payloads [{:type "PRICE" :values [42.5]}]}]})

(def raw-ven
  {:id "ven-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "VEN"
   :venName "My VEN"})

(def raw-resource
  {:id "res-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "RESOURCE"
   :resourceName "Battery"
   :venID "ven-001"})

(def raw-report
  {:id "rpt-001"
   :createdDateTime "2024-06-15T09:30:00Z"
   :modificationDateTime "2024-06-15T10:00:00Z"
   :objectType "REPORT"
   :eventID "evt-001"
   :clientName "my-client"
   :clientID "client-xyz"
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
   :objectOperations [{:objects ["PROGRAM"]
                       :operations ["POST"]
                       :callbackUrl "https://example.com/webhook"}]})

;; ---------------------------------------------------------------------------
;; Raw schemas validate raw fixtures
;; ---------------------------------------------------------------------------

(deftest raw-schema-validation-test
  (testing "raw Program validates"
    (is (m/validate raw/Program raw-program)))
  (testing "raw Event validates"
    (is (m/validate raw/Event raw-event)))
  (testing "raw Ven validates"
    (is (m/validate raw/Ven raw-ven)))
  (testing "raw Resource validates"
    (is (m/validate raw/Resource raw-resource)))
  (testing "raw Report validates"
    (is (m/validate raw/Report raw-report)))
  (testing "raw Subscription validates"
    (is (m/validate raw/Subscription raw-subscription))))

(deftest raw-schema-rejects-invalid-test
  (testing "missing required field is rejected"
    (is (not (m/validate raw/Program (dissoc raw-program :programName))))
    (is (not (m/validate raw/Event (dissoc raw-event :programID))))
    (is (not (m/validate raw/Ven (dissoc raw-ven :venName))))))

;; ---------------------------------------------------------------------------
;; Coerced schemas validate coerced entities
;; ---------------------------------------------------------------------------

(deftest coerced-program-schema-test
  (is (m/validate schema/Program (entities/->program raw-program))))

(deftest coerced-event-schema-test
  (is (m/validate schema/Event (entities/->event raw-event))))

(deftest coerced-ven-schema-test
  (is (m/validate schema/Ven (entities/->ven raw-ven))))

(deftest coerced-resource-schema-test
  (is (m/validate schema/Resource (entities/->resource raw-resource))))

(deftest coerced-report-schema-test
  (is (m/validate schema/Report (entities/->report raw-report))))

(deftest coerced-subscription-schema-test
  (is (m/validate schema/Subscription (entities/->subscription raw-subscription))))

;; ---------------------------------------------------------------------------
;; Coerced sub-entity schemas
;; ---------------------------------------------------------------------------

(deftest coerced-interval-period-schema-test
  (let [ip (entities/->interval-period {:start "2024-06-15T12:00:00Z" :duration "PT1H"})]
    (is (m/validate schema/IntervalPeriod ip))))

(deftest coerced-interval-schema-test
  (let [i (entities/->interval {:id 0 :payloads [{:type "PRICE" :values [1.0]}]})]
    (is (m/validate schema/Interval i))))

(deftest coerced-payload-schema-test
  (let [p (entities/coerce-payload {:type "PRICE" :values [42.5]})]
    (is (m/validate schema/Payload p))))

(deftest coerced-notification-schema-test
  (let [n (entities/->notification {:objectType "PROGRAM"
                                    :operation "POST"
                                    :object raw-program})]
    (is (m/validate schema/Notification n))))
