(ns jenkins-dl.core
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import (java.net InetAddress)
           (java.io File
                    FileOutputStream))
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:gen-class))

(def cli-options
  [["-h" "--host HOSTNAME" "Hostname or IP-Address"
    :default (InetAddress/getByName "localhost")
    :default-desc "localhost"
    :parse-fn #(InetAddress/getByName %)]
   ["-o" "--output-dir DIRECTORY" "Directory to download the artifacts to"
    :default "."
    :parse-fn #(File. %)
    :validate  [#(and (.isDirectory %) (.canWrite %))]]
   ["-p" "--port PORT" "Port number"
    :default 8080
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-v" "--view VIEW" "View"
    :default nil]
   ["-j" "--path PATH" "Path to Jenkins on Host"
    :default "/"]
   ["-s" "--https"]])

(defn usage [options-summary]
  (->> ["Downloads artifacts from jenkins servers."
        ""
        "Usage: program-name [options]"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message), or a map containing the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      errors
      {:exit-message (error-msg errors)}

      :else
      {:options options})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn append-json-api [url]
  (str url "/api/json"))

(defn create-root-url [host port path view https]
  (let [view-part (if view (str "view/" view) nil)]
    (append-json-api (str (if https "https://" "http://") (.getHostName host) ":" port "/" path "/" view-part))))

(defn getAsJsonMap [url]
  (json/read-str (:body (client/get url)) :key-fn keyword))

(defn get-job-maps [host port path view https]
  (let [root-url (create-root-url host port path view https)]
    (try+ (let [root (getAsJsonMap root-url)]
            (map #(identity {:name (:name %) :url (:url %)}) (root :jobs)))
          (catch Object _
            (log/error "Could not retrieve job maps on url: " root-url)
            (throw+)))))

(defn get-last-successful-build-url [job-url]
  (let [job-desc (getAsJsonMap (append-json-api job-url))]
    (:url (job-desc :lastSuccessfulBuild))))

(defn get-artifacts-urls [build-url]
  (let [build-desc (getAsJsonMap (append-json-api build-url))]
    (map #(str build-url "artifact/" %) (map :relativePath (build-desc :artifacts)))))

(defn get-projects-with-artifacts-maps [host port path view https]
  (let [job-maps (get-job-maps host port path view https)]
    (map #(assoc % :artifacts (get-artifacts-urls (:build-url %)))
         (map #(assoc % :build-url (get-last-successful-build-url (:url %))) job-maps))))

(defn bslurp [f]
  (let [dest (java.io.ByteArrayOutputStream.)]
    (with-open [src (io/input-stream f)]
      (io/copy src dest))
    (.toByteArray dest)))

(defn bspit [file content]
  (with-open [out (io/output-stream file)]
    (.write out (byte-array content))))

(defn download-artifact-to-dir [url directory]
  (let [artifact-name (subs url (+ 1 (string/last-index-of url "/")))]
    (bspit (java.io.File. directory artifact-name) (bslurp url))))

(defn download-artifacts-to-dir [name artifacts directory]
  (let [proj-dir (java.io.File. directory name)]
    (.mkdir proj-dir)
    (doseq [artifact artifacts]
      (download-artifact-to-dir artifact proj-dir))))

(defn download-artifacts [{:keys [host port output-dir path view https]}]
  (let [proj-artifacts-maps (get-projects-with-artifacts-maps host port path view https)]
    (doseq [proj-map proj-artifacts-maps]
      (log/info "Downloading artifacts for project " (:name proj-map))
      (download-artifacts-to-dir (:name proj-map) (:artifacts proj-map) output-dir))))

(defn -main [& args]
  (let [{:keys [options exit-message]} (validate-args args)]
    (if exit-message
      (exit 1 exit-message)
      (download-artifacts options))))
