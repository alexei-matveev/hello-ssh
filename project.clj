(defproject hello-ssh "0.1.0-SNAPSHOT"
  :description "Try SSH from Clojure"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-commons/clj-ssh "0.5.15"]
                 ;; Apache MINA SSHD,
                 ;; https://github.com/apache/mina-sshd
                 [org.apache.sshd/sshd-core "2.3.0"]
                 ;; SSHD Server reguires at runtime:
                 [org.slf4j/slf4j-api "1.6.2"]
                 [org.slf4j/slf4j-simple "1.6.2"]]
  :repl-options {:init-ns hello-ssh.core}
  :main hello-ssh.server)
