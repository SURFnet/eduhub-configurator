(ns ooapi-gateway-configurator.versioning
  "A mechanism for concurrent editing.

  ## Synopsis

    (let [{:keys [version contents]} (versioning/checkout \"some-file.txt\")]
       (versioning/stage! \"some-file.txt\" version \"new contents\")
       (versioning/commit \"some-file.txt\"))

  The ultimate goal of this versioning scheme is to update a `source`
  file safely when multiple people will be editing it, and edits may
  be completed by issuing multiple updates before committing.

  Updates are issued though this package by calling `checkout` and
  `stage!`. When the user is satisfied with the staged changes, she can
  `commit!` them to the `source`.

      O Source: version 4f02d...  -- checkout
      |\\
      | O Stage: version 1e8a3... -- stage!
      | |
      | O Stage: version 93a7c... -- stage!
      |/
      O Source: version 93a7c...  -- commit!

  Staging will check that there has been no concurrent change to the
  stage. The stage is managed by this package and may not be modified
  via other means.

  ## Conflict resolution

  Commits will check that there has been no concurrent change to the
  source file (which may be edited by any mechanism). If the source
  was changed by direct manipulation, any staged changes will be
  discarded. See also the documentation for `commit!`

  Stages will check that there has been no concurrent change to the
  stage. If a conflict is detected, the stage will be discarded. See
  also the documentation for `stage!`"
  (:refer-clojure :exclude [reset!])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [ooapi-gateway-configurator.digest :as digest])
  (:import java.io.PushbackReader))

(defn- digest
  [contents]
  (digest/hex (digest/sha256 contents)))

(defn- read-source-file
  [file]
  (let [f        (io/as-file file)
        contents (clojure.core/slurp f)
        version  (digest contents)]
    {:contents       contents
     :version        version
     :source-version version}))

(defn- read-edn
  [file]
  (let [f (io/as-file file)]
    (when (.exists f)
      (with-open [r (io/reader f)]
        (edn/read (PushbackReader. r))))))

(defn- write-edn
  [file contents]
  (with-open [w (io/writer (io/as-file file))]
    (binding [*out*            w
              *print-length*   nil
              *print-level*    nil
              *print-readably* true]
      (pr contents))))

(defn stage-file-path
  [source-path]
  (str source-path ".stage"))

(defn- read-stage
  [source-path]
  (read-edn (stage-file-path source-path)))


;; all file access is serialized through this monitor
;; that means this whole thing is thread safe but slow
;; if you want to version a whole bunch of files concurrently.
;;
;; for our use-case this is fine
;;
;; since we only expect to run a single instance of this application,
;; this should be sufficient. If we run multiple instances, we should
;; implement some kind of file locks.

(defonce ^:private monitor (Object.))

(defn checkout
  "Get the contents of the latest version of the tracked file. If the
  source file has been modified since the last commit, throw away any
  edits and start anew from the source file.

  Returns a map of :contents, :version and :source-version. If
  `parse-fn` is provided, it's applied to :contents after calculating
  the digest."
  ([source-path parse-fn]
   (update (checkout source-path) :contents parse-fn))
  ([source-path]
   (locking monitor
     (let [staged (read-stage source-path)
           source (read-source-file source-path)]
       (if (= (:version source) (:source-version staged))
         staged
         source)))))

(defn uncommitted?
  "true if the given checkout was modified compared to the source version"
  [{:keys [version source-version]}]
  (not= version source-version))

(defn stage!
  "Write the new version of the data to the staging file.

  If the stage succeeds, returns `true`. If a conflict is detected,
  discards the contents and returns `nil`."
  ([source-path previous-version contents]
   {:pre [(string? source-path) (string? previous-version) (string? contents)]}
   (locking monitor
     (let [{:keys [source-version version]} (checkout source-path)]
       (when (= version previous-version)
         (write-edn (stage-file-path source-path)
                    {:contents       contents
                     :version        (digest contents)
                     :source-version source-version})
         true))))
  ([source-path source-version contents serialize-fn]
   (stage! source-path source-version (serialize-fn contents))))

(defn unstage!
  "Remove the staged version. The next checkout will return the
  then-current source version.

  Returns true if stage was deleted, nil if nothing was staged."
  [source-path]
  (let [f (io/as-file (stage-file-path source-path))]
    (when (.exists f)
      (.delete f)
      true)))

(defn backup-file-path
  [source-path]
  (let [f        (io/as-file source-path)
        modified (.lastModified f)]
    (when-not (.exists f)
      (throw (ex-info (str "Can't backup non-existing file '" source-path "'")
                      {:source-path source-path})))
    (str source-path ".backup." modified)))

(defn commit!
  "Write the staged file to the source file. Keep a backup of the source.

  Returns true if the commit succeeded. If the source was changed
  since the last commit, discard this commit and return nil."
  [source-path]
  (let [stage-path (stage-file-path source-path)]
    (locking monitor
      (when-let [staged (read-edn stage-path)]
        (let [source      (read-source-file source-path)
              backup-path (backup-file-path source-path)]
          (when (= (:source-version staged) (:source-version source))
            (clojure.core/spit backup-path (:contents source))
            (clojure.core/spit source-path (:contents staged))
            (.delete (io/as-file stage-path))
            true))))))

(defn- list-siblings
  [path]
  (let [f        (io/as-file path)
        dir-path (.getParent f)
        dir      (io/as-file (.getParent f))]
    (when dir
      (map #(str dir-path "/" %) (.list dir)))))

(defn reset!
  "Stage an earlier backup of `source-path`."
  [source-path previous-version timestamp]
  {:pre [(string? source-path)
         (string? previous-version)
         (or (int? timestamp) (inst? timestamp))]}
  (let [timestamp   (if (int? timestamp)
                      timestamp
                      (inst-ms timestamp))
        backup-path (str source-path ".backup." timestamp)]
    (stage! source-path previous-version (slurp backup-path))))

(defn backups
  [source-path]
  (let [backup? (fn [b]
                  (and (.isFile (io/as-file b))
                       (string/starts-with? b (str source-path ".backup."))))]
    (->> source-path
         list-siblings
         (filter backup?)
         (map (fn [path]
                {:path      path
                 :timestamp (-> path
                                (string/replace #".*\.backup\." "")
                                Long/parseLong
                                java.time.Instant/ofEpochMilli)}))
         (sort-by :timestamp)
         reverse)))

(defn versions
  [source-path]
  (cons {:path      source-path
         :deployed? true
         :timestamp (java.time.Instant/ofEpochMilli (.lastModified (io/file source-path)))}
        (backups source-path)))
