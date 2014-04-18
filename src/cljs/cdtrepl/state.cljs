(ns cdtrepl.state
  (:refer-clojure :exclude [get-in get])
  (:require 
    [cljs.core.async :as async]
    [cdtrepl.preferences :as prefs]
    [reagent.core :as reagent :refer [atom]]))

(defn empty-keyed-list [] 
  {
    :key 0
    :items 
      []
  }
)

(def state
  {
    :in-channel   (async/chan)
    :out-channel  (async/chan)
    
    :toolbar {
      :on-reset (async/chan)
    }

    :log  {
      :entries (atom (empty-keyed-list))
    }     

    :input {
      :history   (atom [])
      :history-index (atom nil)

      :statement (atom "")

      :on-execute   (async/chan)
      :on-history   (async/chan)
    }
    
    :settings {
      :visible (atom false)
    }
    
    :tab {
      :ns         (atom "cljs.user")
      :info       (atom nil)
      :url        (atom nil)
      
      :on-inject-agent  (async/chan)
    }
    
    :progress (atom false)
    
    :preferences prefs/model
  } 
)

(defn get [key]
  (state key))

(defn get-in [path]
  (clojure.core/get-in state path))


