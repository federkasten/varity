(ns varity.hgvs-to-vcf.cdna
  (:require [clj-hgvs.mutation :as mut]
            [cljam.fasta :as fa]
            [varity.ref-gene :as rg]
            [varity.util :refer [revcomp-bases]])
  (:import [clj_hgvs.mutation DNASubstitution]))

(defn- vcf-variant
  [fa-rdr {:keys [chrom strand] :as rg} mut*]
  (if (instance? DNASubstitution mut*)
    (if-let [pos (rg/cds-coord->genomic-pos (:coord mut*) rg)]
      {:chr chrom
       :pos pos
       :ref (cond-> (:ref mut*)
              (= strand "-") (revcomp-bases))
       :alt (cond-> (:alt mut*)
              (= strand "-") (revcomp-bases))})
    (throw (IllegalArgumentException. "Unsupported mutation"))))

(defn rg->vcf-variant
  [fa-rdr rg hgvs]
  (let [mut* (first (:mutations hgvs))]
    (vcf-variant fa-rdr rg mut*)))
