(ns ast.cf
  (:require
   [ast.ast :refer [AstNode] :as ast]
   [ast.common :as com]
   [emission :as em])
  (:import [dgir.core.debug Location]
           [dgir.core.ir Value]
           dgir.dialect.func.FuncOps$ReturnOp
           dgir.dialect.scf.ScfOps$ContinueOp
           dgir.dialect.scf.ScfOps$EndOp
           dgir.dialect.scf.ScfOps$IfOp
           dgir.dialect.scf.ScfOps$WhileOp))

(defrecord If [location condition then else]
  AstNode
  (validate [_this] (do (ast/validate condition)
                        (ast/validate then)
                        (ast/validate else)))
  (emit [_this context]
    (let [cond-value (ast/emit condition context)
          _ (when-not (instance? Value cond-value) (throw (ex-info "condition must be a value" {:cond cond-value})))
          then-context (em/new-context context)
          else-context (em/new-context context)
          if-op (ScfOps$IfOp. (com/token-location-to-ir-location location) ^Value cond-value true)
          then-region (.getThenRegion if-op)
          else-region (.getElseRegion if-op)]
      (ast/emit then then-context)
      (ast/emit else else-context)
      (em/emit-into-region then-context then-region)
      (em/emit-into-region else-context else-region)
      (em/add-expression context if-op))
    nil)
  (is-jump [_this] true))

(defrecord While [location condition body]
  AstNode
  (validate [_this] (do (ast/validate condition)
                        (ast/validate body)))
  (emit [_this context]
    (let [while-op (ScfOps$WhileOp. (com/token-location-to-ir-location location))]
        ;; Condition
      (let [condition-context (em/new-context context)
            _ (em/set-in-loop condition-context true)
            then-context (em/new-context condition-context)
            else-context (em/new-context condition-context)
            cond-value (ast/emit condition condition-context)
            if-op (ScfOps$IfOp. (Location/UNKNOWN) cond-value true)]
        (em/add-expression then-context (ScfOps$ContinueOp. (Location/UNKNOWN)))
        (em/add-expression else-context (ScfOps$EndOp. (Location/UNKNOWN)))
        (em/emit-into-region then-context (.getThenRegion if-op))
        (em/emit-into-region else-context (.orElseThrow (.getElseRegion if-op)))
        (em/add-expression condition-context if-op)
        (em/emit-into-region condition-context (.getConditionRegion while-op)))
      ;; Body
      (let [new-context (em/new-context context)]
        (em/set-in-loop new-context true)
        (ast/emit body new-context)
        (em/emit-into-region new-context (.getBodyRegion while-op)))
      (em/add-expression context while-op))
    nil)
  (is-jump [_this] false))

(defrecord Return [location expression]
  AstNode
  (validate [_this] (ast/validate expression))
  (emit [_this context]
    (let [value (if expression (ast/emit expression context) nil)]
      (if value
        (em/add-expression context (FuncOps$ReturnOp. (com/token-location-to-ir-location location) value))
        (em/add-expression context (FuncOps$ReturnOp. (com/token-location-to-ir-location location))))
      nil))

  (is-jump [_this] true))

(defrecord Break [location]
  AstNode
  (validate [_this] nil)
  (emit [_this context]
    (when-not (em/get-is-in-loop context)
      (throw (ex-info "Cannot break out of non-loop context" {})))
    (em/add-expression context (ScfOps$EndOp. (com/token-location-to-ir-location location))))

  (is-jump [_this] true))

(defrecord Continue [location]
  AstNode
  (validate [_this] nil)
  (emit [_this context]
    (when-not (em/get-is-in-loop context)
      (throw (ex-info "Cannot continue out of non-loop context" {})))
    (em/add-expression context (ScfOps$ContinueOp. (com/token-location-to-ir-location location))))
  (is-jump [_this] true))
