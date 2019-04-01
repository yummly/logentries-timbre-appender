# logentries-timbre-appender

[![Build Status](https://travis-ci.org/yummly/logentries-timbre-appender.svg?branch=master)](https://travis-ci.org/yummly/logentries-timbre-appender)
[![Clojars Project](https://img.shields.io/clojars/v/yummly/logentries-timbre-appender.svg)](https://clojars.org/yummly/logentries-timbre-appender)

A logger for [logentries](https://logentries.com/) for use with [timbre](https://github.com/ptaoussanis/timbre/).

## Usage

``` clojure
(timbre/set-config!
 {:appenders
  {:logentries
   (logentries-appender "my-logentries-log-token"
                        {:user-tags {:any-random "values you want to add to every message"}})}})
```

### Uploading to clojars

* `clojure -A:jar`
* `clojure -Spom`
* Edit pom.xml, fix the groupId, artifactId, version
* `CLOJARS_USERNAME=user CLOJARS_PASSWORD=password clojure -A:deploy`

See [tools.deps tools](https://github.com/clojure/tools.deps.alpha/wiki/Tools) for more information.

## License

Copyright © 2018 Yummly

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
