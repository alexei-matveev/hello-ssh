(ns hello-ssh.server
  (:import (org.apache.sshd.server SshServer)
           (org.apache.sshd.server.keyprovider SimpleGeneratorHostKeyProvider)
           (org.apache.sshd.server.shell ProcessShellFactory)
           (org.apache.sshd.server.auth.password PasswordAuthenticator)))

;; https://keathmilligan.net/embedding-apache-mina-sshd
(defn -main []
  (let [server (SshServer/setUpDefaultServer)]
    ;; The default "server" ist far from ready to be used:
    (println server)
    (doto server
      (.setPort 2222)
      (.setHost "127.0.0.1")
      ;; This host key  provider generates new key on  every start, so
      ;; you will have to reset your known_hosts constantly:
      (.setKeyPairProvider (SimpleGeneratorHostKeyProvider.))
      ;; (.setFileSystemFactory ...)
      ;; This  password authenticator  allows everyone  in. Note  that
      ;; when  the host  key  verification fails  the  SSH client  may
      ;; choose to disable password authentication on suspicion of the
      ;; man-in-the-middle:
      (.setPasswordAuthenticator
       (reify
         PasswordAuthenticator
         (authenticate [_ username password session] true)))
      ;; With the permissible  authenticator you dont want  to offer a
      ;; real schell:
      (.setShellFactory (ProcessShellFactory. ["/bin/true"]))
      (.start))
    ;; Termination would also stop the server, therefore sleep ...
    (println server)
    (Thread/sleep 100000)))
