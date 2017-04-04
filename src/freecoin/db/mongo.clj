;; Freecoin - digital social currency toolkit

;; part of Decentralized Citizen Engagement Technologies (D-CENT)
;; R&D funded by the European Commission (FP7/CAPS 610349)

;; Copyright (C) 2015 Dyne.org foundation
;; Copyright (C) 2015 Thoughtworks, Inc.

;; Sourcecode designed, written and maintained by
;; Denis Roio <jaromil@dyne.org>

;; With contributions by
;; Duncan Mortimer <dmortime@thoughtworks.com>

;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Affero General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.

;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License
;; along with this program.  If not, see <http://www.gnu.org/licenses/>.

(ns freecoin.db.mongo
  (:require [monger.collection :as mc]
            [taoensso.timbre :as log]
            [monger.query :as mq]
            [monger.core :as mongo]))

(def ^:private wallet-collection "wallets")
(def ^:private confirmation-collection "confirmations")
(def ^:private transaction-collection "transactions")

(defn get-mongo-db-and-conn [mongo-uri]
  (let [db-and-conn (mongo/connect-via-uri mongo-uri)]
    db-and-conn))

(defn get-mongo-db [mongo-uri]
  (:db (get-mongo-db-and-conn mongo-uri)))

(defprotocol FreecoinStore
  (store! [e k item]
    "Store item against the key k")
  (update! [e k update-fn]
    "Update the item found using key k by running the update-fn on it and storing it")
  (fetch [e k]
    "Retrieve item based on primary id")
  (query [e query params]
    "Items are returned using a query map")
  (delete! [e k]
    "Delete item based on primary id")
  (aggregate [e formula]
    "Process data records and return computed results")
  (delete-all! [e]
    "Delete all items from a coll"))

(defn apply-skip
  [q skip-num]
  (if skip-num
    (do
      (log/warn "Applying skip:" skip-num)
      (mq/skip q skip-num))
    (do
      (log/warn "Not applying skip:" skip-num)
      q)))

(defn apply-limit
  [q limit-num]
  (if limit-num
    (do
      (mq/limit q limit-num))
    (do
      q)))

(defn debug-q
  [q]
  (doto q (clojure.pprint/pprint)))

(defrecord MongoStore [mongo-db coll]
  FreecoinStore
  (store! [this k item]
    (-> (mc/insert-and-return mongo-db coll (assoc item :_id (k item)))
        (dissoc :_id)))

  (update! [this k update-fn]
    (when-let [item (mc/find-map-by-id mongo-db coll k)]
      (let [updated-item (update-fn item)]
        (-> (mc/save-and-return mongo-db coll updated-item)
            (dissoc :_id)))))

  (fetch [this k]
    (when k
      (-> (mc/find-map-by-id mongo-db coll k)
          (dissoc :_id))))

  (query [this query {skip-num :skip limit-num :limit}]
    (map #(dissoc % :_id)
         (mq/with-collection mongo-db coll
           (-> (mq/find query)
               (apply-skip skip-num)
               (apply-limit limit-num)))))

  (delete! [this k]
    (when k
      (mc/remove-by-id mongo-db coll k)))

  (aggregate [this formula]
    (mc/aggregate mongo-db coll formula))

  (delete-all! [this]
    (mc/remove mongo-db coll)))

(defn create-mongo-store [mongo-db coll]
  (MongoStore. mongo-db coll))

(defrecord MemoryStore [data]
  FreecoinStore
  (store! [this k item]
    (do (swap! data assoc (k item) item)
        item))

  (update! [this k update-fn]
    (when-let [item (@data k)]
      (let [updated-item (update-fn item)]
        (swap! data assoc k updated-item)
        updated-item)))

  (fetch [this k] (@data k))

  (query [this query {:keys [skip limit]}]
    (let [results (filter #(= query (select-keys % (keys query))) (vals @data))
          skipped (if skip (drop skip results) results)]
      (if limit
        (take limit skipped)
        skipped)))

  (delete! [this k]
    (swap! data dissoc k))

  (delete-all! [this]
    (reset! data {}))
  
  ;; TODO aggregate
  )

(defn create-memory-store
  "Create a memory store"
  ([] (create-memory-store {}))
  ([data]
   (MemoryStore. (atom data))))

(defn create-wallet-store [db]
  (create-mongo-store db wallet-collection))

(defn create-confirmation-store [db]
  (create-mongo-store db confirmation-collection))

(defn create-transaction-store [db]
  (create-mongo-store db transaction-collection))
