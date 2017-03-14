;;   Copyright (c) 7theta. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://www.eclipse.org/legal/epl-v10.html)
;;   which can be found in the LICENSE file at the root of this
;;   distribution.
;;
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any others, from this software.

(defproject com.7theta/crusta "0.1.1"
  :description "A library for managing execution of external programs"
  :url "https://github.com/7theta/crusta"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.7theta/utilis "0.8.3"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* true}
                   :dependencies [[reloaded.repl "0.2.3"]
                                  [org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]}}
  :clean-targets ^{:protect false} ["out" "target"]
  :scm {:name "git"
        :url "https://github.com/7theta/crusta"})
