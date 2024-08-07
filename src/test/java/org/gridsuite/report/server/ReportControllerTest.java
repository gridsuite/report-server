/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import com.jayway.jsonpath.Configuration;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import lombok.SneakyThrows;
import org.gridsuite.report.server.dto.Report;
import org.gridsuite.report.server.entities.TreeReportEntity;
import org.gridsuite.report.server.repositories.TreeReportRepository;
import org.gridsuite.report.server.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.report.server.utils.TestUtils.assertRequestsCount;
import static org.junit.Assert.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = {ReportApplication.class})
public class ReportControllerTest {

    public static final String URL_TEMPLATE = "/" + ReportApi.API_VERSION;
    @Autowired
    private MockMvc mvc;

    @Autowired
    private TreeReportRepository treeReportRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    ObjectMapper objectMapper;

    @Before
    public void setUp() {
        Configuration.defaultConfiguration();
        MockitoAnnotations.openMocks(this);
        reportService.deleteAll();
        SQLStatementCountValidator.reset();
    }

    @After
    public void tearDown() {
        reportService.deleteAll();
    }

    private static final String REPORT_UUID = "7165e1a1-6aa5-47a9-ba55-d1ee4e234d13";

    private static final String REPORT_ONE = "/reportOne.json";
    private static final String REPORT_TWO = "/reportTwo.json";
    private static final String REPORT_THREE = "/reportThree.json";
    private static final String REPORT_CONCAT = "/reportConcat.json";
    private static final String REPORT_CONCAT2 = "/reportConcat2.json";
    private static final String EXPECTED_SINGLE_REPORT = "/expectedSingleReport.json";
    private static final String EXPECTED_STRUCTURE_ONLY_REPORT1 = "/expectedStructureOnlyReportOne.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1 = "/expectedStructureAndElementsReportOne.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_ERRORS = "/expectedStructureAndElementsReportOneWithOnlyErrors.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_INFOS = "/expectedStructureAndElementsReportOneWithOnlyInfos.json";
    private static final String EXPECTED_STRUCTURE_AND_NO_REPORT_ELEMENT = "/expectedStructureAndNoElementReportOne.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORTER1 = "/expectedReporterAndElements.json";
    private static final String EXPECTED_STRUCTURE_AND_NO_REPORTER_ELEMENT = "/expectedReporterAndNoElement.json";
    private static final String DEFAULT_EMPTY_REPORT1 = "/defaultEmptyReport1.json";
    private static final String DEFAULT_EMPTY_REPORT_WITH_ID = "/defaultEmptyReportWithId.json";
    private static final String DEFAULT_EMPTY_REPORT2 = "/defaultEmptyReport2.json";
    private static final String REPORT_LOADFLOW = "/reportLoadflow.json";

    public String toString(String resourceName) {
        try {
            return new String(ByteStreams.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream(resourceName))), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void test() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();
        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_SINGLE_REPORT));

        // insert a second Report
        insertReport(REPORT_UUID, toString(REPORT_TWO));
        // now we have 2 Reports in the result
        testImported(REPORT_UUID, REPORT_CONCAT);

        // idem with a 3rd Report
        insertReport(REPORT_UUID, toString(REPORT_THREE));
        testImported(REPORT_UUID, REPORT_CONCAT2);

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID)).andExpect(status().isOk());

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID)).andExpect(status().isNotFound());

        MvcResult resultAfterDeletion = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
                .andExpect(status().isOk())
                .andReturn();
        assertReportListsAreEqualIgnoringIds(resultAfterDeletion, toString(DEFAULT_EMPTY_REPORT1));
    }

    @Test
    public void testGetReport() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        // expect 6 batched inserts of different tables and no updates
        assertRequestsCount(2, 6, 0, 0);
        SQLStatementCountValidator.reset();

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_ONLY_REPORT1));
        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetReportWithSeverityFilters() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1));
        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetReportWithExactMatchingTrue() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        final String filterValue = "roundTripReporterJsonTest";
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR&reportNameFilter=" + filterValue + "&reportNameMatchingType=EXACT_MATCHING"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1));
        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetReportWithExactMatchingFalse() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        final String filterValue = "__noMatchingValue__";
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?reportNameFilter=" + filterValue + "&reportNameMatchingType=EXACT_MATCHING"))
                .andExpect(status().isOk())
                .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(DEFAULT_EMPTY_REPORT1));
        assertRequestsCount(2, 0, 0, 0);
    }

    @Test
    public void testGetReportWithEndsWithTrue() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        final String filterEndsWithValue = "ReporterJsonTest";
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR&reportNameFilter=" + filterEndsWithValue + "&reportNameMatchingType=ENDS_WITH"))
                .andExpect(status().isOk())
                .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1));
        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetReportWithEndsWithFalse() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        final String filterEndsWithValue = "__noMatchingValue__";

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?reportNameFilter=" + filterEndsWithValue + "&reportNameMatchingType=ENDS_WITH"))
                .andExpect(status().isOk())
                .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(DEFAULT_EMPTY_REPORT1));
        assertRequestsCount(2, 0, 0, 0);
    }

    @Test
    public void testGetReportWithNoSeverityFilters() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
                .andExpect(status().isOk())
                .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_NO_REPORT_ELEMENT));
        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetReportWithSeverityFiltersOnError() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_ERRORS));
        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetReportWithSeverityFiltersOnInfo() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true&severityLevels=INFO"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_INFOS));
        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetSubReport() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        List<TreeReportEntity> reporters = treeReportRepository.findByName("UcteReading");
        assertEquals(1, reporters.size());
        String uuidReporter = reporters.get(0).getIdNode().toString();

        SQLStatementCountValidator.reset();

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/subreports/" + uuidReporter + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORTER1));
        assertRequestsCount(3, 0, 0, 0);
    }

    @Test
    public void testGetSubReportWithNoSeverityFilters() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        List<TreeReportEntity> reporters = treeReportRepository.findByName("UcteReading");
        assertEquals(1, reporters.size());
        String uuidReporter = reporters.get(0).getIdNode().toString();

        SQLStatementCountValidator.reset();

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/subreports/" + uuidReporter))
                .andExpect(status().isOk())
                .andReturn();

        assertReportsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_NO_REPORTER_ELEMENT));
        assertRequestsCount(3, 0, 0, 0);
    }

    @SneakyThrows
    @Test
    public void testDefaultEmptyReport() {
        MvcResult result1 = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();
        assertReportListsAreEqualIgnoringIds(result1, toString(DEFAULT_EMPTY_REPORT1));

        MvcResult result2 = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?defaultName=test"))
            .andExpect(status().isOk())
            .andReturn();
        assertReportListsAreEqualIgnoringIds(result2, toString(DEFAULT_EMPTY_REPORT2));
    }

    @Test
    public void testDeleteSubreports() throws Exception {
        String testReportLoadflow = toString(REPORT_LOADFLOW);
        insertReport(REPORT_UUID, testReportLoadflow);

        // Expect 5 batched inserts only and no updates
        assertRequestsCount(2, 5, 0, 0);

        Map<UUID, String> reportsKeys = new HashMap<>();
        reportsKeys.put(UUID.fromString(REPORT_UUID), "LoadFlow");

        mvc.perform(delete(URL_TEMPLATE + "/treereports")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reportsKeys)))
            .andExpect(status().isOk())
            .andReturn();

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT_WITH_ID)));
    }

    @Test
    public void testDeleteReportWithFilter() throws Exception {
        String testReportLoadflow = toString(REPORT_LOADFLOW);
        insertReport(REPORT_UUID, testReportLoadflow);
        Map<UUID, String> reportsKeys = new HashMap<>();
        final String reportType = "LoadFlow";
        reportsKeys.put(UUID.fromString(REPORT_UUID), reportType);

        SQLStatementCountValidator.reset();

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?reportTypeFilter=" + reportType)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportsKeys)))
                .andExpect(status().isOk())
                .andReturn();

        assertRequestsCount(3, 0, 0, 8);

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT_WITH_ID)));
    }

    @Test
    public void testDeleteReportWithBadFilter() throws Exception {
        String testReportLoadflow = toString(REPORT_LOADFLOW);
        insertReport(REPORT_UUID, testReportLoadflow);
        Map<UUID, String> reportsKeys = new HashMap<>();
        final String reportType = "LoadFlow";
        reportsKeys.put(UUID.fromString(REPORT_UUID), reportType);

        SQLStatementCountValidator.reset();

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?reportTypeFilter=noMatchingFilter")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportsKeys)))
                .andExpect(status().isOk())
                .andReturn();

        // no deletion here
        assertRequestsCount(1, 0, 0, 0);

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
                .andExpect(status().isOk());
    }

    private void testImported(String reportId, String expectedResult) throws Exception {
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + reportId + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();
        assertReportListsAreEqualIgnoringIds(result, toString(expectedResult));
    }

    private void insertReport(String reportId, String content) throws Exception {
        mvc.perform(put(URL_TEMPLATE + "/reports/" + reportId)
            .content(content)
            .contentType(APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    private void assertReportListsAreEqualIgnoringIds(MvcResult result, String expectedContent) throws JsonProcessingException, UnsupportedEncodingException {
        List<Report> expectedReportList = objectMapper.readValue(expectedContent, new TypeReference<>() { });
        List<Report> actualReportList = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        TestUtils.assertReportListsAreEqualIgnoringIds(expectedReportList, actualReportList);
    }

    private void assertReportsAreEqualIgnoringIds(MvcResult result, String expectedContent) throws JsonProcessingException, UnsupportedEncodingException {
        Report expectedReport = objectMapper.readValue(expectedContent, new TypeReference<>() { });
        Report actualReport = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        TestUtils.assertReportsAreEqualIgnoringIds(expectedReport, actualReport);
    }
}
