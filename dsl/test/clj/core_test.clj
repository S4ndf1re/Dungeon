(ns core-test
  (:require
   [ast.ast :as ast]
   [clojure.test :as t :refer [deftest testing]]
   [core :as c]
   [emission :as em]
   [filebuffer :as fb]
   [util.print-op :as po]
   [glr-parser.parser.parser :as par])
  (:import
   [dgir.core.ir.types.builtin.hmx HMXInference]
   [dgir.core.ir.types.compatibility ExprOrOperator]
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

(defn simple-test-fn []
  (par/run-lr-1 parser "(add (mul 1 3) 2)" "test"))

(defn function-def-test-fn []
  (par/run-lr-1 parser "(defn hello-fn [foo bar] foo)
                  (hello-fn 1 \"test string\")" "test"))

(defn simple-prog-test []
  (c/init-hmx)
  (fb/add-file "test" "
                 (defn abc [a b] (return (+ a b)))
                 (defn main []
                   (abc 1 2))
                 ")
  (let [ctx (em/new-context)]
    (-> "test"
        (fb/get-file-content)
        (#(par/run-lr-1 parser % "test"))
        (:data)
        (ast/emit ctx))
    (let [program (.getFirst (.getOperations (first (em/get-blocks ctx))))
          _ (po/print-op program)
          inference (HMXInference.)
          solver (.getNewSolverInstance inference)
          exprOrOp (ExprOrOperator/of program)]
      (.solve solver exprOrOp))))

(defn let-set-test []
  (c/init-hmx)
  (fb/add-file "test" "
                 (defn abc [a b]
                     (let [a 10]
                         (set a 20)
                         (return a)))
                 (defn main []
                   (abc 1 2))
                 ")
  (let [ctx (em/new-context)]
    (-> "test"
        (fb/get-file-content)
        (#(par/run-lr-1 parser % "test"))
        (:data)
        (ast/emit ctx))
    (let [program (.getFirst (.getOperations (first (em/get-blocks ctx))))
          _ (po/print-op program)
          inference (HMXInference.)
          solver (.getNewSolverInstance inference)
          exprOrOp (ExprOrOperator/of program)]
      (.solve solver exprOrOp))))

; (simple-test-fn)
; (function-def-test-fn)
; (simple-prog-test)
(let-set-test)

(deftest expression-test
  (testing "Test if a simple sexpr parses"
    (simple-test-fn))
  (testing "Test if a simple fn def parses"
    (function-def-test-fn))
  (testing "Test if a simple program with an add operation works"
    (simple-prog-test))
  (testing "Interplay between let and set"
    (let-set-test)))


