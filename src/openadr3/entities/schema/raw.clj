(ns openadr3.entities.schema.raw
  "Malli schemas for the raw OpenADR 3 API response shape.

  These mirror the JSON exactly: camelCase keys, string values.
  Most consumers should use `openadr3.entities.schema` (the coerced schemas)
  instead — these are primarily useful for boundary validation.")

(def IntervalPeriod
  [:map
   [:start {:optional true} :string]
   [:duration {:optional true} :string]
   [:randomizeStart {:optional true} :string]])

(def ValuesMap
  [:map
   [:type :string]
   [:values [:vector :any]]])

(def Interval
  [:map
   [:id :int]
   [:intervalPeriod {:optional true} IntervalPeriod]
   [:payloads [:vector ValuesMap]]])

(def EventPayloadDescriptor
  [:map
   [:objectType [:= "EVENT_PAYLOAD_DESCRIPTOR"]]
   [:payloadType :string]
   [:units {:optional true} [:maybe :string]]
   [:currency {:optional true} [:maybe :string]]])

(def ReportPayloadDescriptor
  [:map
   [:objectType [:= "REPORT_PAYLOAD_DESCRIPTOR"]]
   [:payloadType :string]
   [:readingType {:optional true} [:maybe :string]]
   [:units {:optional true} [:maybe :string]]
   [:accuracy {:optional true} [:maybe number?]]
   [:confidence {:optional true} [:maybe :int]]])

(def Program
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType [:= "PROGRAM"]]
   [:programName :string]
   [:intervalPeriod {:optional true} IntervalPeriod]
   [:programDescriptions {:optional true} [:maybe [:vector [:map [:URL :string]]]]]
   [:payloadDescriptors {:optional true} [:maybe [:vector :map]]]
   [:attributes {:optional true} [:maybe [:vector ValuesMap]]]
   [:targets {:optional true} [:maybe [:vector :string]]]])

(def Event
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
   [:intervalPeriod {:optional true} IntervalPeriod]
   [:intervals {:optional true} [:vector Interval]]])

(def Ven
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType :string]
   [:venName :string]
   [:clientID {:optional true} :string]
   [:attributes {:optional true} [:maybe [:vector ValuesMap]]]
   [:targets {:optional true} [:maybe [:vector :string]]]])

(def Resource
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType :string]
   [:resourceName :string]
   [:venID :string]
   [:clientID {:optional true} :string]
   [:attributes {:optional true} [:maybe [:vector ValuesMap]]]
   [:targets {:optional true} [:maybe [:vector :string]]]])

(def ReportResource
  [:map
   [:resourceName :string]
   [:intervalPeriod {:optional true} IntervalPeriod]
   [:intervals [:vector Interval]]])

(def Report
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
   [:resources [:vector ReportResource]]])

(def ObjectOperation
  [:map
   [:objects [:vector :string]]
   [:operations [:vector :string]]
   [:callbackUrl :string]
   [:bearerToken {:optional true} [:maybe :string]]])

(def Subscription
  [:map
   [:id :string]
   [:createdDateTime :string]
   [:modificationDateTime :string]
   [:objectType [:= "SUBSCRIPTION"]]
   [:clientName :string]
   [:clientID :string]
   [:programID {:optional true} :string]
   [:objectOperations [:vector ObjectOperation]]
   [:targets {:optional true} [:maybe [:vector :string]]]])

(def Notification
  "Raw MQTT notification (snake_case keys from JSON)."
  [:map
   [:object_type :string]
   [:operation :string]
   [:object :map]
   [:targets {:optional true} [:maybe [:vector :any]]]])
