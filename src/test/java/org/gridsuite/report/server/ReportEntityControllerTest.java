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
import lombok.SneakyThrows;
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
public class ReportEntityControllerTest {

    public static final String URL_TEMPLATE = "/" + ReportApi.API_VERSION + "/reports/";
    @Autowired
    private MockMvc mvc;

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

    private static final String DEFAULT_EMPTY_REPORT1 = "/defaultEmpty1.json";

    private static final String DEFAULT_EMPTY_REPORT2 = "/defaultEmpty2.json";

    private static final String REPORT_SIMULATORS = "/reportSimulators.json";
    private static final String EXPECTED_DELETED_SIMULATORS = "/expectedDeletedSimulators.json";

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

        String expectedReport = toString(EXPECTED_SINGLE_REPORT);
        mvc.perform(get(URL_TEMPLATE))
            .andExpect(status().isOk())
            .andExpect(content().json(expectedReport));

        mvc.perform(get(URL_TEMPLATE + REPORT_UUID))
            .andExpect(status().isOk())
            .andExpect(content().json(expectedReport));

        insertReport(REPORT_UUID, toString(REPORT_TWO));

        testImported(REPORT_UUID, REPORT_CONCAT);

        insertReport(REPORT_UUID, toString(REPORT_ONE));
        testImported(REPORT_UUID, REPORT_CONCAT2);

        mvc.perform(delete(URL_TEMPLATE + REPORT_UUID)).andExpect(status().isOk());
        mvc.perform(delete(URL_TEMPLATE + REPORT_UUID)).andExpect(status().isNotFound());

        mvc.perform(get(URL_TEMPLATE + REPORT_UUID)).andExpect(status().isNotFound());
    }

    @SneakyThrows
    @Test
    public void testDefaultEmptyReport() {
        mvc.perform(get(URL_TEMPLATE + REPORT_UUID + "?errorOnReportNotFound=false"))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT1)));

        mvc.perform(get(URL_TEMPLATE + REPORT_UUID + "?errorOnReportNotFound=false&defaultName=test"))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(DEFAULT_EMPTY_REPORT2)));
    }

    @Test
    public void testDeleteSubreports() throws Exception {
        String testReport1 = toString(REPORT_SIMULATORS);
        insertReport(REPORT_UUID, testReport1);
        Map reportsKeys = new HashMap<>();
        reportsKeys.put(REPORT_UUID, "LoadFlow");

        mvc.perform(delete("/" + ReportApi.API_VERSION + "/" + "subreports")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reportsKeys)))
            .andExpect(status().isOk())
            .andReturn();

        mvc.perform(get(URL_TEMPLATE))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    private void testImported(String report1Id, String reportConcat2) throws Exception {
        mvc.perform(get(URL_TEMPLATE + report1Id))
            .andExpect(status().isOk())
            .andExpect(content().json(toString(reportConcat2)));
    }

    private void insertReport(String reportsId, String content) throws Exception {
        mvc.perform(put(URL_TEMPLATE + reportsId)
            .content(content)
            .contentType(APPLICATION_JSON))
            .andExpect(status().isOk());
    }

}
