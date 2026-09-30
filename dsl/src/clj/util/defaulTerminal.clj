(ns util.defaulTerminal
  (:import
   [dgir.core.debug Location]
   [dgir.core.ir Block]
   [dgir.core.traits ITerminator]
   dgir.dialect.func.FuncOps$ReturnOp
   dgir.dialect.scf.ScfOps$ContinueOp
   dgir.dialect.cf.CfOps$BranchOp))

(defn block-insert-default-terminal
  "Insert a default terminal if needed for the basic block, based on the value of the successor block"
  [^Block block ^Block succ-block is-in-loop]
  (let [ops (.getOperations block)
        last-op (.getLast ops)]
    (cond
      ;; TODO: this is not actually correct, as the correct terminator is actually context dependent (continue in loops, return in functions!)
      (and (nil? succ-block) (not (instance? ITerminator last-op)) (not is-in-loop)) (.addOperation block
                                                                                      (.getOperation (FuncOps$ReturnOp. (Location/UNKNOWN))))
      (and (nil? succ-block) (not (instance? ITerminator last-op)) is-in-loop) (.addOperation block
                                                                                (.getOperation (ScfOps$ContinueOp. (Location/UNKNOWN))))
      (and (not (nil? succ-block)) (not (instance? ITerminator last-op))) (.addOperation block
                                                                           (.getOperation (CfOps$BranchOp. (Location/UNKNOWN) succ-block)))
      :else nil)))
