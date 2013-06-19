(ns drift-db-postgresql.test.flavor
  (:use drift-db-postgresql.flavor
        clojure.test)
  (:require [clojure.tools.logging :as logging]
            [drift-db.core :as drift-db]
            [drift-db-postgresql.test.column :as column-test]))

(def dbname "drift_db_test")

(def username "drift-db")
(def password "drift-db-pass")

(deftest test-order-clause
  (is (= (order-clause { :order-by :test}) " ORDER BY \"test\""))
  (is (= (order-clause { :order-by { :expression :test }}) " ORDER BY \"test\""))
  (is (= (order-clause { :order-by { :expression :test :direction :aSc }}) " ORDER BY \"test\" ASC"))
  (is (= (order-clause { :order-by { :expression :test :direction :aSc :nulls "fIrSt" }})
         " ORDER BY \"test\" ASC NULLS FIRST"))
  (is (= (order-clause { :order-by { :expression :test :nulls :last }}) " ORDER BY \"test\" NULLS LAST"))
  (is (= (order-clause { :order-by [:test :test2]}) " ORDER BY \"test\", \"test2\""))
  (is (= (order-clause { :order-by [{ :expression :test :direction :aSc :nulls "fIrSt" }
                                    { :expression :test2 :direction :desc :nulls "last" }]})
         " ORDER BY \"test\" ASC NULLS FIRST, \"test2\" DESC NULLS LAST")))

(deftest create-flavor
  (let [flavor (postgresql-flavor username password dbname)]
    (try
      (is flavor)
      (drift-db/init-flavor flavor)
      (drift-db/create-table :test
        (drift-db/integer :id { :auto-increment true :primary-key true })
        (drift-db/string :name { :length 20 :not-null true })
        (drift-db/date :created-at)
        (drift-db/date-time :edited-at)
        (drift-db/decimal :bar)
        (drift-db/text :description)
        (drift-db/time-type :deleted-at)
        (drift-db/boolean :foo))
      (is (drift-db/table-exists? :test))
      (let [table-description (drift-db/describe-table :test)
            expected-columns [{ :name :id :not-null true :type :integer :auto-increment true }
                              { :name :name :length 20 :not-null true :type :string }
                              { :name :created-at :type :date }
                              { :name :edited-at :type :date-time }
                              { :name :bar :scale 6 :precision 20 :type :decimal }
                              { :name :description :type :text }
                              { :name :deleted-at :type :time }
                              { :name :foo :type :boolean }]]
        (is (= (get table-description :name) :test))
        (is (doall (get table-description :columns)))
        (is (= (count (get table-description :columns)) (count expected-columns)))
        (doseq [column-pair (map #(list %1 %2) (get table-description :columns) (reverse expected-columns))]
          (column-test/assert-column-map (first column-pair) (second column-pair))))
      (is (drift-db/column-exists? :test :id))
      (is (drift-db/column-exists? :test "bar"))

      (drift-db/add-column :test
        (drift-db/string :added))
      (column-test/assert-column-map
        (drift-db/find-column :test :added)
        { :length 255, :name :added, :type :string })

      (drift-db/update-column :test
        :added (drift-db/string :altered-test))
      (column-test/assert-column-map 
        (drift-db/find-column :test :altered-test)
        { :type :string, :name :altered-test, :length 255 })

      (drift-db/update-column :test
        :altered-test (drift-db/string :altered { :length 100 :not-null true }))
      (column-test/assert-column-map
        (drift-db/find-column (drift-db/describe-table :test) :altered)
        { :type :string, :name :altered, :length 100, :not-null true })

      (drift-db/drop-column :test :altered)
      (is (not (drift-db/column-exists? :test :altered)))

      (drift-db/drop-column-if-exists :test :bar)
      (is (not (drift-db/column-exists? :test :bar)))

      (drift-db/create-index :test :name-index { :columns [:name] 
                                                 :unique? true
                                                 :method :btree
                                                 :direction :descending
                                                 :nulls :last })
      (drift-db/drop-index :test :name-index)

      (finally 
        (drift-db/drop-table-if-exists :test)
        (is (not (drift-db/table-exists? :test)))))))

(deftest test-rows
  (let [table-name :test-test
        column-name :name-test
        column-name-str "name_test"
        flavor (postgresql-flavor username password dbname)]
    (try
      (is flavor)
      (drift-db/init-flavor flavor)
      (drift-db/create-table table-name
        (drift-db/string column-name { :length 20 :not-null true :primary-key true }))
      (is (drift-db/table-exists? table-name))
      (is (drift-db/column-exists? table-name column-name))
      (let [test-row-name "blah"
            test-row-name2 "blah2"
            test-row { column-name test-row-name }
            test-row2 { column-name test-row-name2 }]
        (drift-db/insert-into table-name test-row)
        (is (= (first (drift-db/sql-find { :table table-name :where [(str column-name-str " = '" test-row-name "'")] :limit 1
                                           :order-by column-name }))
               test-row))
        (drift-db/update table-name [(str column-name-str " = ?") test-row-name] { column-name test-row-name2 })
        (is (= (first (drift-db/sql-find { :table table-name :where [(str column-name-str " = ?") test-row-name2] }))
               test-row2))
        (drift-db/update table-name { column-name test-row-name2 } { column-name test-row-name })
        (is (= (first (drift-db/sql-find { :table table-name :where [(str column-name-str " = ?") test-row-name] }))
               test-row))
        (drift-db/delete table-name [(str column-name-str " = ?") test-row-name])
        (is (nil? (first (drift-db/sql-find { :table table-name :where { column-name test-row-name } })))))
      (finally 
        (drift-db/drop-table table-name)
        (is (not (drift-db/table-exists? table-name)))))))
