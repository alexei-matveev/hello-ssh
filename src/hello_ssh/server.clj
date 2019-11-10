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
           (org.apache.sshd.server.auth.password PasswordAuthenticator)
           (org.apache.sshd.server.command Command CommandFactory)))

;; A   Command  is   an  object   that  implements   two  methods   of
;; CommandLifecylce:
;;
;;     void start (ChannelSession channel, Environment env) throws IOException;
;;     void destroy (ChannelSession channel) throws Exception;
;;
;; Four methods of Command itself:
;;
;;     void setInputStream (InputStream in);
;;     void setOutputStream (OutputStream out);
;;     void setErrorStream (OutputStream err);
;;     void setExitCallback (ExitCallback callback);

;; This  particular function  produces  high quality  noops, doing  it
;; quite  efficiently. Well  the  start method  should probably  write
;; something or at least close some streams ...
(defn- make-command []
  (reify
    Command
    ;; Start method ist strongly advised to call Thread/start ...
    (start [_ channgel env]
      (println "noop started")
      (throw (java.io.IOException. "noop failed to start ...")))
    (destroy [_ channel]
      (println "noop destroyed"))
    (setInputStream [_ in]
      (println "noop in=" in))
    (setOutputStream [_ out]
      (println "nooop out=" out))
    (setErrorStream [_ err]
      (println "noop err=" err))
    ;; Callback used by the shell to notify the SSH server is has
    ;; exited:
    (setExitCallback [_ callback]
      (println "exit callback=" callback))))

;; Prepares CommandFactory for use  in setCommandFactory().  FWIW, the
;; CommandFactory is declared as a FunctionalInterface:
(defn- make-command-factory []
  (reify
    CommandFactory
    ;; Command createCommand (ChannelSession channel, String command)
    ;; throws IOException;
    (createCommand [_ channel command]
      (make-command))))

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

      ;; So called "shell requests" are handled bei "ShellFactory" for
      ;; sessions  like   "ssh  user@hiost".   With   the  permissible
      ;; authenticator you dont want to offer a real shell: FIXME: the
      ;; whole environment including the CWD of the SSH server, in our
      ;; test case  that of the "lein  run" process, is taken  over to
      ;; the forked  shell.  With the  interactive bash shell  the tty
      ;; echoes each character twice.
      (.setShellFactory
       (if false
         (ProcessShellFactory. ["/usr/bin/env" "-i" "/bin/bash" "--login" "-i"])
         (ProcessShellFactory. ["/bin/echo" "hello-ssh v0.0.0-alpha0" "no shell access, sorry!"])))

      ;; So called  "exec requests" are handled  by CommandFactory for
      ;; sessions  like "ssh  user@host cmd".   CommandFactory can  be
      ;; used  for  SCP  and  implementing commands  in  pure  Java  &
      ;; Clojure:
      (.setCommandFactory (make-command-factory))
      (.start))
    ;; Termination would also stop the server, therefore sleep ...
    (println server)
    (Thread/sleep (* 100 1000))
    ;; This  is likely  the correct  was  to terminate  the server  at
    ;; runtime:
    (.stop server)))
