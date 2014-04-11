(ns cdtrepl.core
  (:require 
     
      [reagent.core :as reagent :refer [atom]]
      [cdtrepl.ui :as ui]
      [cdtrepl.eval :as eval]
      [cdtrepl.background :as background]
      [cdtrepl.comp :as comp]
      [khroma.devtools :as devtools]
      [khroma.extension :as extension]
      [khroma.runtime :as runtime]
      [cljs.core.async :as async] 
      [khroma.tabs :as tabs]
      [khroma.log :as log])
 
  (:require-macros 
    [cljs.core.async.macros :refer [go alt! go-loop]]))

(defn empty-keyed-list [] 
  {
    :key 0
    :items 
      []
  }
)

(defn append-keyed-entry! [target entry]
  (swap! target
    (fn [{:keys [key items] :as old}]
      (let [new-key (+ key 1)]
        (assoc old
          :key new-key
          :items 
            (conj items 
              (assoc entry :key new-key)))))))


(declare page-model)

(defn last-index [history]
  (let [size (count history)]
    (if (> size 0)
      (- size 1))))


(defn append-history [{:keys [history history-index]} statement]
  (let [last-index (last-index @history)]
    (when (or (nil? last-index) (not= statement (nth @history last-index)))
      (swap! history conj statement)
      (reset! history-index nil))))

(defn compute-idx-backward [history idx]
  (cond
    (nil? idx)
      (last-index history)
    (= 0 idx)
      0
    :else
        (- idx 1)))


(defn compute-idx-forward [history idx]
  (cond
    (or (nil? idx) (= idx (last-index history)))
      nil

    :else
        (+ idx 1)))


(defn history-step [{:keys [history history-index statement] :as model} history-fn] 
  (let [new-index (history-fn @history @history-index)]
    (when (not= new-index history-index)
      (reset! history-index new-index)
      (reset! statement (if new-index (nth @history new-index) "")))))


(def page-model 
  {
    :in-channel   (async/chan)
 
    :out-channel  (async/map>
                    (fn [result]
                      (append-keyed-entry! (-> page-model :log :entries) result)
                      (reset! (-> page-model :input :statement) "")) 
                    (async/chan 
                      (async/dropping-buffer 1)))
    
    
    :toolbar {
      :on-reset #(reset! (-> page-model :log :entries) 
             (empty-keyed-list))
    }

    :log  {
      :entries (atom (empty-keyed-list))
    }     


    :input {
      :history   (atom [])
      :history-index (atom nil)

      :statement (atom "")


      :on-execute   #(let [statement-atom (-> page-model :input :statement)
                           statement (clojure.string/trim @statement-atom)]

                            (when-not (empty? statement)
                              (append-history (:input page-model) statement)
                              (go
                                (log/debug "in-channel < " statement)

                                (>! 
                                  (:in-channel page-model) {:clj-statement statement :ns @(get-in page-model [:tab :ns])}))))



      :on-history-backward #(history-step (:input page-model) compute-idx-backward)
      :on-history-forward  #(history-step (:input page-model) compute-idx-forward)
    }
    
    :settings {
      :visible (atom false)
    }
    
    :tab {
      :ns         (atom "cljs.user")
      :info       (atom nil)
      :url        (atom nil)
      
      :on-inject-agent  #(background/inject-agent @devtools/tab-id)
      :on-inject-agent-auto  #(background/inject-agent @devtools/tab-id)
    }
    
    :progress (atom false)
  } 
) 

(defn ns-handler [in out]
  (let [[in pass] (async/split #(:ns-change %) in)]
    (async/pipe 
      (async/map< 
        (fn [{:keys [response-ns] :as request}]
          (reset! (get-in page-model [:tab :ns]) response-ns)
          (background/create-ns response-ns)
          (assoc request :eval-status "ok" :eval-result response-ns)     
        ) in) out) pass))

(defn setup-routing [in out err]
  (-> in
      (comp/compiler)
      (comp/divert-errors err)
      (ns-handler out)
      (eval/evaluator)  
      (async/pipe out)))

(background/handler "tab-info"
  (fn [{:keys [info] :as message}] 
      (reset! 
        (get-in page-model [:tab :url]) (:url info))
      
      (let [ai (:agentInfo info)]
        (when ai
          (if (:is_cljs ai)
            (background/create-ns 
              @(get-in page-model [:tab :ns]))
            (background/inject-cljs)))
           
        (reset! 
          (get-in page-model [:tab :info]) ai))))

(defn progress [delay]
  (reset! (:progress page-model) true)    
  
  (go
    (<! (async/timeout delay))
    (reset! (:progress page-model) false)))


(defn ^:export run []
  (progress 250)      
      
  (when (and devtools/available? runtime/available?)
    (background/connect-and-listen @devtools/tab-id))
      
  (setup-routing 
    (:in-channel page-model) (:out-channel page-model) (:out-channel page-model))
      
    (reagent/render-component [(ui/root-div page-model)]
      (.-body js/document)))
