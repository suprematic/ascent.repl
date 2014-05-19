(ns cdtrepl.util
  (:require 
      [cljs.core.async :as async] 
      [cljs.core.async.impl.protocols :as impl]
      [om.core :as om :include-macros true]
      [om.dom :as dom :include-macros true]
      [khroma.log :as log])
  
  (:require-macros 
      [cljs.core.async.macros :refer [go alt! go-loop]]))

(defn consume [chan func]
  (let [kill-ch (async/chan)]
    (go-loop []
      (when-let [[message source] (async/alts! [chan (or kill-ch (async/chan))])]
        (when-not (= source kill-ch)
          (try
            (func message)
            (catch js/Object ex
              (log/error "consumption error: " ex)))
          (recur)))) kill-ch))

(def no-op 
  (constantly nil))

(defn broadcast [& ports]
  (reify
    impl/WritePort
      (put! [_ val handler]
        (doseq [port ports]
          (async/put! port val)))))


(defn >channel [owner cid message]
  (when-let [ch (om/get-shared owner [:channels cid])]
    (async/put! ch message)))

(defn <channel [owner cid func]
  (when-let [ch (om/get-shared owner [:channels cid])]
    (consume ch func)))