(ns util.print-op
  "Pretty-print an operation tree: every operation, its regions, and their blocks,
  recursively. Regions/blocks are collected into plain clojure maps so pprint
  renders them readably."
  (:require [clojure.pprint :as pp])
  (:import [dgir.core.ir Block Operation Region]))

(defn- op->map
  "Collect an operation and everything nested below it into a clojure map."
  [^Operation op]
  {:op (.toString op)
   :regions (mapv (fn [^Region region]
                    {:region (.toString region)
                     :blocks (mapv (fn [^Block block]
                                     {:block (.toString block)
                                      :operations (mapv op->map (.getOperations block))})
                                   (.getBlocks region))})
                  (.getRegions op))})

(defn print-op
  "Pretty-print the operation with all regions, blocks and nested operations,
  down every layer."
  [^Operation op]
  (pp/pprint (op->map op)))
