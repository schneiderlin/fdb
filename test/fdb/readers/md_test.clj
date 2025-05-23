(ns fdb.readers.md-test
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [fdb.readers.md :as sut]
   [fdb.utils :as u]))

(deftest metadata-test
  (let [config-path (-> "md" io/resource io/file fs/parent (fs/path "fdbconfig.edn") str)]
    (is (= {:aliases    ["something" "else" "another"],
            :checkbox   true,
            :cssclasses [".foo" ".bar"],
            :date       #inst "1986-04-25T00:00:00.000-00:00",
            :datetime   #inst "1986-04-25T16:00:00.000-00:00",
            :list       ["one" "two" "three"],
            :list-links ["[[another file]]" "[[other file]]"],
            :number     14,
            :tags       ["accepted" "applied" "action-item"],
            :text       "one two three",
            :text-link  "[[another file]]",
            :fdb/refs   #{"/vault/other file.md"
                          "/vault/inbox/another file.md"
                          "/vault/another file.md"
                          "/vault/md link.md"},
            :fdb/k      {:foo "bar"},
            :fdb.a/ks   ['n.s/sym {:call 'n.s/another-sym} [:sh "echo"]],}
           (sut/read {:config-path config-path
                      :config      {:mounts {:vault "./md"}}
                      :self-path   (u/sibling-path config-path "./md/file.md")
                      :self        {:xt/id "/vault/file.md"}})))))
