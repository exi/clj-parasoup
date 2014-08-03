(ns clj-parasoup.util.random-strings)

(defn get-hex-string [length]
  (apply
   str
   (flatten
    (take
     length
     (repeatedly
      #(rand-nth "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"))))))
