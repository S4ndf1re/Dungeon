#!/bin/zsh
# Runs an inference probe on a clojure snippet. Usage: run-probe.sh '<script>'
set -e
cd "/Users/jankleinmann/development/java/my-dungeon-ir/dsl"

BASE=$(clj -Spath -Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.12.1"}
                                  metosin/malli {:mvn/version "0.20.2"}
                                  com.phronemophobic/clj-graphviz {:mvn/version "0.6.4"}
                                  com.taoensso/nippy {:mvn/version "3.6.2"}}}')

JARS=$(find ~/.gradle/caches/modules-2/files-2.1 \( \
  -name "commons-lang3-[0-9]*.jar" -o \
  -name "jackson-databind-3*.jar" -o \
  -name "jackson-core-3*.jar" -o \
  -name "tools.jackson.core-jackson-annotations*.jar" -o \
  -name "org.eclipse.lsp4j-1*.jar" -o \
  -name "org.eclipse.lsp4j.debug-1*.jar" -o \
  -name "org.eclipse.lsp4j.jsonrpc*.jar" -o \
  -name "eclipse-collections-1*.jar" -o \
  -name "eclipse-collections-api-1*.jar" \) 2>/dev/null | grep -v sources | grep -v javadoc | paste -sd : -)

CP="src/clj:test/clj:../dgir/build/classes/java/main:../dgir/vm/build/classes/java/main:../dgir/build/resources/main:$JARS:$BASE"

exec java -cp "$CP" clojure.main -e "(require 'probe) (probe/infer-script \"$1\")"
