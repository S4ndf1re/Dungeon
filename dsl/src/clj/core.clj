(ns core
  (:require
   [ast.cf :as cf]
   [ast.common :as com]
   [ast.functions :as func]
   [ast.literals :as lit]
   [glr-parser.common.token :as tok]
   [glr-parser.lexer :as lex]
   [glr-parser.parser.parser :as par]
   [glr-parser.regex :as rgx])
  (:import
   [dgir.core.ir Dialect]
   [dgir.core.ir.types.builtin.algorithmw AlgorithmWInference]
   [dgir.core.ir.types.builtin.hmx HMXInference]
   [dgir.core.ir.types.compatibility ConverterRegistry]
   [dgir.dialect.arith ArithAlgoWConversion ArithHMXConversion]
   [dgir.dialect.builtin BuiltinAlgoWConversion BuiltinHMXConversion]
   [dgir.dialect.cf CfAlgoWConversion]
   [dgir.dialect.func FuncAlgoWConversion FuncHMXConversion]
   [dgir.dialect.io IoAlgoWConversion IoHMXConversion]
   [dgir.dialect.scf ScfAlgoWConversion ScfHMXConversion]))

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
      (lex/add-const :if "if")
      (lex/add-const :while "while")
      (lex/add-const :do "do")
      (lex/add-const :let "let")
      (lex/add-const :return "return")
      (lex/add-const :break "break")
      (lex/add-const :continue "continue")
      ;; Symbols
      (lex/add-const :colon ":")
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

(defn fn-def-to-ast [loc [_ _ ident _ arg-idents _ children _]]
  (func/->FnDef loc (:data ident) (:data arg-idents) (com/->Do (tok/location children)
                                                               (:data children))))

(defn fn-call-to-ast [loc [_ ident args _]]
  (func/->FnCall loc (:data ident) (:data args)))

(defn int-lit-to-ast [loc [lit-str]]
  (->> (:data lit-str)
       (Integer/parseInt)
       (lit/->IntLit loc)))

(defn float-lit-to-ast [loc [lit-str]]
  (->> (:data lit-str)
       (Double/parseDouble)
       (lit/->FloatLit loc)))

(defn string-lit-to-ast [loc [lit-str]]
  (->> (:data lit-str)
       (lit/->StringLit loc)))

(defn id-to-ast [loc [ident]]
  (->> (:data ident)
       (com/->Ident loc)))

; (defn annotation-to-ast [loc [expr _ type-ident]]
;   (com/->Typed loc (:data expr) (:data type-ident)))

(defn if-to-ast [loc [_ _ condition then-case else-case _]] (cf/->If loc (:data condition) (:data then-case) (:data else-case)))
(defn while-to-ast [loc [_ _ condition body-exprs _]] (cf/->While loc (:data condition) (com/->Do (tok/location body-exprs) (:data body-exprs))))
(defn do-to-ast [loc [_ _ exprs _]] (com/->Do loc (:data exprs)))
(defn let-to-ast [loc [_ _ _ bindings _ body  _]] (com/->Let loc (:data bindings) (:data body)))
(defn return-to-ast [loc [_ _ expr _]] (cf/->Return loc (:data expr)))
(defn break-to-ast [loc [_ _ _]] (cf/->Break loc))
(defn continue-to-ast [loc [_ _ _]] (cf/->Continue loc))
(defn build-untyped-binding [loc [ident expr]] {:loc loc :ident ident :expr expr :type nil})
(defn build-typed-binding [loc [ident _ ty expr]] {:loc loc :ident ident :expr expr :type ty})
(defn program-to-ast [loc [expressions]] (com/->Program loc (:data expressions)))
(defn unwarp-single [[single]] (:data single))

(defn build-parser [lexer]
  (-> (par/new-parser-builder lexer)
      (par/add-rule :ID [:id id-to-ast])
      (par/add-rule :Program [:TopLevelSExpr* program-to-ast])

      ;; ============== Supported Arithmetic Operations ==============
      (par/add-rule :Arith [[:plus id-to-ast]
                            [:minus id-to-ast]
                            [:asteric id-to-ast]
                            [:slash id-to-ast]])

      ;; ============== Normal S-Exprs with all supported function arguments (excluding special keywords) ==============
      (par/add-rule :SExprFn [[:ID unwarp-single]
                              [:Arith unwarp-single]])
      (par/add-rule :SExprLit [[:ID unwarp-single]
                               [:int int-lit-to-ast]
                               [:float float-lit-to-ast]
                               [:string string-lit-to-ast]])
      (par/add-rule :SExpr [[:lparen :SExprFn :SExpr* :rparen fn-call-to-ast]
                            [:SExprLit unwarp-single]
                            [:lparen :rparen (fn [loc _] (lit/->UnitLit loc))]])

      ;; ============== Special Builtin S-Expression ==============
      ;; TODO(jan): implement a global deftype, to rely on id resolution!
      (par/add-rule :Type [:ID unwarp-single])
      (par/add-rule :FnArg [[:ID unwarp-single]])
                            ; [:id :colon :Type annotation-to-ast]])
      (par/add-rule :FnDef [:lparen :defn :ID :lbrack :FnArg* :rbrack :BodySExpr* :rparen fn-def-to-ast])
      (par/add-rule :If [:lparen :if :SExpr :BodySExpr :BodySExpr :rparen if-to-ast])
      (par/add-rule :While [:lparen :while :SExpr :BodySExpr* :rparen while-to-ast])
      (par/add-rule :Do [:lparen :do :BodySExpr* :rparen do-to-ast])
      (par/add-rule :LetBinding [[:ID :SExpr build-untyped-binding]
                                 [:ID :colon :Type :SExpr build-typed-binding]])
      (par/add-rule :Let [:lparen :let :lbrack :LetBinding* :rbrack :BodySExpr* :rparen let-to-ast])
      (par/add-rule :Return [:lparen :return :SExpr :rparen return-to-ast])
      (par/add-rule :Break [:lparen :break :rparen break-to-ast])
      (par/add-rule :Continue [:lparen :continue :rparen continue-to-ast])

      ;; ============== All Allowed Top level expressions ==============
      (par/add-rule :TopLevelSExpr [[:BodySExpr unwarp-single]
                                    [:FnDef unwarp-single]])

      (par/add-rule :BodySExpr [[:SExpr unwarp-single]
                                [:If unwarp-single]
                                [:While unwarp-single]
                                [:Do unwarp-single]
                                [:Return unwarp-single]
                                [:Break unwarp-single]
                                [:Continue unwarp-single]])

      (par/build-lr-1 :Program)))

(defn init-hmx []
  (ConverterRegistry/registerDialect HMXInference)
  (Dialect/registerAllDialects)
  (FuncHMXConversion/registerBuiltinAlgoWConversion)
  (BuiltinHMXConversion/registerBuiltinAlgoWConversion)
  (ArithHMXConversion/registerBuiltinAlgoWConversion)
  (IoHMXConversion/registerBuiltinAlgoWConversion)
  (ScfHMXConversion/registerBuiltinAlgoWConversion))

(defn init-algow []
  (ConverterRegistry/registerDialect AlgorithmWInference)
  (Dialect/registerAllDialects)
  (FuncAlgoWConversion/registerBuiltinAlgoWConversion)
  (BuiltinAlgoWConversion/registerBuiltinAlgoWConversion)
  (ArithAlgoWConversion/registerBuiltinAlgoWConversion)
  (IoAlgoWConversion/registerBuiltinAlgoWConversion)
  (ScfAlgoWConversion/registerBuiltinAlgoWConversion)
  (CfAlgoWConversion/registerBuiltinAlgoWConversion))
