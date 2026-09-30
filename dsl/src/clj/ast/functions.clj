(ns ast.functions
  (:require
   [ast.ast :refer [AstNode] :as ast]
   [ast.common :as com]
   [emission :as em])
  (:import [dgir.core.ir MaybeType Value]
           dgir.dialect.arith.ArithAttrs$BinModeAttr$BinMode
           dgir.dialect.arith.ArithOps$BinaryOp
           dgir.dialect.func.FuncOps$CallOp
           dgir.dialect.func.FuncOps$FuncOp
           dgir.dialect.func.FuncTypes$FuncType))

(defrecord FnDef [location ident arg-idents body]
  AstNode
  (validate [_this] (do
                      (ast/validate ident)
                      (run! ast/validate arg-idents)
                      (ast/validate body)))
  (emit [_this context]
    (println "Emitting Function Definition " location ident arg-idents body)
    (let [child-context (em/new-context context)
          arg-count (count arg-idents)
          fn-op (new FuncOps$FuncOp
                     (com/token-location-to-ir-location location)
                     (:ident ident)
                     (FuncTypes$FuncType/of (vec (for [_n (range arg-count)] (MaybeType/of))) (MaybeType/of)))
          main-region (.orElseThrow (.getFirstRegion fn-op))
          arg-values (filter #(not (nil? (second %)))
                             (map
                              #(vector (:ident %1) (.orElse (.getRegionValue main-region %2) nil))
                              arg-idents
                              (range arg-count)))]
      ;; this is actually an in place modification of the environment!
      (doseq [[ident value] arg-values]
        (em/set-ident-value child-context ident value))
      ;; Emit the children!
      (ast/emit body child-context)
      (em/emit-into-op child-context fn-op)
      ;; Add the fn operation to the parent scope emission context!
      (em/add-expression context fn-op)
      nil))
  (is-jump [_this] false))

(defn- build-call-op-from-ident [location ident params]
  (case (:ident ident)
    "+" (ArithOps$BinaryOp.
         (com/token-location-to-ir-location location)
         ^Value (first params) ^Value (second params)
         ArithAttrs$BinModeAttr$BinMode/ADD)
    "-" (ArithOps$BinaryOp.
         (com/token-location-to-ir-location location)
         ^Value (first params) ^Value (second params)
         ArithAttrs$BinModeAttr$BinMode/SUB)
    "*" (ArithOps$BinaryOp.
         (com/token-location-to-ir-location location)
         ^Value (first params) ^Value (second params)
         ArithAttrs$BinModeAttr$BinMode/MUL)
    "/" (ArithOps$BinaryOp.
         (com/token-location-to-ir-location location)
         ^Value (first params) ^Value (second params)
         ArithAttrs$BinModeAttr$BinMode/DIV)
    (FuncOps$CallOp.
     (com/token-location-to-ir-location location)
     (:ident ident)
     params
     (FuncTypes$FuncType/empty))))

(defrecord FnCall [location fn-ident params]
  AstNode
  (validate [_this] (do
                      (ast/validate fn-ident)
                      (run! ast/validate params)))
  (emit [_this context]
    (println "Emitting Function Call " location fn-ident params)
    ;; TODO: this function call needs special handling based on builtin operations (+, -, *, /, etc.)
    ;; NOTE: the emission has side effects, the mapv is only present for the actuall operator values!
    (let [arg-values (mapv #(ast/emit % context) params)
          ;; Make sure the arg-list is actually not null
          _ (when (contains? arg-values nil) (throw (ex-info (str "Argument Value must never contain nil values " location) {:arguments params})))
          call-op (build-call-op-from-ident location fn-ident arg-values)]
      (em/add-expression context call-op)
      (.orElse (.getOutputValue call-op) nil)))
  (is-jump [_this] false))
