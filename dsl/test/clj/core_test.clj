(ns core-test
  (:require
   [ast.ast :as ast]
   [clojure.test :as t :refer [deftest testing]]
   [core :as c]
   [emission :as em]
   [filebuffer :as fb]
   [glr-parser.parser.parser :as par]
   [util.print-op :as po])
  (:import
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

(defn infer-script [script]
  (c/init-hmx)
  (fb/add-file "test" script)
  (let [ctx (em/new-context)]
    (-> "test"
        (fb/get-file-content)
        (#(par/run-lr-1 parser % "test"))
        (:data)
        (ast/emit ctx))
    (let [program (.getFirst (.getOperations (first (em/get-blocks ctx))))
          vm (VM.)
          _ (po/print-op program)
          inference (HMXInference.)
          solver (.getNewSolverInstance inference)
          exprOrOp (ExprOrOperator/of program)
          solve-result (.solve solver exprOrOp)
          instantiated (.instantiated solve-result)
          underlying-op (.get (.getUnderlyingOperation instantiated))
          _ (po/print-op underlying-op)
          resultOp (cast BuiltinOps$ProgramOp (.asOp underlying-op))]
      (.init vm resultOp)
      (.run vm))))

(defn simple-test-fn []
  (par/run-lr-1 parser "(add (mul 1 3) 2)" "test"))

(defn function-def-test-fn []
  (par/run-lr-1 parser "(defn hello-fn [foo bar] foo)
                  (hello-fn 1 \"test string\")" "test"))

(defn simple-prog-test []
  (c/init-hmx)
  (infer-script "
                 (defn abc [a b] (return (+ a b)))
                 (defn main []
                   (abc 1 2))
                 "))

(defn let-set-test []
  (infer-script "
                 (defn abc [a b]
                     (let [a 10]
                         (set a 20)
                         (return a)))
                 (defn main []
                   (abc 1 2))
                 "))

(defn while-loop-test []
  (infer-script "(defn main []
                       (let [a true]
                           (while a
                               (set a false))
                           (assert (not a))
                       ))"))

(defn nested-if-test []
  (infer-script "(defn main []
                       (let [a true
                             b false
                             c 0]
                           (if a
                             (if b
                               (set c 1)
                               (set c 2))
                             (if b
                               (set c 3)
                               (set c 4)))
                           (assert (= c 2))
                       ))"))

(defn recursive-function []
  (infer-script "
                (defn test-fn [a]
                  (if (< a 10)
                    (test-fn (+ a 1))
                    ()
                    )
                )

                (defn main []
                  (test-fn 0)
                       )"))

(defn cyclic-function-test []
  (infer-script "
                (defn foo [a]
                  (if (< a 10)
                    (return (bar (+ a 1)))
                    (return a)
                    )
                )

                (defn bar [a] (return (foo a)))

                (defn main []
                  (assert (= (bar 0) 10))
                  (assert (= (bar 10) 10))
                  (assert (= (bar 11) 11))
                  (return a)
                       )"))

; (while-loop-test)
; (simple-test-fn)
; (function-def-test-fn)
; (simple-prog-test)
; (let-set-test)
; (nested-if-test)
; (recursive-function)
(cyclic-function-test)

(deftest expression-test
  (testing "Test if a simple sexpr parses"
    (simple-test-fn))
  (testing "Test if a simple fn def parses"
    (function-def-test-fn))
  (testing "Test if a simple program with an add operation works"
    (simple-prog-test))
  (testing "Interplay between let and set"
    (let-set-test))
  (testing "While loop withset"
    (while-loop-test))
  (testing "Nested if test"
    (nested-if-test))
  (testing "Recursive Function Test"
    (recursive-function))
  (testing "Cyclic Function Test"
    (cyclic-function-test)))


