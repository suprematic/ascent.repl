(ns khroma.runtime
)


(def available?
  (not (nil? js/chrome.runtime)))

(def manifest
  (delay 
    (js->clj 
      (.getManifest js/chrome.runtime))))
