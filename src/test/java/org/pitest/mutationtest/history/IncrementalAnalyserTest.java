package org.pitest.mutationtest.history;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.allOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.pitest.mutationtest.DetectionStatus.KILLED;
import static org.pitest.mutationtest.DetectionStatus.NOT_STARTED;
import static org.pitest.mutationtest.DetectionStatus.SURVIVED;
import static org.pitest.mutationtest.DetectionStatus.TIMED_OUT;
import static org.pitest.mutationtest.LocationMother.aLocation;
import static org.pitest.mutationtest.LocationMother.aMutationId;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pitest.classinfo.ClassName;
import org.pitest.coverage.CoverageDatabase;
import org.pitest.coverage.TestInfo;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationStatusTestPair;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.util.Log;

public class IncrementalAnalyserTest {

  private IncrementalAnalyser testee;

  @Mock
  private CodeHistory history;

  @Mock
  private CoverageDatabase coverage;

  private LogCatcher logCatcher;

  @Before
  public void configureLogCatcher() {
    logCatcher = new LogCatcher();
    final Logger logger = Log.getLogger();
    logger.addHandler(logCatcher);
    logger.setLevel(Level.ALL);
  }

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    this.testee = new IncrementalAnalyser(this.history, this.coverage);
  }

  @After
  public void removeLogCatcher() {
    Log.getLogger().removeHandler(logCatcher);
  }

  @Test
  public void shouldNotPreprocessNewMutations() {
    final MutationDetails md = makeMutation("foo");
    when(this.history.getPreviousResult(any(MutationIdentifier.class)))
        .thenReturn(Optional.empty());

    final Collection<MutationResult> actual = this.testee.analyse(singletonList(md));

    assertThat(actual).isEmpty();
    assertThat(logCatcher.logEntries)
        .contains("Incremental analysis reduced number of mutations by 0");
  }

  @Test
  public void shouldStartPreviousSurvivedMutationsAtAStatusOfNotStartedWhenCoverageHasChanged() {
    final MutationDetails md = makeMutation("foo");
    setHistoryForAllMutationsTo(DetectionStatus.SURVIVED);
    when(coverage.getCoverageIdForClass(any(ClassName.class)))
        .thenReturn(BigInteger.ONE);
    when(this.history.hasCoverageChanged(any(ClassName.class), any(BigInteger.class)))
        .thenReturn(true);

    final Collection<MutationResult> actual = this.testee.analyse(singletonList(md));

    assertThat(actual).haveAtLeastOne(withStatus(NOT_STARTED));
    assertThat(logCatcher.logEntries).contains(
        "Incremental analysis set 1 mutations to a status of NOT_STARTED",
        "Incremental analysis reduced number of mutations by 0");
  }

  @Test
  public void shouldStartPreviousSurvivedMutationsAtAStatusOfSurvivedWhenCoverageHasNotChanged() {
    final MutationDetails md = makeMutation("foo");
    setHistoryForAllMutationsTo(DetectionStatus.SURVIVED);
    when(this.history.hasCoverageChanged(any(ClassName.class), any(BigInteger.class)))
        .thenReturn(false);

    final Collection<MutationResult> actual = this.testee.analyse(singletonList(md));

    assertThat(actual).haveAtLeastOne(withStatus(SURVIVED));
    assertThat(logCatcher.logEntries).contains(
        "Incremental analysis set 1 mutations to a status of SURVIVED",
        "Incremental analysis reduced number of mutations by 1");
  }

  @Test
  public void shouldStartPreviousTimedOutMutationsAtAStatusOfNotStartedWhenClassHasChanged() {
    final MutationDetails md = makeMutation("foo");
    setHistoryForAllMutationsTo(DetectionStatus.TIMED_OUT);
    when(this.history.hasClassChanged(any(ClassName.class))).thenReturn(true);

    final Collection<MutationResult> actual = this.testee.analyse(singletonList(md));

    assertThat(actual).haveAtLeastOne(withStatus(NOT_STARTED));
    assertThat(logCatcher.logEntries).contains(
        "Incremental analysis set 1 mutations to a status of NOT_STARTED",
        "Incremental analysis reduced number of mutations by 0");
  }

  @Test
  public void shouldStartPreviousTimedOutMutationsAtAStatusOfTimedOutWhenClassHasNotChanged() {
    final MutationDetails md = makeMutation("foo");
    setHistoryForAllMutationsTo(DetectionStatus.TIMED_OUT);
    when(this.history.hasClassChanged(any(ClassName.class))).thenReturn(false);

    final Collection<MutationResult> actual = this.testee.analyse(singletonList(md));

    assertThat(actual).haveAtLeastOne(withStatus(TIMED_OUT));
    assertThat(logCatcher.logEntries).contains(
        "Incremental analysis set 1 mutations to a status of TIMED_OUT",
        "Incremental analysis reduced number of mutations by 1");
  }

  @Test
  public void shouldStartPreviousKilledMutationsAtAStatusOfKilledWhenNeitherClassOrTestHasChanged() {
    final MutationDetails md = makeMutation("foo");
    final String killingTest = "fooTest";
    setHistoryForAllMutationsTo(DetectionStatus.KILLED, killingTest);

    final Collection<TestInfo> tests = Collections.singleton(new TestInfo(
        "TEST_CLASS", killingTest, 0, Optional.empty(), 0));
    when(this.coverage.getTestsForClass(any(ClassName.class))).thenReturn(tests);
    when(this.history.hasClassChanged(any(ClassName.class))).thenReturn(false);

    final Collection<MutationResult> actual = this.testee.analyse(singletonList(md));

    assertThat(actual).haveAtLeastOne(allOf(withStatus(KILLED), withKillingTest(killingTest)));
    assertThat(logCatcher.logEntries).contains(
        "Incremental analysis set 1 mutations to a status of KILLED",
        "Incremental analysis reduced number of mutations by 1");
  }

  @Test
  public void shouldStartPreviousKilledMutationsAtAStatusOfKilledWhenNeitherClassHasChangedNorTestHasChangedForAtLeastOneKillingTest() {
    final MutationDetails md = makeMutation("foo");
    final String killingTestChanged = "fooTest";
    final String killingTestUnchanged = "killerTest";

    setHistoryForAllMutationsTo(DetectionStatus.KILLED, killingTestChanged, killingTestUnchanged);

    final TestInfo testChanged = new TestInfo("TEST_CLASS_CHANGED", killingTestChanged, 0, Optional.empty(), 0);
    final TestInfo testUnchanged = new TestInfo("TEST_CLASS_UNCHANGED", killingTestUnchanged, 0, Optional.empty(), 0);

    when(this.coverage.getTestsForClass(ClassName.fromString("clazz")))
        .thenReturn(asList(testChanged, testUnchanged));

    when(this.history.hasClassChanged(ClassName.fromString("clazz"))).thenReturn(false);
    when(this.history.hasClassChanged(ClassName.fromString("TEST_CLASS_CHANGED"))).thenReturn(true);
    when(this.history.hasClassChanged(ClassName.fromString("TEST_CLASS_UNCHANGED"))).thenReturn(false);

    final Collection<MutationResult> actual = this.testee.analyse(singletonList(md));

    assertThat(actual).haveAtLeastOne(allOf(withStatus(KILLED), withKillingTest(killingTestUnchanged)));
    assertThat(logCatcher.logEntries).contains(
        "Incremental analysis set 1 mutations to a status of KILLED",
        "Incremental analysis reduced number of mutations by 1");
  }

  @Test
  public void shouldStartPreviousKilledMutationsAtAStatusOfNotStartedWhenTestHasChanged() {
    final MutationDetails md = makeMutation("foo");
    final String killingTest = "fooTest";
    setHistoryForAllMutationsTo(DetectionStatus.KILLED, killingTest);

    final Collection<TestInfo> tests = Collections.singleton(new TestInfo(
        "TEST_CLASS", killingTest, 0, Optional.empty(), 0));
    when(this.coverage.getTestsForClass(any(ClassName.class))).thenReturn(tests);
    when(this.history.hasClassChanged(ClassName.fromString("clazz"))).thenReturn(false);
    when(this.history.hasClassChanged(ClassName.fromString("TEST_CLASS"))).thenReturn(true);

    final Collection<MutationResult> actual = this.testee.analyse(singletonList(md));

    assertThat(actual).haveAtLeastOne(withStatus(NOT_STARTED));
    assertThat(logCatcher.logEntries).contains(
        "Incremental analysis set 1 mutations to a status of NOT_STARTED",
        "Incremental analysis reduced number of mutations by 0");
  }

  @Test
  public void prioritisesLastKillingTestWhenClassHasChanged() {
    final String killingTest = "fooTest";
    setHistoryForAllMutationsTo(DetectionStatus.KILLED, killingTest);
    Collection<TestInfo> tests = Arrays.asList(testNamed("one"), testNamed("two"), testNamed(killingTest), testNamed("three"));

    final MutationDetails md = makeMutation("foo");
    md.addTestsInOrder(tests);

    when(this.coverage.getTestsForClass(any(ClassName.class))).thenReturn(tests);
    when(this.history.hasClassChanged(ClassName.fromString("clazz"))).thenReturn(true);
    when(this.history.hasClassChanged(ClassName.fromString("TEST_CLASS"))).thenReturn(false);

    MutationResult actual = this.testee.analyse(singletonList(md)).stream().findFirst().get();

    assertThat(actual.getDetails().getTestsInOrder()).hasSize(4);
    assertThat(actual.getDetails().getTestsInOrder().get(0)).isEqualTo(testNamed(killingTest));
  }

  @Test
  public void prioritisesLastKillingTestWhenTestHasChanged() {
    final String killingTest = "fooTest";
    setHistoryForAllMutationsTo(DetectionStatus.KILLED, killingTest);
    Collection<TestInfo> tests = Arrays.asList(testNamed("one"), testNamed("two"), testNamed(killingTest), testNamed("three"));

    final MutationDetails md = makeMutation("foo");
    md.addTestsInOrder(tests);

    when(this.coverage.getTestsForClass(any(ClassName.class))).thenReturn(tests);
    when(this.history.hasClassChanged(ClassName.fromString("clazz"))).thenReturn(false);
    when(this.history.hasClassChanged(ClassName.fromString("TEST_CLASS"))).thenReturn(true);

    MutationResult actual = this.testee.analyse(singletonList(md)).stream().findFirst().get();

    assertThat(actual.getDetails().getTestsInOrder()).hasSize(4);
    assertThat(actual.getDetails().getTestsInOrder().get(0)).isEqualTo(testNamed(killingTest));
  }

  @Test
  public void assessMultipleMutationsAtATime() {
    final MutationDetails md1 = makeMutation("foo");
    final MutationDetails md2 = makeMutation("bar");
    final MutationDetails md3 = makeMutation("baz");
    final MutationDetails md4 = makeMutation("bumm");

    final String killingTest = "killerTest";
    final TestInfo test = new TestInfo("TEST_CLASS", killingTest, 0, Optional.empty(), 0);

    when(this.history.getPreviousResult(md1.getId()))
        .thenReturn(Optional.of(new MutationStatusTestPair(0, KILLED,
            singletonList(killingTest), emptyList(), emptyList())));
    when(this.history.getPreviousResult(md2.getId()))
        .thenReturn(Optional.of(new MutationStatusTestPair(0, KILLED,
            singletonList(killingTest), emptyList(), emptyList())));
    when(this.history.getPreviousResult(md3.getId()))
        .thenReturn(Optional.of(new MutationStatusTestPair(0, SURVIVED,
            emptyList(), emptyList(), emptyList())));
    when(this.history.getPreviousResult(md4.getId()))
        .thenReturn(Optional.empty());

    when(this.coverage.getTestsForClass(ClassName.fromString("clazz")))
        .thenReturn(singletonList(test));

    when(this.history.hasClassChanged(ClassName.fromString("clazz"))).thenReturn(false);
    when(this.history.hasClassChanged(ClassName.fromString("TEST_CLASS"))).thenReturn(false);

    final Collection<MutationResult> actual = this.testee.analyse(asList(md1, md2, md3, md4));

    assertThat(actual).extracting(MutationResult::getStatus)
        .containsExactly(KILLED, KILLED, SURVIVED);
    assertThat(logCatcher.logEntries).contains(
        "Incremental analysis set 2 mutations to a status of KILLED",
        "Incremental analysis set 1 mutations to a status of SURVIVED",
        "Incremental analysis reduced number of mutations by 3");
  }

  private TestInfo testNamed(String name) {
    return new TestInfo("TEST_CLASS", name, 0, Optional.empty(), 0);
  }

  private static Condition<MutationResult> withStatus(final DetectionStatus status) {
    return new Condition<>(mr -> status.equals(mr.getStatus()),
        "a mutation result with status %s", status);
  }

  private static Condition<MutationResult> withKillingTest(final String expected) {
    return new Condition<>(
        mr -> mr.getKillingTest().filter(expected::equals).isPresent(),
        "a mutation result with killing test named %s", expected);
  }

  private MutationDetails makeMutation(final String method) {
    final MutationIdentifier id = aMutationId().withLocation(
        aLocation().withMethod(method)).build();
    return new MutationDetails(id, "file", "desc", 1, 2);
  }

  private void setHistoryForAllMutationsTo(final DetectionStatus status) {
    setHistoryForAllMutationsTo(status, "bar");
  }

  private void setHistoryForAllMutationsTo(final DetectionStatus status, final String... test) {
    when(this.history.getPreviousResult(any(MutationIdentifier.class)))
        .thenReturn(Optional.of(new MutationStatusTestPair(0, status,
            asList(test), emptyList(), emptyList())));
  }

  private static class LogCatcher extends Handler {

    final ArrayList<String> logEntries = new ArrayList<>();

    @Override
    public void publish(final LogRecord record) {
      logEntries.add(record.getMessage());
    }

    @Override
    public void flush() {
      // no-op
    }

    @Override
    public void close() throws SecurityException {
      // no-op
    }
  }
}
