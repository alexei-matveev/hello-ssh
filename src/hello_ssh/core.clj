(ns hello-ssh.core
  (:require [clj-ssh.cli :as cli]
            [clj-ssh.ssh :as ssh]))

(defn -main []
  ;; Your ~/.ssh/known_hosts will be full of new entries:
  (cli/default-session-options {:strict-host-key-checking :no})
  ;; Everything  in the  .localhost  domain resolves  to localhost  on
  ;; Ubuntu. It takes ~5 Seconds for 10 logins here ...
  (time
   (let [ids (doall
              (for [x (range 10)
                    :let [host (str "prefix-" x ".localhost")]]
                (cli/ssh host "id")))]
     (println ids)))
  ;; Try 10 Times the same on a single session and it takes 1.5s. Note
  ;; that in  this case one has  to work with functions  & macros from
  ;; the clj-ssh.ssh namespace:
  (time
   (let [agent (ssh/ssh-agent {})]
     (let [session (ssh/session agent "localhost" {:strict-host-key-checking :no})]
       (let [ids (ssh/with-connection session
                   (doall
                    (for [_ (range 10)]
                      (ssh/ssh session {:cmd "id"}))))]
         (println ids))))))
