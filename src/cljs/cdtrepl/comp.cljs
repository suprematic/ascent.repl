(ns cdtrepl.comp
  (:require 
    [clojure.walk :as cw]
    [khroma.devtools :as devtools]
    [khroma.log :as log]
    [cljs.core.async :as async]
  )

  (:require-macros 
    [cljs.core.async.macros :refer [go alt! go-loop]]))


(defn- response-text [request]
  (.-responseText request))

(defn- make-response [request]
  (if (= (.-status request) 200)
    (merge
      (cw/keywordize-keys 
        (js->clj (.parse js/JSON (response-text request)))))

    {  :status  "error"
       :message (response-text request) }))


(defn compile [request]
  (let [result (async/chan) http-request (js/XMLHttpRequest.)]
    (.open http-request "POST" "http://cdtrepl.suprematic.net/compile" false)
    (.setRequestHeader http-request "Content-Type" "application/json")
      
    (set! (.-onload http-request) 
      (fn []
        (go 
          (>! result (merge request (make-response  http-request)))))) 
    
    (try
      (.send  http-request 
      (.stringify js/JSON 
        (clj->js request)))

      (catch  js/Object e
        (go 
          (>! result 
            { :status "error" :message (str e) }))))
    
    result))

(defn compiler [in-ch]
  (let [out-ch (async/chan)]
    (go-loop [request (<! in-ch)]
      (log/debug "compiler < " request)
      (>! out-ch (<! (compile request)))  
      (recur (<! in-ch)))

    out-ch))

(defn divert-errors [in-ch err-ch]
  (let [[err pass] 
       (async/split 
        #(= (:compile-status %) "error") in-ch)]

       (async/pipe err err-ch)
       pass))