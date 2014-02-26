(ns cdtrepl.eval
	(:require 
	    [reagent.core :as reagent :refer [atom]]
		[clojure.walk :as cw]
		[khroma.devtools :as devtools]
		[khroma.log :as log]
		[khroma.util :as kutil]
		[cdtrepl.comp :as comp]
		[cljs.core.async :as async])

 	(:require-macros 
 		[cljs.core.async.macros :refer [go alt! go-loop]]))

(defn wrap-statement [statement]
	(str "(function () {" 
		"if(!this.hasOwnProperty('cljs') || !this.cljs.hasOwnProperty('core')) "
			"throw new Error('Inspected window does not contain unoptimized ClojureScript core namespace');"
		"var result = " statement ";"
		"return String(result);"
		"})()"))

(defn ok-result [request result] 
	(assoc request :eval-status "ok" :eval-result result))

(defn error-result [request message] 
	(assoc request :eval-status "error" :eval-message message))

(defn js-eval! [js-statement & {:keys [ignore-exception?]}]
	(try 
		(js/eval 
			js-statement)

		(catch js/Object e
			(if ignore-exception? nil (throw e)))))


(defn apply-eval [eval-fn request]
	(eval-fn 
		(wrap-statement (:js-statement request))
		:ignore-exception? (:ignore-exception? request)))

(defn js-evaluator [in-ch]
	(let [out-ch (async/chan)]
		(go-loop  [request (<! in-ch)]
			(log/debug "js evaluator < " request)

			(try
				(let [result (apply-eval js-eval! request)]
					(>! out-ch (ok-result request (js->clj result))))
		
				(catch js/Object e
					(>! out-ch
						(error-result request (str e)))))

			(recur (<! in-ch)))

		out-ch))

(defn cdt-evaluator [in-ch]
	(let [out-ch (async/chan)]
		(go-loop [request (<! in-ch)]
			(log/debug "cdt evaluator < " request)

			(let [result (<! (apply-eval devtools/inspected-eval! request))]
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
	(if devtools/available? cdt-evaluator js-evaluator))

