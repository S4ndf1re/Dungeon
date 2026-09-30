(ns ast.literals
  (:require
   [ast.ast :refer [AstNode] :as ast]
   [ast.common :as com]
   [emission :as emit])

  (:import dgir.dialect.arith.ArithOps$ConstantOp))

(defrecord IntLit [location value]
  AstNode
  (validate [_this] (when-not (int? value) (throw (ex-info (str "Value " value " is not of type int") {:value value}))))
  (emit [_this context]
    (let [expr (ArithOps$ConstantOp. (com/token-location-to-ir-location location) value)]
      (emit/add-expression context expr)
      (.getResult expr)))

  (is-jump [_this] false))

(defrecord FloatLit [location value]
  AstNode
  (validate [_this] (when-not (float? value) (throw (ex-info (str "Value " value " is not of type float") {:value value}))))
  (emit [_this context]
    (let [expr (ArithOps$ConstantOp. (com/token-location-to-ir-location location) value)]
      (emit/add-expression context expr)
      (.getResult expr)))

  (is-jump [_this] false))

(defrecord StringLit [location value]
  AstNode
  (validate [_this] (when-not (string? value) (throw (ex-info (str "Value " value " is not of type string") {:value value}))))
  (emit [_this context]
    (let [expr (ArithOps$ConstantOp. (com/token-location-to-ir-location location) value)]
      (emit/add-expression context expr)
      (.getResult expr)))

  (is-jump [_this] false))

(defrecord UnitLit [location]
  AstNode
  (validate [_this] nil)
  (emit [_this context]
    (let [expr (ArithOps$ConstantOp. (com/token-location-to-ir-location location))]
      (emit/add-expression context expr)
      (.getResult expr)))

  (is-jump [_this] false))
