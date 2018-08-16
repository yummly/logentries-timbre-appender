# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## 0.1.1 - 2018-08-16

- Remove `:properties` map from analyzed errors returned by aviso, helps prevent cheshire json conversion errors
- Put `newline` writing for connection in a `finally` to make sure any errors on a line don't effect the following logging message

## 0.1.0 - 2018-02-16
### Added
Initial Release

[Unreleased]: https://github.com/tanzoniteblack/logentries-timbre-appender/compare/0.1.0...HEAD
