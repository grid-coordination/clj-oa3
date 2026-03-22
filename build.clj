(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [codox.md.build :as doc]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'energy.grid-coordination/clj-oa3)
(def version "0.1.0-SNAPSHOT")
(def class-dir "target/classes")

(defn test "Run all the tests." [opts]
  (let [basis    (b/create-basis {:aliases [:test]})
        cmds     (b/java-command
                  {:basis      basis
                   :main      'clojure.main
                   :main-args ["-m" "kaocha.runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn- jar-opts [opts]
  (assoc opts
         :lib lib :version version
         :jar-file (format "target/%s-%s.jar" (name lib) version)
         :description "Clojure client library for the OpenADR 3 API with spec-driven HTTP and entity coercion"
         :url "https://grid-coordination.energy"
         :pom-data [[:licenses
                     [:license
                      [:name "MIT"]
                      [:url "https://opensource.org/licenses/MIT"]]]]
         :scm {:tag (str "v" version)
               :url "https://github.com/grid-coordination/clj-oa3"}
         :basis (b/create-basis {})
         :class-dir class-dir
         :target "target"
         :src-dirs ["src"]))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (test opts)
  (b/delete {:path "target"})
  (let [opts (jar-opts opts)]
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nGenerating API docs...")
    (doc/generate-docs {:lib lib :version version
                        :description (:description opts)
                        :source-uri (str (get-in opts [:scm :url])
                                         "/blob/{git-commit}/{filepath}#L{line}")})
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src" "target/doc-resources"] :target-dir class-dir})
    (println "\nBuilding JAR...")
    (b/jar opts))
  opts)

(defn install "Install the JAR locally." [opts]
  (let [opts (jar-opts opts)]
    (b/install opts))
  opts)

(defn deploy "Deploy the JAR to Clojars." [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
