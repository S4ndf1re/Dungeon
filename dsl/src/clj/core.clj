(ns core
  (:require
   [glr-parser.lexer :as lex]
   [glr-parser.parser.parser :as par]
   [glr-parser.regex :as rgx]
   [ast :as ast]))

(def int-regex
  (rgx/->OneOrMore (rgx/->Digit)))

(def float-regex
  (rgx/->Sequence
   [(rgx/->ZeroOrMore (rgx/->Digit))
    (rgx/->Constant \.)
    (rgx/->OneOrMore (rgx/->Digit))]))

(def id-regex
  (rgx/->Sequence
   [(rgx/->Range \a \z)
    (rgx/->ZeroOrMore (rgx/->Or
                       [(rgx/->Range \a \z)
                        (rgx/->Range \A \Z)
                        (rgx/->Constant \-)
                        (rgx/->Constant \_)
                        (rgx/->Digit)]))]))

;; FIXME(jan): this will not actually work for umlaute and unicode strings.
;; i.e. this must actually be fixed in the future, if unicode should be supported.
;; For now, this is fine, as this is just a toy dsl.
(def string-regex
  (rgx/->Sequence [(rgx/->Constant \")
                   (rgx/->ZeroOrMore
                    (rgx/->Or [(rgx/->Range \space \!)
                               (rgx/->Range \# \[)
                               (rgx/->Range \] \~)
                               (rgx/->Sequence [(rgx/->Constant \\)
                                                (rgx/->Or [(rgx/->Constant \\)
                                                           (rgx/->Constant \")
                                                           (rgx/->Constant \n)
                                                           (rgx/->Constant \t)
                                                           (rgx/->Constant \r)])])]))
                   (rgx/->Constant \")]))

(defn build-lexer []
  (-> (lex/new-empty)
      ;; Keywords
      (lex/add-const :defn "defn")
      (lex/add-const :defn "if")
      (lex/add-const :defn "while")
      ;; Symbols
      (lex/add-const :plus "+")
      (lex/add-const :minus "-")
      (lex/add-const :asteric "*")
      (lex/add-const :slash "/")
      (lex/add-const :lparen "(")
      (lex/add-const :rparen ")")
      (lex/add-const :lbrack "[")
      (lex/add-const :rbrack "]")
      (lex/add-const :lbrace "{")
      (lex/add-const :rbrace "}")
      (lex/add-rule :ws (rgx/->Or [(rgx/->Constant \newline)
                                   (rgx/->Constant \tab)
                                   (rgx/->Constant \space)
                                   (rgx/->Constant \formfeed)
                                   (rgx/->Constant \backspace)
                                   (rgx/->Constant \return)]))
      (lex/add-skip :ws)
      (lex/add-rule :int int-regex)
      (lex/add-rule :float float-regex)
      (lex/add-rule :string string-regex)
      (lex/add-rule :id id-regex)
      (lex/build)))

(defn fn-def-to-ast [[_ _ ident _ arg-idents _ children _]]
  (ast/->FnDef (:data ident) (:data arg-idents) (:data children)))

(defn fn-call-to-ast [[_ ident args _]]
  (ast/->FnCall (:data ident) (:data args)))

(defn int-lit-to-ast [[lit-str]]
  (-> (:data lit-str)
      (Integer/parseInt)
      (ast/->IntLit)))

(defn float-lit-to-ast [[lit-str]]
  (-> (:data lit-str)
      (Double/parseDouble)
      (ast/->FloatLit)))

(defn string-lit-to-ast [[lit-str]]
  (-> (:data lit-str)
      (ast/->StringLit)))

(defn id-to-ast [[ident]]
  (-> (:data ident)
      (ast/->Ident)))

(defn build-parser [lexer]
  (-> (par/new-parser-builder lexer)
      (par/add-rule :Program [:SExprOrFnDef*])
      (par/add-rule :Arith [[:plus id-to-ast]
                            [:minus id-to-ast]
                            [:asteric id-to-ast]
                            [:slash id-to-ast]])
      (par/add-rule :FnDef [:lparen :defn :id :lbrack :id* :rbrack :SExpr* :rparen fn-def-to-ast])
      (par/add-rule :SExprFn [[:id id-to-ast]
                              [:Arith]])
      (par/add-rule :SExprLit [[:id id-to-ast]
                               [:int int-lit-to-ast]
                               [:float float-lit-to-ast]
                               [:string string-lit-to-ast]])
      (par/add-rule :SExpr [[:lparen :SExprFn :SExpr* :rparen fn-call-to-ast]
                            [:SExprLit]])
      (par/add-rule :SExprOrFnDef [[:SExpr]
                                   [:FnDef]])
      (par/build-lr-1 :Program)))
