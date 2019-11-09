(ns hello-ssh.core
  (:require [clj-ssh.cli :as ssh]))

(defn -main
  "I don't do a whole lot."
  []
  ;; Your ~/.ssh/known_hosts will be full of new entries:
  (ssh/default-session-options {:strict-host-key-checking :no})
  ;; Everything  in the  .localhost  domain resolves  to localhost  on
  ;; Ubuntu. It takes ~5 Seconds for 10 logins here ...
  (time
   (let [ids (for [x (range 10)
                   :let [host (str "prefix-" x ".localhost")]]
               (ssh/ssh host "id"))]
     (println ids))))
