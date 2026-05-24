# pitest-history

A file-based incremental-analysis history plugin for [PIT mutation
testing](https://pitest.org).

Restores the default history support that was removed from pitest core
after version 1.16.3, packaged as a standalone plugin that works with
current pitest releases.

## Why this exists

PIT's incremental analysis lets a run reuse mutation results from a
previous run when the bytecode and coverage of a class haven't changed.
The implementation was extracted from pitest core in commit
[`1b87fddb`](https://github.com/hcoles/pitest/commit/1b87fddb) ("remove
default history analysis"), leaving an `ErroringHistoryFactory` that
points users at commercial plugins.

This project re-packages the pre-removal implementation as a free,
Apache-2.0-licensed plugin so the feature stays available on current
pitest versions — particularly useful if you need pitest's JDK 25
support and also want incremental analysis.

## Compatibility

| pitest-history | pitest    | Java |
|----------------|-----------|------|
| 0.1.x          | 1.25.x    | 11+  |

Built and tested against `org.pitest:pitest-entry:1.25.0`.

## Install — Maven

Add the plugin as a dependency of `pitest-maven`:

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.25.0</version>
    <dependencies>
        <dependency>
            <groupId>io.github.mibimiflo</groupId>
            <artifactId>pitest-history</artifactId>
            <version>0.1.0</version>
        </dependency>
    </dependencies>
    <configuration>
        <historyInputFile>${project.build.directory}/pitest/history.bin</historyInputFile>
        <historyOutputFile>${project.build.directory}/pitest/history.bin</historyOutputFile>
    </configuration>
</plugin>
```

With the plugin on the classpath, the `default_history` feature
activates automatically (it is declared `withOnByDefault(true)`).
You can disable it explicitly with `--features=-default_history`,
which causes pitest to fall back to its `ErroringHistoryFactory`.

## Install — Command line

Add the jar to the pitest CLI classpath:

```
java -cp pitest-1.25.0.jar:pitest-entry-1.25.0.jar:pitest-history-0.1.0.jar:... \
     org.pitest.mutationtest.commandline.MutationCoverageReport \
     --historyInputLocation=history.bin \
     --historyOutputLocation=history.bin \
     ...
```

## How it works

- Implements `org.pitest.mutationtest.HistoryFactory` (the pitest
  plugin SPI).
- Registered via `META-INF/services/org.pitest.mutationtest.HistoryFactory`,
  so pitest's `PluginServices.findHistory()` discovers it automatically.
- Provides `default_history` as an internal, on-by-default `Feature`.
- Stores each mutation result as a Base64-encoded `ObjectOutputStream`
  record, one per line. Class-path metadata is written as a header
  section keyed by `HierarchicalClassId` + coverage hash, so changes
  to either invalidate the cached result.
- The `IncrementalAnalyser` reuses prior results when:
  - `KILLED` — class hash unchanged and at least one historic killing
    test still exists with an unchanged test class hash.
  - `TIMED_OUT` — class hash unchanged.
  - `SURVIVED` — class hash and coverage id unchanged.
  - Otherwise, marks the mutation `NOT_STARTED` for re-analysis.

## Building from source

```
mvn clean verify
```

Requires JDK 21 or later (compiles to JDK 11 bytecode).

## Releasing

Tag a version (`vX.Y.Z`), then:

```
mvn -P release clean deploy
```

GPG signing and Sonatype Central publishing are wired in the `release`
profile. Set `MAVEN_USERNAME`, `MAVEN_PASSWORD`, `GPG_PRIVATE_KEY`,
`GPG_PASSPHRASE` in your environment / GitHub Actions secrets.

## Provenance and license

The four production classes under `org.pitest.mutationtest.history`
are derivative works of pitest's original 1.16.3 implementation,
licensed under Apache 2.0. See [NOTICE](NOTICE) for attribution and
[LICENSE.txt](LICENSE.txt) for the full license text.
