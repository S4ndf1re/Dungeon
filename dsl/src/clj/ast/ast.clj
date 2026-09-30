(ns ast.ast)

(defprotocol AstNode
  (validate [this] "validate or throw ex-info")
  (emit [this context] "emit operations into the context")
  (is-jump [this] "return truth value, if this operation is actually a jump opertion (for example if, while, break, etc"))
