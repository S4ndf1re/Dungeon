(ns ast.functions
  (:require
   [ast.ast :refer [AstNode] :as ast]
   [ast.common :as com]
   [emission :as em])
  (:import [dgir.core.ir MaybeType Type Value]
           dgir.dialect.arith.ArithAttrs$BinModeAttr$BinMode
           dgir.dialect.arith.ArithAttrs$UnaryModeAttr$UnaryMode
           dgir.dialect.arith.ArithOps$BinaryOp
           dgir.dialect.arith.ArithOps$UnaryOp
           dgir.dialect.func.FuncOps$CallOp
           dgir.dialect.func.FuncOps$FuncOp
           dgir.dialect.func.FuncTypes$FuncType))

(defn new-arg
  ([loc ident] (new-arg loc ident nil))
  ([loc ident annotation] {:loc loc :ident ident :annotation annotation}))

(defn arg-validate [arg]
  (ast/validate (:ident arg))
  (when (:annotation arg)
    (ast/validate (:annotation arg))))

(defn arg-ident-ident [arg] (:ident (:ident arg)))
(defn arg-annotation-ident [arg] (:ident (:annotation arg)))

(defn arg-to-maybe-type [arg]
  (if (:annotation arg)
    (MaybeType/of (Type/fromParameterizedIdent (arg-annotation-ident arg)))
    (MaybeType/of)))

(defrecord FnDef [location ident arg-idents body]
  AstNode
  (validate [_this] (do
                      (ast/validate ident)
                      (run! arg-validate arg-idents)
                      (ast/validate body)))
  (emit [_this context]
    (let [child-context (em/new-context context)
          fn-op (new FuncOps$FuncOp
                     (com/token-location-to-ir-location location)
                     (:ident ident)
                     (FuncTypes$FuncType/of (mapv arg-to-maybe-type arg-idents) (MaybeType/of)))
          main-region (.orElseThrow (.getFirstRegion fn-op))
          ;; Map the arguments to the actual region values, by index (taken from count arg-idents in combination with map)
          arg-values (filter #(not (nil? (second %)))
                             (map
                              #(vector (arg-ident-ident %1) (.orElse (.getRegionValue main-region %2) nil))
                              arg-idents
                              (range (count arg-idents))))]
      ;; this is actually an in place modification of the environment!
      (doseq [[ident value] arg-values]
        (em/set-ident-value child-context ident value))
      ;; Emit the children!
      ;; But first, remove all blocks.
      ;; NOTE: This is only safe, because the body is always a do operation that creates its own blocks!
      (em/remove-blocks-unsafe child-context)
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
    "<" (ArithOps$BinaryOp.
         (com/token-location-to-ir-location location)
         ^Value (first params) ^Value (second params)
         ArithAttrs$BinModeAttr$BinMode/LT)
    "<=" (ArithOps$BinaryOp.
          (com/token-location-to-ir-location location)
          ^Value (first params) ^Value (second params)
          ArithAttrs$BinModeAttr$BinMode/LE)
    ">" (ArithOps$BinaryOp.
         (com/token-location-to-ir-location location)
         ^Value (first params) ^Value (second params)
         ArithAttrs$BinModeAttr$BinMode/GT)
    ">=" (ArithOps$BinaryOp.
          (com/token-location-to-ir-location location)
          ^Value (first params) ^Value (second params)
          ArithAttrs$BinModeAttr$BinMode/GE)
    "=" (ArithOps$BinaryOp.
         (com/token-location-to-ir-location location)
         ^Value (first params) ^Value (second params)
         ArithAttrs$BinModeAttr$BinMode/EQ)
    "and" (ArithOps$BinaryOp.
           (com/token-location-to-ir-location location)
           ^Value (first params) ^Value (second params)
           ArithAttrs$BinModeAttr$BinMode/AND)
    "or" (ArithOps$BinaryOp.
          (com/token-location-to-ir-location location)
          ^Value (first params) ^Value (second params)
          ArithAttrs$BinModeAttr$BinMode/OR)
    "not" (ArithOps$UnaryOp.
           (com/token-location-to-ir-location location)
           ^Value (first params)
           ArithAttrs$UnaryModeAttr$UnaryMode/LOGICAL_COMPLEMENT)
    (FuncOps$CallOp.
     (com/token-location-to-ir-location location)
     (:ident ident)
     params
     (MaybeType/of))))

(defrecord FnCall [location fn-ident params]
  AstNode
  (validate [_this] (do
                      (ast/validate fn-ident)
                      (run! ast/validate params)))
  (emit [_this context]
    ;; TODO: this function call needs special handling based on builtin operations (+, -, *, /, etc.)
    ;; NOTE: the emission has side effects, the mapv is only present for the actuall operator values!
    (let [arg-values (mapv #(ast/emit % context) params)
          ;; Make sure the arg-list is actually not null
          _ (when (contains? arg-values nil) (throw (ex-info (str "Argument Value must never contain nil values " location) {:arguments params})))
          call-op (build-call-op-from-ident location fn-ident arg-values)]
      (em/add-expression context call-op)
      (.orElse (.getOutputValue call-op) nil)))
  (is-jump [_this] false))
