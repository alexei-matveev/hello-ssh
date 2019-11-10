;;
;; See blog entry [1] and the Java example [2].
;;
;; [1] https://keathmilligan.net/embedding-apache-mina-sshd
;; [2] https://github.com/keathmilligan/sshdtest
;;
(ns hello-ssh.server
  (:import (org.apache.sshd.server SshServer ExitCallback)
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
;; and four methods of the Command interface itself:
;;
;;     void setInputStream (InputStream in);
;;     void setOutputStream (OutputStream out);
;;     void setErrorStream (OutputStream err);
;;     void setExitCallback (ExitCallback callback);
;;
;; The command  must call the  onExit method of the  ExitCallback upon
;; completion    or   throw    an    IOException    on   failure    to
;; start/execute. Just exiting the start method does not suffice.
;;

;; This  particular function  produces  high quality  noops, doing  it
;; Clojure style.  Well  the start method MUST call  the exit callback
;; supplied by  a setter,  and SHOULD probably  write something  or at
;; least close some streams ...
(defn- make-command [command]
  ;; We need  something to store  the state  in. Gotta love  all those
  ;; setters ...
  (let [exit-callback (atom nil)
        prefix (str command ":")]
    ;; Simulated object as a closure over state in the atom:
    (reify
      Command
      ;; Start method ist strongly advised to call Thread/start ...
      (start [_ channel env]
        (println prefix "channel=" channel "env=" env)
        ;; Callback  with  exit code  on  completion  or Exception  on
        ;; failure.
        (if true
          (.onExit ^ExitCallback @exit-callback 42)
          (throw
           (java.io.IOException. "noop failed to start ..."))))
      (destroy [_ channel]
        (println prefix  "destroyed, channel=" channel))
      (setInputStream [_ in]
        (println prefix "in=" in))
      (setOutputStream [_ out]
        (println prefix "out=" out))
      (setErrorStream [_ err]
        (println prefix "err=" err))
      ;; Callback used by the shell to notify the SSH server is has
      ;; exited:
      (setExitCallback [_ callback]
        (println prefix "callback=" callback)
        (reset! exit-callback callback)))))

;;
;; Next    function    prepares     CommandFactory    for    use    in
;; setCommandFactory().   FWIW, the  CommandFactory is  declared as  a
;; FunctionalInterface.
;;
;; Something  is very  wrong  about  the SSH  exec  protocoll ---  the
;; "command" argument is a single string!  Whenever you execute
;;
;;     touch "x y z"
;;     ssh ls -l "x y z"
;;
;; the  client executable,  here the  "ssh", gets  a nicely  separated
;; array with the file name as a single string:
;;
;;     argv = ["ssh" "ls" "-l" "x y z"]
;;
;; but command factory on the server side apparently receives a single
;; unparsed string
;;
;;     command = "ls -l x y z"
;;
;; and you end with
;;
;;     ls: cannot access 'x': No such file or directory
;;     ls: cannot access 'y': No such file or directory
;;     ls: cannot access 'z': No such file or directory
;;
(defn- make-command-factory []
  (reify
    CommandFactory
    ;; Command createCommand (ChannelSession channel, String command)
    ;; throws IOException;
    (createCommand [_ channel command]
      ;; Will we ever need channel too?
      (make-command command))))

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
