(ns cdtrepl.settings
  (:require 
    [reagent.core :as reagent :refer [atom]]
    [alandipert.storage-atom :as webstorage :refer [local-storage]]))

(def model
  {
    :auto-inject 
      (local-storage 
        (atom #{}) :auto-inject)
      
    :service-url
      (local-storage
        (atom "ws://localhost:9093/ws") :service-url)
  }
)


(defn auto-inject? [url]
  (let [auto-inject @(:auto-inject model)]
    (auto-inject url)))

(defn add-auto-inject! [url]
  (swap! (:auto-inject model) conj url))

(defn disj-all [coll items]
  (apply disj coll items))

(defn remove-auto-inject! [urls]
  (swap! (:auto-inject model) disj-all urls)
)

