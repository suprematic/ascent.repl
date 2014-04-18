(ns cdtrepl.preferences
  (:require 
    [reagent.core :as reagent :refer [atom]]
    [alandipert.storage-atom :as webstorage :refer [local-storage]]))

(def model
  (local-storage 
    (atom {:auto-inject #{}}) :preferences))

(defn auto-inject? [url]
  (let [auto-inject (:auto-inject @model)]
    (auto-inject url)))     

(defn add-auto-inject! [url]
  (swap! model 
    (fn [{:keys [auto-inject] :as original}]
      (assoc original :auto-inject (conj auto-inject url))))
  
  
)      
