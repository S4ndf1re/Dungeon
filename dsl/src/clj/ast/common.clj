(ns ast.common
  (:require
   [ast.ast :as ast]
   [emission :as em]
   [filebuffer :as fb]
   [util.defaulTerminal :as dt])
  (:import
   [dgir.core.debug Location]
   [dgir.dialect.builtin BuiltinOps$ProgramOp]
   [dgir.dialect.scf ScfOps$ScopeOp]))

(defn split-all-at
  "Split a list of expressions on jump expressions, resulting a list of pseudo blocks!
    it is not guaranteed that each block has a valid terminal operation (i.e. return or jump).
    Only the cfg like structure is created"
  [cond-fn collection]
  (loop [splitted (vector)
         running (vector)
         [x & xs] collection]
    (if x
      (if (cond-fn x)
        (recur (conj splitted (conj running x)) (vector) xs)
        (recur splitted (conj running x) xs))
      (if (empty? running)
        splitted
        (conj splitted running)))))

(defn token-location-to-ir-location
  "Given a file contained within the global filebuffer, find the line and column where the token index is situated!
  This is a hepler to split {filename start end} into {filename line col}.
  Additionally, a direct IR Location is created from this information"
  [location]
  (let [line-col (fb/position-to-line-column (:filename location) (:start location))]
    (if line-col
      (new Location (:filename location) (first line-col) (second line-col))
      Location/UNKNOWN)))

(defrecord Program [location expressions]
  ast/AstNode
  (validate [_this] (run! ast/validate expressions))
  (emit [_this context]
    (let [child-context (em/new-context context)
          program-op (new BuiltinOps$ProgramOp (token-location-to-ir-location location))]
      (doseq [expr expressions] (ast/emit expr child-context))
      ;; Emit into the current region
      (em/emit-into-op child-context program-op)
      (em/add-expression context program-op))
    nil)
  (is-jump [_this] true))

(defrecord Ident [location ident]
  ast/AstNode
  (validate [_this] (when-not (string? ident) (throw (ex-info (str "Ident " ident " is not of type string") {:ident ident}))))
  (emit [_this context]
    (if (em/lookup-ident context ident)
      (em/lookup-ident context ident)
      nil))
  (is-jump [_this] false))

; (defrecord Typed [location expr type-ident]
;   ast/AstNode
;   (validate [_this] (do (ast/validate expr)
;                         (when-not (string? type-ident) (throw (ex-info (str "Type Ident " type-ident " is not of type string") {:ident type-ident})))))
;   (emit [_this _context] nil) ;; Its not really clear how to actually emit the ident. Put it into a value?
;   (split-to-blocks [this] this)
;   (is-jump [_this] false)
;   (get-location [_this] location))

(defrecord Do [location expressions]
  ast/AstNode
  (validate [_this] (run! ast/validate expressions))
  (emit [_this context]
      ;; this has to be reversed, as the successor blocks must be known beforehand!
      ;; Remember to reverse the resulting blocks as well, to get the correct starting block!
    (let [splitted (reverse (split-all-at ast/is-jump expressions))
          ;; the context is only emporary, all its blocks will get placed within the old context after emission
          new-context (em/new-context context)]
      ;; We have to remove the blocks, as the loop creates its own blocks, otherwise leaving a block empty and invalid for further emission
      (em/remove-blocks-unsafe new-context)
      (doseq [block-like splitted]
        (let [succ-block (em/get-current-block new-context)
              new-block (em/add-new-block new-context)]
          ;; TODO: before emission, make sure to insert default branch operations to the previous
          ;; (next, due to reverse of the block list) blocks, in order to generate correct IR trees
          (doseq [expr block-like]
            (ast/emit expr new-context))
          (dt/block-insert-default-terminal new-block succ-block (em/get-is-in-loop new-context))))
      (em/add-blocks context (reverse (em/get-blocks new-context))))
    nil)
  (is-jump [_this] true))

(defrecord Let [location bindings body]
  ast/AstNode
  (validate [_this]
    (run! ast/validate (map :expr bindings))
    (ast/validate body))
  (emit [_this context]
    (let [child-context (em/new-context context)
          scope-op (ScfOps$ScopeOp. (token-location-to-ir-location location))]
      (doseq [binding bindings]
        (let [bound-value (ast/emit (:expr binding) child-context)]
          (em/set-ident-value child-context (:ident binding) (bound-value))))
      (let [body-value (ast/emit body child-context)]
        (em/emit-into-op child-context scope-op)
        (em/add-expression context scope-op)
        body-value)))
  (is-jump [_this] true))
