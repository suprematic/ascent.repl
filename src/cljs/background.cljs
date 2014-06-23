(ns background
	(:require
		[cljs.core.async :as async]
		[khroma.log :as log]
		[goog.string :as gstring]
		[clojure.string :as s])
	(:require-macros
		[cljs.core.async.macros :refer [go go-loop]]))

(defn format [fmt & args]
  (apply gstring/format fmt args))

(def debug
	(atom true))

(def tab-infos
	(atom {}))

(def connections
	(atom {}))

(defn- put-in-map [map-atom string-key value]
	(swap! map-atom assoc (keyword string-key) value))

(defn- remove-from-map [map-atom string-key]
	(swap! map-atom dissoc (keyword string-key)))

(defn- get-from-map [map-atom string-key]
	((keyword string-key) @map-atom))

(defn set-port [destination tab-id port]
	{:pre [(string? tab-id), (string? destination)]}
	(if-not ((keyword destination) connections)
		(put-in-map connections tab-id (assoc port (keyword tab-id) tab-id))))

(defn set-tab-info [tab-id info]
	{:pre [(string? tab-id)]}
	(put-in-map tab-infos tab-id info))

(defn get-tab-info [tab-id]
	{:pre [(string? tab-id)]}
	(get-from-map tab-infos tab-id))

(defn remove-tab-info [tab-id]
	{:pre [(string? tab-id)]}
	(remove-from-map tab-infos tab-id))

(defn get-port [destination tab-id]
	{:pre [(string? tab-id), (string? destination)]}
	(if-let [for-destination (get-from-map connections destination)]
		(get-from-map for-destination tab-id)
		nil))

(defn remove-port [destination tab-id]
	{:pre [(string? tab-id), (string? destination)]}
	(put-in-map connections destination (dissoc (get-from-map destination connections) tab-id)))

(defn set-local-port [destination handler]
	{:pre [(string? destination)]}
	(set-port "background" destination {:post-message handler :tab-id "*"}))

(defn get-local-port [destination]
	{:pre [(string? destination)]}
	(get-port "background", destination))

(defn connect [connection destination tab-id]
	(set-port destination tab-id connection)
	(let [local-on-message (fn [message sender send-response]
		(if-let [port (fn []
			(if (= "background" (:destination message))
				(get-local-port (:type message))
				(get-port (:destination message) tab-id)))]
			(.postMessage port (assoc message :source destination :source-tab-id tab-id))
			(if (@debug) (if ("log" not= (:type message)) (log/debug (format "message %s:%s -> /dev/null" destination tab-id message)))))) on-disconnect (fn [connection]
		(do
			(if (@debug) (log/debug (format "desconnecting %s:%s" destination tab-id)))
			(.removeListener js/connection.onMessage local-on-message)
			(remove-port destination tab-id)))]
		(.addListener js/connection.onMessage local-on-message)
		(.addListener js/connection.onDisconnect on-disconnect)))


(defn boot []
	(log/debug "Just for boot checking..."))

(defn init []
	(boot)

	(.addListener js/chrome.runtime.onConnect
		(fn [connection]
			(let [parts (s/split (:name connection) ":")]
				(if (= 2 (count parts))
					(do
						(if (@debug) (log/debug (format "incoming connection from %s" (:name connection))))
						(connect connection (get parts 0) (get parts 1))
						(if-let [tab-info (get-tab-info (get parts 1))]
							(.postMessage connection {:type "tab-info" :info tab-info})))))))

	(set-local-port "log"
		(fn [message]
			(if (@debug) (log/debug "*** " (:text message)))))

	(set-local-port "inject-agent"
		(fn [message]
			(if (@debug) (log/debug "agent injection requested: " (:text message)))
			(.executeScript js/chrome.tabs (:tab-id message) {:file "js/injected.js"}
				(fn [result]
					(if (@debug) (log/debug (format "connecting to tab:%s" (:tab-id message))))
					(connect (.connect js/chrome.tabs (:tab-id message) {:name (str "background:" (:tab-id message))}) "tab" (str (:tab-id message)))))))

	(set-local-port "tab-info"
		(fn [message]
			(if (@debug) (log/debug "background page received tab-info: " (:text message)))
			(if (= "tab" (:source message))
				(if-let [tab-info (get-tab-info (:source-tab-id message))]
					(if-let [port (get-port "repl" (:source-tab-id message))]
						(let [out {:type "tab-info" :info (assoc tab-info :agent-info (:agent-info message))}]
							(if (@debug)
								(do
									(log/debug "sending tab-info to repl:" (:source-tab-id message) out)
									(log/debug "port:" port)))
							(.postMessage port out))
						(log/warn (format "cannot find port repl:%s" (:source-tab-id message))))
					(log/warn "no tab-info found for tab-info message: " (:text message))))))

	(.addListener js/chrome.tabs.onUpdated
		(fn [tab-id change-info tab]
			(if (= "complete" (:status change-info))
				(set-tab-info (str tab-id) {:agent-info nil :url (:url tab)})
				(when (get-port "repl" (str tab-id))
					(if (@debug) (log/debug (format "to repl:%s" (str tab-id) (get-tab-info (str tab-id)))))
					(.postMessage (get-port "repl" (str tab-id)) {:type "tab-info" :info (get-tab-info (str tab-id))})))))

	(.addListener js/chrome.tabs.onRemoved
		(fn [tab-id remove-info]
			(remove-tab-info (str tab-id))))

	(.addListener js/chrome.tabs.onReplaced
		(fn [added-tab-id removed-tab-id]
			(remove-tab-info (str added-tab-id)))))