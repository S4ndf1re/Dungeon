(ns util.defaulTerminal
  (:import
   [dgir.core.debug Location]
   [dgir.core.ir
    Block
    Operation
    Region]
   [dgir.core.traits ITerminator]
   dgir.dialect.cf.CfOps$BranchOp
   dgir.dialect.func.FuncOps$ReturnOp
   dgir.dialect.scf.ScfOps$ContinueOp))

(declare block-get-last-exprs)
(declare region-get-last-exprs)

(defn- op-get-last-exprs
  [^Operation op]
  (let [regions (.getRegions op)]
    (->> regions
         (mapcat region-get-last-exprs))))

(defn- region-get-last-exprs
  [^Region region]
  (let [blocks (.getBlocks region)]
    (->> blocks
         (mapcat block-get-last-exprs))))

(defn- block-get-last-exprs
  "Return a list of terminal-op and block pairs for all depth of all last operators. For the operators that return nil as the terminal, the insert defaul terminal method should get called"
  [^Block block]
  (let [last-op (.getLast (.getOperations block))]
    (if last-op
      (if (instance? ITerminator (.asOp last-op))
        [(list last-op block)]
        (let [final-blocks (op-get-last-exprs last-op)]
          (if (empty? final-blocks)
            [(list nil block)]
            final-blocks)))
      [(list nil block)])))

(defn- block-insert-default-terminal-single
  "Insert a default terminal if needed for the basic block, based on the value of the successor block."
  [^Block block ^Block succ-block is-in-loop]
  (when block
    (let [ops (.getOperations block)
          last-op (some-> ops .getLast .asOp)]
      (cond
        (and (nil? succ-block) (not (instance? ITerminator last-op)) (not is-in-loop)) (.addOperation block
                                                                                                      (.getOperation (FuncOps$ReturnOp. (Location/UNKNOWN))))
        (and (nil? succ-block) (not (instance? ITerminator last-op)) is-in-loop) (.addOperation block
                                                                                                (.getOperation (ScfOps$ContinueOp. (Location/UNKNOWN))))
        (and (not (nil? succ-block)) (not (instance? ITerminator last-op))) (.addOperation block
                                                                                           (.getOperation (CfOps$BranchOp. (Location/UNKNOWN) succ-block)))
        :else nil))))

(defn block-insert-default-terminal
  "Insert a default terminal if needed for the basic block, based on the value of the successor block."
  [^Block block ^Block succ-block is-in-loop]
  (when block
    (println "Blocks: " (block-get-last-exprs block))
    (let [final-blocks (filter #(nil? (first %)) (block-get-last-exprs block))]
      (doseq [block (map second final-blocks)]
        (block-insert-default-terminal-single block succ-block is-in-loop)))))
