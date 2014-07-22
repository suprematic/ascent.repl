(ns background
	(:require
		[cljs.core.async :as async]
		[clojure.walk :as walk]
		[khroma.log :as log]
		[khroma.tabs :as tabs]
		[khroma.runtime :as runtime]

		[clojure.string :as cstring])
	(:require-macros
		[cljs.core.async.macros :refer [go go-loop]]))

(def ^:const dst-log "log")
(def ^:const dst-inject-agent "inject-agent")
(def ^:const dst-tab-info "tab-info")
(def ^:const dst-background "background")

(def debug
	(atom true))

(def tab-infos
	(atom {}))

(def connections
	(atom {}))

(defn set-port! [destination tab-id port]
	(let [path [(str destination) (str tab-id)]]
		(swap! connections #(assoc-in % path port))))

(defn get-port [destination tab-id]
	(get-in @connections [(str destination) (str tab-id)]))

(defn remove-port! [destination tab-id]
	(swap! connections #(update-in % [(str destination)] dissoc (str tab-id))))

(defn set-local-port! [destination handler]
	(set-port! dst-background destination handler))

(defn get-local-port [destination]
	(get-port dst-background destination))

(defn set-tab-info! [tab-id info]
	(swap! tab-infos assoc (str tab-id) info))

(defn get-tab-info [tab-id]
	(@tab-infos (str tab-id)))

(defn remove-tab-info! [tab-id]
	(swap! tab-infos dissoc (str tab-id)))

(defn connect [port source tabId]
	(set-port! source tabId
		(fn [message]
			(log/debug "sending message to %s:%s:%s" source tabId message)

			(async/put! port message)))

	(go-loop []
		(if-let [message (<! port)]
			(let [message (walk/keywordize-keys message) {:keys [type destination]} message background? (= destination dst-background)]
				(log/debug "received message: %s;" " background: %s" message background?)

				(if-let [port-fn
					(if background?
						(get-local-port type)
						(get-port destination tabId))]

					(do
						(when (and @debug (not= type "log"))
							(log/debug "message %s:%s -> %s:%s" source tabId destination (if-not background? tabId "*")) message)
						(port-fn (assoc message :source source :sourceTabId tabId)))

					(log/debug "message %s:%s -> /dev/null" source tabId))
				(recur))

			(remove-port! source tabId))))

(defn init []
	(log/debug "Just for boot checking...")

	(let [ch (runtime/connections)]
		(go-loop []
			(when-let [connection (<! ch)]
				(let [connection-name (runtime/port-name connection) parts (cstring/split connection-name ":")]
					(when (= 2 (count parts))
						(let [destination (first parts) tabId (second parts)]
							(when @debug
								(log/debug (str "incoming connection from " connection-name)))

							(connect connection destination tabId)

							(when-let [tab-info (get-tab-info tabId)]
								(async/put! connection {:type "tab-info" :info tab-info})))))
				(recur))))

	(set-local-port! dst-log
		(fn [{:keys [text]} message]
			(when @debug
				(log/debug "*** %s" text))))

	(set-local-port! dst-inject-agent
		(fn [{:keys [tabId]} message]
			(when @debug
				(log/debug "agent injection requested for: %s" tabId))

			(let [inject-details (clj->js {:file "js/injected.js"})]
				(.executeScript js/chrome.tabs tabId inject-details
					(fn [result]
						(when @debug
							(log/debug "connecting to tab: %s" tabId))

						(let [connect-info (clj->js {:name (str "background:" tabId)})]
							(let [port (runtime/channel-from-port (.connect js/chrome.tabs tabId connect-info))]
								(connect port "tab" tabId))))))))

	(set-local-port! dst-tab-info
		(fn [{:keys [agentInfo source sourceTabId] :as message}]
			(when @debug
				(log/debug "background page received tab-info: " message))

			(when (= "tab" source)
				(if-let [tab-info (get-tab-info sourceTabId)]
					(if-let [port-fn (get-port "repl" sourceTabId)]
						(let [out {:type "tab-info" :info (assoc tab-info :agentInfo agentInfo)}]

							(when @debug
								(log/debug "sending tab-info to repl:" sourceTabId out))

							(port-fn out))
						(log/warn "cannot find port repl: %s" sourceTabId))
					(log/warn "no tab-info found for tab-info message: %s" message)))))

	(let [ch (tabs/tab-updated-events)]
		(go-loop []
			(when-let [{:keys [tabId changeInfo tab]} (<! ch)]
				(when (= "complete" (:status changeInfo))
					(when @debug
						(log/debug "tab updated: %s" (:url tab)))

					(set-tab-info! tabId {:agentInfo nil :url (:url tab)})

					(when-let [port-fn (get-port "repl" tabId)]
						(let [tab-info (get-tab-info tabId)]
							(when @debug
								(log/debug "to repl: %s" tabId tab-info))

							(port-fn {:type "tab-info" :info tab-info}))))
				(recur))))

	(let [ch (tabs/tab-removed-events)]
		(go-loop []
			(when-let [{:keys [tabId removeInfo]} (<! ch)]
				(remove-tab-info! tabId)
				(recur))))

	(let [ch (tabs/tab-replaced-events)]
		(go-loop []
			(when-let [{:keys [added removed]} (<! ch)]
				(remove-tab-info! removed)
				(recur)))))