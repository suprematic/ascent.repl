(ns cdtrepl.ui
  (:require 
      [cdtrepl.settings-ui]
      [cdtrepl.settings :as settings]
      [cdtrepl.background :as background]
      [khroma.runtime :as kruntime]
      [khroma.log :as log]
      [cljs.core.async :as async] 
      [cdtrepl.history :as history]
      [cdtrepl.ui-components :as ui-compnents] 
      [cdtrepl.util :as util]
      [om.core :as om :include-macros true]
      [om.dom :as dom :include-macros true]
      [khroma.util :as kutil]) 
   
  (:require-macros 
    [cljs.core.async.macros :refer [go alt! go-loop]]))

(defn listen-channel [chan func state owner]
  (util/consume chan 
    (partial func state owner)))

(defn on-result [state owner message]
  (om/transact! state :log #(conj % message))
  (om/transact! state :history #(history/append % (:clj-statement message)))
  (om/update!   state :history-index nil)
  (util/>channel owner :input ""))

(defn on-tab-info [state owner message]
  (om/transact! state :tab-info #(merge % message)))

(defn on-clear [state owner message]
  (om/update! state :log []))

(defn on-history [state owner {:keys [direction]}]
  (let [{:keys [history history-index]} @state]
    (let [hfn (case direction :forward history/forward :backward history/backward) nix (hfn history history-index)]
      (when (not= nix history-index)
        (om/update! state :history-index nix)
        (util/>channel owner :input (nth history nix))))))

(defn on-settings [state owner {:keys [show service-url]}]
  (when-not (nil? show)
    (om/update! state :settings-visible show)))

(defn on-reload [state owner message]
  (let [ns (get-in @state [:tab-info :ns])]
    (background/reload! ns)))

(defn listent-channels [channels state owner] 
  (util/broadcast    
    (listen-channel (:result channels) on-result state owner)
    (listen-channel (:clear channels) on-clear state owner)
    (listen-channel (:reload channels) on-reload state owner)
    (listen-channel (:settings channels) on-settings state owner)
    (listen-channel (:history channels) on-history state owner)
    (listen-channel (:tab-info channels) on-tab-info state owner)))

(defn root [state owner]
  (reify
    om/IRender
      (render [_]
        (dom/div 
          #js {:style #js {:height "100%"} 
            :onClick 
              #(async/put! 
                (om/get-shared owner [:channels :focus]) true)}
                 
          (om/build 
            (if (get-in state [:tab-info :agent-info]) ui-compnents/repl ui-compnents/no-agent) state)
          
          (when (:settings-visible state)
            (om/build cdtrepl.settings-ui/root (:settings state)))))

    om/IWillMount
      (will-mount [_]
        (log/info "root/will-mount")
        (om/set-state! owner :kill
          (listent-channels (om/get-shared owner :channels) state owner)))
      
    om/IWillUnmount
      (will-unmount [_]
        (log/info "root/will-unmount")
        (async/put! 
          (om/get-state owner :kill) true))))


(defn make-channels [] {
  :execute      (async/chan 256)
  :result       (async/chan 256)
  :reload       (async/chan 256)
  :input        (async/chan 256)
  :tab-info     (async/chan 256)
  :inject-agent (async/chan 256)
  :clear        (async/chan 256)
  :focus        (async/chan 256)
  :history      (async/chan 256)
  :settings     (async/chan 256)})


(def state
    {
      :log  []
      
      :history  []
      :history-index nil

      :tab-info {
        :ns "cljs.user"                 
        :url nil
        :agent-info nil
      }
      
      :progress false
      :settings-visible false
      :settings (settings/>state settings/model)
      :no-agent-delay 1000
    })


(defn tx-listen [{:keys [path new-value] :as tx-data} tx-cursor]
  (when (= (first path) :settings) ; list for settings changes
    (reset! (settings/model (second path)) new-value))) ; and save them

(defn run-ui [target]
  (let [channels (make-channels)]      
    (om/root root state 
      {:target (.-body js/document) :shared {:channels channels} :tx-listen tx-listen}) channels))





