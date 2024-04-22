/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.report.server;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import lombok.SneakyThrows;
import org.gridsuite.report.server.entities.TreeReportEntity;
import org.gridsuite.report.server.repositories.TreeReportRepository;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.report.server.utils.TestUtils.assertRequestsCount;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private TreeReportRepository treeReportRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    ObjectMapper objectMapper;

    @Before
    public void setUp() {
        Configuration.defaultConfiguration();
        MockitoAnnotations.initMocks(this);
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);

        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider(objectMapper);
            private final MappingProvider mappingProvider = new JacksonMappingProvider(objectMapper);

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
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
    private static final String EXPECTED_SINGLE_REPORT = "/expectedSingleReport.json";
    private static final String EXPECTED_STRUCTURE_ONLY_REPORT1 = "/expectedStructureOnlyReportOne.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1 = "/expectedStructureAndElementsReportOne.json";
    private static final String EXPECTED_STRUCTURE_AND_NO_REPORT_ELEMENT = "/expectedStructureAndNoElementReportOne.json";
    private static final String EXPECTED_STRUCTURE_AND_ELEMENTS_REPORTER1 = "/expectedReporterAndElements.json";
    private static final String EXPECTED_STRUCTURE_AND_NO_REPORTER_ELEMENT = "/expectedReporterAndNoElement.json";
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

        String res = mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true&severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true&severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(EXPECTED_SINGLE_REPORT)));

        insertReport(REPORT_UUID, toString(REPORT_TWO));

        testImported(REPORT_UUID, REPORT_CONCAT);

        insertReport(REPORT_UUID, toString(REPORT_ONE));
        testImported(REPORT_UUID, REPORT_CONCAT2);

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID)).andExpect(status().isOk());

        mvc.perform(delete(URL_TEMPLATE + "/reports/" + REPORT_UUID)).andExpect(status().isNotFound());

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true"))
                .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT1)))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetReportNoElements() throws Exception {
         String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        // expect 6 batched inserts of different tables and no updates
        assertRequestsCount(2, 6, 0, 0);
        SQLStatementCountValidator.reset();

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(EXPECTED_STRUCTURE_ONLY_REPORT1)));

        assertRequestsCount(3, 0, 0, 0);
    }

    @Test
    public void testGetReportWithElements() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true&severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1)))
            .andReturn();

        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetReportWithExactMatchingTrue() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        final String filterValue = "roundTripReporterJsonTest";
        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true&severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR&reportNameFilter=" + filterValue + "&reportNameMatchingType=EXACT_MATCHING"))
                .andExpect(status().isOk())
                .andExpect(content().json(toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1)));

        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetReportWithExactMatchingFalse() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        final String filterValue = "__noMatchingValue__";
        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true&reportNameFilter=" + filterValue + "&reportNameMatchingType=EXACT_MATCHING"))
                .andExpect(status().isOk())
                .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT1)));

        assertRequestsCount(2, 0, 0, 0);
    }

    @Test
    public void testGetReportWithEndsWithTrue() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        final String filterEndsWithValue = "ReporterJsonTest";
        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true&severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR&reportNameFilter=" + filterEndsWithValue + "&reportNameMatchingType=ENDS_WITH"))
                .andExpect(status().isOk())
                .andExpect(content().json(toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORT1)));

        assertRequestsCount(4, 0, 0, 0);
    }

    @Test
    public void testGetReportWithEndsWithFalse() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();
        final String filterEndsWithValue = "__noMatchingValue__";

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true&reportNameFilter=" + filterEndsWithValue + "&reportNameMatchingType=ENDS_WITH"))
                .andExpect(status().isOk())
                .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT1)));

        assertRequestsCount(2, 0, 0, 0);
    }

    @Test
    public void testGetReportWithNoSeverityFilters() throws Exception {
        String testReport1 = toString(REPORT_ONE);
        insertReport(REPORT_UUID, testReport1);

        SQLStatementCountValidator.reset();

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true"))
                .andExpect(status().isOk())
                .andExpect(content().json(toString(EXPECTED_STRUCTURE_AND_NO_REPORT_ELEMENT)))
                .andReturn();

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

        mvc.perform(get(URL_TEMPLATE + "/subreports/" + uuidReporter + "?severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(EXPECTED_STRUCTURE_AND_ELEMENTS_REPORTER1)));

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

        mvc.perform(get(URL_TEMPLATE + "/subreports/" + uuidReporter))
                .andExpect(status().isOk())
                .andExpect(content().json(toString(EXPECTED_STRUCTURE_AND_NO_REPORTER_ELEMENT)));

        assertRequestsCount(3, 0, 0, 0);
    }

    @SneakyThrows
    @Test
    public void testDefaultEmptyReport() {
        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT1)));

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?defaultName=test"))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT2)));
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

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true"))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT1)));
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

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true"))
                .andExpect(status().isOk())
                .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT1)));
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

        mvc.perform(get(URL_TEMPLATE + "/reports/" + REPORT_UUID + "?withElements=true"))
                .andExpect(status().isOk());
    }

    private void testImported(String report1Id, String reportConcat2) throws Exception {
        String res = mvc.perform(get(URL_TEMPLATE + "/reports/" + report1Id + "?withElements=true&severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        mvc.perform(get(URL_TEMPLATE + "/reports/" + report1Id + "?withElements=true&severityLevels=INFO&severityLevels=TRACE&severityLevels=ERROR"))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(reportConcat2)));
    }

    private void insertReport(String reportsId, String content) throws Exception {
        mvc.perform(put(URL_TEMPLATE + "/reports/" + reportsId)
            .content(content)
            .contentType(APPLICATION_JSON))
            .andExpect(status().isOk());
    }

}
