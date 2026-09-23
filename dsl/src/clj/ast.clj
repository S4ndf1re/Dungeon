(ns ast)

(defprotocol AstNode)

(defrecord FnDef [ident arg-idents body-exprs]
  AstNode)

(defrecord IntLit [value]
  AstNode)

(defrecord FloatLit [value]
  AstNode)

(defrecord StringLit [value]
  AstNode)

(defrecord FnCall [fn-ident params]
  AstNode)

(defrecord Ident [ident]
  AstNode)



