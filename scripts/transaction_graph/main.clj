(ns transaction-graph.main
  (:require [clojure.test.check.generators :as gen]
            [monger.core :as monger]
            [freecoin-lib.db.mongo :as mongo]
            [freecoin-lib.db.storage :as storage]
            [freecoin-lib.core :as blockchain]
            [freecoin-lib.db.wallet :as wallet]
            [freecoin-lib.db.uuid :as uuid])
  (:gen-class))

(defn db-url [db-name] (str "mongodb://localhost:27017/" db-name))

(def ^:private wallet-counter (atom 0))

(defn new-wallet! [wallet-store blockchain wallet-data-generator]
  (let [{:keys [name email]} (wallet-data-generator)]
    (wallet/new-empty-wallet! wallet-store blockchain name email)))

(defn new-transaction! [blockchain transaction-data-generator wallets-and-secrets]
  (let [{:keys [from-account-id amount
                to-account-id secret]} (transaction-data-generator wallets-and-secrets)]
    (Thread/sleep 10)
    (blockchain/create-transaction blockchain from-account-id amount to-account-id {:secret secret})))

(defn create-wallet-generator [index-generator]
  (fn []
    (let [index (index-generator)]
      {:name (str "name" index)
       :email (str "test-" index "@email.com")})))

(defn create-transaction-generator [from-selector to-selector amount-generator]
  (fn [wallets-and-secrets]
    (let [from (from-selector wallets-and-secrets)
          to (to-selector wallets-and-secrets)
          amount (amount-generator)]
      {:from-account-id (-> from :wallet :account-id)
       :amount amount
       :to-account-id (-> to :wallet :account-id)
       :secret (:apikey from)})))

(defn random-selection [collection]
  (first (gen/sample (gen/elements collection) 1)))

(defn random-amount []
  (-> (rand) (* 20) Math/floor (/ 2)))

(defn create-index-generator []
  (let [current-index (atom 0)]
    (fn []
      (swap! current-index inc))))

(defn populate-db [db n-wallets n-transactions]
  (let [stores-m (storage/create-mongo-stores db)
        blockchain (blockchain/new-mongo stores-m)
        wallet-store (:wallet-store stores-m)
        wallet-data-generator (create-wallet-generator (create-index-generator))
        transaction-data-generator (create-transaction-generator random-selection random-selection random-amount)
        wallets-and-secrets (->> #(new-wallet! wallet-store blockchain
                                               wallet-data-generator)
                                 repeatedly
                                 (take n-wallets)
                                 doall)]
    (doall (->> #(new-transaction! blockchain transaction-data-generator wallets-and-secrets)
                repeatedly
                (take n-transactions)))))

(defn -main [& args]
  (if (= (count args) 3)
    (let [db-name (first args)
          {db :db conn :conn} (mongo/get-mongo-db-and-conn (db-url db-name))
          _ (monger/drop-db conn db-name)
          [n-wallets n-transactions] (map #(Integer/parseInt %) (drop 1 args))]
      (populate-db db n-wallets n-transactions)
      (prn (str "Populated database " db " with "
                n-wallets " wallets and " n-transactions " transactions."))
      )
    (prn "Usage: lein test-transactions <db-name> <n-wallets> <n-transactions>")))
