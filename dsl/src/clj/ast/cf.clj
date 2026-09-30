(ns ast.cf
  (:require
   [ast.ast :refer [AstNode] :as ast]
   [ast.common :as com]
   [emission :as em])
  (:import [dgir.core.ir Value]
           dgir.dialect.scf.ScfOps$IfOp))

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
  (emit [_this _context] nil)
  (is-jump [_this] false))

(defrecord Return [location expression]
  AstNode
  (validate [_this] (ast/validate expression))
  (emit [_this _context] nil)
  (is-jump [_this] true))

(defrecord Break [location]
  AstNode
  (validate [_this] nil)
  (emit [_this _context] nil)
  (is-jump [_this] true))

(defrecord Continue [location]
  AstNode
  (validate [_this] nil)
  (emit [_this _context] nil)
  (is-jump [_this] true))
