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
import com.powsybl.commons.report.ReportNode;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import lombok.SneakyThrows;
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
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.Assert.*;

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
    private static final String REPORT_CONCAT = "/reportConcat.json";
    private static final String REPORT_CONCAT2 = "/reportConcat2.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1 = "/expectedStructureAndElementsReportOne.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_ERRORS = "/expectedStructureAndElementsReportOneWithOnlyErrors.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_INFOS = "/expectedStructureAndElementsReportOneWithOnlyInfos.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORTER1 = "/expectedReporterAndElements.json";
    private static final String DEFAULT_EMPTY_REPORT1 = "/defaultEmpty1.json";
    private static final String DEFAULT_EMPTY_REPORT2 = "/defaultEmpty2.json";
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
        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1));

        insertReport(REPORT_UUID, toString(REPORT_TWO));

        testImported(REPORT_UUID, REPORT_CONCAT);

        insertReport(REPORT_UUID, toString(REPORT_ONE));
        testImported(REPORT_UUID, REPORT_CONCAT2);

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID)).andExpect(status().isOk());

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID)).andExpect(status().isNotFound());

        MvcResult resultAfterDeletion = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
                .andExpect(status().isOk())
                .andReturn();
        assertReportListsAreEqualIgnoringIds(resultAfterDeletion, toString(DEFAULT_EMPTY_REPORT1));
    }

    @Test
    public void testGetReportWithNoSeverityFilters() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1));
        assertRequestsCount(72, 0, 0, 0);
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
        assertRequestsCount(72, 0, 0, 0);
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
        assertRequestsCount(72, 0, 0, 0);
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
        assertRequestsCount(5, 0, 0, 0);
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
        assertRequestsCount(72, 0, 0, 0);
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
        assertRequestsCount(5, 0, 0, 0);
    }

    @Test
    public void testGetReportWithSeverityFiltersOnError() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_ERRORS));
        assertRequestsCount(62, 0, 0, 0);
    }

    @Test
    public void testGetReportWithSeverityFiltersOnInfo() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?severityLevels=INFO"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1_ONLY_WITH_INFOS));
        assertRequestsCount(59, 0, 0, 0);
    }

    @Test
    public void testGetSubReport() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        List<ReportNodeEntity> reporters = reportNodeRepository.findByMessageTemplateKey("UcteReading");
        assertEquals(1, reporters.size());
        String uuidReporter = reporters.get(0).getId().toString();

        SQLStatementCountValidator.reset();

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/subreports/" + uuidReporter + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORTER1));
        assertRequestsCount(27, 0, 0, 0);
    }

    @Test
    public void testGetSubReportWithNoSeverityFilters() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        List<ReportNodeEntity> reporters = reportNodeRepository.findByMessageTemplateKey("UcteReading");
        assertEquals(1, reporters.size());
        String uuidReporter = reporters.get(0).getId().toString();

        SQLStatementCountValidator.reset();

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/subreports/" + uuidReporter))
                .andExpect(status().isOk())
                .andReturn();

        assertReportListsAreEqualIgnoringIds(result, toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORTER1));
        assertRequestsCount(27, 0, 0, 0);
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

        Map<UUID, String> reportsKeys = new HashMap<>();
        reportsKeys.put(UUID.fromString(REPORT_UUID), "LoadFlow");

        SQLStatementCountValidator.reset();
        mvc.perform(delete(URL_TEMPLATE + "/treereports")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reportsKeys)))
            .andExpect(status().isOk())
            .andReturn();
        assertRequestsCount(23, 0, 0, 14);

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();
        assertReportListsAreEqualIgnoringIds(result, toString(DEFAULT_EMPTY_REPORT1));
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
        assertRequestsCount(24, 0, 0, 15);

        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andReturn();
        assertReportListsAreEqualIgnoringIds(result, toString(DEFAULT_EMPTY_REPORT1));
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
        assertRequestsCount(5, 0, 0, 0);

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
                .andExpect(status().isOk());
    }

    private void testImported(String report1Id, String reportConcat2) throws Exception {
        MvcResult result = mvc.perform(get(URL_TEMPLATE + "/reports/" + report1Id + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andReturn();
        assertReportListsAreEqualIgnoringIds(result, toString(reportConcat2));
    }

    private void insertReport(String reportsId, String content) throws Exception {
        mvc.perform(put(URL_TEMPLATE + "/reports/" + reportsId)
            .content(content)
            .contentType(APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    private void assertReportListsAreEqualIgnoringIds(MvcResult result, String expectedContent) throws JsonProcessingException, UnsupportedEncodingException {
        List<ReportNode> expectedReportNodeList = objectMapper.readValue(expectedContent, new TypeReference<>() { });
        List<ReportNode> actualReportNodeList = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        TestUtils.assertReportListsAreEqualIgnoringIds(expectedReportNodeList, actualReportNodeList);
    }
}
