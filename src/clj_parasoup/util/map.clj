(ns clj-parasoup.util.map)

(defn map-over-map-values [m f]
  (reduce (fn [acc [k v]] (assoc acc k (f v))) {} m))

(defn map-over-map-keys [m f]
  (reduce (fn [acc [k v]] (assoc acc (f k) v)) {} m))
