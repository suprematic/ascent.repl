(ns cdtrepl.eval
  (:require 
    [reagent.core :as reagent :refer [atom]]
    [clojure.walk :as cw]
    [khroma.devtools :as devtools]
    [khroma.log :as log]
    [khroma.util :as kutil]
    [khroma.extension :as extension]
    [cdtrepl.comp :as comp]
    [cljs.core.async :as async])

  (:require-macros 
    [cljs.core.async.macros :refer [go alt! go-loop]]))

(defn wrap-statement [statement]
  (str "(function () {" 
    "var result = " statement ";"
    "return String(result);"
    "})()")) 

(defn ok-result [request result] 
  (assoc request :eval-status "ok" :eval-result result))

(defn error-result [request message] 
  (assoc request :eval-status "error" :eval-message message))

(defn js-eval! [js-statement & {:keys [ignore-exception?]}]
  (go
    (kutil/escape-nil
      (try 
        (js/eval 
          js-statement)

        (catch js/Object e
          (if ignore-exception? nil {"isException" true "value" (str e)}))))))


(defn apply-eval [eval-fn request]
  (eval-fn 
    (wrap-statement (:js-statement request))
    :ignore-exception? (:ignore-exception? request)))

(defn evaluator* [eval-fn in-ch]
  (let [out-ch (async/chan)]
    (go-loop [request (<! in-ch)]
      (log/debug "cdt evaluator < " request)

      (let [result (<! (apply-eval eval-fn request))]
        (let [result  (kutil/unescape-nil result)]
          (>! out-ch
            (if-not (devtools/eval-failed? result)
              (ok-result request result)
              (error-result request (devtools/eval-message result))))))

      (recur (<! in-ch)))

    out-ch))

(def eval!
  (if devtools/available? devtools/inspected-eval! js-eval!))

(def evaluator 
  (partial evaluator* eval!))

(defn eval-form! [form]
  (let [compiled (comp/compile {:clj-statement (str form) :ns "cljs.user"})]
    (go
       (let [compiled (<! compiled)]
        (log/debug "eval-form! - compiled: " compiled)    
        (when (= (:compile-status compiled) "ok")
          (eval! (:js-statement compiled)))))))
 
(defn _inject []
  (eval! (js/sprintf "document.head.appendChild(document.createElement('script'));")))



(defn immigrate-expression [from-ns to-ns]
  (str "(function () { for(prop in " from-ns ") " to-ns "[prop] = " from-ns  "[prop]; })();"))

(defn immigrate! [from-ns to-ns]
  (eval!
    (immigrate-expression from-ns to-ns)))

(defn create-ns! [ns-name immigrate?]
  (eval!
    (str " try { goog.provide('" ns-name "'); } catch(x) {};  goog.require('cljs.core'); " (if immigrate? (immigrate-expression "cljs.core" ns-name)))
    :ignore-exception? true))



(defn inject-script* [path & {:keys [id]}]
  (let [url (extension/get-url path)]
    (eval!    
      (str 
        "(function () { "
        "var script = document.createElement('script');"
        
        (if id 
          (str "script.setAttribute('id', '" id "');"))
        
        "script.setAttribute('src','" url "');"
        "document.head.appendChild(script);"
        "})();"
      ))))


(defn has-cljs-core? []
  (go 
    (let [result (kutil/unescape-nil (<! (eval! "(this.hasOwnProperty('cljs') && this.cljs.hasOwnProperty('core'))")))]
       result)))
 
(defn inject-cljs-core []
  (go
    (<! (inject-script* "js/compiled/goog/base.js"))
    (<! (inject-script* "js/injected_boot.js" :id "__injected_boot"))))

(defn inject-script [path]
  (let [url (extension/get-url path)]
    (eval-form! 
      `(let [script (.createElement js/document "script")]
        (.setAttribute script "src" ~url)
        (.appendChild js/document.head script)))))
      





