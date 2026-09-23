(ns core-test
  (:require
   [clojure.pprint :as pprint]
   [clojure.test :as t :refer [deftest testing]]
   [glr-parser.parser.parser :as par]
   [core :as c]))

(def parser (-> (c/build-lexer) (c/build-parser)))

(defn simple-test-fn []
  (par/run-lr-1 parser "(add (mul 1 3) 2)" "test")
  (println "Done simple-test-fn"))

(defn function-def-test-fn []
  (pprint/pprint (:data (par/run-lr-1 parser "(defn hello-fn [foo bar] foo)
                  (hello-fn 1 \"test string\")" "test")))
  (println "Done function-def-test-fn"))

(simple-test-fn)
(function-def-test-fn)

(deftest simple-sexpr-test
  (testing "Test if a simple sexpr parses"
    (simple-test-fn)))

(deftest function-def-test-fn-test
  (testing "Test if a simple fn def parses"
    (function-def-test-fn)))
