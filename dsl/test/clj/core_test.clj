(ns core-test
  (:require
   [ast.ast :as ast]
   [clojure.test :as t :refer [deftest testing]]
   [core :as c]
   [emission :as em]
   [filebuffer :as fb]
   [glr-parser.parser.parser :as par])
  (:import
   [dgir.core.ir.types.builtin.hmx HMXInference]
   [dgir.core.ir.types.compatibility ExprOrOperator]))

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
          inference (HMXInference.)
          solver (.getNewSolverInstance inference)
          exprOrOp (ExprOrOperator/of program)]
      (.solve solver exprOrOp))))

(simple-test-fn)
(function-def-test-fn)
(simple-prog-test)

(deftest simple-sexpr-test
  (testing "Test if a simple sexpr parses"
    (simple-test-fn)))

(deftest function-def-test-fn-test
  (testing "Test if a simple fn def parses"
    (function-def-test-fn)))

(deftest simple-add-program
  (testing "Test if a simple program with an add operation works"
    (simple-prog-test)))
