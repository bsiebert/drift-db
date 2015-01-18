(ns drift-db-mysql.test.flavor
  (:use drift-db-mysql.flavor
        clojure.test)
  (:require [drift-db.core :as drift-db]
            [drift-db-mysql.test.column :as column-test]))

(def dbname "drift_db_test")

(def username "drift-db")
(def password "drift-db-pass")

(deftest test-order-clause
  (is (= (order-clause { :order-by :test}) " ORDER BY `test`"))
  (is (= (order-clause { :order-by { :expression :test }}) " ORDER BY `test`"))
  (is (= (order-clause { :order-by { :expression :test :direction :aSc }}) " ORDER BY `test` ASC"))
  (is (= (order-clause { :order-by [:test :test2]}) " ORDER BY `test`, `test2`"))
  (is (= (order-clause { :order-by [{ :expression :test :direction :aSc }
                                    { :expression :test2 :direction :desc }]})
         " ORDER BY `test` ASC, `test2` DESC")))

(deftest create-flavor
  (let [flavor (mysql-flavor username password dbname)]
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
            expected-columns [{ :length 11 :not-null true :primary-key true :name :id :type :integer :auto-increment true }
                              { :default "" :length 20 :not-null true :name :name :type :string }
                              { :name :created-at :type :date }
                              { :name :edited-at :type :date-time }
                              { :scale 6 :precision 20 :name :bar :type :decimal }
                              { :name :description :type :text }
                              { :name :deleted-at :type :time }
                              { :name :foo :type :integer :length 1 }]]
        (is (= (get table-description :name) :test))
        (is (get table-description :columns))
        (is (= (count (get table-description :columns)) (count expected-columns)))
        (doseq [column-pair (map #(list %1 %2) (get table-description :columns) expected-columns)]
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
        { :length 255, :name :altered-test, :type :string })

      (drift-db/update-column :test
        :altered-test (drift-db/string :altered { :length 100 :not-null true }))
      (column-test/assert-column-map
        (drift-db/find-column (drift-db/describe-table :test) :altered)
        { :type :string, :name :altered, :length 100, :not-null true })

      (drift-db/drop-column :test :altered)
      (is (not (drift-db/column-exists? :test :altered)))
      (drift-db/drop-column-if-exists :test :altered)

      (drift-db/drop-column-if-exists :test :bar)
      (is (not (drift-db/column-exists? :test :bar)))

      (drift-db/create-index :test :name-index { :columns [:name] :unique? true :method :hash :direction :descending })
      (drift-db/drop-index :test :name-index)

      (finally 
        (drift-db/drop-table-if-exists :test)
        (is (not (drift-db/table-exists? :test)))))))

(deftest test-rows
  (let [flavor (mysql-flavor username password dbname)]
    (try
      (is flavor)
      (drift-db/init-flavor flavor)
      (drift-db/create-table :test
        (drift-db/string :name { :length 20 :not-null true :primary-key true }))
      (is (drift-db/table-exists? :test))
      (let [test-row-name "blah"
            test-row-name2 "blah2"
            test-row { :name test-row-name }
            test-row2 { :name test-row-name2 }]
        (drift-db/insert-into :test test-row)
        (is (= (first (drift-db/sql-find { :table :test :where [(str "NAME = '" test-row-name "'")] :limit 1 :order-by :name }))
               test-row))
        (drift-db/update :test ["NAME = ?" test-row-name] { :name test-row-name2 })
        (is (= (first (drift-db/sql-find { :table :test :where ["NAME = ?" test-row-name2] })) test-row2))
        (drift-db/update :test { :name test-row-name2 } { :name test-row-name })
        (is (= (first (drift-db/sql-find { :table :test :where ["NAME = ?" test-row-name] })) test-row))
        (drift-db/delete :test ["NAME = ?" test-row-name])
        (is (nil? (first (drift-db/sql-find { :table :test :where { :name test-row-name } })))))
      (finally 
        (drift-db/drop-table :test)
        (is (not (drift-db/table-exists? :test)))))))
