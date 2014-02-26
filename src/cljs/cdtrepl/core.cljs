(ns cdtrepl.core
	(:require 
	    [reagent.core :as reagent :refer [atom]]
	    [cdtrepl.ui :as ui]
	    [cdtrepl.eval :as eval]
	    [cdtrepl.comp :as comp]
	    [khroma.devtools :as devtools]
	    [cljs.core.async :as async]
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

(defn immigrate-expression [from-ns to-ns]
	(str "(function () { for(prop in " from-ns ") " to-ns "[prop] = " from-ns  "[prop]; })();"))

(defn immigrate! [from-ns to-ns]
	(eval/eval!
		(immigrate-expression from-ns to-ns)))

(defn create-ns! [ns-name immigrate?]
	(eval/eval!
		(str "goog.provide('" ns-name "'); goog.require('cljs.core'); " (if immigrate? (immigrate-expression "cljs.core" ns-name)))
		:ignore-exception? true))


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
		:out-channel  (let [chan (async/chan)]
						(go-loop [result (<! chan)]
							(append-keyed-entry! (-> page-model :log :entries) result)
							(reset! (-> page-model :input :statement) "")


							(when (:ns-change result)
								(immigrate! "cljs.core" (:response-ns result)) 
								(reset! (:ns page-model) (:response-ns result))) 

							(recur (<! chan)))

						chan)

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
											(:in-channel page-model) {:clj-statement statement :ns @(:ns page-model)}))))



			:on-history-backward #(history-step (:input page-model) compute-idx-backward)
			:on-history-forward #(history-step (:input page-model) compute-idx-forward)
		}

		:ns (atom "cljs.user")
	} 
) 


(defn exception-supressor [in-ch]
	(async/map< 
		(fn [request]
			(assoc request :ignore-exception? (:ns-change request))) in-ch))

(defn setup-routing [in out err]
	(-> in
  		(comp/compiler)
  		(comp/divert-errors err)
  		(exception-supressor)
  		(eval/evaluator)	
  		(async/pipe out)))

(defn ^:export run []
	(setup-routing 
		(:in-channel page-model) (:out-channel page-model) (:out-channel page-model))

  	(create-ns! @(:ns page-model) true)

  	(reagent/render-component [(ui/root-div page-model)]
    	(.-body js/document)))
