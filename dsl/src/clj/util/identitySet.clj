(ns util.identitySet
  (:import
   [java.util Collections IdentityHashMap Set]))

(defn contains-key? [^Set set key]
  (.contains set key))

(defn add [^Set set key]
  (.add set key))

(defn new-set
  ([])
  ([entries]
   (let [set (Collections/newSetFromMap (IdentityHashMap.))]
     (doseq [entry entries]
       (add set entry))
     set)))

