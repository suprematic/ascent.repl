(ns cdtrepl.background
  (:require 
    [cljs.core.async :as async]
    [khroma.runtime :as runtime]
    [khroma.log :as log]
    [khroma.extension :as extension]
    [clojure.walk :as walk])
  
  (:require-macros 
    [cljs.core.async.macros :refer [go alt! go-loop]]))

(def handlers 
  (atom {}))

(def background-channel 
  (atom nil))

(defn handler [type handler]
  (swap! handlers assoc type handler))

(defn log [& parts]
  (if-let [ch @background-channel]
    (go (>! ch {:destination "background" :type "log" :text (apply str parts)}))))

(defn- on-message [message]
  (if-let [handler (@handlers (:type message))]
    (handler message)
    (log (str "no listener found for message: " message))))

(defn connect-and-listen [tab-id]
  (let [ch (runtime/connect :connectInfo {:name (str "repl:" tab-id)})]
    (reset! background-channel ch)   
      (go-loop []
        (when-let [message (<! ch)]
          (on-message 
            (walk/keywordize-keys message))
          (recur)))))

(defn inject-script [url]
  (go 
    (>! @background-channel  
      {:destination "tab" :command "inject" :url url})))

(defn make-dependencies [& deps]
  (map 
    (fn [[module provides requires]]
      {
        :module   module
        :provides provides
        :requires requires
      }
    ) deps))


(defn >background [message]
  (go
    (>! @background-channel 
      (clj->js message))))


(defn inject-cljs []
  (>background
    {
      :destination  "tab" 
      :type      "inject" 
      :dependencies 
        (make-dependencies
          ["js/compiled/goog/base.js"                ["goog"]                                        []]
          ["js/compiled/goog/string/string.js"       ["goog.string" "goog.string.Unicode"]           []]
          ["js/compiled/goog/debug/error.js"         ["goog.debug.Error"]                            []]
          ["js/compiled/goog/asserts/asserts.js"     ["goog.asserts" "goog.asserts.AssertionError"]  ["goog.debug.Error" "goog.string"]]
          ["js/compiled/goog/array/array.js"         ["goog.array" "goog.array.ArrayLike"]           ["goog.asserts"]]
          ["js/compiled/goog/object/object.js"       ["goog.object"]                                 []]
          ["js/compiled/goog/string/stringbuffer.js" ["goog.string.StringBuffer"]                    []]
          
          ["js/compiled/cljs/core.js"                ["cljs.core"]                                   ["goog.string" "goog.array" "goog.object" "goog.string.StringBuffer"]])
        
       :requires ["cljs.core"]}))


(defn create-ns [name]
  (>background
    {
      :destination  "tab"
      :type      "create-ns"
      :immigrate    true
      :ns           name
    }))


(def requests 
  (atom {}))

(handler "eval-response"
  (fn [{:keys [id result source]}]
    (when (= source "tab")
      (log "eval response with id: " id)
      (if-let [ch (@requests id)]
        (do
          (go
            (>! ch result)
            (async/close! ch))
          (swap! requests dissoc id))
      
      (log "cannot find channel for id: " id)))))

(defn eval [statement ns]
  (let [id (str (gensym))
        ch (async/chan)]
       
    (>background 
      {
        :destination "tab"
        :type "eval"
        :id id
        :statement statement
        :ns ns
      })
  
    (go 
      (<! (async/timeout 1000))
      (when-let [id (@requests id)]
        (log "timeout while waiting for eval result for id: " id)
      
        (swap! requests dissoc id)
        (>! ch {"isError" true "code" "CommunicationError: Timeout"})
        (async/close! ch)))
    
    (swap! requests assoc id ch) ch))

(defn inject-agent [tab-id]
  (>background
    {
      :destination "background"
      :type        "inject-agent"
      :tabId        tab-id  
    }))

(defn reload! [ns]
  (>background {
    :destination "tab"
    :type "reload"
    :ns ns }))









