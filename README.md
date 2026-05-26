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

Add `pitest-history` as a `<dependency>` of the `pitest-maven`
plugin block. Pitest scans its plugin classpath for
`META-INF/services` registrations and discovers `DefaultHistoryFactory`
automatically.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-maven</artifactId>
            <version>1.25.0</version>
            <dependencies>
                <dependency>
                    <groupId>io.github.mibimiflo</groupId>
                    <artifactId>pitest-history</artifactId>
                    <version>0.1.2</version>
                </dependency>
            </dependencies>
            <configuration>
                <historyInputFile>${project.build.directory}/pit/history.bin</historyInputFile>
                <historyOutputFile>${project.build.directory}/pit/history.bin</historyOutputFile>
            </configuration>
        </plugin>
    </plugins>
</build>
```

With both `<historyInputFile>` and `<historyOutputFile>` pointing at
the same path, the second `mvn pitest:mutationCoverage` run reuses
results from the first.

### Convenience: `<withHistory>`

Pitest has a built-in shortcut that picks a temp-dir history file
for you:

```xml
<configuration>
    <withHistory>true</withHistory>
</configuration>
```

Same effect, but the file path is
`${java.io.tmpdir}/<groupId>.<artifactId>.<version>_pitest_history.bin`.
Handy for local development; the explicit-path form is better for CI
where `target/` survives between steps but `/tmp/` may not.

## Install — Command line

Add the jar to the pitest CLI classpath:

```
java -cp pitest-1.25.0.jar:pitest-entry-1.25.0.jar:pitest-history-0.1.0.jar:... \
     org.pitest.mutationtest.commandline.MutationCoverageReport \
     --historyInputLocation=history.bin \
     --historyOutputLocation=history.bin \
     ...
```

## Verifying the plugin is loaded

After `mvn pitest:mutationCoverage`, the log should contain one of:

```
INFO : Will read history at .../history.bin
INFO : Will write history to .../history.bin
INFO : Incremental analysis reduced number of mutations by N
```

If you instead see this message, the plugin is **not** on the
classpath — re-check the `<dependencies>` block on `pitest-maven`,
the coordinates, and that the artifact is in a reachable repository:

```
History has been enabled but no history plugin has been installed/activated.
If you are using https://www.arcmutate.com remember to activate the history
plugin with +arcmutate_history
```

That's pitest's `ErroringHistoryFactory` speaking — the fallback that
fires when no `HistoryFactory` plugin can be discovered.

## Disabling temporarily

The plugin's feature key is `default_history` (declared
`withOnByDefault(true)`). To disable it for a single run without
removing the dependency:

```bash
mvn pitest:mutationCoverage -Dfeatures="-default_history"
```

Or via POM:

```xml
<configuration>
    <features>
        <feature>-default_history</feature>
    </features>
</configuration>
```

When disabled, pitest falls back to `ErroringHistoryFactory` — so if
you've also set `historyInputFile`/`historyOutputFile`, the build will
fail with the "no history plugin" message. Either remove the history
file configuration when disabling the feature, or just leave the
feature on.

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
