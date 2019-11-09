(ns hello-ssh.core
  (:require [clj-ssh.cli :as cli]
            [clj-ssh.ssh :as ssh]))

(defn- many-hosts [n]
  ;; Simple  REPL  from  clj-ssh.cli   namespace.  Everything  in  the
  ;; .localhost domain resolves to localhost on Ubuntu.
  (doall
   (for [x (range n)
         :let [host (str "prefix-" x ".localhost")]]
     (cli/ssh host "id"))))

(defn- many-commands [n]
  ;; Note that  in this case one  has to work with  functions & macros
  ;; from the clj-ssh.ssh namespace:
  (let [agent (ssh/ssh-agent {})
        session (ssh/session agent "localhost" {:strict-host-key-checking :no})]
     (ssh/with-connection session
       (doall
        (for [_ (range n)]
          (ssh/ssh session {:cmd "id"}))))))

(defn -main []
  ;; Your ~/.ssh/known_hosts will be full of new entries:
  (cli/default-session-options {:strict-host-key-checking :no})
  (let [n 20]
    ;; It takes about n * 0.5 seconds for n simple commands here:
    (time (println (many-hosts n)))
    ;; It takes about 0.5 + n * 0.1 seconds for n simple commands on a
    ;; single session:
    (time (println (many-commands n)))))
