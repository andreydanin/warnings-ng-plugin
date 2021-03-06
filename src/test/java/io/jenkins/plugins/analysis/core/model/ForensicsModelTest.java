package io.jenkins.plugins.analysis.core.model;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.analysis.Issue;
import edu.hm.hafner.analysis.Report;

import io.jenkins.plugins.analysis.core.model.ForensicsModel.ForensicsRow;
import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

import static io.jenkins.plugins.analysis.core.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link ForensicsModel}.
 *
 * @author Ullrich Hafner
 */
class ForensicsModelTest extends AbstractDetailsModelTest {
    private static final int EXPECTED_COLUMNS_SIZE = 7;
    private static final String FILE_NAME = "/path/to/file-1";

    @Test
    void shouldConvertIssueToArrayWithAllColumnsAndRows() {
        Report report = new Report();
        report.add(createIssue(1));
        report.add(createIssue(2));
        RepositoryStatistics statistics = mock(RepositoryStatistics.class);

        ForensicsModel model = createModel(statistics);

        assertThat(model.getHeaders(report)).hasSize(EXPECTED_COLUMNS_SIZE);
        assertThat(model.getWidths(report)).hasSize(EXPECTED_COLUMNS_SIZE);
        assertThat(model.getColumnsDefinition(report)).isEqualTo("["
                + "{\"data\": \"description\"},"
                + "{"
                + "  \"type\": \"string\","
                + "  \"data\": \"fileName\","
                + "  \"render\": {"
                + "     \"_\": \"display\","
                + "     \"sort\": \"sort\""
                + "  }"
                + "},"
                + "{\"data\": \"age\"},"
                + "{\"data\": \"authorsSize\"},"
                + "{\"data\": \"commitsSize\"},"
                + "{"
                + "  \"type\": \"num\","
                + "  \"data\": \"modifiedDays\","
                + "  \"render\": {"
                + "     \"_\": \"display\","
                + "     \"sort\": \"sort\""
                + "  }"
                + "},"
                + "{"
                + "  \"type\": \"num\","
                + "  \"data\": \"addedDays\","
                + "  \"render\": {"
                + "     \"_\": \"display\","
                + "     \"sort\": \"sort\""
                + "  }"
                + "}"
                + "]");
        assertThat(model.getContent(report)).hasSize(2);
    }

    @Test
    void shouldShowIssueWithForensics() {
        Report report = new Report();
        Issue issue = createIssue(1);
        report.add(issue);

        RepositoryStatistics statistics = mock(RepositoryStatistics.class);

        FileStatistics fileStatistics = mock(FileStatistics.class);
        when(fileStatistics.getNumberOfAuthors()).thenReturn(15);
        when(fileStatistics.getNumberOfCommits()).thenReturn(20);
        when(fileStatistics.getLastModifiedInDays()).thenReturn(25);
        when(fileStatistics.getAgeInDays()).thenReturn(30);

        when(statistics.get(FILE_NAME)).thenReturn(fileStatistics);
        when(statistics.contains(FILE_NAME)).thenReturn(true);

        ForensicsModel model = createModel(statistics);

        ForensicsRow actualRow = model.getRow(report, issue);
        assertThat(actualRow).hasDescription(EXPECTED_DESCRIPTION)
                .hasAge("1")
                .hasAuthorsSize("15")
                .hasCommitsSize("20");
        assertThat(actualRow.getFileName()).hasDisplay(createExpectedFileName(issue)).hasSort("/path/to/file-1:0000015");

        assertThat(actualRow.getModifiedDays()).hasDisplay("4 weeks ago").hasSort("25");
        assertThat(actualRow.getAddedDays()).hasDisplay("1 month ago").hasSort("30");
    }

    @Test
    void shouldShowIssueWithoutForensics() {
        Report report = new Report();
        Issue issue = createIssue(1);
        report.add(issue);

        RepositoryStatistics blames = mock(RepositoryStatistics.class);

        ForensicsModel model = createModel(blames);

        ForensicsRow actualRow = model.getRow(report, issue);
        assertThat(actualRow).hasDescription(EXPECTED_DESCRIPTION)
                .hasAge("1")
                .hasAuthorsSize(ForensicsModel.UNDEFINED)
                .hasCommitsSize(ForensicsModel.UNDEFINED);
        assertThat(actualRow.getFileName()).hasDisplay(createExpectedFileName(issue)).hasSort("/path/to/file-1:0000015");

        assertThat(actualRow.getModifiedDays()).hasSort("0");
        assertThat(actualRow.getAddedDays()).hasSort("0");
    }

    private ForensicsModel createModel(final RepositoryStatistics statistics) {
        return new ForensicsModel(createAgeBuilder(), createFileNameRenderer(), issue -> DESCRIPTION, statistics);
    }
}
