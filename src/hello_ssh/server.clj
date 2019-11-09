;;
;; See blog entry [1] and the Java example [2].
;;
;; [1] https://keathmilligan.net/embedding-apache-mina-sshd
;; [2] https://github.com/keathmilligan/sshdtest
;;
(ns hello-ssh.server
  (:import (org.apache.sshd.server SshServer)
           (org.apache.sshd.server.keyprovider SimpleGeneratorHostKeyProvider)
           (org.apache.sshd.server.shell ProcessShellFactory)
           (org.apache.sshd.server.auth.password PasswordAuthenticator)))

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
      ;; You may want to disable strict host checking when testing:
      ;;
      ;;    ssh -p 2222 -o StrictHostKeyChecking=no user@127.0.0.1
      ;;
      (.setKeyPairProvider (SimpleGeneratorHostKeyProvider.))
      ;; (.setFileSystemFactory  ...)

      ;; The password  authenticator below  allows everyone  in.  Note
      ;; that when the host key  verification fails the SSH client may
      ;; choose to disable password authentication on suspicion of the
      ;; man-in-the-middle.    Beware   that   the  public   keys   in
      ;; ~user/.ssh/authorized_keys  will be  reloaded  as needed  and
      ;; accepted for key-based auth too!
      (.setPasswordAuthenticator
       (reify
         PasswordAuthenticator
         (authenticate [_ username password session]
           ;; Username  ist   what  the  client  specified   e.g.   in
           ;; "user@127.0.0.1". FIXME: password may leak in plain text
           ;; here. Overall  ist is  probably not a  good idea  to use
           ;; password auth ...
           (println {:u username :p "censored" :s session})
           ;; let the one in ...
           true)))

      ;; With the permissible  authenticator you dont want  to offer a
      ;; real shell:
      (.setShellFactory
       (ProcessShellFactory. ["/bin/echo" "hello-ssh" "v0.0.0-alpha0"]))
      (.start))
    ;; Termination would also stop the server, therefore sleep ...
    (println server)
    (Thread/sleep (* 100 1000))
    ;; This  is likely  the correct  was  to terminate  the server  at
    ;; runtime:
    (.stop server)))
