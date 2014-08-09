(ns clj-parasoup.database.protocol)


(defprotocol DatabaseService
  (put-file [this file-name byte-data content-type])
  (get-file [this file-name])
  (get-gfy [this file-name])
  (put-gfy [this file-name gfy-name])
  (check-file [this file-name])
  (put-token [this token data])
  (get-token [this token]))
