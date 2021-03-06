(ns varity.lift
  "Functions to convert a genome coordinate between assemblies."
  (:require [varity.chain :as ch]))

(defn- calc-diff
  [rpos data]
  (loop [[{:keys [^long size ^long dt ^long dq]} & r] data, s 0, d 0]
    (let [e (+ s size)]
      (if-not (< rpos s)
        (if (< rpos e)
          d
          (if (some? dt)
            (recur r (+ e dt) (+ d (- dq dt)))))))))

(defn- convert-coord*
  [pos {:keys [header data]}]
  (let [rpos (- pos (:t-start header))]
    (if-let [d (calc-diff rpos data)]
      (let [new-pos (+ pos d (- (:q-start header) (:t-start header)))]
        {:chr (:q-name header)
         :pos (if (= (:q-strand header) "-")
                (- (:q-size header) new-pos 1)
                new-pos)}))))

(defn convert-coord
  "Converts {:chr :pos} between different assemblies, returning a new
  {:chr :pos}. The last argument, chain, must be a path to
  srcToDest.over.chain(.gz) or chain index."
  [{:keys [chr pos]} chain]
  (let [chidx (if (map? chain)
                chain
                (ch/index (ch/load-chain chain)))]
    (some->> (ch/search-chains chr pos chidx)
             (sort-by (comp :score :header) >)
             first
             (convert-coord* pos))))
