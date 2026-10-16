(ns ast.cf
  (:require
   [ast.ast :refer [AstNode] :as ast]
   [ast.common :as com]
   [emission :as em])
  (:import [dgir.core.debug Location]
           [dgir.core.ir Value]
           [dgir.dialect.cf CfOps$BranchCondOp]
           [dgir.dialect.cf CfOps$AssertOp]
           dgir.dialect.func.FuncOps$ReturnOp
           dgir.dialect.scf.ScfOps$ContinueOp
           dgir.dialect.scf.ScfOps$EndOp
           dgir.dialect.scf.ScfOps$WhileOp))

(defrecord If [location condition then else]
  AstNode
  (validate [_this] (do (ast/validate condition)
                        (ast/validate then)
                        (ast/validate else)))
  (emit [_this context]
    ;; FIXME: this will not work, as if statements are isolated form above, and return statements cannot return from if statmenets. BLocks are needed instead
    ;; The alternative is to heavily alternate the IRs functionality.
    ;; There is just one PROBLEM: The current logic: when an if is reached, the succsessor block must be known already! Otherwise the else branch would get executed non the less!
    (let [cond-value (ast/emit condition context)
          _ (when-not (instance? Value cond-value) (throw (ex-info "condition must be a value" {:cond cond-value})))
          then-context (em/new-context context)
          else-context (em/new-context context)]
      (ast/emit then then-context)
      (ast/emit else else-context)
      (em/add-expression context (CfOps$BranchCondOp. (com/token-location-to-ir-location location) cond-value (em/get-first-block then-context) (em/get-first-block else-context)))
      (em/add-blocks context (em/get-blocks then-context))
      (em/add-blocks context (em/get-blocks else-context)))
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
            cond-value (ast/emit condition condition-context)]
        (em/add-expression then-context (ScfOps$ContinueOp. (Location/UNKNOWN)))
        (em/add-expression else-context (ScfOps$EndOp. (Location/UNKNOWN)))
        (em/add-expression condition-context (CfOps$BranchCondOp.
                                              (com/token-location-to-ir-location location)
                                              cond-value
                                              (em/get-current-block then-context)
                                              (em/get-current-block else-context)))
        (em/add-blocks condition-context (em/get-blocks then-context))
        (em/add-blocks condition-context (em/get-blocks else-context))
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

(defrecord Assert [location expression message]
  AstNode
  (validate [_this]
    (ast/validate expression)
    (when message
      (ast/validate message)))
  (emit [_this context]
    (let [value (ast/emit expression context)
          msg-value (if message (ast/emit message context) nil)]
      (if msg-value
        (em/add-expression context (CfOps$AssertOp. (com/token-location-to-ir-location location) value msg-value))
        (em/add-expression context (CfOps$AssertOp. (com/token-location-to-ir-location location) value)))
      nil))
  (is-jump [_this] false))
