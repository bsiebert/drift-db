(ns drift-db.column.test.byte-array
  (:use [drift-db.column.byte-array])
  (:use [clojure.test])
  (:require [drift-db.column.column :as column-protocol]
            [drift-db.spec :as spec]))

(deftest test-type
  (let [name :test
        column (create-column name)]
    (is (= (spec/type column) :column))
    (is (= (column-protocol/type column) :byte-array))
    (is (= (column-protocol/name column) name))
    (is (nil? (column-protocol/length column)))
    (is (nil? (column-protocol/precision column)))
    (is (nil? (column-protocol/scale column)))
    (is (column-protocol/nullable? column))
    (is (not (column-protocol/primary-key? column)))
    (is (not (column-protocol/auto-increment? column)))))