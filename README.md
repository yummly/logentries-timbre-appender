# logentries-timbre-appender

[![Build Status](https://travis-ci.org/yummly/logentries-timbre-appender.svg?branch=master)](https://travis-ci.org/yummly/logentries-timbre-appender)

A logger for [logentries](https://logentries.com/) for use with [timbre](https://github.com/ptaoussanis/timbre/).

## Usage

``` clojure
(timbre/set-config!
 {:appenders
  {:logentries
   (logentries-appender "my-logentries-log-token"
                        {:user-tags {:any-random "values you want to add to every message"}})}})
```

## License

Copyright © 2018 Yummly

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
