# com.7theta/crusta

[![Current Version](https://img.shields.io/clojars/v/com.7theta/crusta.svg)](https://clojars.org/com.7theta/crusta)
[![GitHub license](https://img.shields.io/github/license/7theta/crusta.svg)](LICENSE)
[![Circle CI](https://circleci.com/gh/7theta/crusta.svg?style=shield)](https://circleci.com/gh/7theta/crusta)
[![Dependencies Status](https://jarkeeper.com/7theta/crusta/status.svg)](https://jarkeeper.com/7theta/crusta)

A library for executing external processes and managing their shell
environments.

The library centers around two functions in `crusta.core` - `run` and
`exec`.

`run` covers the most common use case of executing an external
process and capturing the contents of its `stdout` as a string. The
call returns a future, which can be dereferenced to get the contents of
`stdout`. If the process exits with a non-zero error code, an
`ex-info` is thrown containing the contents of `stderr` as well as the
exit code of the process.

`exec` provides for more sophisticated use cases like streaming the
output of a process as it is executing etc. The function is
accompanied by a series of functions allowing the caller to interact
with `stdout`, `stdin` and `stderr`. The following code segment prints
the contents of the `stdout` for a process as they become available
using `stdout-seq` which provides a lazy sequence of lines.

```clj
(require '[crusta.core :refer [exec stdout-seq]])

(let [p (exec "some-long-running-command with-parameters")]
  (doseq [line (stdout-seq p)]
    (println line)))
```

The 'command' passed to both functions can take the form of a single
string or a sequence of strings. Additionally environment variables
and working directories etc. can be specified via keyword parameters
documented in the doc strings of the individual functions.

## Copyright and License

Copyright © 2017 7theta

Distributed under the Eclipse Public License.
