(ns varity.hgvs-to-vcf.cdna
  (:require clj-hgvs.mutation
            [cljam.io.sequence :as cseq]
            [varity.ref-gene :as rg]
            [varity.util :refer [revcomp-bases]]))

;; AGT => ["AGT"]
;; AGTAGT => ["AGT" "AGTAGT"]
(defn- repeat-units
  [s]
  (->> (range (count s))
       (map inc)
       (map #(partition-all % s))
       (filter #(apply = %))
       (map first)
       (mapv #(apply str %))))

(defmulti vcf-variant (fn [mut* seq-rdr rg] (class mut*)))

(defmethod vcf-variant clj_hgvs.mutation.DNASubstitution
  [mut* _ {:keys [chr strand] :as rg}]
  (if-let [pos (rg/cds-coord->genomic-pos (:coord mut*) rg)]
    {:chr chr
     :pos pos
     :ref (cond-> (:ref mut*)
            (= strand "-") (revcomp-bases))
     :alt (cond-> (:alt mut*)
            (= strand "-") (revcomp-bases))}))

(defmethod vcf-variant clj_hgvs.mutation.DNADeletion
  [mut* seq-rdr {:keys [chr strand] :as rg}]
  (let [coord-end (or (:coord-end mut*) (:coord-start mut*))
        start (rg/cds-coord->genomic-pos (case strand
                                           "+" (:coord-start mut*)
                                           "-" coord-end)
                                         rg)
        end (rg/cds-coord->genomic-pos (case strand
                                         "+" coord-end
                                         "-" (:coord-start mut*))
                                       rg)]
    (if (and start end)
      (let [ref (cseq/read-sequence seq-rdr {:chr chr, :start (dec start), :end end})]
        {:chr chr
         :pos (dec start)
         :ref ref
         :alt (subs ref 0 1)}))))

(defmethod vcf-variant clj_hgvs.mutation.DNADuplication
  [mut* seq-rdr {:keys [chr strand] :as rg}]
  (let [coord-end (or (:coord-end mut*) (:coord-start mut*))
        start (rg/cds-coord->genomic-pos (case strand
                                           "+" (:coord-start mut*)
                                           "-" coord-end)
                                         rg)
        end (rg/cds-coord->genomic-pos (case strand
                                         "+" coord-end
                                         "-" (:coord-start mut*))
                                       rg)]
    (if (and start end)
      (let [dup (cseq/read-sequence seq-rdr {:chr chr, :start start, :end end})
            base (case strand
                   "+" (subs dup (dec (count dup)))
                   "-" (cseq/read-sequence seq-rdr {:chr chr, :start (dec start), :end (dec start)}))]
        {:chr chr
         :pos (case strand
                "+" end
                "-" (dec start))
         :ref base
         :alt (str base dup)}))))

(defmethod vcf-variant clj_hgvs.mutation.DNAInsertion
  [mut* seq-rdr {:keys [chr strand] :as rg}]
  (if-let [start (rg/cds-coord->genomic-pos (case strand
                                              "+" (:coord-start mut*)
                                              "-" (:coord-end mut*))
                                            rg)]
    (let [ref (cseq/read-sequence seq-rdr {:chr chr, :start start, :end start})]
      {:chr chr
       :pos start
       :ref ref
       :alt (str ref (cond-> (:alt mut*)
                       (= strand "-") (revcomp-bases)))})))

(defmethod vcf-variant clj_hgvs.mutation.DNAInversion
  [mut* seq-rdr {:keys [chr strand] :as rg}]
  (let [start (rg/cds-coord->genomic-pos (case strand
                                           "+" (:coord-start mut*)
                                           "-" (:coord-end mut*))
                                         rg)
        end (rg/cds-coord->genomic-pos (case strand
                                         "+" (:coord-end mut*)
                                         "-" (:coord-start mut*))
                                       rg)]
    (if (and start end)
      (let [ref (cseq/read-sequence seq-rdr {:chr chr, :start (dec start), :end end})]
        {:chr chr
         :pos (dec start)
         :ref ref
         :alt (str (first ref) (revcomp-bases (subs ref 1)))}))))

(defmethod vcf-variant clj_hgvs.mutation.DNAIndel
  [mut* seq-rdr {:keys [chr strand] :as rg}]
  (let [coord-end (or (:coord-end mut*) (:coord-start mut*))
        start (rg/cds-coord->genomic-pos (case strand
                                           "+" (:coord-start mut*)
                                           "-" coord-end)
                                         rg)
        end (rg/cds-coord->genomic-pos (case strand
                                         "+" coord-end
                                         "-" (:coord-start mut*))
                                       rg)]
    (if (and start end)
      (let [ref (cseq/read-sequence seq-rdr {:chr chr, :start (dec start), :end end})]
        (if (or (nil? (:ref mut*))
                (= (subs ref 1) (cond-> (:ref mut*)
                                  (= strand "-") (revcomp-bases))))
          {:chr chr
           :pos (dec start)
           :ref ref
           :alt (str (first ref) (cond-> (:alt mut*)
                                   (= strand "-") (revcomp-bases)))})))))

(defmethod vcf-variant clj_hgvs.mutation.DNARepeatedSeqs
  [mut* seq-rdr {:keys [chr strand] :as rg}]
  (let [start* (rg/cds-coord->genomic-pos (:coord-start mut*) rg)
        end* (cond
               (:coord-end mut*) (rg/cds-coord->genomic-pos (:coord-end mut*) rg)
               (:ref mut*) (dec (+ start* (count (:ref mut*))))
               :else start*)]
    (if (and start* end*)
      (let [[start end] (cond-> [start* end*] (= strand "-") reverse)
            dup (cseq/read-sequence seq-rdr {:chr chr, :start start, :end end})]
        (if (= (count (repeat-units dup)) 1)
          (let [base (case strand
                       "+" (subs dup (dec (count dup)))
                       "-" (cseq/read-sequence seq-rdr {:chr chr, :start (dec start), :end (dec start)}))
                nunit (inc (- end start))
                rep (case strand
                      "+" (cseq/read-sequence seq-rdr {:chr chr, :start start, :end (dec (+ start (* nunit (:ncopy mut*))))})
                      "-" (cseq/read-sequence seq-rdr {:chr chr, :start (inc (- start (* nunit (:ncopy mut*)))), :end end}))
                m (case strand
                    "+" (count (filter #(= (apply str %) dup) (partition nunit rep)))
                    "-" (count (filter #(= % (reverse dup)) (partition nunit (reverse rep)))))]
            {:chr chr
             :pos (case strand
                    "+" end
                    "-" (dec start))
             :ref base
             :alt (str base (apply str (repeat (- (:ncopy mut*) m) dup)))}))))))

(defn ->vcf-variant
  [hgvs seq-rdr rg]
  (vcf-variant (:mutation hgvs) seq-rdr rg))
