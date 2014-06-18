(ns cdtrepl.settings-ui
  (:require 
            [goog.array :as g-array]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cdtrepl.util :as util]
            [cdtrepl.settings :as settings])   
    
  (:require-macros 
    [cljs.core.async.macros :refer [go alt! go-loop]]))

(let [link-style #js {:float "right" :margin-left ".7em;"}]
  (defn service-url [state owner]
    (reify
      om/IRender
        (render [_]    
          (let [ref (name (gensym))]
            (dom/div #js {:style #js {:clear "both" :padding-left "5px" :padding-top "10px" :color "#888" :width "250px"}}
              (dom/div nil "Service URL")
              (dom/input #js {
                :ref ref
                :style #js {
                  :outline "none" 
                  :width "100%" 
                  :border "1px solid #888"  
                  :font-size "10pt !important"}
                
                :onChange util/no-op
                :onClick #(.stopPropagation %)
                                           
                :type "text" 
                :value (:service-url state)})
              
                (dom/div nil
                  (dom/a #js {:style link-style :href "#" :onClick #(set! (.-value (om/get-node owner ref)) (:service-url @state))}  "Reset")
                  (dom/a #js {:style link-style :href "#" :onClick #(om/update! state :service-url (.-value (om/get-node owner ref)))}  "Save")))))))

  (defn selected-urls [selected-options]
    (map #(.-value %)      
      (js->clj
        (g-array/toArray selected-options))))
  
  (defn remove-urls [state component]
    (let [selected (selected-urls (.-selectedOptions component))]
      (om/transact! state :auto-inject #(apply disj % selected))))

  (defn auto-inject [state owner]
    (reify 
      om/IRender
        (render [_]      
          (let [ref (name (gensym))]
            (dom/div #js {:style #js {:clear "both" :padding-left "5px" :padding-top "10px" :color "#888" :width "250px"}}
              (dom/div nil "Auto Inject")
              
              (apply dom/select #js {
                :ref ref
                :style #js {:outline "none" :border "1px solid #888" :font-size "10pt !important" :width "100%"}
                :name "autourls"  :multiple "true" :size "5"} 
                
                (map #(dom/option #js {:value % :title %} %) (:auto-inject state)))
             
              (dom/div #js {:style #js {:width "100%"}}
                (dom/a #js {:style link-style :href "#" :onClick #(remove-urls state (om/get-node owner ref))}  "Remove"))))))))
      

(defn settings [state owner]
    (dom/div #js { 
      :style #js {
        :width "100%"
        :height "100%"  
        :padding "0px"}} 
      
      (dom/div #js {
        :style #js {
          :float "left" 
          :margin-top "0px" 
          :padding-left "5px" 
          :font-size "18pt !important" 
          :width "100px;" 
          :color "#888"}} "Settings")
      
      (dom/div #js {
          :style #js {
            :margin-top "5px"
            :padding-right "5px"
            :width "16px"
            :height "16px"
            :background "url(img/close.png) no-repeat center center"
            :float "right" 
            :margin-right "3px"  
            :cursor "pointer"}
          
          :onClick #(util/>channel owner :settings {:show false})
          :title "Close"})
      
      (om/build service-url state)
      (om/build auto-inject state)))


(defn root [state owner]
  (reify
    om/IRender
      (render [_]    
        (dom/div #js {
          :style  #js {
            :position "absolute"
            :top "0px"
            :bottom "0px"
            :left "0px"
            :right "0px"}}    
                        
        (dom/div #js {
          :style  #js {
            :position "absolute"
            :top "10px"
            :bottom "10px"
            :left "10px"
            :right "10px"
            :background-color "white"            
            :border "1px solid #aaa"
            :-webkit-box-shadow "2px 2px 1px rgba(99, 99, 99, 0.75)"}}
              (settings state owner))))))



