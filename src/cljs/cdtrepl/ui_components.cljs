(ns cdtrepl.ui-components
  (:require 
      [clojure.string]
      [cdtrepl.settings-ui :as settings]
      [khroma.runtime :as kruntime]
      [khroma.log :as log]
      [cljs.core.async :as async] 
      [cdtrepl.util :as util]
      [om.core :as om :include-macros true]
      [om.dom :as dom :include-macros true]
      [khroma.util :as kutil])
  
  (:require-macros 
    [cljs.core.async.macros :refer [go alt! go-loop]]))

(defn progress-container [model owner {:keys [body]}] 
  (reify
    om/IInitState
      (init-state [_]
        {:progress false})
    
    om/IRenderState
      (render-state [_ {:keys [progress]}]
        (if progress
          (dom/div #js {:style #js {:padding "5" :color "grey"}} "Waiting...")
          (om/build body model)))  
       
    om/IWillMount
      (will-mount [_]
        (let [ch (om/get-shared owner [:channels :on-progress])]
          (go-loop []
            (let [message (<! ch)]
              (when (not (nil? message))   
                (om/set-state! owner :progress message)                      
                (recur))))))))

(defn image-str [image]
  (str "url(" image ") no-repeat center center"))

(defn button [{:keys [float image on-click]}]
  (dom/div 
    #js {
      :onClick #(on-click %) 
      :style #js {
      :height "100%"
      :width "16px"
      :margin-left "5px"
      :background (image-str image)
      :float (or float "left")       
      :cursor "pointer"}}))


(defn toolbar [{:keys [ns] :as model} owner]
  (reify
    om/IRender
      (render [_]
        (dom/div 
          #js {:style 
                #js {:height "23px"
                     :width  "100%"
                     :margin "0px"
                     :border-bottom "1px solid #DDD"}} 
          
          (button {:image "img/clear.png" :on-click #(util/>channel owner :clear true)})
          
          (dom/div #js {
            :style #js {
              :padding-top "4px"
              :margin-left "15px" 
              :float "left"
              :color "grey"}}  (str "<ns: " ns ">"))

          (button {:image "img/reload.png" :on-click #(util/>channel owner :reload true)})
          
          (button {:float "right" :image "img/about.png"})
          (button {:float "right" :image "img/settings.png" :on-click #(util/>channel owner :settings {:show true})})))))



(defn prompt [{:keys [image]}]
  (dom/div 
    #js {
      :style 
        (clj->js   
          {
            :height "20px"
            :width "20px"
            :padding-top "0px"
            :float "left"
            :clear "both"
            :background (if image (image-str image) "none")})}))

(defn log-line [{:keys [text color image] :as model}]
  (dom/div #js {:style #js {:clear "both" :min-height "20px"}}
    (prompt model)
    (dom/div 
      #js {:style #js {:padding-top "4px" :margin-left "20px":color color}}
      text)))

(defn compile-ok? [entry]
  (= (:compile-status entry) "ok"))

(defn eval-ok? [entry]
  (= (:eval-status entry) "ok"))

(defn value-str [value]
  (cond 
    (nil? value)
      "nil"

    :else
       (str value)))

(defn log-entry [state owner]
  (reify
    om/IRender
      (render [_] 
        (dom/div 
          #js {
            :style #js {
              :border-bottom "1px solid #EEE"
              :width "100%"}}
         
          (log-line 
            { :text  (:clj-statement state)
              :color "#367cf1"
              :image "img/prompt_log.png"}) 
          
          (if-not (compile-ok? state)
            (log-line {
              :text  (str "ClojureScriptError: "(:compile-message state))
              :color "red"
              :image "img/error.png"})
            
            (if (eval-ok? state)
              (log-line {
                :text  (value-str (:eval-result state))
                :color "blue"})  
              
              (log-line {
                :text  (:eval-message state)
                :color "red"
                :image "img/error.png"})))))))

(defn log [state owner]
  (reify 
    om/IRender
      (render [_]
        (apply dom/div nil
          (om/build-all log-entry (:log state))))))

(defn input [state owner]
  (let [reference "statement"]
    (reify
      om/IWillMount
        (will-mount [_]
          (om/set-state! owner :kill 
            [                        
              (util/<channel owner :input 
                #(set! (.-value (om/get-node owner reference)) (str %)))

              (util/<channel owner :focus
                #(when-let [input (om/get-node owner reference)]
                  (.focus input)))
            ]))
      
      om/IWillUnmount
        (will-unmount [_]
          (doseq [kill (om/get-state owner :kill)]                      
            (async/put! kill true)))
      
      om/IRender
        (render [_]
          (dom/div 
            #js {
              :style #js {
                :padding "0px"
                :padding-top "3px"
                :margin "0px"
                :height "20px"
                :width "100%" 
                :clear "both"}}
            
            (prompt {:image "img/prompt.png"})
            
            (dom/input 
              #js {
              :type "text"
              :spellCheck "false"
              :ref reference
              :onKeyDown #(let [key (.-which %)] 
                              (case key
                                13
                                  (let [statement (.-value (om/get-node owner reference)) ns (get-in @state [:tab-info :ns])]
                                    (when-not (clojure.string/blank? statement)
                                      (util/>channel owner :execute {:clj-statement statement :ns ns})))

                                38
                                 (util/>channel owner :history {:direction :backward})

                                40
                                 (util/>channel owner :history {:direction :forward}) nil))
                                
              :style #js {
                :clear "both"
                :border "none"
                :margin "0"
                :padding "0"
                :height "100%"
                :width "90%"
                :outline "0" }}))))))

(defn no-agent [state owner]
  (reify
    om/IInitState
    (init-state [_]
      {:visible false})
    
    om/IWillMount
    (will-mount [_]
      (go
        (<! (async/timeout (:no-agent-delay @state)))
        (when (.isMounted owner)
          (om/set-state! owner :visible true))))
    
    om/IRenderState
    (render-state [_ {:keys [visible]}]
      (let [url (get-in state [:tab-info :url])]
        (dom/div #js {
           :style #js {
            :height "100%"
            :color    "blue"       
            :padding-left  "15px"
            :padding-top "5px"}}
      
            (if visible
              (dom/p nil
                "Click here to "
                (dom/a #js {:style #js {:font-weight "bold"} :href "#" 
                  :onClick #(util/>channel owner :inject-agent {:save-auto false})} "inject the agent code once")
                
                ", or here to "
                (dom/a #js {:style #js {:font-weight "bold"} :href "#"
                  :onClick #(util/>channel owner :inject-agent {:save-auto true :url url})} "automatically inject it every time for this url.")) 
              (dom/p #js{:style #js {:color "grey"}} "Waiting...")))))))

(defn repl [state owner]
  (reify
    om/IDidUpdate
      (did-update [this prev-props prev-state]
        (when-let [div (om/get-node owner :input-and-log)]
          (set! (.-scrollTop div) (.-scrollHeight div))))
    
    om/IRender
    (render [_]
      (dom/div nil
        (om/build toolbar (:tab-info state))
        
        (dom/div #js {:ref :input-and-log :style #js {:position "absolute" :overflow "scroll" :top "25px" :bottom "0px" :right 0 :left 0}}
          (om/build log state)
          (om/build input state))))))
