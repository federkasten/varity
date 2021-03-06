(ns varity.vcf-to-hgvs-test
  (:require [clojure.test :refer :all]
            [clj-hgvs.core :as hgvs]
            [varity.ref-gene :as rg]
            [varity.vcf-to-hgvs :refer :all]
            [varity.t-common :refer :all]))

(defn- vcf-variant->cdna-hgvs-texts
  [variant seq-rdr rgidx]
  (map #(hgvs/format % {:show-bases? true
                        :range-format :coord})
       (vcf-variant->cdna-hgvs variant seq-rdr rgidx)))

(defslowtest vcf-variant->cdna-hgvs-test
  (cavia-testing "returns cDNA HGVS strings"
    (let [rgidx (rg/index (rg/load-ref-genes test-ref-gene-file))]
      (are [chr pos ref alt e]
          (= (vcf-variant->cdna-hgvs-texts {:chr chr, :pos pos, :ref ref, :alt alt}
                                           test-ref-seq-file rgidx) e)
        ;; Substitution
        "chr7" 55191822 "T" "G" '("NM_005228:c.2573T>G") ; cf. rs121434568 (+)
        "chr1" 11796321 "G" "A" '("NM_005957:c.665C>T") ; cf. rs1801133 (-)

        ;; Deletion
        "chr1" 963222 "GCG" "G" '("NM_198317:c.1157_1158delCG")
        "chr3" 116193879 "ACAC" "A" '("NM_001318915:c.156-107326_156-107324delGTG"
                                      "NM_002338:c.156-107326_156-107324delGTG") ; cf. rs17358

        ;; Duplication
        "chr2" 47806842 "T" "TGACT" '("NM_000179:c.4062_4065dupGACT"
                                      "NM_001281492:c.3672_3675dupGACT"
                                      "NM_001281493:c.3156_3159dupGACT"
                                      "NM_001281494:c.3156_3159dupGACT") ; cf. rs55740729 (+)
        "chr2" 26254257 "G" "GACT" '("NM_000183:c.4_6dupACT"
                                     "NM_001281512:c.4_6dupACT"
                                     "NM_001281513:c.-146_-144dupACT") ; cf. rs3839049 (+)
        "chr1" 42752620 "T" "TGGAGTTC" '("NM_001146289:c.1383_1389dupGAACTCC"
                                         "NM_001243246:c.1383_1389dupGAACTCC"
                                         "NM_022356:c.1383_1389dupGAACTCC") ; cf. rs137853953 (-)

        ;; Insertion
        "chr1" 69567 "A" "AT" '("NM_001005484:c.477_478insT")
        "chr3" 122740443 "G" "GAGA" '("NM_024610:c.1368_1369insTCT"
                                      "NM_001320728:c.1284_1285insTCT") ;cf. rs16338 (-)

        ;; inversion
        "chr2" 47806747 "AAAACTTTTTTTTTTTTTTTTTTAA" "ATTAAAAAAAAAAAAAAAAAAGTTT"
        '("NM_000179:c.4002-31_4002-8inv"
          "NM_001281492:c.3612-31_3612-8inv"
          "NM_001281493:c.3096-31_3096-8inv"
          "NM_001281494:c.3096-31_3096-8inv") ; cf. rs267608133 (+)
        ;; NOTE: strand - example is not found on dbSNP

        ;; indel
        "chr3" 37006994 "AAG" "AGTT" '("NM_000249:c.385_386delAGinsGTT"
                                       "NM_001258271:c.385_386delAGinsGTT"
                                       "NM_001258273:c.-339_-338delAGinsGTT"
                                       "NM_001167617:c.91_92delAGinsGTT"
                                       "NM_001167618:c.-339_-338delAGinsGTT"
                                       "NM_001167619:c.-247_-246delAGinsGTT"
                                       "NM_001258274:c.-339_-338delAGinsGTT") ; cf. rs63751710 (+)
        "chr1" 21887514 "CTG" "CC" '("NM_001291860:c.862_863delCAinsG"
                                     "NM_005529:c.862_863delCAinsG") ; cf. rs2010297 (-)

        ;; repeated sequences
        "chr7" 55191822 "T" "TGCTGCT" '("NM_005228:c.2571_2573[3]") ; not actual example (+)
        "chr3" 126492636 "C" "CCTCT" '("NM_001165974:c.1690-122_1690-121[3]"
                                       "NM_144639:c.1510-122_1510-121[3]") ; cf. rs2307882 (-)
        "chr2" 237363239 "T" "TA" '("NM_004369:c.6063+6[9]"
                                    "NM_057166:c.4242+6[9]"
                                    "NM_057167:c.5445+6[9]") ; cf. rs11385011 (-)
        )))
  (cavia-testing "throws Exception if inputs are illegal"
    (let [rgidx (rg/index (rg/load-ref-genes test-ref-gene-file))]
      (is (thrown? Exception
                   (vcf-variant->cdna-hgvs {:chr "chr7", :pos 55191823, :ref "T", :alt "G"}
                                           test-ref-seq-file rgidx))))))

(defn- vcf-variant->protein-hgvs-texts
  [variant seq-rdr rgidx]
  (map #(hgvs/format % {:amino-acid-format :short
                        :ter-format :short})
       (vcf-variant->protein-hgvs variant seq-rdr rgidx)))

(defslowtest vcf-variant->protein-hgvs-test
  (cavia-testing "returns protein HGVS strings"
    (let [rgidx (rg/index (rg/load-ref-genes test-ref-gene-file))]
      (are [chr pos ref alt e]
          (= (vcf-variant->protein-hgvs-texts {:chr chr, :pos pos, :ref ref, :alt alt}
                                              test-ref-seq-file rgidx) e)
        ;; Substitution
        "chr7" 55191822 "T" "G" '("p.L858R") ; cf. rs121434568
        "chr1" 11796321 "G" "A" '("p.A222V") ; cf. rs1801133

        ;; deletion
        "chr1" 1286041 "CCTT" "C" '("p.F227del")
        "chr3" 198153259 "GGCAGCAGCA" "G" '("p.Q82_Q84del"); cf. rs56683636 (+)
        "chr1" 247815239 "AAGG" "A" '("p.S163del") ; cf. rs35979231 (-)
        "chr1" 84574315 "CGCAGCGCCA" "C" '("p.L31_L33del"); cf. rs3217269 (-)

        ;; Duplication
        "chr2" 26254257 "G" "GACT" '("p.T2dup") ; cf. rs3839049 (+)
        "chr2" 233521093 "T" "TTTC" '("p.K752dup") ; cf. rs59586144 (-)

        ;; Insertion
        "chr3" 73062352 "T" "TTGG" '("p.L91_E92insV") ; cf. rs143235716 (+)
        "chr3" 122740443 "G" "GAGA" '("p.P456_Q457insS"
                                      "p.P428_Q429insS") ; cf. rs71270423 (-)

        ;; indel
        "chr2" 47445589 "CTTACTGAT" "CCC" '("p.L440_D442delinsP" "p.L374_D376delinsP") ; cf. rs63749931 (+)
        "chr1" 152111364 "TGC" "TCG" '("p.E617_Q618delinsDE") ; cf. rs35444647 (-)

        ;; repeated sequences
        "chr7" 55191823 "G" "GCTGCTG" '("p.L858[3]") ; not actual example (+)
        "chr1" 11796319 "C" "CGGCGGC" '("p.A222[3]") ; not actual example (-)

        ;; Frame shift
        "chr1" 69567 "A" "AT" '("p.L160Sfs*7")
        "chr1" 963222 "GCG" "G" '("p.A386Gfs*12")

        ;; Extension
        "chr2" 189011772 "T" "C" '("p.*1467Qext*45") ; cf. ClinVar 101338
        ;; NOTE: There are very few correct examples...
        )))
  (cavia-testing "throws Exception if inputs are illegal"
    (let [rgidx (rg/index (rg/load-ref-genes test-ref-gene-file))]
      (is (thrown? Exception
                   (vcf-variant->protein-hgvs {:chr "chr7", :pos 55191823, :ref "T", :alt "G"}
                                              test-ref-seq-file rgidx))))))
