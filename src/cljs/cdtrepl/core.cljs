(ns cdtrepl.core
  (:require 
      [reagent.core :as reagent :refer [atom]]
      [cdtrepl.ui :as ui]
      [cdtrepl.eval :as eval]
      [cdtrepl.state :as state]
      [cdtrepl.background :as background]
      [cdtrepl.comp :as comp]
      [cdtrepl.settings :as settings]
      [khroma.devtools :as devtools]
      [khroma.extension :as extension]
      [khroma.runtime :as runtime]
      [cljs.core.async :as async] 
      [khroma.tabs :as tabs]
      [khroma.log :as log]
      )
 
  (:require-macros 
    [cljs.core.async.macros :refer [go alt! go-loop]]))


(defn append-keyed-entry! [target entry]
  (swap! target
    (fn [{:keys [key items] :as old}]
      (let [new-key (+ key 1)]
        (assoc old
          :key new-key
          :items 
            (conj items 
              (assoc entry :key new-key)))))))

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

(defn map>nil [f]
  (async/map> f 
    (async/chan 
      (async/dropping-buffer 1))))

(defn map<nil [ch f]
  (async/pipe
    (async/map< f ch)      
      (async/chan (async/dropping-buffer 1))))


(map<nil (state/get-in [:tab :on-inject-agent])
  (fn [message]
    (when (:save-auto message)
      (settings/add-auto-inject! @(state/get-in [:tab :url])))
    
    (background/inject-agent @devtools/tab-id)))

(map<nil (state/get-in [:toolbar :on-reset])
  #(reset! (state/get-in [:log :entries]) 
    (state/empty-keyed-list)))

(map<nil (state/get-in [:toolbar :on-about])
  #(log/info "about"))

(map<nil (state/get-in [:toolbar :on-settings-show])
  #(reset! (state/get-in [:settings :visible]) true))

(map<nil (state/get-in [:settings :on-settings-hide])
  #(reset! (state/get-in [:settings :visible]) false))

(map<nil (state/get-in [:input :on-execute])
  #(let [statement-atom (state/get-in [:input :statement])
         statement (clojure.string/trim @statement-atom)]

        (when-not (empty? statement)
          (append-history (state/get-in [:input]) statement)
          (go
            (log/debug "in-channel < " statement)

            (>! 
              (state/get :in-channel)  {:clj-statement statement :ns @(state/get-in [:tab :ns])})))))  
  
(map<nil (state/get-in [:out-channel])
  (fn [result]
    (append-keyed-entry! (state/get-in [:log :entries]) result)
    (reset! (state/get-in [:input :statement]) "")))

(map<nil (state/get-in [:input :on-history])
  #(history-step (state/get :input) 
    (case (:direction %) :backward compute-idx-backward :forward compute-idx-forward)))


(defn ns-handler [in out]
  (let [[in pass] (async/split #(:ns-change %) in)]
    (async/pipe 
      (async/map< 
        (fn [{:keys [response-ns] :as request}]
          (reset! (state/get-in [:tab :ns]) response-ns)
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
      (state/get-in [:tab :url]) (:url info))
    
    (let [ai (:agentInfo info)]
      (reset! 
        (state/get-in [:tab :info]) ai)             
      
      (if ai
        (if (:is_cljs ai)
          (background/create-ns 
            @(state/get-in [:tab :ns]))
          (background/inject-cljs))
        
        (if (settings/auto-inject? (:url info))  
          (background/inject-agent @devtools/tab-id))))))
           
(defn progress [delay]
  (reset! (state/get :progress) true)    
  
  (go
    (<! (async/timeout delay))
    (reset! (state/get :progress) false)))

(defn ^:export run []
  (progress 250)      
      
  (when (and devtools/available? runtime/available?)
    (background/connect-and-listen @devtools/tab-id))

  (background/log "starting REPL ui")    
        
  (setup-routing 
    (state/get :in-channel) (state/get :out-channel) (state/get :out-channel))
      
    (reagent/render-component [(ui/root-div state/state)]
      (.-body js/document)))
