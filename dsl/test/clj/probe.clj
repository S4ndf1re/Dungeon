(ns probe
  (:require
   [ast.ast :as ast]
   [core :as c]
   [emission :as em]
   [filebuffer :as fb]
   [glr-parser.parser.parser :as par]
   [util.print-op :as po])
  (:import
   [dgir.core.ir.types.builtin.hmx HMXInference]
   [dgir.core.ir.types.compatibility ExprOrOperator]
   [dgir.dialect.builtin BuiltinOps$ProgramOp]
   [dgir.vm.api VM]))

(def parser (-> (c/build-lexer) (c/build-parser)))

(defn infer-script [script]
  (c/init-hmx)
  (fb/add-file "probe" script)
  (let [ctx (em/new-context)]
    (-> "probe"
        (fb/get-file-content)
        (#(par/run-lr-1 parser % "probe"))
        (:data)
        (ast/emit ctx))
    (let [program (.getFirst (.getOperations (first (em/get-blocks ctx))))
          inference (HMXInference.)
          solver (.getNewSolverInstance inference)
          exprOrOp (ExprOrOperator/of program)
          solve-result (.solve solver exprOrOp)
          instantiated (.instantiated solve-result)
          underlying-op (.get (.getUnderlyingOperation instantiated))
          resultOp (cast BuiltinOps$ProgramOp (.asOp underlying-op))
          vm (VM.)]
      (.init vm resultOp)
      (.run vm)
      (po/print-op underlying-op)
      :ok)))
