(ns hello-ssh.server
  (:import (org.apache.sshd.server SshServer)
           (org.apache.sshd.server.keyprovider SimpleGeneratorHostKeyProvider)
           (org.apache.sshd.server.shell ProcessShellFactory)
           (org.apache.sshd.server.auth.password PasswordAuthenticator)))

;; https://keathmilligan.net/embedding-apache-mina-sshd
(defn -main []
  (let [server (SshServer/setUpDefaultServer)]
    ;; The default "server" is far from  ready to be used --- even the
    ;; port number is set to 0 ...
    (println server)
    (doto server
      (.setPort 2222)
      (.setHost "127.0.0.1")

      ;; The "simple generator host key provider" used below generates
      ;; new key on every start, so  you will constantly have to reset
      ;; your known_hosts with
      ;;
      ;;    ssh-keygen -f ~/.ssh/known_hosts -R "[127.0.0.1]:2222"
      ;;
      (.setKeyPairProvider (SimpleGeneratorHostKeyProvider.))
      ;; (.setFileSystemFactory  ...)

      ;; The password  authenticator below  allows everyone  in.  Note
      ;; that when the host key  verification fails the SSH client may
      ;; choose to disable password authentication on suspicion of the
      ;; man-in-the-middle.  Moreover,  Keys in ~/.ssh/authorized_keys
      ;; will be  reloaded es needed  and accepted for  key-based auth
      ;; too.
      (.setPasswordAuthenticator
       (reify
         PasswordAuthenticator
         (authenticate [_ username password session] true)))

      ;; With the permissible  authenticator you dont want  to offer a
      ;; real shell:
      (.setShellFactory
       (ProcessShellFactory. ["/bin/echo" "hello-ssh" "v0.0.0-alpha0"]))
      (.start))
    ;; Termination would also stop the server, therefore sleep ...
    (println server)
    (Thread/sleep (* 100 1000))))
