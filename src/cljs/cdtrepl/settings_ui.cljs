(ns cdtrepl.settings-ui
  (:require 
            [reagent.core :as reagent :refer [atom]]
            [goog.array :as g-array]
            [cdtrepl.settings :as settings])   
    
  (:require-macros 
    [cljs.core.async.macros :refer [go alt! go-loop]]))

(defn selected-urls [selected-options]
  (map #(.-value %)      
    (js->clj
      (g-array/toArray selected-options))))


(defn service-uri [{:keys [service-url  original]}]
  (let [input (atom "input")]    
    [:div {:style {:clear "both" :padding-left "5px" :padding-top "10px" :color "#888"}}
      [:div "Service URL"]
      [:input {:type "text" 
               :value @input
               :on-change #(reset! input (-> % .-target .-value))
               
               :style {:outline "none" :width "400px" :border "1px solid #888"  :font-size "10pt !important"}
               }]
      [:div
        [:a {:href "#" :on-click #(reset! input original)} "Reset"]
        [:a {:href "#" :on-click #(reset! service-url @input)} "Save"]
      ]
    ]
  )
)

(defn settings-div [{:keys [on-settings-hide model]}]
    [:div { :style {
          :width "100%"
          :height "100%"  
          :padding "0px"}
      } 
      
      [:form
        [:input {:type "text" 
                 :value "initial"
                 :on-change #(if 0 1 1)}]]
      
      
      [:div {:style {:float "left" :margin-top "0px" :padding-left "5px" :font-size "18pt !important" :width "100px;" :color "#888"}} "Settings"]
      [:div {:style {
            :margin-top "5px"
            :padding-right "5px"
            :width "16px"
            :height "16px"
            :background "url(img/close.png) no-repeat center center"
            :float "right" 
            :margin-right "3px"  
            :cursor "pointer"}
          
          :on-click #(go (>! on-settings-hide {}))
          :title "Close"}]       
      
      (let [original @(:service-url model)]
        [service-uri (assoc model :input (atom original) :original original)])
      
      (let [selected (reagent/atom [])]
        [:div {:style {:clear "both" :padding-left "5px" :padding-top "10px" :color "#888" :padding-right "0px" }}
          [:div "Auto Inject"]
          
          [:select {:name "autourls"  :multiple "true" :size "5" 
                    :style {:outline "none" :border "1px solid #888" :font-size "10pt !important" :width "404px"}
                    :on-change #(reset! selected (selected-urls (-> % .-target .-selectedOptions)))}
            (for [url (sort @(:auto-inject model))]
              [:option {:key (gensym) :value url :title url} url])
          ]
          
          [:div
            [:button
             {:on-click #(settings/remove-auto-inject! @selected)}
             "Remove"
            ]
          ]
        ]
      )
  ]
)
