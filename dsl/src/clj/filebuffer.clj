(ns filebuffer)

(defn new-file-buffer [] {})

(def global-file-buffer (atom (new-file-buffer)))

(defn add-file [filename content]
  (swap! global-file-buffer assoc filename content))

(defn get-file-content [filename]
  (get @global-file-buffer filename))

(defn position-to-line-column [filename position]
  (if (get-file-content filename)
    (loop [[x xs] (seq (get-file-content filename))
           lines 0
           columns 0
           counter 0]
      (if (and x (< counter position))
        (if (= \newline x)
          (recur xs (inc lines) 0 (inc counter))
          (recur xs lines (inc columns) (inc counter)))
        [lines columns]))
    nil))

