;;
;; See the blog  entry [1] for a starter and  the correspoding minimal
;; Java  example  [2].   The  Apache   MINA  SSHD  Project  offers  an
;; "AbstractCommandSupport"  [3],  sadly   for  subclassing,  not  for
;; composition. It may still provide some inspiration though.
;;
;; [1] https://keathmilligan.net/embedding-apache-mina-sshd
;; [2] https://github.com/keathmilligan/sshdtest
;; [3] https://github.com/apache/mina-sshd/blob/master/sshd-core/src/main/java/org/apache/sshd/server/command/AbstractCommandSupport.java
;;
(ns hello-ssh.server
  (:require [clojure.java.io :as io])
  (:import (org.apache.sshd.server SshServer ExitCallback)
           (org.apache.sshd.server.keyprovider SimpleGeneratorHostKeyProvider)
           (org.apache.sshd.server.shell ProcessShellFactory)
           (org.apache.sshd.server.auth.password PasswordAuthenticator)
           (org.apache.sshd.server.command Command CommandFactory)
           (java.io IOException)))

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

;; This  particular function  produces high  quality but  useless test
;; Command, doing  it functional Clojure  style.  At the  very minimum
;; the start method MUST call the  exit callback supplied by a setter,
;; and SHOULD probably do something with the IO streams.
(defn- make-command [command]
  ;; We need  something to store the  state in.  Throw in  a few atoms
  ;; and bindings. Gotta love all those setters ...
  (let [exit-callback (atom nil)
        input-stream (atom nil)
        output-stream (atom nil)
        error-stream (atom nil)
        prefix (str command ":")
        commands-should-succeed true
        ;; This is how to notify the  server code that the command has
        ;; finished:
        exit (fn [code]
               (.onExit ^ExitCallback @exit-callback code))]
    ;; Simulated object as a closure over state in the atoms:
    (reify
      Command

      ;; Start method is strongly advised to do its work in a separate
      ;; thread.  This particular Command calls back with an exit code
      ;; on completion  from a future  or throws Exception  on failure
      ;; from the main thread.  The optional callback message seems to
      ;; not appear anywhere the exception ends up in the log.
      (start [_ channel env]
        (if commands-should-succeed
          (future
            (let [inp (io/reader @input-stream)
                  out (io/writer @output-stream)]
              ;; Header:
              (doto out
                (.write (str "Hello from " command "\n"))
                (.write "Here is a copy of your input:\n")
                (.flush))

              ;; Does not close either stream, buffered:
              (io/copy inp out)

              ;; Footer. You need to flush to avoid loosing output:
              (doto out
                (.write "Bye!\n")
                (.flush)))
            (exit 0))
          (throw
           (IOException. (str prefix " failed!")))))

      ;; Maybe kill the thread if it hangs!
      (destroy [_ channel]
        nil)

      (setInputStream [_ in]
        (reset! input-stream in))

      (setOutputStream [_ out]
        (reset! output-stream out))

      (setErrorStream [_ err]
        (reset! error-stream err))

      ;; Callback used by the shell to notify the SSH server is has
      ;; exited:
      (setExitCallback [_ callback]
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
;;     ssh localhost ls -l "x y z"
;;
;; the client  executable, here  the "ssh",  gets a  nicely structured
;; string array with the file name as a single string:
;;
;;     argv = ["ssh" "localhost" "ls" "-l" "x y z"]
;;
;; but the  command factory on  the server side apparently  receives a
;; single unparsed string
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
