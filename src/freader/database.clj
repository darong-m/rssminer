(ns freader.database
  (:use [clojure.java.jdbc :only [with-connection do-commands]]
        [clojure.java.io :only [resource]]
        [clojure.java.jdbc :only [with-connection do-commands]])
  (:require [freader.config :as conf]
            [clojure.string :as str])
  (:import org.h2.jdbcx.JdbcConnectionPool))

(defonce h2-db-factory  (atom {:factory nil
                               :ds nil}))

(defn close-global-h2-factory []
  (if-let [ds (:ds @h2-db-factory)]
    (.dispose ds)
    (reset! h2-db-factory nil)))

(defn use-h2-database! [file]
  (close-global-h2-factory)
  (let [ds (JdbcConnectionPool/create (str "jdbc:h2:" file)
                                      "sa" "sa")
        f (fn [& args]  (.getConnection ds))]
    (reset! h2-db-factory {:factory f
                           :ds ds})))

(defn import-h2-schema! []
  (let [stats (filter (complement str/blank?)
                      (str/split (slurp (resource "feed_crawler.sql"))
                                 #"\s*----*\s*"))]
    (with-connection @h2-db-factory
      (apply do-commands (cons "DROP ALL OBJECTS" stats)))))
