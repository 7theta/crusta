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
  (:import [java.util ArrayList]
           [java.io InputStream OutputStream BufferedReader]))

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
                     redirect-stderr]}]
  (let [builder (ProcessBuilder. ^ArrayList (prepare-command command))]
    (when clear-environment (.clear (.environment builder)))
    (doseq [[k v] environment] (.put (.environment builder) k v))
    (when directory (.directory builder (io/file directory)))
    (when redirect-stderr (.redirectErrorStream builder true))
    (let [^Process process (.start builder)]
      {:process process
       :stdin (.getOutputStream process)
       :stdout (.getInputStream process)
       :stderr (.getErrorStream process)})))

(defn kill
  "Kills the process referenced by 'process'"
  [process]
  (.destroy ^Process (:process process)))

(defn exit-code
  "Returns the exit code for 'process'. If the process has not exited,
  the call will block until the process exits. An optional :timeout
  in ms can be supplied to prevent blocking forever. ':timeout' will
  be returned if the timeout expires before the process exits."
  [process & {:keys [timeout]}]
  (let [^Process process (:process process)]
    (if-not timeout
      (.waitFor process)
      (deref (future (.waitFor process)) timeout :timeout))))

(defn stdin-stream
  "Returns an OutputStream connected to the stdin of 'process'"
  [process]
  (:stdin process))

(defn stdout-stream
  "Returns an InputStream containing the contents of stdout for 'process'"
  [process]
  (:stdout process))

(defn stdout-seq
  "Returns a lazy sequence containing the lines of text in stdout for 'process'"
  [process]
  (stream->seq (stdout-stream process)))

(defn stderr-stream
  "Returns an InputStream containing the contents of stderr for 'process'.
  A nil will be returned if :redirect-stderr was passed to 'exec' when
  launching the process."
  [process]
  (:stderr process))

(defn stderr-seq
  "Returns a lazy sequence containing the lines of text in stderr for 'process'.
  A nil will be returned if :redirect-stderr was passed to 'exec' when
  launching the process."
  [process]
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
                     redirect-stderr]
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

(defn pipe
  "Executes a series of 'processes' piping the stdout from one process into
  the stdin for the next. The stdin for the first process and the stdout for
  the last process are exposed. Processes are created with 'exec'"
  [& processes]
  (->> processes
       (partition 2 1)
       (pmap (fn [[src dest]]
               (with-open [out ^InputStream (stdout-stream src)
                           in ^OutputStream (stdin-stream dest)]
                 (io/copy out in))))
       doall)
  {:processes processes
   :stdin (stdin-stream (first processes))
   :stdout (stdout-stream (last processes))})


;;; Private

(defn- prepare-command
  [command]
  (let [split #(split % #"\s+" :quote-chars [\" \'])
        command (->> (cond-> command (string? command) split)
                     (mapcat (fn [segment]
                               (if (vector? segment)
                                 [(st/join " " segment)]
                                 (split segment)))))]
    (let [command-list (ArrayList.)]
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
