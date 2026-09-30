(ns emission
  (:require [clojure.core :as c]
            [env :as env]
            [util.identitySet :as idSet])
  (:import [dgir.core.ir Block Op Region]))

(defn set-in-loop [context is-in-loop]
  (vswap! context assoc :is-in-loop is-in-loop))

(defn get-is-in-loop [context]
  (if (nil? context)
    false
    (get @context :is-in-loop false)))

(defn new-context
  "Create a new mutable context, that serves as the emission tracker.
  TODO(jan): maybe make the emission context also track the environment for code generation"
  ([] (new-context nil))
  ([parent]
   (let [initial-block (Block.)]
     (volatile! {:parent parent
                 :blocks [initial-block]
                 :current-block initial-block
                 :is-in-loop (get-is-in-loop parent)
                 :env (env/new-env)}))))

(defn pop-context
  "Drop the current context, returing its parent, or nil if the parent is not set"
  [context]
  (:parent @context))

(defn add-expression
  "Add a new expression to the current emission context"
  [context expr]
  (when-not (instance? Op expr)
    (throw (ex-info "Expression is not an instance of Op" {:expr expr})))
  (.addOperation (:current-block @context) expr)
  context)

(defn get-blocks
  "Get all blocks from the current emission context"
  [context]
  (get @context :blocks []))

(defn get-current-block
  "Get the current block from the current emission context"
  [context]
  (get @context :current-block))

(defn add-block [context block]
  (vswap! context assoc :current-block block)
  (vswap! context update :blocks conj block)
  context)

(defn remove-blocks-unsafe
  "remove blocks in an unsafe manner, leaving the context invalid, until a new block is pushed!"
  [context]
  (vswap! context assoc :blocks [])
  ;; This line here is the unsafe part!
  (vswap! context assoc :current-block nil))

(defn add-new-block
  "add a new block and return this created block"
  [context]
  (let [block (Block.)]
    (add-block context block)
    block))

(defn add-blocks
  "add a list of blocks into the current context, updating the blocks list and the current block"
  [context blocks]
  (doseq [block blocks]
    (add-block context block)))

(defn lookup-ident [context ident]
  (let [looked-up-value (env/get-for-ident (:env @context) ident)]
    (cond
      looked-up-value looked-up-value
      (:parent @context) (lookup-ident (:parent @context) ident)
      :else nil)))

(defn reverse-blocks
  "Reverse the blocks within this context"
  [context]
  (vswap! context update :blocks reverse)
  (vswap! context assoc :current-block (last (:blocks @context)))
  context)

(defn set-ident-value [context ident value]
  (vswap! context update :env env/set-for-ident ident value))

(defn validate
  "Validate the context by checking if the current block is contained within the block list, and each block's successor is also contained within this block list!"
  [context]
  (let [contained-blocks (idSet/new-set (get-blocks context))]
    (when (empty? (get-blocks context))
      (throw (ex-info "At least one block is expected for emission!" {})))
    (when-not (idSet/contains-key? contained-blocks (:current-block @context))
      (throw (ex-info "Invalid block layout, the current block is not found within the block list of the emission context" {})))
    (doseq [block (get-blocks context)]
      (doseq [successor (.getSuccessors block)]
        (when-not (idSet/contains-key? contained-blocks successor)
          (throw (ex-info "successor block is not contained within emission context" {})))))))

(defn emit-into-region [context ^Region region]
  (when (nil? region)
    (throw (ex-info "Region must not be nil" {})))
  (validate context)
  (.removeAllBlocks region)
  (let [blocks (get-blocks context)]
    (doseq [block blocks]
      (.addBlock region block))))

(defn emit-into-op [context ^Op op]
  (emit-into-region context (.orElse (.getFirstRegion op) nil)))

