;;   Copyright (c) 7theta. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://www.eclipse.org/legal/epl-v10.html)
;;   which can be found in the LICENSE file at the root of this
;;   distribution.
;;
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any others, from this software.

(ns crusta.core
  (:require [utilis.fn :refer [apply-kw]]
            [utilis.string :refer [split]]
            [clojure.java.io :as io]
            [clojure.string :as st])
  (:import [java.io InputStream OutputStream BufferedReader]))

(declare prepare-command stream->seq)

;;; Public

(defn exec
  "Executes the 'command' represented as a string or a sequence of strings
  in a separate process and returns a reference to the process.

  Additional keyword parameters can be used to control the behaviour:
    :environment - A hash map of the variables and their values to be
       merged into the existing environment
    :clear-environment - A boolean indicating whether the existing
       environment should be cleared. If combined with the :environment
       option above, the resulting environment will only contain the
       variables provided in the :environment parameter
    :directory - The working directory for the process
    :redirect-stderr - Redirects stderr into stdout"
  [command & {:keys [environment clear-environment
                     directory
                     redirect-stderr
                     wrap-shell]}]
  (let [builder (ProcessBuilder. (prepare-command command :wrap-shell wrap-shell))]
    (when clear-environment (.clear (.environment builder)))
    (doseq [[k v] environment] (.put (.environment builder) k v))
    (when directory (.directory builder (io/file directory)))
    (when redirect-stderr (.redirectErrorStream builder true))
    (.start builder)))

(defn kill
  "Kills the process referenced by 'process'"
  [^Process process]
  (.destroy process))

(defn exit-code
  "Returns the exit code for 'process'. If the process has not exited,
  the call will block until the process exits. An optional :timeout
  in ms can be supplied to prevent blocking forever. ':timeout' will
  be returned if the timeout expires before the process exits."
  [^Process process & {:keys [timeout]}]
  (if-not timeout
    (.waitFor process)
    (deref (future (.waitFor process)) timeout :timeout)))

(defn stdout-stream
  "Returns an InputStream containing the contents of stdout for 'process'"
  [^Process process]
  (.getInputStream process))

(defn stdout-seq
  "Returns a lazy sequence containing the lines of text in stdout for 'process'"
  [^Process process]
  (stream->seq (stdout-stream process)))

(defn stdin-stream
  "Returns an OutputStream connected to the stdin of 'process'"
  [^Process process]
  (.getOutputStream process))

(defn stderr-stream
  "Returns an InputStream containing the contents of stderr for 'process'.
  A nil will be returned if :redirect-stderr was passed to 'exec' when
  launching the process."
  [^Process process]
  (.getErrorStream process))

(defn stderr-seq
  "Returns a lazy sequence containing the lines of text in stderr for 'process'.
  A nil will be returned if :redirect-stderr was passed to 'exec' when
  launching the process."
  [^Process process]
  (stream->seq (stderr-stream process)))

(defn run
  "Executes the 'command' represented as a string or a sequence of strings
  in a separate process and returns the contents of stdout when the process
  completes. If the process terminates abnormally, an exception is thrown
  with the contents of stderr.

  Additional keyword parameters can be used to control the behaviour:
    :environment - A hash map of the variables and their values to be
       merged into the existing environment
    :clear-environment - A boolean indicating whether the existing
       environment should be cleared. If combined with the :environment
       option above, the resulting environment will only contain the
       variables provided in the :environment parameter
    :directory - The working directory for the process
    :redirect-stderr - Redirects stderr into stdout"
  [command & {:keys [environment clear-environment
                     directory
                     redirect-stderr
                     wrap-shell]
              :as options}]
  (future
    (let [process (apply-kw exec command options)
          exit-code (exit-code process)]
      (if (zero? exit-code)
        (st/join "\n" (stdout-seq process))
        (throw (ex-info "Abnormal process termination"
                        {:command command
                         :process process
                         :exit-code exit-code
                         :stdout (st/join "\n" (stdout-seq process))
                         :stderr (st/join "\n" (stderr-seq process))}))))))

;;; Implementation

(defn- prepare-command
  [command & {:keys [wrap-shell]}]
  (let [command (-> (cond->> command (not (string? command)) (st/join " "))
                    (split #"\s+" :quote-chars [\" \'])
                    (->> (map #(st/replace % #"'" "")))
                    (cond->> wrap-shell (concat ["sh" "-c"])))]
    (let [command-list (java.util.ArrayList.)]
      (doseq [chunk command]
        (.add command-list chunk))
      command-list)))

(defn- stream->seq
  [stream]
  (when stream
    (let [line-seq (fn line-seq [^BufferedReader reader]
                     (lazy-seq
                      (if-let [line (.readLine reader)]
                        (cons line (line-seq reader))
                        (.close reader))))]
      (line-seq (io/reader stream)))))
