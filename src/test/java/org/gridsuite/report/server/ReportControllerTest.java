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
import org.gridsuite.report.server.dto.ReportLog;
import org.gridsuite.report.server.entities.ReportNodeEntity;
import org.gridsuite.report.server.repositories.ReportNodeRepository;
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

import static org.gridsuite.report.server.utils.TestUtils.*;
import static org.junit.Assert.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    private ReportService reportService;

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    private ReportNodeRepository reportNodeRepository;

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
    private static final String REPORT_FOUR = "/reportFour.json";
    private static final String REPORT_CONCAT = "/reportConcat.json";
    private static final String REPORT_CONCAT2 = "/reportConcat2.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1 = "/expectedStructureAndElementsReportOne.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_ERRORS = "/expectedStructureAndElementsReportOneWithOnlyErrors.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_INFOS = "/expectedStructureAndElementsReportOneWithOnlyInfos.json";
    private static final String EXPECTED_REPORT_MESSAGE_WITH_MESSAGE_FILTER = "/expectedReportMessagesWithMessageFilter.json";
    private static final String EXPECTED_REPORT_MESSAGE_WITHOUT_FILTERS = "/expectedReportMessagesWithoutFilters.json";
    private static final String EXPECTED_REPORT_MESSAGE_WITH_SEVERITY_FILTERS = "/expectedReportMessagesWithSeverityFilter.json";
    private static final String EXPECTED_REPORT_MESSAGE_WITH_SEVERITY_AND_MESSAGE_FILTERS = "/expectedReportMessagesWithSeverityAndMessageFilter.json";
    private static final String DEFAULT_EMPTY_REPORT1 = "/defaultEmpty1.json";
    private static final String DEFAULT_EMPTY_REPORT2 = "/defaultEmpty2.json";

    public String toString(String resourceName) {
        try {
            return new String(ByteStreams.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream(resourceName))), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testAppendReports() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();
        assertReportsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1));

        insertReport(REPORT_UUID, toString(REPORT_TWO));

        testImported(REPORT_UUID, REPORT_CONCAT);

        insertReport(REPORT_UUID, toString(REPORT_ONE));
        testImported(REPORT_UUID, REPORT_CONCAT2);

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID)).andExpect(status().isOk());

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID)).andExpect(status().isNotFound());

        MvcResult resultAfterDeletion = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
                .andExpect(status().isOk())
                .andReturn();
        assertReportsAreEqualIgnoringIds(resultAfterDeletion, toString(DEFAULT_EMPTY_REPORT1));
    }

    @Test
    public void testGetReportWithNoSeverityFilters() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();

        assertReportsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1));
        assertRequestsCount(2, 0, 0, 0);
    }

    @Test
    public void testGetReportWithSeverityFilters() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1));
        assertRequestsCount(2, 0, 0, 0);
    }

    @Test
    public void testGetReportWithSeverityFiltersOnError() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_ERRORS));
        assertRequestsCount(2, 0, 0, 0);
    }

    @Test
    public void testGetReportWithSeverityFiltersOnInfo() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=INFO"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_INFOS));
        assertRequestsCount(2, 0, 0, 0);
    }

    @Test
    public void testGetReportMessages() throws Exception {
        String testReport4 = toString(REPORT_FOUR);
        insertReport(REPORT_UUID, testReport4);

        List<ReportNodeEntity> reporters = reportNodeRepository.findAllByMessage("Reading UCTE network file");
        assertEquals(1, reporters.size());
        String uuidReporter = reporters.get(0).getId().toString();

        SQLStatementCountValidator.reset();

        //Test without filters
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + uuidReporter + "/logs"))
                .andExpect(status().isOk())
                .andReturn();

        assertReportMessagesAreEqual(result, toString(EXPECTED_REPORT_MESSAGE_WITHOUT_FILTERS));
        assertRequestsCount(1, 0, 0, 0);
        SQLStatementCountValidator.reset();

        //Test with a filter on the message that will return results
        result = mvc.perform(get(URL_TEMPLATE + "/reports/" + uuidReporter + "/logs?message=line"))
                .andExpect(status().isOk())
                .andReturn();

        assertReportMessagesAreEqual(result, toString(EXPECTED_REPORT_MESSAGE_WITH_MESSAGE_FILTER));
        assertRequestsCount(1, 0, 0, 0);
        SQLStatementCountValidator.reset();

        //Test with a filter on the message that won't return results
        result = mvc.perform(get(URL_TEMPLATE + "/reports/" + uuidReporter + "/logs?message=thisfilterwontbematched"))
                .andExpect(status().isOk())
                .andReturn();

        TypeReference<List<ReportLog>> listTypeReference = new TypeReference<>() { };
        List<ReportLog> response = objectMapper.readValue(result.getResponse().getContentAsString(), listTypeReference);
        assertEquals(0, response.size());
        assertRequestsCount(1, 0, 0, 0);
        SQLStatementCountValidator.reset();

        //Test with a filter on the severity that will return results
        result = mvc.perform(get(URL_TEMPLATE + "/reports/" + uuidReporter + "/logs?severityLevels=INFO"))
                .andExpect(status().isOk())
                .andReturn();

        assertReportMessagesAreEqual(result, toString(EXPECTED_REPORT_MESSAGE_WITH_SEVERITY_FILTERS));
        assertRequestsCount(1, 0, 0, 0);
        SQLStatementCountValidator.reset();

        //Test with a filter on the severity that won't return results
        result = mvc.perform(get(URL_TEMPLATE + "/reports/" + uuidReporter + "/logs?severityLevels=NO"))
                .andExpect(status().isOk())
                .andReturn();

        response = objectMapper.readValue(result.getResponse().getContentAsString(), listTypeReference);
        assertEquals(0, response.size());
        assertRequestsCount(1, 0, 0, 0);
        SQLStatementCountValidator.reset();

        //Test with both filters on and expect some results
        result = mvc.perform(get(URL_TEMPLATE + "/reports/" + uuidReporter + "/logs?severityLevels=INFO&message=line"))
                .andExpect(status().isOk())
                .andReturn();

        assertReportMessagesAreEqual(result, toString(EXPECTED_REPORT_MESSAGE_WITH_SEVERITY_AND_MESSAGE_FILTERS));
        assertRequestsCount(1, 0, 0, 0);
        SQLStatementCountValidator.reset();
    }

    @SneakyThrows
    @Test
    public void testDefaultEmptyReport() {
        MvcResult result1 = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();
        assertReportsAreEqualIgnoringIds(result1, toString(DEFAULT_EMPTY_REPORT1));

        MvcResult result2 = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?defaultName=test"))
            .andExpect(status().isOk())
            .andReturn();
        assertReportsAreEqualIgnoringIds(result2, toString(DEFAULT_EMPTY_REPORT2));
    }

    @Test
    public void testDeleteReport() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);
        List<UUID> reportUuids = Arrays.asList(UUID.fromString(REPORT_UUID));
        String jsonContent = new ObjectMapper().writeValueAsString(reportUuids);
        mvc.perform(delete(URL_TEMPLATE + "/reports").content(jsonContent).contentType(APPLICATION_JSON)).andExpect(status().isOk());
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();
        assertReportsAreEqualIgnoringIds(result, toString(DEFAULT_EMPTY_REPORT1));
    }

    private void testImported(String report1Id, String reportConcat2) throws Exception {
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + report1Id + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();
        assertReportsAreEqualIgnoringIds(result, toString(reportConcat2));
    }

    private void insertReport(String reportsId, String content) throws Exception {
        mvc.perform(put(URL_TEMPLATE + "/reports/" + reportsId)
            .content(content)
            .contentType(APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    private void assertReportsAreEqualIgnoringIds(MvcResult result, String expectedContent) throws JsonProcessingException, UnsupportedEncodingException {
        Report expectedReportNode = objectMapper.readValue(expectedContent, Report.class);
        Report actualReportNode = objectMapper.readValue(result.getResponse().getContentAsString(), Report.class);
        TestUtils.assertReportsAreEqualIgnoringIds(expectedReportNode, actualReportNode);
    }

    private void assertReportMessagesAreEqual(MvcResult result, String expectedContent) throws JsonProcessingException, UnsupportedEncodingException {
        TypeReference<List<ReportLog>> listTypeReference = new TypeReference<>() { };
        List<ReportLog> expectedReportLogs = objectMapper.readValue(expectedContent, listTypeReference);
        List<ReportLog> actualReportLogs = objectMapper.readValue(result.getResponse().getContentAsString(), listTypeReference);
        TestUtils.assertReportMessagesAreEqual(expectedReportLogs, actualReportLogs);
    }
}
