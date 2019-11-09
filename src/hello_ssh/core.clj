(ns hello-ssh.core
  (:require [clj-ssh.cli :as cli]
            [clj-ssh.ssh :as ssh]))

(defn- many-hosts [n]
  ;; Everything  in the  .localhost  domain resolves  to localhost  on
  ;; Ubuntu. It takes ~5 Seconds for 10 logins here ...
  (doall
   (for [x (range n)
         :let [host (str "prefix-" x ".localhost")]]
     (cli/ssh host "id"))))

(defn- many-cmds [n]
  (let [agent (ssh/ssh-agent {})
        session (ssh/session agent "localhost" {:strict-host-key-checking :no})]
     (ssh/with-connection session
       (doall
        (for [_ (range n)]
          (ssh/ssh session {:cmd "id"}))))))

(defn -main []
  ;; Your ~/.ssh/known_hosts will be full of new entries:
  (cli/default-session-options {:strict-host-key-checking :no})
  (time (println (many-hosts 10)))
  ;; Try 10 Times the same on a single session and it takes 1.5s. Note
  ;; that in  this case one has  to work with functions  & macros from
  ;; the clj-ssh.ssh namespace:
  (time (println (many-cmds 10))))
