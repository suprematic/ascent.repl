(ns cdtrepl.history)

(defn last-index [history]
  (let [size (count history)]
    (if (> size 0)
      (- size 1))))


(defn append [history statement]
  (let [ix (last-index history)]
    (when (or (nil? ix) (not= statement (nth history ix)))
      (conj history statement))))


(defn backward [history idx]
  (cond
    (nil? idx)
      (last-index history)

    (= 0 idx)
      0
      
    :else 
        (- idx 1)))
 
(defn forward [history idx]
  (cond
    (nil? idx) 
      nil
      
    (= idx (last-index history))
      idx

    :else
        (+ idx 1)))





