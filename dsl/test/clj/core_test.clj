(ns core-test
  (:require
   [ast.ast :as ast]
   [clojure.pprint :as pprint]
   [clojure.test :as t :refer [deftest testing]]
   [core :as c]
   [emission :as em]
   [filebuffer :as fb]
   [glr-parser.parser.parser :as par])
  (:import
   [dgir.core.ir.types.builtin.algorithmw AlgorithmWInference]
   [dgir.core.ir.types.compatibility ExprOrOperator]))

(def parser (-> (c/build-lexer) (c/build-parser)))

(defn simple-test-fn []
  (par/run-lr-1 parser "(add (mul 1 3) 2)" "test")
  (println "Done simple-test-fn"))

(defn function-def-test-fn []
  (par/run-lr-1 parser "(defn hello-fn [foo bar] foo)
                  (hello-fn 1 \"test string\")" "test")
  (println "Done function-def-test-fn"))

(defn simple-prog-test []
  (c/init-algow)
  (fb/add-file "test" "
                 (defn abc [a b] (+ a b))
                 (abc 1 2)
                 ")
  (let [ctx (em/new-context)]
    (-> "test"
        (fb/get-file-content)
        (#(par/run-lr-1 parser % "test"))
        (#(do (pprint/pprint (:data %)) %))
        (:data)
        (ast/emit ctx))
    (let [program (.getFirst (.getOperations (first (em/get-blocks ctx))))
          inference (AlgorithmWInference.)
          solver (.getNewSolverInstance inference)
          exprOrOp (ExprOrOperator/of program)]
      (println (.solve solver exprOrOp)))))

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
