(ns example
  (:require [babashka.cli :as cli]))

(defn copy [m]
  (assoc m :fn :copy))

(defn delete [m]
  (assoc m :fn :delete))

(defn help [m]
  (assoc m :fn :help))

(def table
  [#_{:cmds []         :fn help}
   {:cmds ["copy"]   :fn copy   :args->opts [:file]}
   {:cmds ["delete"] :fn delete :args->opts [:file]}
   {:cmds []         :fn help}])

(defn -main [& args]
  (println "ARGS:" args)
  (println "result:" (cli/dispatch table args)))

(comment
  (-main "copy" "the-file")
  
  
  :rcf)

