(ns ooapi-gateway-configurator.versioning-test
  (:require [ooapi-gateway-configurator.versioning :as versioning]
            [clojure.test :refer [deftest is use-fixtures]]
            [clojure.java.io :as io]))

(def test-path  "resources/test/versioning.txt")
(def initial-contents "This is a versioned file.\n")
(def initial-digest "4f02bdb8d5c82a834f86a24fe46103e2f6112bba548ee0a5c0ecfac22d35ffd6")

(def updated-contents "Updated text.\n")
(def updated-digest "d1e84dea31b08cc86db35d0de1812f5f178d57da2993a7ca7e128b6a6ee50e2a")


(defn fixture
  [f]
  (spit test-path initial-contents)
  (versioning/unstage! test-path)
  (f)
  (versioning/unstage! test-path)
  (doseq [backup (versioning/backups test-path)]
    (.delete (io/as-file backup)))
  (.delete (io/as-file test-path)))

(use-fixtures :each fixture)

(deftest stage-and-commit
  (is (= {:source-version initial-digest
          :version        initial-digest
          :contents       initial-contents}
         (versioning/checkout test-path))
      "Return contents and digest from source")
  (is (versioning/stage! test-path initial-digest updated-contents))
  (is (= {:source-version initial-digest
          :version        updated-digest
          :contents       updated-contents}
         (versioning/checkout test-path))
      "Return updated contents")
  (is (versioning/commit! test-path)
      "committed")
  (is (= {:source-version updated-digest
          :version        updated-digest
          :contents       updated-contents}
         (versioning/checkout test-path)))
  (is (= updated-contents
         (slurp test-path))))

(def concurrent-contents "Something different\n")
(def concurrent-digest "a9d36e7ff3a15c0343d928845f63aa21446077be1c572420c8d21f38c4a041fd")

(deftest stage-and-revert
  (is (= {:source-version initial-digest
          :version        initial-digest
          :contents       initial-contents}
         (versioning/checkout test-path))
      "Return contents and digest from source")
  (is (versioning/stage! test-path initial-digest updated-contents))
  (spit test-path concurrent-contents)
  (is (= {:source-version concurrent-digest
          :version        concurrent-digest
          :contents       concurrent-contents}
         (versioning/checkout test-path))
      "Ignore staged versions when source is overwritten")
  (is (not (versioning/commit! test-path))
      "Commit discarded")
  (is (= {:source-version concurrent-digest
          :version        concurrent-digest
          :contents       concurrent-contents}
         (versioning/checkout test-path))))

(deftest commit-and-revert
  (is (= {:source-version initial-digest
          :version        initial-digest
          :contents       initial-contents}
         (versioning/checkout test-path))
      "Return contents and digest from source")
  (is (versioning/stage! test-path initial-digest updated-contents))
  (is (= {:source-version initial-digest
          :version        updated-digest
          :contents       updated-contents}
         (versioning/checkout test-path))
      "Return updated contents")
  (spit test-path concurrent-contents)
  (is (not (versioning/commit! test-path))
      "Commit discarded")
  (is (= {:source-version concurrent-digest
          :version        concurrent-digest
          :contents       concurrent-contents}
         (versioning/checkout test-path))))
