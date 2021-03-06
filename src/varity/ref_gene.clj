(ns varity.ref-gene
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-hgvs.coordinate :as coord]
            [cljam.util.chromosome :refer [normalize-chromosome-key]]
            [proton.core :refer [as-long]])
  (:import [org.apache.commons.compress.compressors
            CompressorStreamFactory CompressorException]))

;;; Utility

(defn in-cds?
  [pos {:keys [cds-start cds-end]}]
  (<= cds-start pos cds-end))

(defn in-exon?
  [pos {:keys [exon-ranges]}]
  (->> exon-ranges
       (some (fn [[s e]] (<= s pos e)))
       true?))

;;; Parser

(defn- parse-exon-pos
  [s]
  (map as-long (string/split s #",")))

(defn- exon-ranges
  [starts ends]
  (->> (map vector starts ends)
       (sort-by first)
       vec))

(defn- parse-ref-gene-line
  [s]
  (as-> (zipmap [:bin :name :chr :strand :tx-start :tx-end :cds-start :cds-end
                 :exon-count :exon-start :exon-end :score :name2 :cds-start-stat
                 :cds-end-stat :exon-frames]
                (string/split s #"\t")) m
        (update m :bin as-long)
        (update m :chr normalize-chromosome-key)
        (update m :tx-start (comp inc as-long))
        (update m :tx-end as-long)
        (update m :cds-start (comp inc as-long))
        (update m :cds-end as-long)
        (update m :exon-count as-long)
        (update m :exon-start #(map inc (parse-exon-pos %)))
        (update m :exon-end parse-exon-pos)
        (assoc m :exon-ranges (exon-ranges (:exon-start m) (:exon-end m)))
        (dissoc m :exon-start :exon-end)
        (update m :cds-start-stat keyword)
        (update m :cds-end-stat keyword)))

(defn load-ref-genes
  "Loads f (e.g. refGene.txt(.gz)), returning the all contents as a sequence."
  [f]
  (with-open [^java.io.Reader rdr (try (-> (CompressorStreamFactory.)
                                           (.createCompressorInputStream (io/input-stream f))
                                           io/reader)
                                       (catch CompressorException _ (io/reader f)))]
    (->> (line-seq rdr)
         (map parse-ref-gene-line)
         doall)))

;; Indexing
;; --------

(defn- round-int
  "e.g. (round-int 138 100) => 100"
  [a b]
  (- a (mod a b)))

(def ^:private pos-index-block 1000000)

(defn- locus-index
  [rgs]
  (->> (group-by :chr rgs)
       (map (fn [[chr sub-rgs]]
              (let [fs (round-int (apply min (map :tx-start sub-rgs))
                                  pos-index-block)
                    le (round-int (apply max (map :tx-end sub-rgs))
                                  pos-index-block)]
                [chr (loop [s fs, ret {}]
                       (if (<= s le)
                         (let [e (+ s pos-index-block)
                               rgs* (filter (fn [{:keys [tx-start tx-end]}]
                                              (and (<= tx-start e) (<= s tx-end)))
                                            sub-rgs)]
                           (recur e (assoc ret [s e] rgs*)))
                         ret))])))
       (into {})))

(defn- ref-seq-index
  [rgs]
  (group-by :name rgs))

(defn- gene-index
  [rgs]
  (group-by :name2 rgs))

(defrecord RefGeneIndex [locus ref-seq gene])

(defn index
  "Creates refGene index for search."
  [rgs]
  (RefGeneIndex. (locus-index rgs)
                 (ref-seq-index rgs)
                 (gene-index rgs)))

(defn ref-genes
  "Searches for refGene entries with ref-seq, gene or (chr, pos) using index,
  returning results as sequence. See also varity.ref-gene/index."
  ([s rgidx]
   (get-in rgidx (if (re-find #"^(NC|LRG|NG|NM|NR|NP)_" s)
                   [:ref-seq s]
                   [:gene s])))
  ([chr pos rgidx]
   (let [pos-r (round-int pos pos-index-block)]
     (->> (get-in rgidx [:locus
                         (normalize-chromosome-key chr)
                         [pos-r (+ pos-r pos-index-block)]])
          (filter (fn [{:keys [tx-start tx-end]}]
                    (<= tx-start pos tx-end)))))))

(defn in-any-exon?
  "Returns true if chr:pos is located in any ref-gene exon, else false."
  [chr pos rgidx]
  (->> (ref-genes chr pos rgidx)
       (some #(in-exon? pos %))
       (true?)))

;;; Calculation of CDS coordinate
;;;
;;; cf. http://varnomen.hgvs.org/bg-material/numbering/

(defn- nearest-edge-and-offset
  [pos {:keys [strand exon-ranges]}]
  (->> exon-ranges
       (map (fn [[s e]] [[s (- pos s)] [e (- pos e)]]))
       (apply concat)
       (sort-by (fn [[e ^long o]]
                  [(Math/abs o) (case strand
                                  "+" e
                                  "-" (- e))]))
       first))

(defn- exon-pos
  [pos strand exon-ranges]
  (->> exon-ranges
       (map (fn [[s e]]
              (case strand
                "+" (max (min (inc (- pos s)) (inc (- e s))) 0)
                "-" (max (min (inc (- e pos)) (inc (- e s))) 0))))
       (reduce +)))

(defn cds-pos
  [pos {:keys [strand cds-start cds-end exon-ranges]}]
  {:pre [(<= (ffirst exon-ranges) pos (second (last exon-ranges)))]}
  (let [pos* (exon-pos pos strand exon-ranges)
        cds-start* (exon-pos cds-start strand exon-ranges)
        cds-end* (exon-pos cds-end strand exon-ranges)
        [start* end*] (cond-> [cds-start* cds-end*]
                        (= strand "-") reverse)]
    (cond
      (< pos* start*) [(- start* pos*) :upstream]
      (< end* pos*) [(- pos* end*) :downstream]
      :else [(inc (- pos* start*)) nil])))

(defn cds-coord
  [pos rg]
  (let [[pos* offset] (if (in-exon? pos rg)
                        [pos 0]
                        (nearest-edge-and-offset pos rg))
        [cds-pos* region] (cds-pos pos* rg)]
    (coord/cdna-coordinate cds-pos*
                           (case (:strand rg)
                             "+" offset
                             "-" (- offset))
                           region)))

;;; Calculation of genomic coordinate

(defn cds->genomic-pos
  ([cds-pos rg] (cds->genomic-pos cds-pos nil rg))
  ([cds-pos region {:keys [strand cds-start cds-end exon-ranges]}]
   (let [exon-poss (mapcat (fn [[s e]] (range s (inc e))) exon-ranges)
         cds-poss (cond-> (filter #(<= cds-start % cds-end) exon-poss)
                    (= strand "-") reverse)
         upstream-poss (case strand
                         "+" (filter #(< % cds-start) exon-poss)
                         "-" (reverse (filter #(< cds-end %) exon-poss)))
         downstream-poss (case strand
                           "+" (filter #(< cds-end %) exon-poss)
                           "-" (reverse (filter #(< % cds-start) exon-poss)))]
     (case region
       nil (nth cds-poss (dec cds-pos) nil)
       :upstream (nth (reverse upstream-poss) (dec cds-pos) nil)
       :downstream (nth downstream-poss (dec cds-pos) nil)))))

(defn cds-coord->genomic-pos
  [coord {:keys [strand] :as rg}]
  (if-let [base-pos (cds->genomic-pos (:position coord) (:region coord) rg)]
    (+ base-pos (cond-> (:offset coord) (= strand "-") (-)))))
