(ns core-test
  (:require
   [ast.ast :as ast]
   [clojure.java.io :refer [resource]]
   [clojure.test :as t :refer [deftest testing]]
   [core :as c]
   [emission :as em]
   [filebuffer :as fb]
   [glr-parser.parser.parser :as par])
  (:import
   [dgir.core.ir.types.builtin.systemf SystemFInference]
   [dgir.core.ir.types.builtin.hmx HMXInference]
   [dgir.core.ir.types.compatibility ExprOrOperator]
   [dgir.dialect.builtin BuiltinOps$ProgramOp]
   [dgir.vm.api VM]
   [java.io OutputStream PrintStream]))

(defn ->repl-stream ^PrintStream [writer]
  (PrintStream.
   (proxy [OutputStream] []
     (write ([b]        (.write writer (char b)))
       ([bs off l] (.write writer (String. bs off l))))
     (flush [] (.flush writer)))
   true)) ; autoflush

(System/setOut (->repl-stream *out*))
(System/setErr (->repl-stream *err*))

(def parser (-> (c/build-lexer) (c/build-parser)))

(defn infer-script
  ([script] (infer-script script (HMXInference.)))
  ([script inference-engine]
   (c/init-hmx)
   (c/init-systemf)
   (c/init-algow)
   (fb/add-file "test" script)
   (let [ctx (em/new-context)]
     (-> "test"
         (fb/get-file-content)
         (#(par/run-lr-1 parser % "test"))
         (:data)
         (ast/emit ctx))
     (let [program (.getFirst (.getOperations (first (em/get-blocks ctx))))
           vm (VM.)
           ; _ (.printOp program)
           solver (.getNewSolverInstance inference-engine)
           exprOrOp (ExprOrOperator/of program)
           solve-result (.solve solver exprOrOp)
           instantiated (.instantiated solve-result)
           underlying-op (.get (.getUnderlyingOperation instantiated))
           ; _ (.printOp underlying-op)
           resultOp (cast BuiltinOps$ProgramOp (.asOp underlying-op))]
       (.init vm resultOp)
       (.run vm)))))

(defn simple-test-fn []
  (par/run-lr-1 parser "(add (mul 1 3) 2)" "test"))

(defn function-def-test-fn []
  (par/run-lr-1 parser "(defn hello-fn [foo bar] foo)
                  (hello-fn 1 \"test string\")" "test"))

(defn simple-prog-test []
  (c/init-hmx)
  (infer-script (slurp (resource "simple_prog.dg"))))

(defn let-set-test []
  (infer-script (slurp (resource "let_set.dg"))))

(defn while-loop-test []
  (infer-script (slurp (resource "while_loop_test.dg"))))

(defn nested-if-test []
  (infer-script (slurp (resource "nested_if.dg"))))

(defn recursive-function []
  (infer-script (slurp (resource "recursive_function.dg"))))

(defn cyclic-function-test []
  (infer-script (slurp (resource "cyclic_function.dg"))))

(defn annotated-cyclic-function-test []
  (infer-script (slurp (resource "annotated_cyclic_function.dg"))))

(defn subtyped-annotation-recursive-failing-function-test []
  (infer-script (slurp (resource "subtyped_annotation_recursive_failing_function.dg"))))

(defn subtyped-annotation-function-test []
  (infer-script (slurp (resource "subtyped_annotation_function.dg"))))

(defn annotated-function-call-test []
  (infer-script (slurp (resource "annotated_call.dg")) (SystemFInference.)))

(defn unannotated-function-call-test []
  (infer-script (slurp (resource "unannotated_call.dg")) (SystemFInference.)))

(defn fib-test []
  (infer-script (slurp (resource "fib.dg")) (SystemFInference.)))

(fib-test)

; (while-loop-test)
; (simple-test-fn)
; (function-def-test-fn)
; (simple-prog-test)
; (let-set-test)
; (nested-if-test)
; (recursive-function)
; (cyclic-function-test)
; (annotated-cyclic-function-test)
; (subtyped-annotation-recursive-failing-function-test)
; (subtyped-annotation-recursive-failing-function-test)
; (subtyped-annotation-function-test)
;; (annotated-function-call-test)

(deftest simple-sexpr-parse
  (testing "Test if a simple sexpr parses"
    (simple-test-fn)))

(deftest function-def-parse
  (testing "Test if a simple fn def parses"
    (function-def-test-fn)))

(deftest simple-prog
  (testing "Test if a simple program with an add operation works"
    (simple-prog-test)))

(deftest let-set
  (testing "Interplay between let and set"
    (let-set-test)))

(deftest while-loop
  (testing "While loop with set"
    (while-loop-test)))

(deftest nested-if
  (testing "Nested if test"
    (nested-if-test)))

(deftest recursive-function-test
  (testing "Recursive Function Test"
    (recursive-function)))

(deftest cyclic-function
  (testing "Cyclic Function Test"
    (cyclic-function-test)))

(deftest annotated-cyclic-function
  (testing "Annotated Cyclic Function Test"
    (annotated-cyclic-function-test)))

(deftest subtyped-annotation-recursive-failing-function
  (testing "Subtyped generics with letrec/letmix bindings that support both recursive and non recursive blocks"
    (subtyped-annotation-recursive-failing-function-test)))

(deftest subtyped-annotation-function
  (testing "Subtyped Cyclic Function Test"
    (subtyped-annotation-function-test)))

(deftest annotated-function-call
  (testing "Annotated Function Test System-F"
    (annotated-function-call-test)))

(deftest unannotated-function
  (testing "Unannotated Function Test System-F"
    (unannotated-function-call-test)))

(deftest fib
  (testing "Fib Test System-F"
    (fib-test)))
