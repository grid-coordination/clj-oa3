(ns openadr3.entities.schema
  "Malli schemas for coerced OpenADR 3 entities.

  These describe the Clojure-native shape produced by `openadr3.entities` coercion:
  namespaced keywords, Instants, Durations, and tick intervals."
  (:import [java.time Duration]))

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

(def Notification
  "Coerced MQTT notification."
  [:map
   [:openadr.notification/object-type :keyword]
   [:openadr.notification/operation :keyword]
   [:openadr.notification/object :map]
   [:openadr.notification/targets {:optional true} [:maybe [:vector :any]]]])
