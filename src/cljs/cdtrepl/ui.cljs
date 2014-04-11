(ns cdtrepl.ui
  (:require 
      [reagent.core :as reagent :refer [atom]]
      [cdtrepl.ui-settings :as settings]
      [khroma.runtime :as kruntime]
      [khroma.log :as log]
      [khroma.util :as util]))



(defn toolbar-div [{:keys [on-reset tab] :as model}]
  [:div
    {
      :id "toolbar"
      :style {
        :height "23px"
        :width  "100%"
        :margin "0px"
        :border-bottom "1px solid #DDD"

      }
    }

    [:div 
      {
        :id "clear"
        :style {
          :height "100%"
          :width "20px"
          :background-image "url(img/clear_grey.png)"
          :background-position "center"
          :background-repeat "no-repeat"
          :float "left"
        }

        :on-click on-reset
      }
    ]

    [:div
       {
          :style {
            :padding-top "4px"
            :margin-left "15px" 
            :float "left"
            :color "grey"
          }
       }  

       (str "<ns: " @(:ns tab) ">")
    ]
    
 
    
    [:div
      {
        :style {
          :float "right"
          :color "grey"
          :padding-top "4px"
          :margin-right "5px"        
        }
      }    
     
      (str "v. " (if kruntime/available? 
        (@kruntime/manifest "version") "x.x"))
    ]
  ]
)

(defn prompt-div [{:keys [image]}]
  [:div
    {
      :class "prompt-div"
      :style 
        (merge 
          {
            :height "20px"
            :width "20px"
            :padding-top "0px"
            :float "left"
            :clear "both"; 
          }

          (if image
            {
              :background-image (str "url(" image ")")
              :background-position "center"
              :background-repeat "no-repeat"
            }
          )
        )
    }
  ]
)

(defn input-div [{:keys [statement on-execute on-history-backward on-history-forward tab-info]}]
  [:div
    {
      :id "input"
      :class "input-div"
      :style {
        :padding "0px"
        :padding-top "3px"
        :margin "0px"
        :height "20px"
        :width "100%" 
        :clear "both"
      }
    }

    [prompt-div {:image "img/prompt.png"}]

        [:input
          {
            :type "text"
            :style {
              :clear "both"
              :border "none"
              :margin "0"
              :padding "0"
              :height "100%"
              :width "90%"
              :outline "0"
            }

            :spellCheck "false"

            :value @statement

            :on-change #(reset! statement (-> % .-target .-value))
            :on-key-down #(let [key (.-which %)]
                    (case key
                      13
                       (on-execute)

                      38
                       (on-history-backward)

                      40
                       (on-history-forward)


                      nil))
          }
        ]
        
    
  ]
)

(defn log-sub-line [{:keys [color text image]}]
  [:div
    {
      :class "log-sub-line"
     
      :style {
        :clear "both"
        :min-height "20px"
      }
    }
    
    [prompt-div {
        :image image
      }
    ]

    [:div 
      {
        :style {
          :padding-top "4px"
          :margin-left "20px"
          :color color
        }
      }

      text
    ]
  ]
)

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

(defn log-entry [entry]
  [:div
    {
      :style {
        :border-bottom "1px solid #EEE"
        :width "100%"
      }
    }

    [log-sub-line 
      {
        :text  (:clj-statement entry)
        :color "#367cf1"
        :image "img/prompt_log.png" 
      }
    ]
    

    (if-not (compile-ok? entry)
      [log-sub-line 
      {
        :text  (str "ClojureScriptError: "(:compile-message entry))
        :color "red"
        :image "img/error.png"
        
      }
      ]
    )
 
    (if (compile-ok? entry)
      (if (eval-ok? entry)
        [log-sub-line 
        {
          :text  (value-str (:eval-result entry))
          :color "blue"
        }]

        [log-sub-line 
        {
          :text  (:eval-message entry)
          :color "red"
          :image "img/error.png"
        }]
      )
    )
    
  ]
)

(defn log-div [{:keys [entries]}]
  [:div
    (for [entry (:items @entries)]
      [log-entry entry])])

(defn no-agent [tab]
  [:div
    {
     :style {
      :color    "blue"       
      :padding-left  "15px"
      :padding-top "5px"
      }
    }
    
    [:p
     
        "Loaded page does not contain required instrumentation code. Click here to "
      [:a {:style {:font-weight "bold"} :href "#" :on-click (:on-inject-agent tab)} "inject it once"]
      ", or here to "
      [:a {:style {:font-weight "bold"} :href "#" :on-click (:on-inject-agent-auto tab)} "automatically inject"]
      
      " it every time for "
      [:span {:style {:color "grey" :font-style "italic"}} @(:url tab)]
    ]
  ]        
)

(defn repl [model]
  [:div
    [:div
      [toolbar-div (assoc (:toolbar model) :tab (:tab model))]
        [:div 
          {
            :style {
              :overflow-y "scroll"
              :width "100%"
              :position "absolute"
              :top "35px"
              :bottom "0px"
            }
          }
         
          [log-div   (:log model)] 
          [input-div (:input model)] 
        ]
    ]
  ]
)

(defn root-div [model]
  (fn []
    (if-let [ti @(get-in model [:tab :info])]      
      (repl model)
      (if-not @(:progress model)
        (no-agent (:tab model))
        [:div {:style {:padding "5" :color "grey"}} "Waiting..."] 
      )
    )
  )
)
 



