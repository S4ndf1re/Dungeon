(ns core
  (:require
   [ast.cf :as cf]
   [ast.common :as com]
   [ast.functions :as func]
   [ast.literals :as lit]
   [glr-parser.common.token :as tok]
   [glr-parser.lexer :as lex]
   [glr-parser.parser.parser :as par]
   [glr-parser.regex :as rgx]
   [clojure.string :as str])
  (:import
   [dgir.core.ir Dialect]
   [dgir.core.ir.types.builtin.algorithmw AlgorithmWInference]
   [dgir.core.ir.types.builtin.hmx HMXInference]
   [dgir.core.ir.types.compatibility ConverterRegistry]
   [dgir.dialect.arith ArithAlgoWConversion ArithHMXConversion ArithSystemFConversion]
   [dgir.dialect.builtin BuiltinAlgoWConversion BuiltinHMXConversion BuiltinSystemFConversion]
   [dgir.dialect.cell CellAlgoWConversion CellHMXConversion CellSystemFConversion]
   [dgir.dialect.cf CfAlgoWConversion CfHMXConversion CfSystemFConversion]
   [dgir.dialect.func FuncAlgoWConversion FuncHMXConversion FuncSystemFConversion]
   [dgir.dialect.io IoAlgoWConversion IoHMXConversion IoSystemFConversion]
   [dgir.dialect.scf ScfAlgoWConversion ScfHMXConversion ScfSystemFConversion]
   [dgir.vm.api DialectRunner]))

(def comment-regex
  (rgx/->Sequence
   [(rgx/->Constant \;)
    (rgx/->ZeroOrMore (rgx/->AnyOne))
    (rgx/->Constant \newline)]))

(def int-regex
  (rgx/->OneOrMore (rgx/->Digit)))

(def long-regex
  (rgx/->Sequence
   [(rgx/->OneOrMore (rgx/->Digit))
    (rgx/->Constant \l)]))

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
      (lex/add-const :set "set")
      (lex/add-const :assert "assert")
      ;; Symbols
      (lex/add-const :colon ":")
      (lex/add-const :plus "+")
      (lex/add-const :minus "-")
      (lex/add-const :asteric "*")
      (lex/add-const :eq "=")
      (lex/add-const :lt "<")
      (lex/add-const :lte "<=")
      (lex/add-const :gt ">")
      (lex/add-const :gte ">=")
      (lex/add-const :and "and")
      (lex/add-const :or "or")
      (lex/add-const :not "not")
      (lex/add-const :slash "/")
      (lex/add-const :lparen "(")
      (lex/add-const :rparen ")")
      (lex/add-const :lbrack "[")
      (lex/add-const :rbrack "]")
      (lex/add-const :lbrace "{")
      (lex/add-const :rbrace "}")

      ;; Skips
      (lex/add-rule :ws (rgx/->Or [(rgx/->Constant \newline)
                                   (rgx/->Constant \tab)
                                   (rgx/->Constant \space)
                                   (rgx/->Constant \formfeed)
                                   (rgx/->Constant \backspace)
                                   (rgx/->Constant \return)]))
      (lex/add-rule :comment comment-regex)
      (lex/add-skip :ws)
      (lex/add-skip :comment)

      ;; Values
      (lex/add-rule :int int-regex)
      (lex/add-rule :long long-regex)
      (lex/add-rule :float float-regex)
      (lex/add-rule :string string-regex)
      (lex/add-rule :id id-regex)
      (lex/add-const :true "true")
      (lex/add-const :false "false")
      (lex/build)))

(defn fn-def-to-ast [loc [_ _ ident _ arg-idents _ children _]]
  (func/->FnDef loc (:data ident) (:data arg-idents) (com/->Do (tok/location children)
                                                               (:data children))))

(defn fn-call-to-ast [loc [_ ident args _]]
  (func/->FnCall loc (:data ident) (:data args)))

(defn bin-call-to-ast [loc [_ ident lhs rhs _]]
  (func/->FnCall loc (:data ident) (vector (:data lhs) (:data rhs))))

(defn unary-call-to-ast [loc [_ ident lhs _]]
  (func/->FnCall loc (:data ident) (vector (:data lhs))))

(defn int-lit-to-ast [loc [lit-str]]
  (->> (:data lit-str)
       (Integer/parseInt)
       (lit/->IntLit loc)))

(defn long-lit-to-ast [loc [lit-str]]
  (->> (:data lit-str)
       (#(str/replace % "l" "")) ;; Replace the last l character matched for long!
       (Long/parseLong)
       (lit/->LongLit loc)))

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

(defn create-fn-arg [loc [ident]] (func/new-arg loc (:data ident)))
(defn create-fn-arg-annotated [loc [ident _ annotation]] (func/new-arg loc (:data ident) (:data annotation)))

(defn false-value [loc _] (lit/->BoolLit loc false))
(defn true-value [loc _] (lit/->BoolLit loc true))

; (defn annotation-to-ast [loc [expr _ type-ident]]
;   (com/->Typed loc (:data expr) (:data type-ident)))

(defn set-to-ast [loc [_ _ id expr _]] (com/->Set loc (:data id) (:data expr)))
(defn if-to-ast [loc [_ _ condition then-case else-case _]] (cf/->If loc (:data condition) (:data then-case) (:data else-case)))
(defn while-to-ast [loc [_ _ condition body-exprs _]] (cf/->While loc (:data condition) (com/->Do (tok/location body-exprs) (:data body-exprs))))
(defn do-to-ast [loc [_ _ exprs _]] (com/->Do loc (:data exprs)))
(defn let-to-ast [loc [_ _ _ bindings _ body  _]] (com/->Let loc (:data bindings) (com/->Do (tok/location body) (:data body))))
(defn assert-to-ast [loc [_ _ condition _]] (cf/->Assert loc (:data condition) nil))
(defn assert-with-message-to-ast [loc [_ _ condition msg _]] (cf/->Assert loc (:data condition) (:data msg)))
(defn return-to-ast [loc [_ _ expr _]] (cf/->Return loc (:data expr)))
(defn break-to-ast [loc [_ _ _]] (cf/->Break loc))
(defn continue-to-ast [loc [_ _ _]] (cf/->Continue loc))
(defn build-untyped-binding [loc [ident expr]] (com/->LetBinding loc (:data ident) (:data expr) nil))
(defn build-typed-binding [loc [ident _ ty expr]] (com/->LetBinding loc (:data ident) (:data expr) (:data ty)))
(defn program-to-ast [loc [expressions]] (com/->Program loc (:data expressions)))
(defn unwarp-single [[single]] (:data single))

(defn build-parser [lexer]
  (-> (par/new-parser-builder lexer)
      (par/add-rule :ID [:id id-to-ast])
      (par/add-rule :Program [:TopLevelSExpr* program-to-ast])

      ;; ============== Supported IR Ops ==============
      (par/add-rule :BinOp [[:plus id-to-ast]
                            [:minus id-to-ast]
                            [:asteric id-to-ast]
                            [:slash id-to-ast]
                            [:lt id-to-ast]
                            [:lte id-to-ast]
                            [:gt id-to-ast]
                            [:gte id-to-ast]
                            [:eq id-to-ast]
                            [:and id-to-ast]
                            [:or id-to-ast]])

      (par/add-rule :UnaryOp [[:not id-to-ast]])

      (par/add-rule :String [:string string-lit-to-ast])
      ;; ============== Normal S-Exprs with all supported function arguments (excluding special keywords) ==============
      (par/add-rule :SExprLit [[:ID unwarp-single]
                               [:int int-lit-to-ast]
                               [:long long-lit-to-ast]
                               [:float float-lit-to-ast]
                               [:String unwarp-single]
                               [:true true-value]
                               [:false false-value]])
      (par/add-rule :SExprFuncCall [:lparen :ID :SExpr* :rparen fn-call-to-ast])
      (par/add-rule :SExprBinOp [:lparen :BinOp :SExpr :SExpr :rparen bin-call-to-ast])
      (par/add-rule :SExprUnaryOp [:lparen :UnaryOp :SExpr :rparen unary-call-to-ast])
      (par/add-rule :SExpr [[:SExprFuncCall unwarp-single]
                            [:SExprUnaryOp unwarp-single]
                            [:SExprBinOp unwarp-single]
                            [:SExprLit unwarp-single]
                            [:lparen :rparen (fn [loc _] (lit/->UnitLit loc))]])

      ;; ============== Special Builtin S-Expression ==============
      ;; TODO(jan): implement a global deftype, to rely on id resolution!
      (par/add-rule :Type [:ID unwarp-single])
      (par/add-rule :FnArg [[:ID create-fn-arg]
                            [:ID :colon :Type create-fn-arg-annotated]])
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
      (par/add-rule :Set [:lparen :set :ID :SExpr :rparen set-to-ast])
      (par/add-rule :Assert [[:lparen :assert :SExpr :rparen assert-to-ast]
                             [:lparen :assert :SExpr :String :rparen assert-with-message-to-ast]])

      ;; ============== All Allowed Top level expressions ==============
      (par/add-rule :TopLevelSExpr [[:BodySExpr unwarp-single]
                                    [:FnDef unwarp-single]])

      (par/add-rule :BodySExpr [[:SExpr unwarp-single]
                                [:If unwarp-single]
                                [:While unwarp-single]
                                [:Do unwarp-single]
                                [:Return unwarp-single]
                                [:Break unwarp-single]
                                [:Continue unwarp-single]
                                [:Set unwarp-single]
                                [:Let unwarp-single]
                                [:Assert unwarp-single]])

      (par/build-lr-1 :Program)))

(defn init-hmx []
  (ConverterRegistry/registerDialect HMXInference)
  (Dialect/registerAllDialects)
  (DialectRunner/registerAllDialects)
  (FuncHMXConversion/registerBuiltinHMXConversion)
  (BuiltinHMXConversion/registerBuiltinHMXConversion)
  (ArithHMXConversion/registerBuiltinHMXConversion)
  (IoHMXConversion/registerBuiltinHMXConversion)
  (ScfHMXConversion/registerBuiltinHMXConversion)
  (CfHMXConversion/registerBuiltinHMXConversion)
  (CellHMXConversion/registerBuiltinHMXConversion))

(defn init-algow []
  (ConverterRegistry/registerDialect AlgorithmWInference)
  (Dialect/registerAllDialects)
  (DialectRunner/registerAllDialects)
  (FuncAlgoWConversion/registerBuiltinAlgoWConversion)
  (BuiltinAlgoWConversion/registerBuiltinAlgoWConversion)
  (ArithAlgoWConversion/registerBuiltinAlgoWConversion)
  (IoAlgoWConversion/registerBuiltinAlgoWConversion)
  (ScfAlgoWConversion/registerBuiltinAlgoWConversion)
  (CfAlgoWConversion/registerBuiltinAlgoWConversion)
  (CellAlgoWConversion/registerBuiltinAlgoWConversion))

(defn init-systemf []
  (ConverterRegistry/registerDialect AlgorithmWInference)
  (Dialect/registerAllDialects)
  (DialectRunner/registerAllDialects)
  (FuncSystemFConversion/registerBuiltinSystemFConversion)
  (BuiltinSystemFConversion/registerBuiltinSystemFConversion)
  (ArithSystemFConversion/registerBuiltinSystemFConversion)
  (IoSystemFConversion/registerBuiltinSystemFConversion)
  (ScfSystemFConversion/registerBuiltinSystemFConversion)
  (CfSystemFConversion/registerBuiltinSystemFConversion)
  (CellSystemFConversion/registerBuiltinSystemFConversion))
