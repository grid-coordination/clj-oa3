(ns openadr3.api-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [openadr3.api :as api]))

;; ---------------------------------------------------------------------------
;; Spec version resolution
;; ---------------------------------------------------------------------------

(deftest spec-versions-map-test
  (testing "spec-versions contains expected versions"
    (is (= #{"3.0.0" "3.0.1" "3.1.0"} (set (keys api/spec-versions))))
    (is (every? #(re-matches #"openadr3-specification/.+/openadr3\.yaml" %)
                (vals api/spec-versions)))))

(deftest default-spec-version-test
  (is (= "3.1.0" api/default-spec-version)))

(deftest spec-path-test
  (testing "resolves known versions to classpath paths"
    (doseq [v ["3.0.0" "3.0.1" "3.1.0"]]
      (let [path (api/spec-path v)]
        (is (string? path))
        (is (.endsWith path "openadr3.yaml")))))

  (testing "no-arg defaults to 3.1.0"
    (is (= (api/spec-path) (api/spec-path "3.1.0"))))

  (testing "throws on unknown version"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unknown OpenADR spec version"
                          (api/spec-path "9.9.9")))))

;; ---------------------------------------------------------------------------
;; Interceptors
;; ---------------------------------------------------------------------------

(deftest create-authentication-header-test
  (let [interceptor (api/create-authentication-header "my-token")
        ctx ((:enter interceptor) {:request {}})]
    (is (= "Bearer my-token"
           (get-in ctx [:request :headers "Authorization"])))))

(deftest turn-off-exception-throwing-test
  (let [interceptor (api/turn-off-exception-throwing)
        ctx ((:enter interceptor) {:request {}})]
    (is (false? (get-in ctx [:request :throw-exceptions?])))))

(deftest inject-http-client-test
  (let [fake-client :fake-http-client
        interceptor (api/inject-http-client fake-client)
        ctx ((:enter interceptor) {:request {}})]
    (is (= :fake-http-client (get-in ctx [:request :http-client])))))

(deftest safe-coerce-response-test
  (let [safe-cr (->> api/safe-default-interceptors
                     (filter #(= (:name %) :martian.interceptors/coerce-response))
                     first)
        leave-fn (:leave safe-cr)]

    (testing "non-JSON body on 4xx/5xx returns raw body string"
      (let [result (leave-fn {:response {:status 404
                                         :headers {:content-type "application/json"}
                                         :body "Not found"}
                              :coerce-as {:type :default :value :string}})]
        (is (= 404 (get-in result [:response :status])))
        (is (= "Not found" (get-in result [:response :body])))))

    (testing "valid JSON body on 4xx/5xx is still parsed"
      (let [result (leave-fn {:response {:status 404
                                         :headers {:content-type "application/json"}
                                         :body "{\"error\":\"not found\"}"}
                              :coerce-as {:type :default :value :string}})]
        (is (= 404 (get-in result [:response :status])))
        (is (= {:error "not found"} (get-in result [:response :body])))))

    (testing "non-JSON body on 2xx still throws"
      (is (thrown? com.fasterxml.jackson.core.JsonParseException
                   (leave-fn {:response {:status 200
                                         :headers {:content-type "application/json"}
                                         :body "not json"}
                              :coerce-as {:type :default :value :string}}))))))

;; ---------------------------------------------------------------------------
;; Response helpers
;; ---------------------------------------------------------------------------

(deftest success?-test
  (testing "2xx status codes are successful"
    (doseq [status [200 201 204 299]]
      (is (true? (api/success? {:status status})))))

  (testing "non-2xx are not successful"
    (doseq [status [199 300 400 401 403 404 500]]
      (is (false? (api/success? {:status status}))))))

(deftest body-test
  (is (= {:foo "bar"} (api/body {:status 200 :body {:foo "bar"}})))
  (is (nil? (api/body {:status 200}))))

;; ---------------------------------------------------------------------------
;; Utility: hash-map-by
;; ---------------------------------------------------------------------------

(deftest hash-map-by-test
  (testing "creates a unique map"
    (is (= {"a" {:name "a" :v 1}
            "b" {:name "b" :v 2}}
           (api/hash-map-by :name [{:name "a" :v 1} {:name "b" :v 2}]))))

  (testing "throws on duplicate keys"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Duplicate key"
                          (api/hash-map-by :name [{:name "a"} {:name "a"}])))))

;; ---------------------------------------------------------------------------
;; find-by-name helpers (unit-level: verify tolerance of duplicate names)
;; ---------------------------------------------------------------------------

(deftest find-by-name-tolerates-duplicates-test
  (testing "some-based lookup returns first match when duplicates exist"
    (let [programs [{:programName "A" :id "1"}
                    {:programName "B" :id "2"}
                    {:programName "A" :id "3"}]
          find (fn [name coll]
                 (some #(when (= name (:programName %)) %) coll))]
      (is (= {:programName "A" :id "1"} (find "A" programs)))
      (is (= {:programName "B" :id "2"} (find "B" programs)))
      (is (nil? (find "C" programs))))))

;; ---------------------------------------------------------------------------
;; Authorization
;; ---------------------------------------------------------------------------

(deftest authorized?-test
  (testing "returns truthy when scopes overlap"
    (is (api/authorized? #{"read_all" "write_vens"} #{"read_all"})))

  (testing "returns falsy when no overlap"
    (is (not (api/authorized? #{"read_all"} #{"write_programs"}))))

  (testing "returns falsy with empty sets"
    (is (not (api/authorized? #{} #{"read_all"})))
    (is (not (api/authorized? #{"read_all"} #{})))))

;; ---------------------------------------------------------------------------
;; Client creation and introspection (bootstraps from embedded spec)
;; ---------------------------------------------------------------------------

(deftest create-ven-client-test
  (let [ven (api/create-ven-client "token" "http://example.com")]
    (testing "metadata has correct client type"
      (is (= :ven (api/client-type ven))))
    (testing "has VEN scopes"
      (is (contains? (api/scopes ven) "read_all"))
      (is (contains? (api/scopes ven) "write_reports"))
      (is (not (contains? (api/scopes ven) "write_programs"))))
    (testing "has routes"
      (is (pos? (count (api/all-routes ven)))))))

(deftest create-bl-client-test
  (let [bl (api/create-bl-client "token" "http://example.com")]
    (testing "metadata has correct client type"
      (is (= :bl (api/client-type bl))))
    (testing "has BL scopes"
      (is (contains? (api/scopes bl) "read_all"))
      (is (contains? (api/scopes bl) "write_programs"))
      (is (not (contains? (api/scopes bl) "write_reports"))))
    (testing "has routes"
      (is (pos? (count (api/all-routes bl)))))))

(deftest create-client-with-spec-version-test
  (testing "can create clients with different spec versions"
    (let [ven-310 (api/create-ven-client "t" "http://x" {:spec-version "3.1.0"})
          ven-301 (api/create-ven-client "t" "http://x" {:spec-version "3.0.1"})
          ven-300 (api/create-ven-client "t" "http://x" {:spec-version "3.0.0"})]
      (is (> (count (api/all-routes ven-310))
             (count (api/all-routes ven-301))))
      (is (pos? (count (api/all-routes ven-300)))))))

(deftest user-agent-interceptor-test
  (testing "default user-agent is applied"
    (let [client (api/create-ven-client "t" "http://x")
          interceptors (:interceptors client)
          ua-interceptor (some #(when (= ::api/add-user-agent-header (:name %)) %) interceptors)]
      (is (some? ua-interceptor))
      (let [ua (get-in ((:enter ua-interceptor) {:request {:headers {}}})
                       [:request :headers "User-Agent"])]
        (is (str/starts-with? ua (str "clj-oa3/" api/lib-version)))
        (is (re-find #"\((?:mac=[0-9a-f]+|host=.+|unknown)\)" ua)))))
  (testing "custom user-agent is applied"
    (let [client (api/create-ven-client "t" "http://x" {:user-agent "my-app/2.0"})
          interceptors (:interceptors client)
          ua-interceptor (some #(when (= ::api/add-user-agent-header (:name %)) %) interceptors)]
      (is (= "my-app/2.0"
             (get-in ((:enter ua-interceptor) {:request {:headers {}}})
                     [:request :headers "User-Agent"]))))))

;; ---------------------------------------------------------------------------
;; Route introspection
;; ---------------------------------------------------------------------------

(deftest route-introspection-test
  (let [client (api/create-ven-client "token" "http://example.com")]
    (testing "all-routes returns keywords"
      (is (every? keyword? (api/all-routes client))))

    (testing "get-handler finds known routes"
      (is (some? (api/get-handler client :search-all-programs))))

    (testing "get-handler returns nil for unknown routes"
      (is (nil? (api/get-handler client :nonexistent-route))))

    (testing "endpoint-scopes returns a set of strings"
      (let [scopes (api/endpoint-scopes client :search-all-programs)]
        (is (set? scopes))
        (is (every? string? scopes))))

    (testing "unauthenticated routes exist"
      (is (seq (api/get-unauthenticated-routes client))))))
