(ns env)

(defn new-env
  "Create a new environment"
  [] {})

(defn get-for-ident
  "Get the value for the identifier or nil"
  [env ident]
  (get env ident))

(defn set-for-ident
  "Set the value of the identifier"
  [env ident value]
  (assoc env ident value))
